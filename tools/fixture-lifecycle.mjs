import { setTimeout as delay } from 'node:timers/promises'

export function stop(child, signal) {
  if (!child.pid) return
  try {
    process.kill(-child.pid, signal)
  } catch (error) {
    if (error.code !== 'ESRCH') throw error
  }
}

export async function terminateProcessGroups(children, { graceMs = 1500 } = {}) {
  const owned = [...children]
  for (const child of owned) stop(child, 'SIGTERM')
  await delay(graceMs)
  // A wrapper's exit status says nothing about descendants in its process group.
  for (const child of owned) stop(child, 'SIGKILL')
  await Promise.race([
    Promise.all(
      owned.map((child) =>
        child.exitCode !== null || child.signalCode !== null || !child.pid
          ? Promise.resolve()
          : new Promise((resolve) => child.once('exit', resolve)),
      ),
    ),
    delay(5000),
  ])
}
