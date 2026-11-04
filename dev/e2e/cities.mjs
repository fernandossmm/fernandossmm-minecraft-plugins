// Shared by the suites that work with a real End city: finding one, visiting it with a spectator bot so its
// chunks load, leaving until they unload, and reading what End City Shulker Respawn knows about it.

import { readFileSync } from 'node:fs'
import path from 'node:path'
import { RUN, command, inEnd, join, waitFor } from './lib.mjs'

let city = { x: 0, z: 0 }

/** The nearest End city to the centre of the End; later calls inspect this one. */
export async function locateCity () {
  const located = await inEnd('locate structure minecraft:end_city')
  const match = /\[(-?\d+), ~, (-?\d+)\]/.exec(located)
  if (!match) throw new Error(`no End city located: ${located}`)
  city = { x: Number(match[1]), z: Number(match[2]) }
  return city
}

/** What the plugin knows about the city and what is in it. Too long for RCON, so read from a file. */
export async function cityState () {
  const reply = await command(`ecsrtest city ${city.x} ${city.z}`)
  if (!reply.startsWith('written')) throw new Error(`the harness could not report the city: ${reply}`)
  return JSON.parse(readFileSync(path.join(RUN, 'e2e-city.json'), 'utf8'))
}

export const tagged = s => s.shulkers.filter(sh => sh.spot >= 0)
export const sameBlock = (a, b) => a.x === b.x && a.y === b.y && a.z === b.z

export function center (s) {
  return {
    x: Math.floor((s.min.x + s.max.x) / 2),
    y: Math.floor((s.min.y + s.max.y) / 2),
    z: Math.floor((s.min.z + s.max.z) / 2)
  }
}

/** A spectator bot that stays where it is put: mineflayer would otherwise apply gravity itself. */
export async function scout () {
  const bot = await join('Scout')
  bot.physicsEnabled = false
  await command('gamemode spectator Scout')
}

/** The bot goes to the city and stays until its chunks have loaded and been learned. */
export async function visit (at) {
  await inEnd(`tp Scout ${at.x} ${at.y} ${at.z}`)
  return waitFor('the city to load', async () => {
    const s = await cityState()
    return s.found && s.chunksLoaded > 0 && s.learnedChunks >= s.chunksLoaded ? s : null
  }, 120_000, 2000)
}

/** The bot leaves the End until none of the city's chunks is loaded any more. */
export async function leave () {
  await command('execute in minecraft:overworld run tp Scout 0 120 0')
  await waitFor('the city\'s chunks to unload', async () => (await cityState()).chunksLoaded === 0, 180_000, 2000)
}
