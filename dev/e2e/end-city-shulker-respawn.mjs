// End-to-end suite for End City Shulker Respawn: one real End city, learned, emptied and refilled, across a
// restart. A spectator bot loads the city's chunks by visiting it. About five minutes.

import { asPlayer, check, command, inEnd, prepareRun, sleep, startServer, stopServer, waitFor } from './lib.mjs'
import { center, cityState, leave, locateCity, sameBlock, scout, tagged, visit } from './cities.mjs'

const PLUGIN = 'EndCityShulkerRespawn'
const COOLDOWN_SECONDS = 20

async function phaseLearnAndRefill () {
  console.log('\n--- learning a city')
  prepareRun({ [PLUGIN]: { cooldown: `${COOLDOWN_SECONDS}s` } })
  await startServer('ecsr-1')
  const city = await locateCity()
  console.log(`      End city near ${city.x} ${city.z}`)

  await scout()
  let s = await visit({ x: city.x, y: 120, z: city.z })
  s = await visit(center(s)) // from inside the city, every chunk around is in view
  await sleep(3000)
  s = await cityState()
  console.log(`      city ${s.key}: ${s.spots.length} spots, ${s.shulkers.length} shulkers, ${s.chunksLoaded}/${s.chunks} chunks loaded`)
  check('the city\'s shulkers are learned as its spots', s.spots.length > 0 && s.spots.length === s.shulkers.length,
    JSON.stringify({ spots: s.spots.length, shulkers: s.shulkers.length }))
  check('every shulker owns a spot', s.shulkers.every(sh => sh.spot >= 0), JSON.stringify(s.shulkers))
  if (s.spots.length < 3) throw new Error(`this city has too few shulkers to test with: ${s.spots.length}`)

  console.log('\n--- spots settle where vanilla put the shulkers')
  // Learned before vanilla settles new shulkers on their first tick; the spots follow them when the chunks unload.
  const unsettled = s.shulkers.filter(sh => !sameBlock(s.spots[sh.spot], sh) || s.spots[sh.spot].face !== sh.face).length
  console.log(`      spots not yet matching their settled shulker: ${unsettled}`)
  await leave()
  s = await visit(center(s))
  await sleep(3000)
  s = await cityState()
  const off = s.shulkers.filter(sh => sh.spot < 0 || !sameBlock(s.spots[sh.spot], sh) || s.spots[sh.spot].face !== sh.face)
  check('after a visit, every spot is where its shulker sits, stuck to the same face', off.length === 0,
    JSON.stringify(off.slice(0, 5).map(sh => ({ shulker: sh, spot: s.spots[sh.spot] }))))
  const learned = s

  console.log('\n--- killing every shulker')
  const box = `x=${s.min.x},y=${s.min.y},z=${s.min.z},dx=${s.max.x - s.min.x},dy=${s.max.y - s.min.y},dz=${s.max.z - s.min.z}`
  await inEnd(`kill @e[type=minecraft:shulker,${box}]`)
  const killedAt = Date.now()
  await sleep(1000)
  s = await cityState()
  check('every spot starts its cooldown when its shulker dies',
    s.spots.every(spot => !spot.alive && spot.dueInMs > (COOLDOWN_SECONDS - 5) * 1000 && spot.dueInMs <= COOLDOWN_SECONDS * 1000),
    JSON.stringify(s.spots.map(spot => spot.dueInMs)))
  const occupied = s.spots[0]
  const blocked = s.spots[1]
  await inEnd(`summon minecraft:shulker ${occupied.x} ${occupied.y} ${occupied.z}`) // as if bred by a player
  await inEnd(`setblock ${blocked.x} ${blocked.y} ${blocked.z} minecraft:stone`)
  const claimed = claimedSpots(s.spots)
  const claim = JSON.parse(await command(`ecsrtest claim ${claimed.at.x} ${claimed.at.z}`))
  check('a GriefPrevention admin claim is made around a spot', claim.succeeded, JSON.stringify(claim))
  console.log(`      claimed spots: ${claimed.indexes.join(', ')}`)
  const minedOut = loneSpot(s.spots, new Set([0, 1, ...claimed.indexes]))
  for (const [dx, dy, dz] of Object.values(FACES)) {
    const at = s.spots[minedOut]
    await inEnd(`setblock ${at.x + dx} ${at.y + dy} ${at.z + dz} minecraft:air`)
  }
  console.log(`      spot ${minedOut} mined out`)
  const empty = new Set([0, 1, minedOut, ...claimed.indexes]) // the spots expected to stay empty

  console.log('\n--- nothing comes back while the city stays loaded')
  await sleep(Math.max(0, killedAt + (COOLDOWN_SECONDS + 3) * 1000 - Date.now()))
  s = await cityState()
  check('no refill while the city\'s chunks stay loaded', tagged(s).length === 0, JSON.stringify(s.shulkers))

  console.log('\n--- leaving and coming back after the cooldown')
  await leave()
  s = await visit(center(learned))
  await waitFor('the refill', async () => tagged(await cityState()).length >= learned.spots.length - empty.size, 30_000)
    .catch(() => {})
  await sleep(2000)
  s = await cityState()
  const refilled = tagged(s)
  const missing = s.spots.map((spot, i) => ({ i, ...spot })).filter((spot, i) => !empty.has(i) && !spot.alive)
  check('every free spot is refilled on the first load after the cooldown', refilled.length === learned.spots.length - empty.size,
    JSON.stringify({ spots: learned.spots.length, refilled: refilled.length, stillDue: missing.slice(0, 5) }))
  const moved = refilled.filter(sh => !sameBlock(learned.spots[sh.spot], sh) || learned.spots[sh.spot].face !== sh.face)
  check('each new shulker is on its spot, stuck to the same face', moved.length === 0,
    JSON.stringify(moved.slice(0, 5).map(sh => ({ shulker: sh, spot: learned.spots[sh.spot] }))))
  check('each new shulker is stuck to a solid block', refilled.every(sh => sh.attachedToSolid),
    JSON.stringify(refilled.filter(sh => !sh.attachedToSolid)))
  check('new shulkers never despawn', refilled.every(sh => sh.persistent), JSON.stringify(refilled))
  check('a spot taken by another shulker stays empty', !s.spots[0].alive, JSON.stringify(s.spots[0]))
  check('a spot blocked by a block stays empty', !s.spots[1].alive, JSON.stringify(s.spots[1]))
  check('a spot with nothing solid around it stays empty', !s.spots[minedOut].alive, JSON.stringify(s.spots[minedOut]))
  check('spots inside a GriefPrevention claim stay empty', claimed.indexes.every(i => !s.spots[i].alive),
    JSON.stringify(claimed.indexes.map(i => s.spots[i])))
  check('the bred shulker is left alone and owns nothing', s.shulkers.filter(sh => sh.spot < 0).length === 1,
    JSON.stringify(s.shulkers.filter(sh => sh.spot < 0)))
  check('never more shulkers of the city than it had', refilled.length <= learned.spots.length)
  await stopServer()
  return { learned, empty, claimed, minedOut }
}

