// End-to-end suite for Shiny Shulkers, with End City Shulker Respawn: a real End city generated with every
// shulker shiny (one-in: 1), then emptied and refilled, each new shulker rolled again. About four minutes.

import { check, inEnd, prepareRun, sleep, startServer, stopServer, waitFor } from './lib.mjs'
import { center, cityState, leave, locateCity, scout, tagged, visit } from './cities.mjs'

const COOLDOWN_SECONDS = 10

export async function run () {
  console.log('\n--- a city generated with every shulker shiny')
  prepareRun({
    ShinyShulkers: { 'one-in': 1 },
    EndCityShulkerRespawn: { cooldown: `${COOLDOWN_SECONDS}s` }
  })
  await startServer('shiny')
  const city = await locateCity()
  await scout()
  let s = await visit({ x: city.x, y: 120, z: city.z })
  s = await visit(center(s))
  await sleep(3000)
  s = await cityState()
  const colors = new Set(s.shulkers.map(sh => sh.color))
  console.log(`      ${s.shulkers.length} shulkers, colours: ${[...colors].join(', ')}`)
  check('every generated city shulker got a dye colour', s.shulkers.length > 0 && s.shulkers.every(sh => sh.color !== ''),
    JSON.stringify(s.shulkers.filter(sh => sh.color === '')))
  check('colours are mixed, not all one', colors.size > 1, [...colors].join(', '))
  check('End City Shulker Respawn learned each shulker as a spot', s.shulkers.every(sh => sh.spot >= 0))
  // Spots are learned before vanilla settles new shulkers; they follow them once the chunks unload. A spot
  // left in mid-air has nothing to stick to and waits, so the city is visited again before it is emptied.
  await leave()
  await visit(center(s))
  await sleep(3000)
  s = await cityState()
  const learned = s

  console.log('\n--- a shulker summoned later is not rolled')
  const free = learned.spots[0]
  await inEnd(`summon minecraft:shulker ${free.x} ${free.y + 30} ${free.z} {NoAI:1b}`)
  await sleep(1000)
  s = await cityState()
  const summoned = s.shulkers.filter(sh => sh.spot < 0)
  check('a summoned shulker keeps the default purple', summoned.length === 1 && summoned[0].color === '', JSON.stringify(summoned))
  await inEnd(`kill @e[type=minecraft:shulker,x=${free.x + 0.5},y=${free.y + 30.5},z=${free.z + 0.5},distance=..2]`)

  console.log('\n--- refilled shulkers are rolled again')
  const before = new Map(learned.shulkers.map(sh => [sh.spot, sh.color]))
  const box = `x=${s.min.x},y=${s.min.y},z=${s.min.z},dx=${s.max.x - s.min.x},dy=${s.max.y - s.min.y},dz=${s.max.z - s.min.z}`
  await inEnd(`kill @e[type=minecraft:shulker,${box}]`)
  const killedAt = Date.now()
  await leave()
  await sleep(Math.max(0, killedAt + (COOLDOWN_SECONDS + 2) * 1000 - Date.now()))
  await visit(center(learned))
  s = await waitFor('the refill', async () => {
    const st = await cityState()
    return tagged(st).length === learned.spots.length ? st : null
  }, 30_000).catch(() => null) ?? await cityState()
  const refilled = tagged(s)
  check('the whole city is refilled', refilled.length === learned.spots.length,
    JSON.stringify({ spots: learned.spots.length, refilled: refilled.length,
      missing: learned.spots.filter((_, i) => !refilled.some(sh => sh.spot === i)).slice(0, 6) }))
  check('every refilled shulker is rolled (one-in: 1, so all shiny)', refilled.every(sh => sh.color !== ''),
    JSON.stringify(refilled.filter(sh => sh.color === '')))
  // Each new colour matches the old one 1 time in 16; a city of 4 or more keeping all of them is no chance.
  const kept = refilled.filter(sh => sh.color === before.get(sh.spot)).length
  console.log(`      ${kept} of ${refilled.length} came back in the colour they had`)
  check('the colours are rolled anew, not remembered', refilled.length < 4 || kept < refilled.length,
    `${kept} of ${refilled.length} kept their colour`)
  await stopServer()
}
