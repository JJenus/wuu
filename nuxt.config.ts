export default defineNuxtConfig({
  compatibilityDate: '2026-10-01',
  modules: ['@nuxt/ui'],
  css: [
    'leaflet/dist/leaflet.css',
    'leaflet.markercluster/dist/MarkerCluster.css',
    '~/assets/css/main.css',
    '~/assets/css/maps.css',
  ],
  runtimeConfig: {
    public: {
      mapTiles: {
        url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
        attribution: 'OpenStreetMap contributors',
        attributionUrl: 'https://www.openstreetmap.org/copyright',
        maxZoom: 19,
      },
    },
  },
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
      title: 'Wuu — Your fleet, in focus',
      meta: [
        { name: 'application-name', content: 'Wuu' },
        { name: 'apple-mobile-web-app-title', content: 'Wuu' },
        { property: 'og:site_name', content: 'Wuu' },
        {
          name: 'description',
          content:
            'A calmer way to keep your fleet moving. Vehicle visibility, trips, and alerts in one thoughtful workspace.',
        },
      ],
      link: [
        { rel: 'icon', type: 'image/x-icon', href: '/favicon.ico' },
        { rel: 'icon', type: 'image/png', sizes: '32x32', href: '/favicon-32.png' },
        { rel: 'apple-touch-icon', sizes: '180x180', href: '/apple-touch-icon.png' },
        { rel: 'manifest', href: '/site.webmanifest' },
      ],
    },
  },
  icon: { provider: 'server', serverBundle: { collections: ['lucide'] } },
  typescript: { strict: true },
})