const FACES = { DOWN: [0, -1, 0], UP: [0, 1, 0], NORTH: [0, 0, -1], SOUTH: [0, 0, 1], WEST: [-1, 0, 0], EAST: [1, 0, 0] }

/** A spot to mine out: none of the others within 2 blocks, so no other spot loses what it sticks to. */
function loneSpot (spots, taken) {
  const near = (a, b) => Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y), Math.abs(a.z - b.z)) <= 2
  const i = spots.findIndex((spot, i) => !taken.has(i) && spots.every((other, j) => j === i || !near(spot, other)))
  if (i < 0) throw new Error('no spot away from the others to mine out')
  return i
}

/**
 * A spot to claim the 3×3 columns around, away from spots 0 and 1, and every spot inside those columns:
 * GriefPrevention claims reach from bedrock to the sky, so a spot above or below is claimed too.
 */
function claimedSpots (spots) {
  const inColumn = (at, spot) => Math.abs(spot.x - at.x) <= 1 && Math.abs(spot.z - at.z) <= 1
  for (let i = 2; i < spots.length; i++) {
    const at = spots[i]
    if (!inColumn(at, spots[0]) && !inColumn(at, spots[1])) {
      return { at, indexes: spots.map((spot, j) => inColumn(at, spot) ? j : -1).filter(j => j >= 0) }
    }
  }
  throw new Error('no spot to claim away from the others')
}

