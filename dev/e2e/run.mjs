// End-to-end suite for Multiplayer Dragon Rebirth: a real Paper server (./gradlew runE2eServer), bots as
// players (mineflayer, through ViaBackwards), vanilla commands and the harness plugin over RCON.
//
//   cd dev/e2e && npm ci && npm test
//
// Starts from a fresh world in dev/e2e/run/ every time. Takes about ten minutes. Exit code 0 when every
// check passes.

import { spawn, execSync } from 'node:child_process'
import { createWriteStream, existsSync, mkdirSync, rmSync, writeFileSync, readFileSync } from 'node:fs'
import net from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import mineflayer from 'mineflayer'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '../..')
const RUN = path.join(HERE, 'run')
const PLUGIN_DIR = path.join(RUN, 'plugins', 'MultiplayerDragonRebirth')
const PORT = 25566
const RCON_PORT = 25575
const RCON_PASSWORD = 'e2e'
const COUNTDOWN = 3
const BOT_VERSION = '26.1'
const END = 'minecraft:the_end'
// By absolute path: Windows may be set not to run programs from the current directory.
const GRADLEW = path.join(ROOT, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')

// ---------------------------------------------------------------------------------------------- checks

const results = []

function check (name, ok, detail = '') {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok || !detail ? '' : `\n      ${detail}`}`)
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

async function waitFor (description, probe, timeoutMs = 60_000, intervalMs = 1000) {
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

let server

function prepareRun () {
  rmSync(RUN, { recursive: true, force: true })
  mkdirSync(PLUGIN_DIR, { recursive: true })
  writeFileSync(path.join(RUN, 'eula.txt'), 'eula=true\n')
  writeFileSync(path.join(RUN, 'server.properties'), [
    'online-mode=false',
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
  writeConfig({})
}

/** config.yml with defaults, except a short countdown, plus overrides. */
function writeConfig (overrides) {
  const config = { 'countdown-seconds': COUNTDOWN, ...overrides }
  writeFileSync(path.join(PLUGIN_DIR, 'config.yml'),
    Object.entries(config).map(([k, v]) => `${k}: ${v}`).join('\n') + '\n')
}

async function startServer (label) {
  const log = createWriteStream(path.join(HERE, `server-${label}.log`))
  const child = spawn(`"${GRADLEW}"`, ['runE2eServer', '--console=plain'], { cwd: ROOT, shell: true })
  let output = ''
  const onData = data => { output += data.toString(); log.write(data) }
  child.stdout.on('data', onData)
  child.stderr.on('data', onData)
  server = { child, output: () => output, log }
  await waitFor('the server to start', () => /Done \([\d.]+s\)!/.test(output) || child.exitCode !== null, 300_000, 500)
  if (child.exitCode !== null) throw new Error(`the server exited during startup; see server-${label}.log`)
  await rcon.connect()
}

async function stopServer () {
  rcon.close()
  const { child } = server
  child.stdin.write('stop\n')
  try {
    await waitFor('the server to stop', () => child.exitCode !== null, 60_000, 500)
  } catch {
    killTree(child)
  }
  server.log.end()
  server = undefined
}

function killTree (child) {
  try {
    if (process.platform === 'win32') execSync(`taskkill /pid ${child.pid} /T /F`, { stdio: 'ignore' })
    else child.kill('SIGKILL')
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

const command = cmd => rcon.send(2, cmd)
const state = async () => JSON.parse(await command('mdrtest state'))
const inEnd = cmd => command(`execute in ${END} run ${cmd}`)

// ---------------------------------------------------------------------------------------------- bots

const bots = new Map()

async function join (name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, version: BOT_VERSION, auth: 'offline' })
  bot.chatLog = []
  bot.on('messagestr', message => bot.chatLog.push(message))
  await new Promise((resolve, reject) => {
    bot.once('spawn', resolve)
    bot.once('kicked', reason => reject(new Error(`${name} was kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
  })
  bots.set(name, bot)
  // Survives the dragon: no damage, never hungry.
  await command(`effect give ${name} minecraft:resistance infinite 4 true`)
  await command(`effect give ${name} minecraft:saturation infinite 0 true`)
  return bot
}

async function quit (name) {
  const bot = bots.get(name)
  bots.delete(name)
  await new Promise(resolve => { bot.once('end', resolve); bot.quit() })
}

