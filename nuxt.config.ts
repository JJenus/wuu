export default defineNuxtConfig({
  compatibilityDate: '2026-10-01',
  modules: ['@nuxt/ui'],
  css: ['~/assets/css/main.css'],
  ui: { fonts: false },
  colorMode: { preference: 'light' },
  devtools: { enabled: false },
  routeRules: {
    '/**': {
      headers: {
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'DENY',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
        'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
      },
    },
  },
  app: {
    head: {
      htmlAttrs: { lang: 'en' },
      title: 'Wayline — Your fleet, in focus',
      meta: [
        {
          name: 'description',
          content:
            'A calmer way to keep your fleet moving. Vehicle visibility, trips, and alerts in one thoughtful workspace.',
        },
      ],
      link: [{ rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' }],
    },
  },
  icon: { provider: 'server', serverBundle: { collections: ['lucide'] } },
  typescript: { strict: true },
})
