// End-to-end suite for BlueMap SkinsRestorer Heads: bots join an offline-mode server where SkinsRestorer gives
// jeb_ (a real account's name) its skin and Zx9qNoSkin none; the heads BlueMap shows are read back from every
// map's files and from BlueMap's web server, and compared pixel by pixel with the skin each bot has in game.

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { RUN, WEB_PORT, check, command, join, prepareRun, server, sleep, startServer, stopServer, waitFor } from './lib.mjs'

const WEB = `http://127.0.0.1:${WEB_PORT}/`
const SKINNED = 'jeb_'
const PLAIN = 'Zx9qNoSkin'
// Skin pixels sampled, and where BlueMap's 48x48 head shows them: the hat layer is drawn 6x over the whole
// head, the face 5x from (4, 4). Head pixel (11, 11) shows skin pixel (1, 1) of both; (23, 23) shows (3, 3).
const SAMPLES = [{ skin: 1, head: 11 }, { skin: 3, head: 23 }]

const json = async cmd => JSON.parse(await command(cmd))
const pixel = async (file, x, y) => json(`bbmtest pixel ${x} ${y} ${file}`)

function blueMapAcceptsDownload () {
  const dir = path.join(RUN, 'plugins', 'BlueMap')
  mkdirSync(dir, { recursive: true })
  writeFileSync(path.join(dir, 'core.conf'), 'accept-download: true\nrender-thread-count: 1\n')
}

const uuidOf = name => JSON.parse(readFileSync(path.join(RUN, 'usercache.json'), 'utf8')).find(e => e.name === name)?.uuid

/** Java's UUID.hashCode(), which the client uses to pick a default skin. */
function javaHash (uuid) {
  const hex = uuid.replace(/-/g, '')
  const hilo = BigInt('0x' + hex.slice(0, 16)) ^ BigInt('0x' + hex.slice(16))
  return Number(BigInt.asIntN(32, (hilo >> 32n) ^ hilo))
}

const DEFAULT_SKINS = ['slim/alex', 'slim/ari', 'slim/efe', 'slim/kai', 'slim/makena', 'slim/noor', 'slim/steve', 'slim/sunny', 'slim/zuri',
  'wide/alex', 'wide/ari', 'wide/efe', 'wide/kai', 'wide/makena', 'wide/noor', 'wide/steve', 'wide/sunny', 'wide/zuri']
const defaultSkin = uuid => `jar:assets/minecraft/textures/entity/player/${DEFAULT_SKINS[((javaHash(uuid) % 18) + 18) % 18]}.png`

const maps = () => readdirSync(path.join(RUN, 'bluemap', 'web', 'maps')).filter(m => existsSync(path.join(RUN, 'bluemap', 'web', 'maps', m, 'settings.json')))
const headFile = (map, uuid) => `bluemap/web/maps/${map}/assets/playerheads/${uuid}.png`

/** The head pixel a skin's face and hat pixels give: the hat where it is opaque, the face where it is clear. */
function expected (face, hat) {
  const alpha = parseInt(hat.slice(0, 2), 16)
  return alpha === 255 ? hat : alpha === 0 ? 'ff' + face.slice(2) : null
}

/**
 * For each sample, the pixel the head should have, from the skin the player has in game (Mojang's file), as the
 * game draws it: without the hat layer when the game ignores it (an old skin's opaque hat).
 */
async function fromProfile (name) {
  const out = []
  for (const s of SAMPLES) {
    const skin = await json(`bbmtest skin ${name} ${s.skin} ${s.skin}`)
    const want = !skin.face || !skin.hat ? null : skin.hatShown ? expected(skin.face, skin.hat) : 'ff' + skin.face.slice(2)
    out.push({ ...s, url: skin.url, height: skin.height, hatShown: skin.hatShown, want })
  }
  return out
}

async function fromJar (file) {
  const out = []
  for (const s of SAMPLES) {
    const face = (await pixel(file, 8 + s.skin, 8 + s.skin)).argb
    const hat = (await pixel(file, 40 + s.skin, 8 + s.skin)).argb
    out.push({ ...s, want: face && hat ? expected(face, hat) : null })
  }
  return out
}

/** Whether every map has this player's head, 48x48, with the expected pixels. */
async function headsMatch (uuid, want) {
  for (const map of maps()) {
    if (!existsSync(path.join(RUN, headFile(map, uuid)))) return false
    for (const s of want) {
      const p = await pixel(headFile(map, uuid), s.head, s.head)
      if (p.width !== 48 || (s.want !== null && p.argb !== s.want)) return false
    }
  }
  return true
}

