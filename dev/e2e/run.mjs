// End-to-end suites: a real Paper server, bots as players, the harness plugin for assertions.
//
//   cd dev/e2e && npm ci && npm test                          every suite
//   npm test -- end-city-shulker-respawn                      one suite, by its plugin's name
//
// Each suite starts from a fresh world in dev/e2e/run/. Exit code 0 when every check passes.

import { execSync } from 'node:child_process'
import { GRADLEW, ROOT, abandon, results } from './lib.mjs'
import * as dragon from './multiplayer-dragon-rebirth.mjs'
import * as shulkers from './end-city-shulker-respawn.mjs'
import * as shiny from './shiny-shulkers.mjs'
import * as claims from './bluemap-griefprevention.mjs'
import * as banners from './bluemap-banner-markers.mjs'

const suites = {
  'multiplayer-dragon-rebirth': dragon,
  'end-city-shulker-respawn': shulkers,
  'shiny-shulkers': shiny,
  'bluemap-griefprevention': claims,
  'bluemap-banner-markers': banners
}

const requested = process.argv.slice(2)
const unknown = requested.filter(name => !(name in suites))
if (unknown.length > 0) {
  console.error(`Unknown suite ${unknown.join(', ')}; known: ${Object.keys(suites).join(', ')}`)
  process.exit(2)
}

let aborted = false
console.log('Building the plugins and the harness')
execSync(`"${GRADLEW}" assemble --console=plain -q`, { cwd: ROOT, stdio: 'inherit' })
for (const name of requested.length > 0 ? requested : Object.keys(suites)) {
  console.log(`\n=== ${name}`)
  try {
    await suites[name].run()
  } catch (e) {
    aborted = true
    console.error(`\nABORTED ${name}: ${e.stack ?? e}`)
    abandon()
  }
}

const passed = results.filter(r => r.ok).length
console.log(`\n${passed}/${results.length} checks passed${aborted ? ', a suite was aborted' : ''}`)
process.exit(aborted || passed !== results.length ? 1 : 0)