async function phaseRestart ({ learned, empty, claimed, minedOut }) {
  console.log('\n--- after a restart')
  await startServer('ecsr-2')
  await scout() // back where it left: in the city
  await command('op Scout')
  // Counted once the whole city has loaded around the bot again, not while its chunks are still coming in.
  let s = await waitFor('the city to load', async () => {
    const st = await cityState()
    return st.found && tagged(st).length >= learned.spots.length - empty.size ? st : null
  }, 60_000).catch(() => null) ?? await cityState()
  check('the city\'s data survived the restart', s.spots.length === learned.spots.length && s.key === learned.key,
    JSON.stringify({ before: learned.spots.length, after: s.spots.length }))
  check('the refilled shulkers still own their spots', tagged(s).length === learned.spots.length - empty.size, JSON.stringify(s.shulkers))

  await inEnd(`tp Scout ${center(learned).x} ${center(learned).y} ${center(learned).z}`)
  await sleep(1000)
  const where = JSON.parse(await command('ecsrtest whereis Scout'))
  check('the plugin finds the city a player stands in, even in a chunk none of its pieces reach', where.cityAt === learned.key, JSON.stringify(where))
  const status = (await asPlayer('Scout', '/ecsr status')).join(' | ')
  check('/ecsr status describes the city', status.includes(`End city at ${learned.key}: ${learned.spots.length} spots`), status)
  check('/ecsr status counts the claimed spots', status.includes(`${claimed.indexes.length} spots are inside a land claim and stay empty.`), status)

  const occupied = learned.spots[0]
  const blocked = learned.spots[1]
  await inEnd(`kill @e[type=minecraft:shulker,x=${occupied.x + 0.5},y=${occupied.y + 0.5},z=${occupied.z + 0.5},distance=..0.9]`)
  await inEnd(`setblock ${blocked.x} ${blocked.y} ${blocked.z} minecraft:air`)
  const mined = learned.spots[minedOut]
  const [dx, dy, dz] = FACES[mined.face]
  await inEnd(`setblock ${mined.x + dx} ${mined.y + dy} ${mined.z + dz} minecraft:purpur_block`) // something to stick to again
  const unclaim = JSON.parse(await command('ecsrtest unclaim'))
  check('the claim is removed', unclaim.deleted === 1, JSON.stringify(unclaim))
  const refill = (await asPlayer('Scout', '/ecsr refill')).join(' | ')
  check('/ecsr refill answers', refill.includes(`${empty.size} shulkers will come back the next time their chunks load.`), refill)

  await leave()
  s = await visit(center(learned))
  s = await waitFor('the last spots', async () => {
    const st = await cityState()
    return tagged(st).length === learned.spots.length ? st : null
  }, 30_000).catch(() => null) ?? await cityState()
  check('once cleared, unclaimed and built back, the last spots are refilled too: the city is full again',
    tagged(s).length === learned.spots.length && s.spots.every(spot => spot.alive), JSON.stringify(s.spots))
  await stopServer()
}

export async function run () {
  const learned = await phaseLearnAndRefill()
  await phaseRestart(learned)
}
