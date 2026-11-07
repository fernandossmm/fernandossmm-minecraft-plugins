// Shared by the end-to-end suites: a real Paper server (./gradlew runE2eServer), bots as players (mineflayer,
// through ViaBackwards), vanilla commands and the harness plugin over RCON, and the checks' bookkeeping.

import { spawn, execSync } from 'node:child_process'
import { createWriteStream, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import net from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import mineflayer from 'mineflayer'

export const HERE = path.dirname(fileURLToPath(import.meta.url))
export const ROOT = path.resolve(HERE, '../..')
export const RUN = path.join(HERE, 'run')
export const END = 'minecraft:the_end'
// E2E_PORT, E2E_RCON_PORT and E2E_WEB_PORT (BlueMap's web server) let two checkouts run their suites at once.
export const PORT = Number(process.env.E2E_PORT ?? 25566)
const RCON_PORT = Number(process.env.E2E_RCON_PORT ?? 25575)
export const WEB_PORT = Number(process.env.E2E_WEB_PORT ?? 8100)
const RCON_PASSWORD = 'e2e'
export const BOT_VERSION = '26.1'
const MOVEMENT_PACKETS = new Set(['position', 'position_look', 'look', 'flying'])
// By absolute path: Windows may be set not to run programs from the current directory.
export const GRADLEW = path.join(ROOT, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')

// ---------------------------------------------------------------------------------------------- checks

export const results = []

export function check (name, ok, detail = '') {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok || !detail ? '' : `\n      ${detail}`}`)
}

export const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

export async function waitFor (description, probe, timeoutMs = 60_000, intervalMs = 1000) {
  const deadline = Date.now() + timeoutMs
  let last
  while (Date.now() < deadline) {
    last = await probe()
    if (last) return last
    await sleep(intervalMs)
  }
  throw new Error(`timed out waiting for ${description}`)
}

// ---------------------------------------------------------------------------------------------- server

let current

/** The running server: its output so far. */
export const server = () => current

/**
 * A fresh world in dev/e2e/run/, and each plugin's config.yml as given (plugin folder name → entries).
 * SkinsRestorer looks every joining player's skin up online, before the login completes; slow or rate-limited,
 * that runs past the 30 s login limit and the bot is kicked. Only a suite that needs skins turns it on.
 */
export function prepareRun (configs, { skins = false } = {}) {
  // Retried: after an abandoned suite, the killed server may still hold files for a moment (Windows).
  rmSync(RUN, { recursive: true, force: true, maxRetries: 20, retryDelay: 500 })
  mkdirSync(RUN, { recursive: true })
  writeFileSync(path.join(RUN, 'eula.txt'), 'eula=true\n')
  writeFileSync(path.join(RUN, 'server.properties'), [
    'online-mode=false',
    // New servers have the whitelist on since 26.3.
    'white-list=false',
    `server-port=${PORT}`,
    'enable-rcon=true',
    `rcon.port=${RCON_PORT}`,
    `rcon.password=${RCON_PASSWORD}`,
    'spawn-protection=0',
    'difficulty=normal',
    'spawn-monsters=false',
    'view-distance=6',
    'simulation-distance=6',
    'allow-flight=true',
    'level-seed=e2e',
    ''
  ].join('\n'))
  if (!skins) writeConfig('SkinsRestorer', { advanced: '\n    disableOnJoinSkins: true' })
  // GriefPrevention turns away a player who logged in less than a minute ago; suites log bots out and back in.
  writeConfig('GriefPreventionData', { GriefPrevention: '\n  Spam:\n    LoginCooldownSeconds: 0' })
  for (const [plugin, entries] of Object.entries(configs)) writeConfig(plugin, entries)
  if (WEB_PORT !== 8100) {
    mkdirSync(path.join(RUN, 'plugins', 'BlueMap'), { recursive: true })
    writeFileSync(path.join(RUN, 'plugins', 'BlueMap', 'webserver.conf'), `port: ${WEB_PORT}\n`)
  }
}

/** Replaces a plugin's config.yml; keys not given take the plugin's defaults. */
export function writeConfig (plugin, entries) {
  const dir = path.join(RUN, 'plugins', plugin)
  mkdirSync(dir, { recursive: true })
  writeFileSync(path.join(dir, 'config.yml'), Object.entries(entries).map(([k, v]) => `${k}: ${v}`).join('\n') + '\n')
}

/** Starts the server; `task` picks a server set up for one suite (dev/build.gradle.kts). */
export async function startServer (label, { task = 'runE2eServer' } = {}) {
  const log = createWriteStream(path.join(HERE, `server-${label}.log`))
  const child = spawn(`"${GRADLEW}"`, [task, '--console=plain'], { cwd: ROOT, shell: true })
  let output = ''
  const onData = data => { output += data.toString(); log.write(data) }
  child.stdout.on('data', onData)
  child.stderr.on('data', onData)
  current = { child, output: () => output, log }
  await waitFor('the server to start', () => /Done \([\d.]+s\)!/.test(output) || child.exitCode !== null, 300_000, 500)
  if (child.exitCode !== null) throw new Error(`the server exited during startup; see server-${label}.log`)
  await rcon.connect()
}

export async function stopServer () {
  for (const name of [...bots.keys()]) await quit(name)
  rcon.close()
  const { child } = current
  child.stdin.write('stop\n')
  try {
    await waitFor('the server to stop', () => child.exitCode !== null, 60_000, 500)
  } catch {
    killTree(child)
  }
  current.log.end()
  current = undefined
}

/** After a failure: drops every bot and kills the server. */
export function abandon () {
  for (const bot of bots.values()) bot.end()
  bots.clear()
  rcon.close()
  if (current) killTree(current.child)
  current = undefined
}

function killTree (child) {
  try {
    if (process.platform === 'win32') execSync(`taskkill /pid ${child.pid} /T /F`, { stdio: 'ignore' })
    else child.kill('SIGKILL')
  } catch {}
  // The server itself is forked by the Gradle daemon, not by gradlew, so it outlives the tree above; it is
  // found by its port. Left running, it keeps run/ locked and the port taken for the next suite.
  try {
    if (process.platform === 'win32') {
      execSync(`powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort ${PORT} -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }"`, { stdio: 'ignore' })
    } else {
      execSync(`lsof -ti tcp:${PORT} -sTCP:LISTEN | xargs -r kill -9`, { stdio: 'ignore' })
    }
  } catch {}
}

