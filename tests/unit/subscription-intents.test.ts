import { describe, expect, it } from 'vitest'
import { createEnrollmentIntents } from '../../app/features/subscriptions/domain/enrollment-intents'

describe('enrollment intent recovery', () => {
  it('retains an ambiguous request key after reload, scoped to owner and pinned version', () => {
    const values = new Map<string, string>()
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => {
        values.set(key, value)
      },
      removeItem: (key: string) => {
        values.delete(key)
      },
    }
    const first = createEnrollmentIntents(storage, 'tenant1:user1', () => 'key-1')
    expect(first.key('plan:1')).toBe('key-1')
    const reloaded = createEnrollmentIntents(storage, 'tenant1:user1', () => 'key-2')
    expect(reloaded.key('plan:1')).toBe('key-1')
    expect(reloaded.key('plan:2')).toBe('key-2')
    expect(createEnrollmentIntents(storage, 'tenant2:user1', () => 'key-3').key('plan:1')).toBe(
      'key-3',
    )
    reloaded.confirm('plan:1')
    expect(reloaded.key('plan:1')).toBe('key-2')
  })
  it('fails closed if recovery storage is unavailable rather than sending an unremembered mutation', () => {
    const storage = {
      getItem: () => null,
      setItem: () => {
        throw new Error('denied')
      },
      removeItem: () => {},
    }
    expect(() =>
      createEnrollmentIntents(storage, 'tenant:user', () => 'key').key('plan:1'),
    ).toThrow('denied')
  })
})