/**
 * Into a closed obsidian box beside the island, where real players would be: close enough that the dragon
 * keeps being simulated, and the dragon can neither break obsidian nor knock bots into the void.
 */
const enterEnd = name => inEnd(`tp ${name} 28 82 0`)
const leaveEnd = name => command(`execute in minecraft:overworld run tp ${name} 0 100 0`)

async function buildShelter () {
  await inEnd('forceload add 24 -4 32 4')
  await inEnd('fill 24 80 -4 32 86 4 minecraft:obsidian hollow')
}

// ---------------------------------------------------------------------------------------------- fights

const dragonCommand = (by, amount) =>
  inEnd(`damage @e[type=minecraft:ender_dragon,limit=1] ${amount} minecraft:player_attack by ${by}`)

async function waitForDragon (description) {
  return waitFor(description, async () => {
    const s = await state()
    return s.dragon && s.phase === 'NONE' ? s : null
  }, 120_000)
}

/**
 * Kills the dragon (by a player, so the kill counts for them) and returns the state at the end of the death
 * animation: vanilla flies the dying dragon to the portal, plays a ten-second animation, drops the
 * experience during its last part, and removes the dragon and forms the egg at its end. Taken then, before
 * orbs start falling into the exit portal that has just opened.
 */
async function killDragon (by) {
  await command('mdrtest clearorbs')
  await dragonCommand(by, 1000)
  const started = Date.now()
  let s
  while (Date.now() - started < 90_000) {
    await sleep(500)
    s = await state()
    if (!s.dragon && s.previouslyKilled) return s
  }
  throw new Error(`the death animation did not finish: ${JSON.stringify(s)}`)
}

async function chatContains (name, text) {
  return waitFor(`${name} to read "${text}"`, () => bots.get(name).chatLog.some(m => m.includes(text)), 10_000, 250)
    .then(() => true, () => false)
}

const checkLine = async (player, expected) => {
  const reply = await command(`mdr check ${player}`)
  check(`/mdr check ${player} says "${expected}"`, reply.includes(expected), reply)
}

// ---------------------------------------------------------------------------------------------- scenarios

async function phaseFirstRun () {
  console.log('\n--- first run marks players who joined before the plugin')
  prepareRun()
  await startServer('1')
  await join('Old')
  await quit('Old')
  await stopServer()
  rmSync(path.join(PLUGIN_DIR, 'players.json'))
  await startServer('2')
  await checkLine('Old', 'has fought the dragon: existing player')
  check('the first run is logged', server.output().includes('First run: 1 players who have joined this server before'))
}

