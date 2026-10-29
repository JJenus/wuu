import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { once } from 'node:events'
import { setTimeout as delay } from 'node:timers/promises'
import { test, mock } from 'node:test'
import { stop, terminateProcessGroups } from './fixture-lifecycle.mjs'

test('cleanup handles absent and already-removed processes', async () => {
  stop({}, 'SIGTERM')
  const kill = mock.method(process, 'kill', () => {
    throw Object.assign(new Error('gone'), { code: 'ESRCH' })
  })
  try {
    await terminateProcessGroups([{ pid: 123, exitCode: 0 }], { graceMs: 0 })
    assert.equal(kill.mock.callCount(), 2)
  } finally {
    kill.mock.restore()
  }
})

test('cleanup reports unexpected signal failures', () => {
  const kill = mock.method(process, 'kill', () => {
    throw Object.assign(new Error('denied'), { code: 'EPERM' })
  })
  try {
    assert.throws(() => stop({ pid: 123 }, 'SIGTERM'), { code: 'EPERM' })
  } finally {
    kill.mock.restore()
  }
})

test('cleanup also waits for an active wrapper to exit', { timeout: 10000 }, async () => {
  const child = spawn(
    process.execPath,
    ['-e', "console.log('ready'); setInterval(() => {}, 1000)"],
    {
      detached: true,
      stdio: ['ignore', 'pipe', 'ignore'],
    },
  )
  try {
    await once(child.stdout, 'data')
    await terminateProcessGroups([child], { graceMs: 0 })
    assert.ok(child.signalCode !== null || child.exitCode !== null)
  } finally {
    stop(child, 'SIGKILL')
  }
})

test(
  'cleanup kills a TERM-resistant descendant after its wrapper exits',
  { timeout: 10000 },
  async () => {
    const wrapper = spawn(
      process.execPath,
      [
        '-e',
        `
      const { spawn } = require('node:child_process');
      const child = spawn(process.execPath, ['-e',
        "process.on('SIGTERM', () => {}); console.log('ready'); setInterval(() => {}, 1000)"
      ], { stdio: ['ignore', 'pipe', 'ignore'] });
      child.stdout.once('data', () => { console.log(child.pid); child.unref(); process.exit(0); });
    `,
      ],
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
      try {
        process.kill(-wrapper.pid, 'SIGKILL')
      } catch (error) {
        if (error.code !== 'ESRCH') throw error
      }
    }
  },
)
