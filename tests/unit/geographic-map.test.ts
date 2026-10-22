import {describe,it,expect} from 'vitest'
import {validCoordinate,normalizeLocation,circleEdge,tileConfiguration} from '../../app/features/maps/domain/geographic'
describe('geographic map contract',()=>{
 it('accepts real coordinates, rejects missing/nonfinite/out-of-range locations without fallback',()=>{
   expect(validCoordinate({lat:37.77,lng:-122.4})).toBe(true)
   for(const p of [null,undefined,{lat:NaN,lng:0},{lat:91,lng:0},{lat:0,lng:181}])expect(validCoordinate(p)).toBe(false)
   expect(normalizeLocation({latitude:37.77,longitude:-122.4})).toEqual({lat:37.77,lng:-122.4})
   expect(normalizeLocation({latitude:null,longitude:0})).toBeNull()
   expect(normalizeLocation(undefined)).toBeNull()
   expect(normalizeLocation({latitude:0,longitude:0})).toEqual({lat:0,lng:0})
 })
 it('places a circle radius handle a real meter-based distance east of its center',()=>{
   const edge=circleEdge({lat:37.77,lng:-122.4},500)
   expect(edge.lat).toBeCloseTo(37.77,4)
   expect(edge.lng).toBeGreaterThan(-122.4)
   expect(edge.lng).toBeLessThan(-122.39)
 })
 it('requires a safe configurable tile template and attribution',()=>{
   expect(tileConfiguration({url:'https://tile.openstreetmap.org/{z}/{x}/{y}.png',attribution:'OpenStreetMap',attributionUrl:'https://www.openstreetmap.org/copyright',maxZoom:19}).maxZoom).toBe(19)
   for(const url of ['javascript:alert(1)','http://evil.invalid/{z}/{x}/{y}','https://tiles.invalid/no-template'])expect(()=>tileConfiguration({url,attribution:'Tiles',attributionUrl:'https://example.com',maxZoom:19})).toThrow()
   expect(()=>tileConfiguration({url:'https://tiles.invalid/{z}/{x}/{y}',attribution:'',attributionUrl:'https://example.com',maxZoom:19})).toThrow()
 })
})
