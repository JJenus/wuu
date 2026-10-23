// Live HTML contains verified account identity. Never let shared caches retain it.
export default defineEventHandler((event) => {
  if (
    useRuntimeConfig(event).public.dataMode === 'live' &&
    getHeader(event, 'accept')?.includes('text/html')
  ) {
    setResponseHeader(event, 'cache-control', 'private, no-store')
    setResponseHeader(event, 'vary', 'Cookie')
  }
})
