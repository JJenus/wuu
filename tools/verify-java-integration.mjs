// Disposable local acceptance only. Never builds or edits the backend.
import { spawn, execFileSync } from 'node:child_process'
import { randomBytes, randomUUID, createHash } from 'node:crypto'
import { createServer } from 'node:net'
import { mkdtempSync, openSync, readFileSync, chmodSync } from 'node:fs'
import { resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { stop, terminateProcessGroups } from './fixture-lifecycle.mjs'

const root = resolve(import.meta.dirname, '..')
const jar = resolve(root, '../sidie/target/tracking-engine-1.0.0-SNAPSHOT.jar')
const digest = createHash('sha256').update(readFileSync(jar)).digest('hex')
const run = `wuu-java-${randomUUID()}`
const directory = mkdtempSync('/tmp/opencode/wuu-java-')
chmodSync(directory, 0o700)
const secret = () => randomBytes(32).toString('hex')
// Do not inherit deployment secrets or load any existing dotenv files.
const environment = {
  PATH: process.env.PATH,
  HOME: process.env.HOME,
  LANG: 'C.UTF-8',
  CI: '1',
  PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
}
const children = new Set()
const containers = []
let cleanupPromise = null
const docker = (args, env = {}) =>
  execFileSync('docker', args, {
    env: { ...environment, ...env },
    encoding: 'utf8',
    timeout: 30000,
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim()

function cleanup() {
  if (!cleanupPromise) cleanupPromise = cleanupOwned()
  return cleanupPromise
}

async function cleanupOwned() {
  await terminateProcessGroups(children)
  for (const name of containers.reverse()) {
    try {
      if (docker(['inspect', '-f', '{{index .Config.Labels "wuu.fixture"}}', name]) === run)
        docker(['rm', '-f', '-v', name])
    } catch {
      console.error(`Cleanup could not confirm removal: ${name}`)
      process.exitCode = 1
    }
  }
}
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.once(signal, () => {
    void cleanup().finally(() => process.exit(signal === 'SIGINT' ? 130 : 143))
  })
}
function start(command, args, env, log) {
  const fd = openSync(`${directory}/${log}`, 'a', 0o600)
  const child = spawn(command, args, {
    cwd: root,
    env: { ...environment, ...env },
    stdio: ['ignore', fd, fd],
    detached: true,
  })
  children.add(child)
  child.once('error', () => {}) // completed()/wait() report a sanitized failure.
  return child
}
async function completed(child, label) {
  const timeout = setTimeout(() => stop(child, 'SIGKILL'), 300000)
  const code = await new Promise((res, rej) => {
    child.once('exit', res)
    child.once('error', rej)
  })
  clearTimeout(timeout)
  if (code !== 0) throw new Error(`${label} failed; private receipt: ${directory}`)
  console.log(`PASS ${label}`)
}
async function wait(label, check, child) {
  for (let attempt = 0; attempt < 120; attempt++) {
    if (child && (child.exitCode !== null || !child.pid)) break
    try {
      if (await check()) return
    } catch {}
    await delay(1000)
  }
  throw new Error(`${label} not ready; private receipt: ${directory}`)
}
async function reservePort() {
  const server = createServer()
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  return {
    port: server.address().port,
    release: () => new Promise((resolve) => server.close(resolve)),
  }
}
function container(kind, image, internalPort, args, env, command = []) {
  const name = `${run}-${kind}`
  containers.push(name) // Register before starting, including interrupted startup.
  docker(
    [
      'run',
      '-d',
      '--name',
      name,
      '--label',
      `wuu.fixture=${run}`,
      '-p',
      `127.0.0.1::${internalPort}`,
      ...args,
      image,
      ...command,
    ],
    env,
  )
  const mapped = docker(['port', name, `${internalPort}/tcp`])
  if (!/^127\.0\.0\.1:\d+$/.test(mapped)) throw new Error('Unsafe fixture port mapping')
  return { name, port: Number(mapped.split(':')[1]) }
}

try {
  console.log(`Receipt directory: ${directory}\nExisting JAR SHA256: ${digest}`)
  docker(['info'])
  await completed(
    start('pnpm', ['exec', 'nuxt', 'build', '--dotenv', '/dev/null'], {}, 'build.log'),
    'production build',
  )
  const password = secret()
  const redisPassword = secret()
  const brokerPassword = secret()
  const pg = container(
    'postgres',
    'postgres:17-alpine',
    5432,
    ['-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_USER=wuu_fixture', '-e', 'POSTGRES_DB=wuu_fixture'],
    { POSTGRES_PASSWORD: password },
  )
  const redis = container(
    'redis',
    'redis:7-alpine',
    6379,
    ['-e', 'REDISCLI_AUTH', '--entrypoint', '/bin/sh'],
    { REDISCLI_AUTH: redisPassword },
    ['-c', 'exec redis-server --requirepass "$REDISCLI_AUTH"'],
  )
  const broker = container(
    'artemis',
    'apache/activemq-artemis:2.40.0-alpine',
    61616,
    [
      '-e',
      'ARTEMIS_USER=wuu_fixture',
      '-e',
      'ARTEMIS_PASSWORD',
      '-e',
      'ANONYMOUS_LOGIN=false',
      '-e',
      'EXTRA_ARGS=--http-host 0.0.0.0 --relax-jolokia --no-autotune --java-memory 512M',
    ],
    { ARTEMIS_PASSWORD: brokerPassword },
  )
  await wait('PostgreSQL', () =>
    docker(['exec', pg.name, 'pg_isready', '-U', 'wuu_fixture']).includes('accepting'),
  )
  await wait('Redis', () => docker(['exec', redis.name, 'redis-cli', 'ping']) === 'PONG')
  await wait('Artemis', () => docker(['logs', broker.name]).includes('AMQ221007'))
  const backendReservation = await reservePort()
  const frontendReservation = await reservePort()
  const backendPort = backendReservation.port
  const frontendPort = frontendReservation.port
  const backend = `http://127.0.0.1:${backendPort}`
  const frontend = `http://127.0.0.1:${frontendPort}`
  // Hold each reservation until immediately before its process starts, rather
  // than releasing early and hoping the port remains free during container startup.
  await backendReservation.release()
  const java = start(
    'java',
    ['-jar', jar],
    {
      SPRING_PROFILES_ACTIVE: 'postgres',
      SERVER_ADDRESS: '127.0.0.1',
      SERVER_PORT: String(backendPort),
      DB_URL: `jdbc:postgresql://127.0.0.1:${pg.port}/wuu_fixture`,
      DB_USERNAME: 'wuu_fixture',
      DB_PASSWORD: password,
      REDIS_HOST: '127.0.0.1',
      REDIS_PORT: String(redis.port),
      REDIS_PASSWORD: redisPassword,
      ARTEMIS_BROKER_URL: `tcp://127.0.0.1:${broker.port}`,
      ARTEMIS_USER: 'wuu_fixture',
      ARTEMIS_PASSWORD: brokerPassword,
      JWT_SECRET: secret(),
      REGISTRATION_ENABLED: 'true',
      LOGGING_LEVEL_ROOT: 'WARN',
      LOGGING_LEVEL_ORG_HIBERNATE_SQL: 'WARN',
      LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_JMS: 'WARN',
    },
    'java.log',
  )
  await wait(
    'Java health',
    async () =>
      (await fetch(`${backend}/actuator/health`, { signal: AbortSignal.timeout(2000) })).ok,
    java,
  )
  console.log('PASS Java /actuator/health (PostgreSQL, Redis, Artemis)')
  await frontendReservation.release()
  const nuxt = start(
    'node',
    ['.output/server/index.mjs'],
    {
      HOST: '127.0.0.1',
      PORT: String(frontendPort),
      NUXT_PUBLIC_DATA_MODE: 'live',
      NUXT_API_ORIGIN: backend,
      NUXT_SESSION_PASSWORD: secret(),
      NUXT_LOCAL_ACCOUNT_PICKER: 'false',
    },
    'nuxt.log',
  )
  await wait(
    'Nuxt',
    async () => (await fetch(`${frontend}/sign-in`, { signal: AbortSignal.timeout(2000) })).ok,
    nuxt,
  )
  await completed(
    start(
      'pnpm',
      ['exec', 'playwright', 'test', '--config', 'playwright.deployed.config.ts'],
      {
        PLAYWRIGHT_BASE_URL: frontend,
        WUU_DEPLOYED_FIXTURE: '1',
        WUU_DEPLOYED_API: backend,
        WUU_DEPLOYED_OUTPUT: `${directory}/results`,
      },
      'browser.log',
    ),
    'deployed Java browser acceptance',
  )
  if (createHash('sha256').update(readFileSync(jar)).digest('hex') !== digest)
    throw new Error('Backend JAR changed during acceptance')
} catch (error) {
  // Do not print command exceptions: their arguments/environment may contain secrets.
  console.error(
    error instanceof Error && !('cmd' in error)
      ? error.message
      : `Fixture failed; private receipt: ${directory}`,
  )
  process.exitCode = 1
} finally {
  await cleanup()
  console.log('Owned fixture cleanup finished (label-checked; no shared services removed)')
}
