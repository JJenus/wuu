import { readFileSync, existsSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('Wuu brand assets', () => {
  it('ships transparent square PNG exports and appropriately sized platform icons', () => {
    for (const [file,size] of [['public/brand/logo.png',512],['public/brand/logo-96.png',96],['public/brand/logo-192.png',192],['public/favicon-32.png',32],['public/apple-touch-icon.png',180],['public/icon-192.png',192],['public/icon-512.png',512]] as const) {
      expect(existsSync(file),`${file} exists`).toBe(true)
      const png=readFileSync(file)
      expect(png.subarray(1,4).toString()).toBe('PNG')
      expect(png.readUInt32BE(16)).toBe(size)
      expect(png.readUInt32BE(20)).toBe(size)
      if(file.includes('/brand/'))expect(png[25]).toBe(6)
    }
    for(const size of [96,192]){
      const webp=readFileSync(`public/brand/logo-${size}.webp`)
      expect(webp.subarray(0,4).toString()).toBe('RIFF')
      expect(webp.subarray(8,12).toString()).toBe('WEBP')
      expect(webp.length).toBeLessThan(25_000)
    }
    const ico=readFileSync('public/favicon.ico')
    expect(ico.readUInt16LE(2)).toBe(1)
    expect(ico.readUInt16LE(4)).toBe(3)
  })
  it('names the app Wuu and wires only the new icon set', () => {
    expect(JSON.parse(readFileSync('package.json','utf8')).name).toBe('wuu')
    const manifest=JSON.parse(readFileSync('public/site.webmanifest','utf8'))
    expect(manifest.name).toBe('Wuu')
    expect(manifest.short_name).toBe('Wuu')
    for(const icon of manifest.icons)expect(existsSync(`public${icon.src}`)).toBe(true)
    const config=readFileSync('nuxt.config.ts','utf8')
    expect(config).toContain('Wuu — Your fleet, in focus')
    expect(config).toContain('/favicon.ico')
    expect(config).toContain('/apple-touch-icon.png')
    expect(config).toContain('/site.webmanifest')
    expect(config).not.toContain('/favicon.svg')
  })
})
