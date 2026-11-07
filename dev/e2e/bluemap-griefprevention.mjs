// End-to-end suite for BlueMap GriefPrevention: real claims (made through GriefPrevention's API) read back
// from BlueMap's own web endpoint, what a browser gets. About five minutes; BlueMap downloads Minecraft's
// textures from Mojang on its first start.

import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { RUN, WEB_PORT, check, command, join, prepareRun, sleep, startServer, stopServer, waitFor } from './lib.mjs'

const MARKERS_URL = `http://127.0.0.1:${WEB_PORT}/maps/world/live/markers.json`
const SET = 'griefprevention-claims'
const RESYNC_SECONDS = 10

async function markers () {
  try {
    const response = await fetch(MARKERS_URL)
    if (!response.ok) return null
    return (await response.json())[SET] ?? null
  } catch {
    return null
  }
}

/** Waits until the layer's markers satisfy the condition, and returns them. */
async function markersWhen (description, condition, timeoutMs = 15_000) {
  return waitFor(description, async () => {
    const set = await markers()
    return set && condition(set.markers) ? set.markers : null
  }, timeoutMs, 500).catch(async () => (await markers())?.markers ?? {})
}

async function claim (owner, x1, z1, x2, z2) {
  const reply = JSON.parse(await command(`gptest claim world ${owner} ${x1} ${z1} ${x2} ${z2}`))
  if (!reply.succeeded) throw new Error(`GriefPrevention refused the claim ${owner} ${x1},${z1} ${x2},${z2}`)
  return reply.id
}

const hex = c => [c.r, c.g, c.b].map(v => v.toString(16).padStart(2, '0')).join('')
const withDetail = (ms, text) => Object.values(ms).filter(m => m.detail.includes(text))

function blueMapAcceptsDownload () {
  const dir = path.join(RUN, 'plugins', 'BlueMap')
  mkdirSync(dir, { recursive: true })
  writeFileSync(path.join(dir, 'core.conf'), 'accept-download: true\nrender-thread-count: 1\n')
}

/** Any markers.json BlueMap wrote to disk, for a static web server to serve while the game server sleeps. */
function markerFilesOnDisk (dir = RUN, found = []) {
  for (const name of readdirSync(dir)) {
    const p = path.join(dir, name)
    if (statSync(p).isDirectory()) markerFilesOnDisk(p, found)
    else if (name.startsWith('markers.json')) found.push(path.relative(RUN, p))
  }
  return found
}

