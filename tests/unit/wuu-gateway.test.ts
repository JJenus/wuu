import { describe, expect, it } from 'vitest'
import { originIsSafe, upstreamPath } from '../../server/utils/wuu-gateway'

describe('BFF allowlist', () => {
  it('maps only known resources and encodes each path segment', () => {
    expect(upstreamPath('vehicles')).toBe('/api/v1/vehicles')
    expect(upstreamPath('vehicles/VH%201')).toBe('/api/v1/vehicles/VH%201')
    expect(upstreamPath('alerts/geofences/GF-1')).toBe('/alerts/geofences/GF-1')
    expect(upstreamPath('notifications')).toBe('/api/notifications')
  })

  it('rejects unknown resources, traversal, encoded separators, and control bytes', () => {
    expect(upstreamPath(undefined)).toBeNull()
    expect(upstreamPath('')).toBeNull()
    expect(upstreamPath('secrets')).toBeNull()
    expect(upstreamPath('constructor')).toBeNull()
    expect(upstreamPath('__proto__')).toBeNull()
    expect(upstreamPath('toString')).toBeNull()
    expect(upstreamPath('../secrets')).toBeNull()
    expect(upstreamPath('vehicles/%2e%2e/secrets')).toBeNull()
    expect(upstreamPath('vehicles/%2Fsecrets')).toBeNull()
    expect(upstreamPath('vehicles/%5Csecrets')).toBeNull()
    expect(upstreamPath('vehicles/%00secrets')).toBeNull()
    expect(upstreamPath('vehicles/%E0%A4%A')).toBeNull()
  })
})

describe('upstream origin policy', () => {
  it('allows HTTPS and explicit local HTTP development origins', () => {
    expect(originIsSafe('https://api.example.com')).toBe(true)
    expect(originIsSafe('https://api.example.com/base')).toBe(true)
    expect(originIsSafe('http://localhost:8080')).toBe(true)
    expect(originIsSafe('http://127.0.0.1:8080')).toBe(true)
    expect(originIsSafe('http://[::1]:8080')).toBe(true)
  })

  it('rejects credentials, query/fragment ambiguity, remote HTTP, and malformed origins', () => {
    expect(originIsSafe('https://user:pass@api.example.com')).toBe(false)
    expect(originIsSafe('https://api.example.com?target=evil')).toBe(false)
    expect(originIsSafe('https://api.example.com#fragment')).toBe(false)
    expect(originIsSafe('http://api.example.com')).toBe(false)
    expect(originIsSafe('http://localhost.evil.example.com')).toBe(false)
    expect(originIsSafe('file:///etc/passwd')).toBe(false)
    expect(originIsSafe('not a url')).toBe(false)
  })
})
