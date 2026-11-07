// End-to-end suite for BlueMap Banner Markers: bots name real banners with real name tags; the result is
// read back from BlueMap's web endpoint (what a browser gets), from its files on disk, and from the world.
// SkinsRestorer gives jeb_ (a real account's name) a skin; Zx9qNoSkin has none and gets a default face.

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { RUN, WEB_PORT, check, command, join, prepareRun, sleep, startServer, stopServer, waitFor } from './lib.mjs'

const WEB = `http://127.0.0.1:${WEB_PORT}/`
const MARKERS_URL = `${WEB}maps/world/live/markers.json`
const SET = 'banner-waypoints'
const ALICE = 'jeb_'
const BOB = 'Zx9qNoSkin'
const bots = {}

async function layer () {
  try {
    const response = await fetch(MARKERS_URL)
    return response.ok ? (await response.json())[SET] ?? null : null
  } catch {
    return null
  }
}

async function markersWhen (description, condition, timeoutMs = 15_000) {
  return waitFor(description, async () => {
    const set = await layer()
    return set && condition(set.markers) ? set.markers : null
  }, timeoutMs, 500).catch(async () => (await layer())?.markers ?? {})
}

const byLabel = (ms, label) => Object.values(ms).find(m => m.label === label)
const json = async cmd => JSON.parse(await command(cmd))
const displaysNear = async (x, y, z, r = 3) => (await json(`bbmtest displays world ${x} ${y} ${z} ${r}`)).displays
const pixel = async (file, x, y) => json(`bbmtest pixel ${x} ${y} ${file}`)
const near = (a, b) => Math.abs(a - b) < 0.01

function tagCount (name) {
  return bots[name].inventory.items().filter(i => i.name === 'name_tag').reduce((n, i) => n + i.count, 0)
}

/** The bot holds a fresh name tag with this name and right-clicks the block; returns what chat said. */
async function useTag (name, tagName, x, y, z) {
  const bot = bots[name]
  await command(`clear ${name} minecraft:name_tag`)
  await command(`tp ${name} ${x + 0.5} ${y} ${z + 2.5}`)
  await command(`give ${name} minecraft:name_tag[minecraft:custom_name="${tagName}"]`)
  const item = await waitFor('the name tag', () => bot.inventory.items().find(i => i.name === 'name_tag'), 10_000, 200)
  await bot.equip(item, 'hand')
  await sleep(300)
  const before = bot.chatLog.length
  await bot.activateBlock(bot.blockAt(bot.entity.position.clone().set(x, y, z)))
  await sleep(1500)
  return bot.chatLog.slice(before).join(' | ')
}

function blueMapAcceptsDownload () {
  const dir = path.join(RUN, 'plugins', 'BlueMap')
  mkdirSync(dir, { recursive: true })
  writeFileSync(path.join(dir, 'core.conf'), 'accept-download: true\nrender-thread-count: 1\n')
}

/** Expected banner pixel: the pattern where its texture is opaque, the base where it is transparent. */
function tint (argb, rgb) {
  const c = (shift) => Math.floor(((parseInt(argb, 16) >>> shift) & 0xff) * ((rgb >>> shift) & 0xff) / 255)
  return ((0xff << 24 | c(16) << 16 | c(8) << 8 | c(0)) >>> 0).toString(16).padStart(8, '0')
}

const WHITE = 0xF9FFFE
const BLACK = 0x1D1D21