// ---------------------------------------------------------------------------------------------- rcon

const rcon = {
  socket: undefined,
  buffer: Buffer.alloc(0),
  pending: new Map(),
  nextId: 1,

  async connect () {
    // RCON may start listening a moment after the server reports it is done.
    this.socket = await waitFor('RCON', () => new Promise(resolve => {
      const s = net.connect(RCON_PORT, '127.0.0.1', () => resolve(s))
      s.once('error', () => resolve(null))
    }), 30_000, 500)
    this.buffer = Buffer.alloc(0)
    this.socket.on('data', data => this.receive(data))
    await this.send(3, RCON_PASSWORD)
  },

  close () {
    this.socket?.destroy()
    this.socket = undefined
  },

  receive (data) {
    this.buffer = Buffer.concat([this.buffer, data])
    while (this.buffer.length >= 4) {
      const length = this.buffer.readInt32LE(0)
      if (this.buffer.length < 4 + length) return
      const id = this.buffer.readInt32LE(4)
      const body = this.buffer.toString('utf8', 12, 4 + length - 2)
      this.buffer = this.buffer.subarray(4 + length)
      const resolve = this.pending.get(id)
      if (resolve) { this.pending.delete(id); resolve(body) }
    }
  },

  send (type, body) {
    const id = this.nextId++
    const payload = Buffer.from(body, 'utf8')
    const packet = Buffer.alloc(14 + payload.length)
    packet.writeInt32LE(10 + payload.length, 0)
    packet.writeInt32LE(id, 4)
    packet.writeInt32LE(type, 8)
    payload.copy(packet, 12)
    return new Promise(resolve => {
      this.pending.set(id, resolve)
      this.socket.write(packet)
    })
  }
}

/** Runs a console command and returns its output. */
export const command = cmd => rcon.send(2, cmd)
export const inEnd = cmd => command(`execute in ${END} run ${cmd}`)

// ---------------------------------------------------------------------------------------------- bots

export const bots = new Map()

/**
 * A bot joins and spawns; it takes no damage and never gets hungry. A login that ends or stalls without a spawn (seen right after a restart, while the
 * server is still busy) is tried once more, instead of waiting for ever.
 */
export async function join (name, attempts = 2) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, version: BOT_VERSION, auth: 'offline' })
  // Since 26.3, the server kicks the bots for invalid movement on the first movement packet that ViaBackwards
  // translates, even with physics off. The bots never need to move themselves (commands teleport them), so they
  // send none.
  const write = bot._client.write.bind(bot._client)
  bot._client.write = (packet, params) => { if (!MOVEMENT_PACKETS.has(packet)) write(packet, params) }
  bot.chatLog = []
  bot.on('messagestr', message => bot.chatLog.push(message))
  try {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`${name} did not spawn within 60 s`)), 60_000)
      bot.once('spawn', () => { clearTimeout(timer); resolve() })
      bot.once('kicked', reason => { clearTimeout(timer); reject(new Error(`${name} was kicked: ${JSON.stringify(reason)}`)) })
      bot.once('error', error => { clearTimeout(timer); reject(error) })
      bot.once('end', reason => { clearTimeout(timer); reject(new Error(`${name}'s connection ended before spawning: ${reason}`)) })
    })
  } catch (error) {
    bot.removeAllListeners()
    bot.end()
    if (attempts <= 1) throw error
    console.log(`      ${error.message}; joining again`)
    await sleep(5000)
    return join(name, attempts - 1)
  }
  bots.set(name, bot)
  await command(`effect give ${name} minecraft:resistance infinite 4 true`)
  await command(`effect give ${name} minecraft:saturation infinite 0 true`)
  return bot
}

export async function quit (name) {
  const bot = bots.get(name)
  bots.delete(name)
  await new Promise(resolve => { bot.once('end', resolve); bot.quit() })
}

export async function chatContains (name, text, timeoutMs = 10_000) {
  return waitFor(`${name} to read "${text}"`, () => bots.get(name).chatLog.some(m => m.includes(text)), timeoutMs, 250)
    .then(() => true, () => false)
}

/** A bot runs a command as a player and returns what it was told in reply. */
export async function asPlayer (name, cmd) {
  const bot = bots.get(name)
  const before = bot.chatLog.length
  bot.chat(cmd)
  await sleep(1500)
  return bot.chatLog.slice(before)
}