async function phaseFights () {
  await buildShelter()
  await join('Alice')
  await join('Bob')
  await checkLine('Alice', 'is a newcomer')

  console.log('\n--- the world\'s first dragon')
  await enterEnd('Alice')
  let s = await waitFor('the original dragon', async () => { const st = await state(); return st.dragon ? st : null })
  await sleep(COUNTDOWN * 1000 + 2000)
  s = await state()
  check('no summon over a fresh world\'s own first dragon', s.summonEvents === 0, JSON.stringify(s))
  check('the first dragon has its flag down, as vanilla starts', s.previouslyKilled === false)
  await dragonCommand('Alice', 5)
  s = await killDragon('Alice')
  check('first dragon: first-time kill event names Alice', s.killEvents === 1 && s.lastKillNewcomers.join() === 'Alice', JSON.stringify(s))
  check('first dragon: 12,000 experience', s.lastDeathExp === 12_000 && s.xp > 10_000, `death event ${s.lastDeathExp}, orbs ${s.xp}`)
  check('first dragon: the egg is on the portal', s.eggBlocks.length === 1, JSON.stringify(s.eggBlocks))
  check('first dragon: the flag is up again', s.previouslyKilled === true)
  check('the kill message reaches players', await chatContains('Bob', 'The dragon has fallen! Its egg and power await.'))
  await checkLine('Alice', 'has fought the dragon: kill')

  console.log('\n--- crystal positions match vanilla')
  const portal = JSON.parse(await command('mdrtest portal'))
  console.log('      exit portal: ' + JSON.stringify(portal))
  // Checked later, once the fight is idle again, with mdrtest vanilla-respawn.

  console.log('\n--- a newcomer summons; a kill by a veteran alone is not first-time')
  await enterEnd('Bob')
  check('the countdown is announced server-wide', await chatContains('Alice', `A dragon rises for Bob in ${COUNTDOWN} seconds.`))
  s = await waitFor('the summoning to start', async () => { const st = await state(); return st.phase !== 'NONE' ? st : null }, 30_000, 250)
  const summoning = s.portalCrystals.filter(c => c.summoning)
  check('four summoning crystals on the exit portal', summoning.length === 4, JSON.stringify(s.portalCrystals))
  check('summoning crystals are invulnerable', summoning.every(c => c.invulnerable))
  await inEnd(`damage @e[type=minecraft:end_crystal,x=${s.portal.x},y=${s.portal.y},z=${s.portal.z},distance=..6,limit=1] 10 minecraft:player_attack by Alice`)
  await sleep(500)
  s = await state()
  check('a player hitting a summoning crystal does not stop the summoning', s.phase !== 'NONE' || s.dragon, JSON.stringify(s))
  s = await waitForDragon('the summoned dragon')
  check('the summoning produced a dragon', s.dragon && s.summonEvents === 1, JSON.stringify(s))
  check('no summoning crystals are left behind', s.portalCrystals.filter(c => c.summoning).length === 0, JSON.stringify(s.portalCrystals))
  s = await killDragon('Alice')
  check('veteran-only kill: no first-time event', s.killEvents === 1, JSON.stringify(s))
  check('veteran-only kill: 500 experience', s.lastDeathExp === 500 && s.xp <= 500, `death event ${s.lastDeathExp}, orbs ${s.xp}`)
  check('veteran-only kill: no new egg', s.eggBlocks.length === 1 && s.eggItems === 0, JSON.stringify(s))
  await checkLine('Bob', 'is a newcomer')

  console.log('\n--- a veteran entering a quiet End summons nothing')
  await leaveEnd('Alice')
  await enterEnd('Alice')
  await sleep(COUNTDOWN * 1000 + 2000)
  s = await state()
  check('no summon for a veteran', s.summonEvents === 1 && !s.dragon && s.phase === 'NONE', JSON.stringify(s))

  console.log('\n--- crystal positions are the vanilla ones')
  s = JSON.parse(await command('mdrtest vanilla-respawn'))
  check('vanilla accepts crystals in the plugin\'s positions', s.phase !== 'NONE', JSON.stringify(s))
  await waitForDragon('the vanilla-respawned dragon')
  s = await killDragon('Alice')
  check('a hand-respawned dragon killed by a veteran is not first-time', s.killEvents === 1 && s.lastDeathExp === 500, JSON.stringify(s))

  console.log('\n--- the newcomer fights their own dragon')
  await leaveEnd('Bob')
  await enterEnd('Bob')
  await waitFor('the summoning to start', async () => (await state()).phase !== 'NONE', 30_000, 250)
  await waitForDragon('Bob\'s dragon')
  await dragonCommand('Bob', 5)
  s = await killDragon('Alice')
  check('newcomer participating: first-time kill for Bob only', s.killEvents === 2 && s.lastKillNewcomers.join() === 'Bob', JSON.stringify(s))
  check('newcomer participating: 12,000 experience', s.lastDeathExp === 12_000 && s.xp > 10_000, `death event ${s.lastDeathExp}, orbs ${s.xp}`)
  check('a second egg: stacked on the first or dropped, never lost',
    s.eggBlocks.length + s.eggItems === 2, JSON.stringify({ eggBlocks: s.eggBlocks, eggItems: s.eggItems }))
  check('a dropped egg never despawns', s.eggItems === 0 || s.eggItemsPermanent)
  check('the flag is up after the first-time kill', s.previouslyKilled === true)
  await checkLine('Bob', 'has fought the dragon: kill')

  console.log('\n--- vanished players and logging in inside the End')
  await join('Carol')
  await command('mdrtest vanish Carol true')
  await enterEnd('Carol')
  await sleep(COUNTDOWN * 1000 + 2000)
  s = await state()
  check('a vanished newcomer summons nothing', s.summonEvents === 2 && s.phase === 'NONE' && !s.dragon, JSON.stringify(s))
  await command('mdrtest vanish Carol false')
  await quit('Carol')
  await join('Carol') // logs in where she left: in the End
  await waitFor('a summoning after Carol logs in inside the End', async () => (await state()).summonEvents === 3, 30_000)
  check('logging in inside the End summons for a newcomer', true)
  await waitForDragon('Carol\'s dragon')
  s = await killDragon('Alice')
  check('Carol did not hit it, so it is not first-time and she stays a newcomer', s.killEvents === 2, JSON.stringify(s))
  await checkLine('Carol', 'is a newcomer')

  console.log('\n--- /mdr reload')
  writeConfig({ 'countdown-seconds': 1 })
  const reload = await command('mdr reload')
  check('/mdr reload answers', reload.includes('Reloaded config.yml.'), reload)
  await leaveEnd('Carol')
  await enterEnd('Carol')
  check('the reloaded countdown is used', await chatContains('Alice', 'A dragon rises for Carol in 1 seconds.'))
  await waitForDragon('Carol\'s second dragon')

  console.log('\n--- egg-per-newcomer: two newcomers in one fight, two eggs')
  writeConfig({ 'egg-per-newcomer': true })
  await command('mdr reload')
  await join('Erin')
  await enterEnd('Erin') // Carol's dragon is already there: she joins that fight
  const before = await state()
  await dragonCommand('Carol', 5)
  await sleep(1000) // a hit within half a second of another is ignored by vanilla, so it would not count
  await dragonCommand('Erin', 5)
  await sleep(1000)
  await killDragon('Alice')
  await sleep(1000) // the extra eggs are stacked a tick after the first one forms
  s = await state()
  check('egg-per-newcomer: both newcomers are named', s.lastKillNewcomers.slice().sort().join() === 'Carol,Erin', JSON.stringify(s.lastKillNewcomers))
  check('egg-per-newcomer: one egg per newcomer, stacked on the portal',
    s.eggBlocks.length === before.eggBlocks.length + 2 && s.eggItems === before.eggItems,
    JSON.stringify({ before: before.eggBlocks, after: s.eggBlocks, eggItems: s.eggItems }))
  check('egg-per-newcomer: the stacked eggs form one column', s.eggBlocks.every((y, i) => i === 0 || y === s.eggBlocks[i - 1] + 1), JSON.stringify(s.eggBlocks))
  writeConfig({})
  await command('mdr reload')

  console.log('\n--- stopping the server during a first-time kill')
  await join('Frank')
  await enterEnd('Frank')
  await waitForDragon('Frank\'s dragon')
  await dragonCommand('Frank', 5)
  await dragonCommand('Frank', 1000)
  await sleep(3000) // the dying dragon is still flying to the portal
  for (const name of [...bots.keys()]) await quit(name)
  await stopServer()
}