export async function run () {
  console.log('\n--- naming banners')
  prepareRun({ BlueMapBannerMarkers: { 'max-per-player': 2 } }, { skins: true })
  blueMapAcceptsDownload()
  await startServer('bbm-1')
  // The cleared box is dark: without this, creepers spawn in it and blow banners up at random.
  // (server.properties' spawn-monsters no longer does anything; the game rule does.)
  await command('gamerule spawn_monsters false')
  await command('forceload add 90 90 140 140')
  await command('fill 90 49 90 140 49 140 minecraft:stone')
  await command('fill 90 50 90 140 60 140 minecraft:air')
  // Banners: S1 white with a black creeper face; W1 on the north face of a stone block; others plain.
  await command('setblock 103 50 103 minecraft:white_banner[rotation=8]{patterns:[{pattern:"minecraft:creeper",color:"black"}]}')
  await command('setblock 107 50 107 minecraft:stone')
  await command('setblock 107 50 106 minecraft:blue_wall_banner[facing=north]')
  await command('setblock 111 50 103 minecraft:red_banner')
  await command('setblock 122 69 122 minecraft:stone') // GriefPrevention claims start at y 64 here
  await command('setblock 122 70 122 minecraft:green_banner')
  await command('setblock 130 50 100 minecraft:yellow_banner')
  await command('setblock 115 50 103 minecraft:black_banner')
  await command('gptest claim world admin 120 120 125 125')
  await command('kill @e[type=!minecraft:player,x=85,y=40,z=85,dx=60,dy=40,dz=60]')
  for (const name of [ALICE, BOB]) {
    bots[name] = await join(name)
    bots[name].physicsEnabled = false
  }
  await sleep(5000) // SkinsRestorer applies skins shortly after joining
  await waitFor('BlueMap to serve the waypoints layer', layer, 300_000, 2000)
  const set = await layer()
  check('the Waypoints layer exists and is shown by default', set.label === 'Waypoints' && set.toggleable && !set.defaultHidden, JSON.stringify(set))

  let chat = await useTag(ALICE, 'Home <3', 103, 50, 103)
  check('a name tag on a banner makes a waypoint, and says so', chat.includes('Waypoint "Home <3" added to the map'), chat)
  check('the name tag is used up', tagCount(ALICE) === 0, `${tagCount(ALICE)} left`)
  let ms = await markersWhen('the waypoint', m => Object.keys(m).length === 1)
  const home = Object.values(ms)[0]
  check('it is on the map, its name shown on hover', home?.type === 'poi' && home.label === 'Home &lt;3', JSON.stringify(home))
  check('the marker stands on the banner', home?.position.x === 103.5 && home.position.y === 50 && home.position.z === 103.5, JSON.stringify(home?.position))

  console.log('\n--- the banner as the icon')
  const iconFile = `bluemap/web/${home.icon}`
  const served = await fetch(WEB + home.icon)
  check('the icon is served by BlueMap\'s web server', served.ok && served.headers.get('content-type')?.includes('png'), `${served.status} ${WEB + home.icon}`)
  check('the icon is the banner\'s front at 20x40, its foot on the spot', home.anchor?.x === 10 && home.anchor?.y === 40, JSON.stringify(home.anchor))
  // Pixels where the creeper texture is fully opaque or fully transparent, from the client jar.
  let compared = 0
  let mismatches = []
  for (const [x, y] of [[0, 0], [10, 30], [2, 14], [3, 15], [14, 15], [8, 21], [5, 5], [19, 39]]) {
    const creeper = (await pixel('jar:assets/minecraft/textures/entity/banner/creeper.png', x + 1, y + 1)).argb
    const base = (await pixel('jar:assets/minecraft/textures/entity/banner/base.png', x + 1, y + 1)).argb
    const alpha = parseInt(creeper.slice(0, 2), 16)
    if (alpha !== 0 && alpha !== 255) continue
    const expected = alpha === 255 ? tint(creeper, BLACK) : tint(base, WHITE)
    const got = await pixel(iconFile, x, y)
    compared++
    if (got.width !== 20 || got.height !== 40 || got.argb !== expected) mismatches.push({ x, y, expected, got })
  }
  check('its pixels are the client\'s textures tinted with the banner\'s dyes', compared >= 4 && mismatches.length === 0,
    `${compared} compared; ${JSON.stringify(mismatches)}`)

  console.log('\n--- the popup and the face')
  check('the popup says who placed it', home.detail.includes('<b>Home &lt;3</b><br>Placed by jeb_'), home.detail)
  const faceSrc = home.detail.match(/<img src="([^"]+)"/)?.[1]
  check('the popup shows the owner\'s face', !!faceSrc, home.detail)
  const skin = await json(`bbmtest skin ${ALICE} 1 1`)
  const face = await pixel(`bluemap/web/${faceSrc}`, 4, 4) // skin pixel (1, 1), at 4x
  const hatAlpha = parseInt(skin.hat?.slice(0, 2) ?? '0', 16)
  const expectedFace = hatAlpha === 255 ? skin.hat : hatAlpha === 0 ? 'ff' + skin.face?.slice(2) : null
  check('the face is cut from the skin SkinsRestorer gave the player', !!skin.url && face.width === 32 &&
    (expectedFace === null || face.argb === expectedFace), JSON.stringify({ skin, face, expectedFace }))
  check('it is served too', (await fetch(WEB + faceSrc)).ok)

  console.log('\n--- the name over the banner')
  let displays = await displaysNear(103, 51, 103)
  check('a text display shows the name over a standing banner, just above it', displays.length === 1 && displays[0].text === 'Home <3' &&
    near(displays[0].x, 103.5) && near(displays[0].y, 51.95) && near(displays[0].z, 103.5), JSON.stringify(displays))
  check('it faces whoever looks, is hidden by walls and is not saved with the world', displays[0]?.billboard === 'CENTER' &&
    !displays[0].seeThrough && !displays[0].persistent, JSON.stringify(displays[0]))
  chat = await useTag(ALICE, 'Wall', 107, 50, 106)
  displays = await displaysNear(107, 50, 107)
  check('on a wall banner it floats at the flag, against the wall', chat.includes('added') && displays.length === 1 &&
    near(displays[0].x, 107.5) && near(displays[0].y, 50.97) && near(displays[0].z, 106.94), `${chat} ${JSON.stringify(displays)}`)

  console.log('\n--- refusals')
  chat = await useTag(ALICE, 'Third', 111, 50, 103)
  check('past the limit, a waypoint is refused and the tag kept', chat.includes('the most allowed') && tagCount(ALICE) === 1, `${chat}; ${tagCount(ALICE)} tags`)
  chat = await useTag(BOB, 'Mine now', 103, 50, 103)
  check('someone else\'s waypoint cannot be renamed; chat says whose it is, the tag is kept',
    chat.includes('This banner is jeb_\'s waypoint') && tagCount(BOB) === 1, `${chat}; ${tagCount(BOB)} tags`)
  chat = await useTag(BOB, 'Claimed', 122, 70, 122)
  check('a banner in a claim needs build trust there', chat.includes('permission to build') && tagCount(BOB) === 1, chat)
  chat = await useTag(ALICE, 'W'.repeat(25), 103, 50, 103)
  check('a name longer than max-name-length (24) is refused and the tag kept', chat.includes('at most 24') && tagCount(ALICE) === 1, `${chat}; ${tagCount(ALICE)} tags`)
  ms = await markersWhen('nothing new', () => false, 2000)
  check('none of these touched the map', Object.keys(ms).length === 2 && byLabel(ms, 'Home &lt;3'), JSON.stringify(Object.values(ms).map(m => m.label)))

  console.log('\n--- renaming, a player with no skin, enlarging')
  chat = await useTag(ALICE, 'Base', 103, 50, 103)
  ms = await markersWhen('the rename', m => byLabel(m, 'Base'))
  displays = await displaysNear(103, 51, 103)
  check('the owner renames with another tag: map, popup and text follow', chat.includes('renamed') && byLabel(ms, 'Base') &&
    !byLabel(ms, 'Home &lt;3') && displays[0]?.text === 'Base' && displays.length === 1, `${chat} ${JSON.stringify(displays)}`)
  chat = await useTag(BOB, 'Red', 111, 50, 103)
  ms = await markersWhen('Bob\'s waypoint and face', m => byLabel(m, 'Red')?.detail.includes('<img'))
  const red = byLabel(ms, 'Red')
  check('a player without a skin gets the game\'s default face', chat.includes('added') && red?.detail.includes('Placed by Zx9qNoSkin') &&
    red?.detail.includes('<img'), `${chat} ${red?.detail}`)
  const redId = Number(Object.keys(ms).find(k => ms[k] === red).replace('waypoint-', ''))
  const list = await command('bbm list')
  check('/bbm list shows every waypoint with its id', list.includes(`#${redId} "Red" of Zx9qNoSkin`) && list.includes('"Base" of jeb_'), list)
  await command(`bbm scale 2 ${redId}`)
  ms = await markersWhen('the larger icon', m => byLabel(m, 'Red')?.anchor.y === 80)
  const large = byLabel(ms, 'Red')
  const largeIcon = await pixel(`bluemap/web/${large?.icon}`, 0, 0)
  check('/bbm scale 2 doubles a waypoint\'s icon', large?.anchor.x === 20 && largeIcon.width === 40 && largeIcon.height === 80,
    JSON.stringify({ anchor: large?.anchor, largeIcon }))

  console.log('\n--- removal')
  await command(`tp ${ALICE} 103.5 50 105.5`)
  await sleep(500)
  await bots[ALICE].dig(bots[ALICE].blockAt(bots[ALICE].entity.position.clone().set(103, 50, 103)), true)
  ms = await markersWhen('the broken banner', m => !byLabel(m, 'Base'))
  check('breaking the banner removes the waypoint and its name', !byLabel(ms, 'Base') && (await displaysNear(103, 51, 103)).length === 0)
  await command('setblock 107 50 107 minecraft:air destroy')
  ms = await markersWhen('the fallen wall banner', m => !byLabel(m, 'Wall'))
  check('so does breaking the block a wall banner hangs on', !byLabel(ms, 'Wall') && (await displaysNear(107, 50, 107)).length === 0)
  chat = await useTag(BOB, 'Boom', 130, 50, 100)
  await markersWhen('Boom', m => byLabel(m, 'Boom'))
  await command(`tp ${BOB} 105.5 50 105.5`)
  await command('summon minecraft:tnt 130 50 99 {fuse:0}')
  ms = await markersWhen('the explosion', m => !byLabel(m, 'Boom'))
  check('and an explosion', chat.includes('added') && !byLabel(ms, 'Boom'), chat)
  chat = await useTag(ALICE, 'Keep', 115, 50, 103)
  ms = await markersWhen('Keep', m => byLabel(m, 'Keep'))
  const keepId = Object.keys(ms).find(k => ms[k].label === 'Keep')?.replace('waypoint-', '')
  await command(`bbm delete ${keepId}`)
  ms = await markersWhen('the deletion', m => !byLabel(m, 'Keep'))
  const bannerStays = (await command('execute if block 115 50 103 minecraft:black_banner')).includes('passed')
  check('/bbm delete removes a waypoint but leaves the banner', !byLabel(ms, 'Keep') && bannerStays &&
    (await displaysNear(115, 51, 103)).length === 0, `${bannerStays}`)
  chat = await useTag(ALICE, 'Silent', 115, 50, 103)
  await markersWhen('Silent', m => byLabel(m, 'Silent'))
  await command('setblock 115 50 103 minecraft:air') // no event at all
  ms = await markersWhen('the silent removal to be noticed', m => !byLabel(m, 'Silent'), 75_000)
  check('a banner removed without any event is noticed within a minute', !byLabel(ms, 'Silent'))

  console.log('\n--- BlueMap reloading, the server restarting')
  await command('bluemap reload')
  await sleep(3000)
  ms = await markersWhen('the markers after a BlueMap reload', m => byLabel(m, 'Red'), 120_000)
  check('waypoints are back after /bluemap reload', Object.keys(ms).length === 1 && byLabel(ms, 'Red')?.anchor.y === 80, JSON.stringify(Object.keys(ms)))
  await stopServer()
  const onDisk = JSON.parse(readFileSync(path.join(RUN, 'bluemap', 'web', 'maps', 'world', 'live', 'markers.json'), 'utf8'))[SET]
  check('with the server stopped, BlueMap\'s web files still hold the waypoint', !!byLabel(onDisk?.markers ?? {}, 'Red'))
  await startServer('bbm-2')
  ms = await waitFor('the waypoint after a restart', async () => {
    const s = await layer()
    return s && byLabel(s.markers, 'Red') ? s.markers : null
  }, 300_000, 2000).catch(() => ({}))
  check('after a restart the waypoint is on the map again, still large', byLabel(ms, 'Red')?.anchor.y === 80, JSON.stringify(Object.keys(ms)))
  displays = await waitFor('the name over the banner', async () => {
    const d = await displaysNear(111, 51, 103)
    return d.length ? d : null
  }, 30_000).catch(() => [])
  check('and its name floats over the banner again, once', displays.length === 1 && displays[0].text === 'Red', JSON.stringify(displays))
  await stopServer()
}
