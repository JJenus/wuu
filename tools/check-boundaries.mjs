import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'

async function walk(root) {
  const result = []
  for (const entry of await readdir(root, { withFileTypes: true })) {
    const path = join(root, entry.name)
    result.push(...(entry.isDirectory() ? await walk(path) : [path]))
  }
  return result
}
const violations = []
for (const file of await walk('app')) {
  if (!/\.(?:vue|ts)$/.test(file)) continue
  const content = await readFile(file, 'utf8')
  const ui = file.endsWith('.vue') || /\/(?:components|pages)\//.test(file)
  const workflow = /\/composables\//.test(file)
  if (
    (ui || workflow) &&
    /\b(?:\$fetch|fetch|axios|useFetch|useLazyFetch|useRequestFetch)\s*(?:<[^>]+>)?\s*[.(]/.test(
      content,
    )
  )
    violations.push(`${file}: HTTP belongs in API adapters, not UI/workflows`)
  if (ui && /(?:from\s*|import\s*\()\s*['"][^'"]*\/api\//.test(content))
    violations.push(`${file}: UI must use a feature composable, not import API adapters`)
  if (
    /['"`]\/api\/v\d/.test(content) ||
    /(?:from\s*|import\s*\()\s*['"][^'"]*(?:server\/|upstream-api)/.test(content)
  )
    violations.push(
      `${file}: upstream routes and version selection belong exclusively to the server gateway`,
    )
  if (ui && /['"`]\/api\//.test(content))
    violations.push(`${file}: UI must not address API URLs directly`)
  if (file.endsWith('.vue') && /\bbg-white\b|\bdark:bg-/.test(content))
    violations.push(`${file}: component surfaces must use semantic tokens`)
}
for (const file of await walk('server')) {
  if (!file.endsWith('.ts') || file === 'server/utils/upstream-api.ts') continue
  if (/['"`]\/api\/v\d/.test(await readFile(file, 'utf8')))
    violations.push(
      `${file}: use the server upstream-api registry instead of repeating the API version`,
    )
}
if (violations.length) {
  console.error(violations.join('\n'))
  process.exit(1)
}
console.log(
  'PASS: UI/workflows have no direct HTTP; upstream version selection is centralized server-side; UI uses semantic surfaces.',
)
