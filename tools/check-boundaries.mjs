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
  if (!file.endsWith('.vue')) continue
  const content = await readFile(file, 'utf8')
  if (/\b(?:\$fetch|fetch|axios|useFetch)\s*[.(]/.test(content))
    violations.push(`${file}: HTTP belongs in API adapters, not UI`)
  if (/from\s+['"][^'"]*\/api\//.test(content))
    violations.push(`${file}: UI must use a feature composable, not import API adapters`)
  if (/\bbg-white\b|\bdark:bg-/.test(content))
    violations.push(`${file}: component surfaces must use semantic tokens`)
}
if (violations.length) {
  console.error(violations.join('\n'))
  process.exit(1)
}
console.log(
  'PASS: Vue UI has no HTTP calls, direct API imports, or hardcoded light/dark surface classes.',
)
