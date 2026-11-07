// End-to-end suite for DiscordSRV fail-closed: a real Paper server and the real DiscordSRV, in each state the plugin
// must handle, and a bot trying to log in each time. No Discord bot is needed: DiscordSRV's running states are reached
// with a token of the right shape that Discord rejects, or that never reaches Discord. The third part needs internet
// access (Discord has to reject the token). About five minutes.

import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import mineflayer from 'mineflayer'
import { BOT_VERSION, PORT, RUN, check, command, prepareRun, server, sleep, startServer, stopServer, waitFor } from './lib.mjs'

const WITHOUT_DISCORDSRV = { task: 'runE2eFailClosedServer' }
const WITH_DISCORDSRV = { task: 'runE2eFailClosedDiscordServer' }
// Our default message, and DiscordSRV's own while it cannot reach Discord.
const OURS = 'Joining is paused: the Discord account check is offline.'
const DISCORDSRV_STARTING = 'still connecting to Discord'
const LINKING_OFF = 'DiscordSRV\'s "Require linked account to play" is not enabled'
// 72 characters, the shape of a real bot token; DiscordSRV disables itself on anything shorter than 59.
const FAKE_TOKEN = `${'A'.repeat(26)}.${'B'.repeat(6)}.${'C'.repeat(38)}`
const DSRV = path.join(RUN, 'plugins', 'DiscordSRV')

export async function run () {
  console.log('\n--- without DiscordSRV')
  prepareRun({})
  await startServer('dsfc-none', WITHOUT_DISCORDSRV)
  await expectLog('the startup log says the server is closed', 'Every login is refused: DiscordSRV is not installed')
  await expectRefusedByUs('Alex', 'DiscordSRV is not installed')
  // DiscordSRV's own bypass list has these names by default; ours has none.
  await expectRefusedByUs('Scarsz', 'DiscordSRV is not installed')
  await stopServer()

  console.log('\n--- DiscordSRV without a bot token: it disables itself')
  prepareRun({})
  discordSrvConfig({ linking: true })
  await startServer('dsfc-notoken', WITH_DISCORDSRV)
  await expectLog('DiscordSRV disabled itself for the missing token', 'No bot token has been set in the config')
  await expectLog('the plugin logs that DiscordSRV was disabled', 'DiscordSRV was disabled: every login is refused')
  await expectRefusedByUs('Alex', 'DiscordSRV is not enabled')
  await stopServer()

  console.log('\n--- DiscordSRV with a token Discord rejects: it disables itself after trying')
  prepareRun({})
  discordSrvConfig({ token: FAKE_TOKEN, linking: true })
  await startServer('dsfc-badtoken', WITH_DISCORDSRV)
  await expectLog('Discord rejected the token and DiscordSRV disabled itself', 'DiscordSRV was disabled: every login is refused', 90_000)
  await expectRefusedByUs('Alex', 'DiscordSRV is not enabled')
  await stopServer()

  console.log('\n--- DiscordSRV running but unable to reach Discord: its own check decides')
  prepareRun({})
  // A proxy on a closed port: DiscordSRV stays enabled, never ready, and refuses logins itself.
  discordSrvConfig({ token: FAKE_TOKEN, proxy: true, linking: true })
  await startServer('dsfc-running', WITH_DISCORDSRV)
  await expectLog('the startup log leaves logins to DiscordSRV', 'DiscordSRV requires a linked account; logins are left to it')
  let result = await attempt('Alex')
  check('the login reaches DiscordSRV, which refuses it while not connected', result.refused && result.reason.includes(DISCORDSRV_STARTING),
    JSON.stringify(result))
  check('the plugin did not refuse it', !server().output().includes('Refused the login of Alex'))
  check('DiscordSRV is still enabled', !server().output().includes('DiscordSRV was disabled'))

  await stopServer()

  console.log('\n--- DiscordSRV running with its requirement off; the setting that counts is the one it holds')
  prepareRun({})
  discordSrvConfig({ token: FAKE_TOKEN, proxy: true, linking: false })
  await startServer('dsfc-linkoff', WITH_DISCORDSRV)
  await expectLog('the startup log says the server is closed', `Every login is refused: ${LINKING_OFF}`)
  await expectRefusedByUs('Steve', LINKING_OFF)
  // linking.yml switched on, but DiscordSRV reads it only when it starts or reloads: its check is still off.
  linking(true)
  await expectRefusedByUs('Kai', LINKING_OFF)
  await command('discord reload')
  await sleep(3000)
  result = await attempt('Kai')
  check('after DiscordSRV reloads it, logins go to DiscordSRV', result.refused && result.reason.includes(DISCORDSRV_STARTING),
    JSON.stringify(result))
  await stopServer()
}

/** DiscordSRV's config.yml and linking.yml before its first start; the keys left out take DiscordSRV's defaults. */
function discordSrvConfig ({ token, proxy = false, linking: requireLink }) {
  mkdirSync(DSRV, { recursive: true })
  const config = []
  if (token) config.push(`BotToken: "${token}"`)
  if (proxy) config.push('ProxyHost: "127.0.0.1"', 'ProxyPort: 9')
  if (config.length > 0) writeFileSync(path.join(DSRV, 'config.yml'), config.join('\n') + '\n')
  linking(requireLink)
}

function linking (enabled) {
  writeFileSync(path.join(DSRV, 'linking.yml'), [
    'Require linked account to play:',
    `  Enabled: ${enabled}`,
    '  Bypass names: [Scarsz, Vankka]',
    ''
  ].join('\n'))
}

async function expectLog (name, text, timeoutMs = 30_000) {
  const found = await waitFor(name, () => server().output().includes(text), timeoutMs, 500).then(() => true, () => false)
  check(name, found, `"${text}" not in the server log`)
}

async function expectRefusedByUs (name, reason) {
  const result = await attempt(name)
  check(`${name} is refused with the plugin's message`, result.refused && result.reason.includes(OURS), JSON.stringify(result))
  await expectLog(`the log names ${name} and the reason`, `Refused the login of ${name}: ${reason}`, 5000)
}

/** A bot tries to log in: { refused, reason } when turned away, { refused: false } when it spawns (and leaves). */
async function attempt (name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, version: BOT_VERSION, auth: 'offline' })
  const result = await new Promise(resolve => {
    const timer = setTimeout(() => resolve({ refused: false, reason: 'no answer within 60 s' }), 60_000)
    const done = r => { clearTimeout(timer); resolve(r) }
    bot.once('spawn', () => done({ refused: false }))
    bot.once('kicked', reason => done({ refused: true, reason: text(reason) }))
    bot.once('error', error => done({ refused: false, reason: `error: ${error.message}` }))
    bot.once('end', reason => done({ refused: false, reason: `ended: ${reason}` }))
  })
  bot.removeAllListeners()
  bot.end()
  await sleep(1000)
  return result
}

/** The plain text of a kick reason, however mineflayer hands it over (JSON text, a component, or a string). */
function text (reason) {
  let value = reason
  if (typeof value === 'string') {
    try { value = JSON.parse(value) } catch { return value }
  }
  const parts = []
  const walk = node => {
    if (node == null) return
    if (typeof node === 'string') { parts.push(node); return }
    if (Array.isArray(node)) { node.forEach(walk); return }
    if (typeof node === 'object') {
      if ('text' in node) walk(node.text)
      if ('' in node) walk(node[''])
      if ('extra' in node) walk(node.extra)
      if ('value' in node) walk(node.value)
      if ('translate' in node && !('text' in node)) parts.push(node.translate)
    }
  }
  walk(value)
  return parts.join('')
}
