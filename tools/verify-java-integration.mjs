// Disposable local acceptance only. Never builds or edits the backend.
import { spawn, execFileSync } from 'node:child_process'
import { randomBytes, randomUUID, createHash } from 'node:crypto'
import { createServer, createConnection } from 'node:net'
import { mkdtempSync, openSync, readFileSync, chmodSync, mkdirSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { stop, terminateProcessGroups } from './fixture-lifecycle.mjs'
import {
  fullStartOptions,
  startupReceipt,
  startupGuard,
  assertGroupsStopped,
} from './full-start-policy.mjs'

const root = resolve(import.meta.dirname, '..')
const receiptDirectory = resolve(root, process.env.WUU_VERIFICATION_DIRECTORY || '.verification')
mkdirSync(receiptDirectory, { recursive: true, mode: 0o700 })
// Invalidate earlier success before any preflight can throw or the process dies.
writeFileSync(
  resolve(receiptDirectory, 'last-startup.json'),
  JSON.stringify({ status: 'FAIL', stage: 'initializing', time: new Date().toISOString() }),
  { mode: 0o600 },
)
const options = fullStartOptions(process.argv.includes('--full-start'), process.env)
const deviceJar = options.full ? resolve(root, options.deviceJar) : null
const deviceDigest = deviceJar
  ? createHash('sha256').update(readFileSync(deviceJar)).digest('hex')
  : null
const jar = resolve(
  root,
  process.env.WUU_JAVA_JAR || '../sidie/target/tracking-engine-1.0.0-SNAPSHOT.jar',
)
const digest = createHash('sha256').update(readFileSync(jar)).digest('hex')
function revision(path) {
  const git = (args) =>
    execFileSync('git', ['-C', path, ...args], { encoding: 'utf8', timeout: 10000 })
  const hash = createHash('sha256').update(git(['diff', '--binary', 'HEAD']))
  for (const file of git(['ls-files', '--others', '--exclude-standard', '-z'])
    .split('\0')
    .filter(Boolean))
    hash.update(file).update(readFileSync(resolve(path, file)))
  return { commit: git(['rev-parse', 'HEAD']).trim(), worktreeHash: hash.digest('hex') }
}
const revisions = {
  frontend: revision(root),
  backend: revision(resolve(dirname(jar), '..')),
  device: deviceJar ? revision(resolve(dirname(deviceJar), '../..')) : null,
}
const passed = []
let revisionsStable = false
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
const reservations = new Set()
const guard = startupGuard()
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
  for (const reservation of reservations) reservation.close()
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
  for (const child of children)
    if (child.pid) {
      try {
        process.kill(-child.pid, 0)
        process.exitCode = 1
      } catch (error) {
        if (error.code !== 'ESRCH') process.exitCode = 1
      }
    }
  if (!process.exitCode) passed.push('cleanup')
}
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.once(signal, () => {
    guard.abort()
    process.exitCode = signal === 'SIGINT' ? 130 : 143
    void cleanup().finally(() => process.exit(signal === 'SIGINT' ? 130 : 143))
  })
}
function start(command, args, env, log) {
  guard.check()
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
    guard.check()
    if (child && (child.exitCode !== null || child.signalCode !== null || !child.pid)) break
    try {
      if (await check()) {
        guard.check()
        if (child && (child.exitCode !== null || child.signalCode !== null)) break
        return
      }
    } catch {}
    await delay(1000)
  }
  throw new Error(`${label} not ready; private receipt: ${directory}`)
}
async function reservePort() {
  guard.check()
  const server = createServer()
  reservations.add(server)
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  return {
    port: server.address().port,
    release: () =>
      new Promise((resolve) =>
        server.close(() => {
          reservations.delete(server)
          resolve()
        }),
      ),
  }
}
function container(kind, image, internalPort, args, env, command = []) {
  guard.check()
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
  passed.push('dependencies')
  const backendReservation = await reservePort()
  const frontendReservation = await reservePort()
  const backendPort = backendReservation.port
  const frontendPort = frontendReservation.port
  const backend = `http://127.0.0.1:${backendPort}`
  const frontend = `http://127.0.0.1:${frontendPort}`
  // Hold each reservation until immediately before its process starts, rather
  // than releasing early and hoping the port remains free during container startup.
  await backendReservation.release()
  const javaEnvironment = {
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
    // Exercise real rotation without waiting for the default 15-minute TTL.
    USERAUTH_JWT_ACCESS_TOKEN_EXPIRY: '2m',
    REGISTRATION_ENABLED: 'true',
    LOGGING_LEVEL_ROOT: 'WARN',
    LOGGING_LEVEL_ORG_HIBERNATE_SQL: 'WARN',
    LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_JMS: 'WARN',
  }
  let java = start('java', ['-jar', jar], javaEnvironment, 'java.log')
  await wait(
    'Java health',
    async () =>
      (await fetch(`${backend}/actuator/health`, { signal: AbortSignal.timeout(2000) })).ok,
    java,
  )
  console.log('PASS Java /actuator/health (PostgreSQL, Redis, Artemis)')
  passed.push('backend')
  let device
  let deviceEnvironment
  let devicePort
  async function deviceDependencies(log) {
    const frame =
      '*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#'
    await new Promise((resolve, reject) => {
      const socket = createConnection({ host: '127.0.0.1', port: devicePort })
      socket.setTimeout(3000)
      socket.once('connect', () => socket.end(frame, resolve))
      socket.once('error', reject)
      socket.once('timeout', () => {
        socket.destroy()
        reject(new Error('Device probe timed out'))
      })
    })
    await wait(
      'Device real broker publication',
      () =>
        /Published event .* to tracking\.events\.locationdataevent/.test(
          readFileSync(`${directory}/${log}`, 'utf8'),
        ),
      device,
    )
    console.log(
      'PASS synthetic device packet published through real Artemis (not hardware or persisted owner telemetry)',
    )
  }
  async function deviceReady() {
    return new Promise((resolve) => {
      const socket = createConnection({ host: '127.0.0.1', port: devicePort })
      socket.setTimeout(1000)
      socket.once('connect', () => {
        socket.destroy()
        resolve(true)
      })
      socket.once('error', () => {
        socket.destroy()
        resolve(false)
      })
      socket.once('timeout', () => {
        socket.destroy()
        resolve(false)
      })
    })
  }
  if (options.full) {
    const reservation = await reservePort()
    devicePort = reservation.port
    deviceEnvironment = {
      TCP_HOST: '127.0.0.1',
      TCP_PORT: String(devicePort),
      REDIS_HOST: '127.0.0.1',
      REDIS_PORT: String(redis.port),
      SPRING_DATA_REDIS_PASSWORD: redisPassword,
      ARTEMIS_URL: `tcp://127.0.0.1:${broker.port}`,
      ARTEMIS_USER: 'wuu_fixture',
      ARTEMIS_PASSWORD: brokerPassword,
      LOGGING_LEVEL_ROOT: 'WARN',
      LOGGING_LEVEL_COM_SURF_TRACKER_SHARED_PUBSUB: 'INFO',
    }
    await reservation.release()
    device = start('java', ['-jar', deviceJar], deviceEnvironment, 'device.log')
    await wait('Independent device TCP', deviceReady, device)
    await deviceDependencies('device.log')
    passed.push('device')
    console.log('PASS independent loopback device gateway startup')
  }
  // Test-only catalog provisioning in this runner's disposable database. No user
  // elevation or production configuration; browser enrollment still uses real APIs.
  docker([
    'exec',
    pg.name,
    'psql',
    '-U',
    'wuu_fixture',
    '-d',
    'wuu_fixture',
    '-v',
    'ON_ERROR_STOP=1',
    '-c',
    `
    insert into subscription_plan_versions
      (id, plan_id, version, model, cadence, meter, usage_limit, amount, currency, created_at)
    values
      ('00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000001', 1, 'USAGE_TOKEN', null, 'locations', 100, 2.5, 'EUR', now()),
      ('00000000-0000-4000-8000-000000000012', '00000000-0000-4000-8000-000000000002', 1, 'PERIODIC', 'MONTHLY', 'locations', 1000, 10, 'EUR', now()),
      ('00000000-0000-4000-8000-000000000013', '00000000-0000-4000-8000-000000000003', 1, 'PERIODIC', 'YEARLY', 'locations', 12000, 100, 'EUR', now())
  `,
  ])
  console.log('PASS disposable token/monthly/yearly catalog fixture provisioned')
  await frontendReservation.release()
  const nuxtEnvironment = {
    HOST: '127.0.0.1',
    PORT: String(frontendPort),
    NUXT_PUBLIC_DATA_MODE: 'live',
    NUXT_API_ORIGIN: backend,
    NUXT_SESSION_PASSWORD: secret(),
    NUXT_LOCAL_ACCOUNT_PICKER: 'false',
  }
  let nuxt = start('node', ['.output/server/index.mjs'], nuxtEnvironment, 'nuxt.log')
  await wait(
    'Nuxt',
    async () => (await fetch(`${frontend}/sign-in`, { signal: AbortSignal.timeout(2000) })).ok,
    nuxt,
  )
  passed.push('frontend')
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
  passed.push('browser')
  if (options.full) {
    const marker = docker([
      'exec',
      pg.name,
      'psql',
      '-U',
      'wuu_fixture',
      '-d',
      'wuu_fixture',
      '-tAc',
      'select count(*) from tracker_alerts where resolved = true',
    ])
    if (Number(marker) < 1) throw new Error('No persisted resolved alert before restart')
    const previous = new Set([nuxt, java, device])
    await terminateProcessGroups(previous)
    assertGroupsStopped(previous)
    java = start('java', ['-jar', jar], javaEnvironment, 'java-restart.log')
    await wait(
      'Restarted Java health',
      async () =>
        (await fetch(`${backend}/actuator/health`, { signal: AbortSignal.timeout(2000) })).ok,
      java,
    )
    device = start('java', ['-jar', deviceJar], deviceEnvironment, 'device-restart.log')
    await wait('Restarted independent device', deviceReady, device)
    await deviceDependencies('device-restart.log')
    nuxt = start('node', ['.output/server/index.mjs'], nuxtEnvironment, 'nuxt-restart.log')
    await wait(
      'Restarted Nuxt',
      async () => (await fetch(`${frontend}/sign-in`, { signal: AbortSignal.timeout(2000) })).ok,
      nuxt,
    )
    passed.push('restart')
    if (
      docker([
        'exec',
        pg.name,
        'psql',
        '-U',
        'wuu_fixture',
        '-d',
        'wuu_fixture',
        '-tAc',
        'select count(*) from tracker_alerts where resolved = true',
      ]) !== marker
    )
      throw new Error('Persisted alert state changed across restart')
    await completed(
      start(
        'pnpm',
        ['exec', 'playwright', 'test', '--config', 'playwright.deployed.config.ts'],
        {
          PLAYWRIGHT_BASE_URL: frontend,
          WUU_DEPLOYED_FIXTURE: '1',
          WUU_DEPLOYED_API: backend,
          WUU_DEPLOYED_OUTPUT: `${directory}/restart-results`,
        },
        'browser-restart.log',
      ),
      'post-restart real browser acceptance',
    )
    passed.push('persistence')
  }
  if (createHash('sha256').update(readFileSync(jar)).digest('hex') !== digest)
    throw new Error('Backend JAR changed during acceptance')
  if (
    deviceJar &&
    createHash('sha256').update(readFileSync(deviceJar)).digest('hex') !== deviceDigest
  )
    throw new Error('Device JAR changed during acceptance')
  revisionsStable =
    JSON.stringify(revisions) ===
    JSON.stringify({
      frontend: revision(root),
      backend: revision(resolve(dirname(jar), '..')),
      device: deviceJar ? revision(resolve(dirname(deviceJar), '../..')) : null,
    })
  if (!revisionsStable) throw new Error('Source revisions changed during acceptance')
  guard.check()
  for (const service of [java, nuxt, ...(device ? [device] : [])]) {
    if (!service.pid || service.exitCode !== null || service.signalCode !== null)
      throw new Error('An owned service exited before acceptance completed')
    process.kill(-service.pid, 0)
  }
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
  const receipt = {
    ...startupReceipt({ full: options.full, passed, revisionsStable }),
    time: new Date().toISOString(),
    revisions,
    backendSha256: digest,
    deviceSha256: deviceDigest,
    privateLogs: directory,
  }
  if (process.exitCode) receipt.status = 'FAIL'
  if (options.full && receipt.status !== 'PASS') process.exitCode = 1
  writeFileSync(resolve(receiptDirectory, 'last-startup.json'), JSON.stringify(receipt, null, 2), {
    mode: 0o600,
  })
  console.log(`${receipt.status} startup receipt: ${receiptDirectory}/last-startup.json`)
}