async function phaseRestart () {
  await startServer('3')
  const store = JSON.parse(readFileSync(path.join(PLUGIN_DIR, 'players.json'), 'utf8'))
  const names = Object.values(store.players).map(p => p.name)
  check('a kill is recorded when the blow lands, so a stop before the animation ends keeps it', names.includes('Frank'), JSON.stringify(names))
  check('the plugin is enabled after the restart', server.output().includes('Enabling MultiplayerDragonRebirth'))
  const status = await command('mdr status world_the_end')
  check('/mdr status describes the fight', status.includes('world_the_end: dragon'), status)
  await join('Dave')
  await enterEnd('Dave')
  // Whatever vanilla made of the interrupted death, the End must end up with a dragon for the newcomer:
  // the interrupted one, a replacement vanilla spawns for it, or one the plugin summons.
  const s = await waitFor('a dragon for Dave', async () => { const st = await state(); return st.dragon ? st : null }, 120_000)
  check('a newcomer arriving after the restart gets a dragon', s.dragon, JSON.stringify(s))
  await quit('Dave')
  await stopServer()
}

// ---------------------------------------------------------------------------------------------- main

let failed = false
try {
  console.log('Building the plugins and the harness')
  execSync(`"${GRADLEW}" assemble --console=plain -q`, { cwd: ROOT, stdio: 'inherit' })
  await phaseFirstRun()
  await phaseFights()
  await phaseRestart()
} catch (e) {
  failed = true
  console.error(`\nABORTED: ${e.stack ?? e}`)
} finally {
  for (const bot of bots.values()) bot.end()
  if (server) killTree(server.child)
}

const passed = results.filter(r => r.ok).length
console.log(`\n${passed}/${results.length} checks passed${failed ? ', run aborted' : ''}`)
process.exit(failed || passed !== results.length ? 1 : 0)