async function describe (uuid, want) {
  const seen = []
  for (const map of maps()) {
    const file = headFile(map, uuid)
    if (!existsSync(path.join(RUN, file))) { seen.push(`${map}: no head`); continue }
    for (const s of want) seen.push(`${map}@${s.head}: ${JSON.stringify(await pixel(file, s.head, s.head))} want ${s.want}`)
  }
  return seen.join('; ')
}

export async function run () {
  prepareRun({}, { skins: true })
  blueMapAcceptsDownload()
  await startServer('bmsh-1')
  const logStart = server().output().length

  // BlueMap downloads Minecraft's client jar on its first start; the default skins are read from it.
  await waitFor('BlueMap\'s maps', () => existsSync(path.join(RUN, 'bluemap', 'web', 'maps')) && maps().length > 0, 180_000, 1000)
  await waitFor('the client jar', () => readdirSync(path.join(RUN, 'bluemap')).some(f => /^minecraft-client-.*\.jar$/.test(f)), 180_000, 1000)
  console.log(`      maps: ${maps().join(', ')}`)

  console.log('\n--- joining')
  await join(SKINNED)
  await join(PLAIN)
  await sleep(5000) // SkinsRestorer applies skins shortly after joining
  const skinned = uuidOf(SKINNED)
  const plain = uuidOf(PLAIN)

  const skinnedWant = await fromProfile(SKINNED)
  check('SkinsRestorer gave jeb_ a skin (the fixture this suite relies on)', !!skinnedWant[0].url, JSON.stringify(skinnedWant))
  const okSkinned = await waitFor('jeb_\'s head', () => headsMatch(skinned, skinnedWant), 30_000, 1000).then(() => true, () => false)
  check('every map shows jeb_\'s head drawn from the skin SkinsRestorer applied', okSkinned, await describe(skinned, skinnedWant))

  const plainProfile = await json(`bbmtest skin ${PLAIN} 1 1`)
  check('Zx9qNoSkin has no skin in game (the fixture)', !plainProfile.url, JSON.stringify(plainProfile))
  const plainWant = await fromJar(defaultSkin(plain))
  const okPlain = await waitFor('Zx9qNoSkin\'s head', () => headsMatch(plain, plainWant), 30_000, 1000).then(() => true, () => false)
  check('a player without a skin gets the game\'s default skin for their UUID', okPlain, `${defaultSkin(plain)}: ${await describe(plain, plainWant)}`)

  const served = await fetch(`${WEB}maps/${maps()[0]}/assets/playerheads/${skinned}.png`)
  check('BlueMap\'s web server serves the head', served.ok && served.headers.get('content-type')?.includes('png'), `${served.status} ${served.headers.get('content-type')}`)

  console.log('\n--- a skin changed while playing')
  console.log(`      ${(await command(`skin set Notch ${PLAIN}`)).replace(/\s+/g, ' ').slice(0, 160)}`)
  const changed = await waitFor('the new skin in game', async () => (await json(`bbmtest skin ${PLAIN} 1 1`)).url, 20_000, 1000).then(() => true, () => false)
  check('SkinsRestorer changed Zx9qNoSkin\'s skin (the fixture)', changed)
  const newWant = await fromProfile(PLAIN)
  check('Notch\'s skin is an old 64x32 one whose opaque hat the game ignores (the fixture)',
    newWant[0].height === 32 && newWant[0].hatShown === false, JSON.stringify(newWant))
  const okChanged = await waitFor('the new head', () => headsMatch(plain, newWant), 20_000, 1000).then(() => true, () => false)
  check('the head follows within seconds, in every map, showing the face as the game does', okChanged, await describe(plain, newWant))

  console.log('\n--- BlueMap reloads')
  await command('bluemap reload')
  await sleep(15_000)
  const okReload = await headsMatch(skinned, skinnedWant) && await headsMatch(plain, newWant)
  check('after a BlueMap reload the heads are still the in-game skins', okReload, await describe(plain, newWant))

  const log = server().output().slice(logStart)
  const problems = log.split('\n').filter(l => /BlueMapSkinsRestorerHeads/.test(l) && /WARN|ERROR|Exception/.test(l))
  check('the plugin logged no warnings or errors', problems.length === 0, problems.slice(0, 5).join('\n'))

  await stopServer()
}
