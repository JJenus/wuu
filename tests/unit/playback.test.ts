import { describe, expect, it } from 'vitest'
import { sampleTrack, trackTrail, advancePlayback, formatElapsed } from '../../app/features/tracking/domain/playback'
import { movementTrack, tripTrack } from '../../app/features/tracking/api/preview-tracks'

const track = [
  { seconds: 0, x: 10, y: 20, speed: 0 },
  { seconds: 10, x: 30, y: 40, speed: 40 },
  { seconds: 20, x: 50, y: 40, speed: 0 },
]
describe('deterministic playback', () => {
  it('interpolates position and speed, clamps endpoints, and handles missing samples', () => {
    expect(sampleTrack([], 2)).toBeNull()
    expect(sampleTrack(track, -2)).toEqual(track[0])
    expect(sampleTrack(track, Infinity)).toEqual(track[0])
    expect(sampleTrack(track, 30)).toEqual(track[2])
    expect(sampleTrack(track, 5)).toEqual({seconds: 5, x:20, y:30, speed:20})
    expect(sampleTrack([track[0]!], 5)).toEqual(track[0])
    expect(sampleTrack(track, 10)).toEqual(track[1])
  })
  it('builds a traveled trail without future samples or mutating the source', () => {
    expect(trackTrail([], 0)).toEqual([])
    expect(trackTrail(track, 5)).toEqual([track[0], {seconds:5,x:20,y:30,speed:20}])
    expect(trackTrail(track, 20)).toEqual(track)
    const result=trackTrail(track, 0)
    result[0]!.x=999
    expect(track[0]!.x).toBe(10)
  })
  it('supports bounded replay, seamless loops, invalid inputs and elapsed labels', () => {
    expect(advancePlayback(9, 2, 4, 20, false)).toBe(17)
    expect(advancePlayback(19, 1, 4, 20, false)).toBe(20)
    expect(advancePlayback(19, 1, 4, 20, true)).toBe(3)
    expect(advancePlayback(0, 1, 1, 0, true)).toBe(0)
    expect(advancePlayback(NaN, 1, 1, 20, false)).toBe(0)
    expect(advancePlayback(5, -1, 1, 20, false)).toBe(5)
    expect(formatElapsed(65)).toBe('01:05')
    expect(formatElapsed(-1)).toBe('00:00')
    expect(formatElapsed(NaN)).toBe('00:00')
  })
})
describe('explicit preview tracks', () => {
  it('only moving vehicles have a looping track starting at their sample position', () => {
    expect(movementTrack({x:46,y:51,status:'moving'})[0]).toMatchObject({x:46,y:51})
    const route=movementTrack({x:46,y:51,status:'moving'})
    expect(route.at(-1)).toMatchObject({x:46,y:51})
    for(const status of ['idle','parked','offline'] as const)expect(movementTrack({x:10,y:20,status})).toEqual([])
  })
  it('returns isolated illustrative replay fixtures and never invents unknown trips', () => {
    const first=tripTrack('TR-1048')
    expect(first.length).toBeGreaterThan(2)
    first[0]!.x=999
    expect(tripTrack('TR-1048')[0]!.x).not.toBe(999)
    expect(tripTrack('unknown')).toEqual([])
    for(const id of ['TR-1047','TR-1046','TR-1045','TR-1044'])expect(tripTrack(id).length).toBeGreaterThan(2)
  })
})
