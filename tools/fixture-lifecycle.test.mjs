import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { once } from 'node:events'
import { setTimeout as delay } from 'node:timers/promises'
import { test } from 'node:test'
import { terminateProcessGroups } from './fixture-lifecycle.mjs'

test('cleanup kills a TERM-resistant descendant after its wrapper exits', { timeout: 10000 }, async () => {
  const wrapper = spawn(
    process.execPath,
    ['-e', `
      const { spawn } = require('node:child_process');
      const child = spawn(process.execPath, ['-e',
        "process.on('SIGTERM', () => {}); console.log('ready'); setInterval(() => {}, 1000)"
      ], { stdio: ['ignore', 'pipe', 'ignore'] });
      child.stdout.once('data', () => { console.log(child.pid); child.unref(); process.exit(0); });
    `],
    { detached: true, stdio: ['ignore', 'pipe', 'ignore'] },
  )
  try {
    const exited = once(wrapper, 'exit')
    const [output] = await once(wrapper.stdout, 'data')
    const descendant = Number(output.toString().trim())
    await exited
    assert.equal(wrapper.exitCode, 0)
    assert.ok(descendant > 0)
    process.kill(descendant, 0)

    await terminateProcessGroups([wrapper], { graceMs: 30 })

    // An orphan can briefly remain as a zombie until init reaps it; it must not run.
    let running = true
    for (let attempt = 0; attempt < 100; attempt++) {
      try {
        const state = readFileSync(`/proc/${descendant}/stat`, 'utf8').split(') ')[1][0]
        running = state !== 'Z'
      } catch (error) {
        if (error.code !== 'ENOENT') throw error
        running = false
      }
      if (!running) break
      await delay(10)
    }
    assert.equal(running, false, 'descendant must not survive cleanup')
  } finally {
    try { process.kill(-wrapper.pid, 'SIGKILL') } catch (error) {
      if (error.code !== 'ESRCH') throw error
    }
  }
})