export async function run () {
  console.log('\n--- claims appear on the map')
  prepareRun({ BlueMapGriefPrevention: { 'resync-interval': `${RESYNC_SECONDS}s` } })
  blueMapAcceptsDownload()
  await startServer('bmgp-1')
  await join('Alice')
  await join('Bob')
  const set = await waitFor('BlueMap to serve the claims layer', markers, 300_000, 2000)
  check('the Claims layer exists and is shown by default', set.label === 'Claims' && set.toggleable && !set.defaultHidden, JSON.stringify(set))
  const aliceColor = JSON.parse(await command('gptest locator Alice')).rgb
  const bobColor = JSON.parse(await command('gptest locator Bob')).rgb

  const first = await claim('Alice', 0, 0, 9, 9)
  let ms = await markersWhen('Alice\'s claim', m => Object.keys(m).length === 1)
  let alice = withDetail(ms, 'Alice')
  check('a claim becomes a marker', Object.keys(ms).length === 1 && alice.length === 1, JSON.stringify(ms))
  check('the label, shown in BlueMap\'s marker list, does not name the owner', alice[0]?.label === 'Claim', alice[0]?.label)
  check('the popup names the owner and the size', alice[0]?.detail === '<b>Claim of Alice</b><br>100 blocks', alice[0]?.detail)
  check('it is a flat shape seen through the terrain', alice[0]?.type === 'shape' && alice[0]?.depthTest === false, JSON.stringify(alice[0]))
  // Made on y 64, as a golden shovel on blocks at y 69 makes it (ExtendIntoGroundDistance 5): drawn on top of those blocks.
  check('it is drawn at the surface the claim was made on, not at the bottom of its protection', alice[0]?.shapeY === 70,
    `shapeY ${alice[0]?.shapeY}`)
  check('its colour is Alice\'s locator bar colour, as Minecraft computes it', hex(alice[0]?.lineColor ?? {}) === aliceColor,
    `${hex(alice[0]?.lineColor ?? {})} vs ${aliceColor}`)

  console.log('\n--- touching claims become one shape')
  await claim('Alice', 10, 0, 19, 9)
  ms = await markersWhen('the merge', m => withDetail(m, '2 claims').length === 1)
  alice = withDetail(ms, 'Alice')
  check('two touching claims are one rectangle', alice.length === 1 && alice[0].shape.length === 4, JSON.stringify(alice))
  await claim('Alice', 0, 10, 9, 19)
  ms = await markersWhen('the L', m => withDetail(m, '3 claims').length === 1)
  alice = withDetail(ms, 'Alice')
  check('three make an L with six corners', alice.length === 1 && alice[0].shape.length === 6, JSON.stringify(alice[0]?.shape))
  await claim('Alice', 20, 0, 29, 29)
  await claim('Alice', 0, 20, 19, 29)
  ms = await markersWhen('the ring', m => withDetail(m, '5 claims').length === 1)
  alice = withDetail(ms, 'Alice')
  check('a ring of claims is one square with a hole', alice.length === 1 && alice[0].shape.length === 4 && alice[0].holes?.length === 1,
    JSON.stringify({ shape: alice[0]?.shape, holes: alice[0]?.holes }))

  console.log('\n--- other owners, corners, admin claims')
  await claim('Bob', 30, 0, 39, 9)
  const corner = await claim('Alice', 30, 30, 39, 39) // meets Alice's ring only at (30, 30)
  await claim('admin', 100, 100, 109, 109)
  ms = await markersWhen('all markers', m => Object.keys(m).length === 4)
  const bob = withDetail(ms, 'Bob')
  check('a neighbour\'s claim is never merged with yours', bob.length === 1 && hex(bob[0].lineColor) === bobColor, JSON.stringify(bob))
  check('a claim touching only at a corner is its own shape', withDetail(ms, 'Claim of Alice').length === 2)
  const admin = Object.values(ms).filter(m => m.label === 'Admin claim')
  check('admin claims are labelled and bright purple', admin.length === 1 && hex(admin[0].lineColor) === 'bf00ff', JSON.stringify(admin))

  console.log('\n--- changes')
  await command(`gptest transfer ${corner} Bob`)
  ms = await markersWhen('the transfer', m => withDetail(m, 'Claim of Bob').length === 2)
  check('a transferred claim takes its new owner\'s name and colour', withDetail(ms, 'Claim of Bob').every(m => hex(m.lineColor) === bobColor) &&
    withDetail(ms, 'Claim of Bob').length === 2, JSON.stringify(withDetail(ms, 'Bob')))
  const ids = JSON.parse(await command('gptest claim world Alice 200 200 209 209'))
  ms = await markersWhen('a new claim', m => Object.keys(m).length === 5)
  await command(`gptest delete ${ids.id}`)
  ms = await markersWhen('the deletion', m => Object.keys(m).length === 4)
  check('a deleted claim disappears', Object.keys(ms).length === 4)
  const resized = JSON.parse(await command(`gptest resize ${first} 0 0 4 9`)) // through the API, which fires no event
  check('GriefPrevention resized the claim', resized.succeeded === true, JSON.stringify(resized))
  ms = await markersWhen('the periodic redraw', m => withDetail(m, '5 claims · 750 blocks').length === 1, (RESYNC_SECONDS + 5) * 1000)
  check('a change without an event is caught by the periodic redraw', withDetail(ms, '750 blocks').length === 1,
    JSON.stringify(withDetail(ms, 'Alice').map(m => m.detail)))

  console.log('\n--- BlueMap reloading, the server restarting')
  await command('bluemap reload')
  await sleep(3000)
  ms = await markersWhen('the markers after a BlueMap reload', m => Object.keys(m).length === 4, 120_000)
  check('the claims are back after /bluemap reload', Object.keys(ms).length === 4, JSON.stringify(Object.keys(ms)))
  await stopServer()
  const onDisk = JSON.parse(readFileSync(path.join(RUN, 'bluemap', 'web', 'maps', 'world', 'live', 'markers.json'), 'utf8'))[SET]
  check('the claims stay in BlueMap\'s web files with the server stopped, for a static web server',
    Object.keys(onDisk?.markers ?? {}).length === 4, `files: ${markerFilesOnDisk().join(', ')}`)
  await startServer('bmgp-2')
  ms = await waitFor('the markers after a restart', async () => {
    const s = await markers()
    return s && Object.keys(s.markers).length === 4 ? s.markers : null
  }, 300_000, 2000).catch(() => ({}))
  check('the claims are drawn again after a restart', Object.keys(ms).length === 4, JSON.stringify(Object.keys(ms)))
  await stopServer()
}
