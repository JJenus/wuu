<script setup lang="ts">
import type { Vehicle } from '../domain/fleet'
const props = defineProps<{ vehicles: Vehicle[]; full?: boolean }>()
const selected = ref<string | null>('VH-001')
const zoom = ref(1)
const layers = ref(true)
function resetView() {
  zoom.value = 1
  selected.value = null
}
const current = computed(() => props.vehicles.find((v) => v.id === selected.value))
const streets = Array.from({ length: 25 }, (_, i) => i * 34)
const avenues = Array.from({ length: 25 }, (_, i) => i * 30)
</script>

<template>
  <div class="fleet-map" :class="{ 'full-map': full }">
    <div class="map-canvas" :style="{ transform: `scale(${zoom})` }">
      <svg
        class="map-art"
        viewBox="0 0 900 490"
        preserveAspectRatio="xMidYMid slice"
        aria-hidden="true"
      >
        <defs>
          <clipPath id="map-land-clip">
            <path
              d="M0 0H568L605 30 631 66 655 79 660 108 698 142 704 181 694 201 728 227 710 254 739 277 717 300 759 324 744 349 778 381 769 410 806 450 824 490H0Z"
            />
          </clipPath>
          <pattern
            id="blocks"
            width="34"
            height="30"
            patternUnits="userSpaceOnUse"
            patternTransform="rotate(-18)"
          >
            <rect width="34" height="30" fill="var(--map-land)" />
            <rect x="4" y="4" width="26" height="22" rx="2" fill="var(--map-block)" />
            <path d="M0 0H34M0 0V30" stroke="var(--map-street)" stroke-width="4" />
          </pattern>
          <pattern id="water" width="22" height="22" patternUnits="userSpaceOnUse">
            <path
              d="M0 11q5-3 11 0t11 0"
              fill="none"
              stroke="var(--map-water-line)"
              stroke-width=".5"
              opacity=".45"
            />
          </pattern>
          <filter id="routeShadow">
            <feDropShadow
              dx="0"
              dy="2"
              stdDeviation="3"
              flood-color="#0d9488"
              flood-opacity=".15"
            />
          </filter>
        </defs>
        <rect width="900" height="490" fill="var(--map-water)" />
        <rect width="900" height="490" fill="url(#water)" />
        <path
          d="M0 0H568L605 30 631 66 655 79 660 108 698 142 704 181 694 201 728 227 710 254 739 277 717 300 759 324 744 349 778 381 769 410 806 450 824 490H0Z"
          fill="url(#blocks)"
          stroke="var(--map-coast)"
          stroke-width="2"
        />
        <g fill="var(--map-park)" stroke="var(--map-park-border)" stroke-width="1">
          <path d="M0 62 184 34 208 104 15 157Z" />
          <path d="M0 236 242 160 258 210 0 299Z" />
          <path d="m313 279 49-15 22 47-52 18Z" />
          <path d="m383 75 38-12 13 39-40 13Z" />
          <path d="m438 362 54-14 19 50-55 15Z" />
          <path d="m601 356 37-10 15 39-39 12Z" />
        </g>
        <g clip-path="url(#map-land-clip)">
          <g
            transform="rotate(-18 450 245)"
            stroke="var(--map-street)"
            stroke-width="5"
            opacity=".85"
          >
            <path v-for="street in streets" :key="`s${street}`" :d="`M${street - 100} -200V800`" />
            <path v-for="avenue in avenues" :key="`a${avenue}`" :d="`M-100 ${avenue - 100}H800`" />
          </g>
        </g>
        <path
          d="M-30 376 155 315 314 254 478 200 592 146 676 146M-20 148 177 103 414 64 586 35M381 510 396 418 419 331 478 200 538 84 565-20"
          fill="none"
          stroke="var(--map-highway-border)"
          stroke-width="9"
        />
        <path
          d="M-30 376 155 315 314 254 478 200 592 146 676 146M-20 148 177 103 414 64 586 35M381 510 396 418 419 331 478 200 538 84 565-20"
          fill="none"
          stroke="var(--map-highway)"
          stroke-width="5"
        />
        <path d="M650 137 900 29" stroke="var(--map-coast)" stroke-width="11" />
        <path d="M650 137 900 29" stroke="var(--map-street)" stroke-width="7" />
        <path
          d="m522 420-22-57-55-34-28-78 66-23 35-74 81-22 42 25"
          fill="none"
          stroke="var(--map-street)"
          stroke-width="8"
          stroke-linecap="round"
          filter="url(#routeShadow)"
        />
        <path
          d="m522 420-22-57-55-34-28-78 66-23 35-74 81-22 42 25"
          fill="none"
          stroke="var(--ui-primary)"
          stroke-width="4"
          stroke-linecap="round"
          stroke-dasharray="7 5"
        />
        <g
          font-family="Arial,sans-serif"
          text-anchor="middle"
          fill="var(--map-label)"
          font-size="10"
          letter-spacing="1.8"
        >
          <text x="301" y="87">PACIFIC HEIGHTS</text>
          <text x="492" y="58">RUSSIAN HILL</text>
          <text x="567" y="111">NORTH BEACH</text>
          <text x="584" y="201">FINANCIAL DISTRICT</text>
          <text x="341" y="193">WESTERN ADDITION</text>
          <text x="451" y="301">SOMA</text>
          <text x="273" y="292">HAYES VALLEY</text>
          <text x="295" y="416">MISSION DISTRICT</text>
          <text x="590" y="414">MISSION BAY</text>
          <text x="108" y="196">RICHMOND</text>
        </g>
        <g font-family="Arial,sans-serif" fill="var(--map-road-label)" font-size="9">
          <text x="160" y="124" transform="rotate(-12 160 124)">California St</text>
          <text x="482" y="222" transform="rotate(-25 482 222)">Market Street</text>
          <text x="430" y="372" transform="rotate(-18 430 372)">16th Street</text>
          <text x="185" y="259" transform="rotate(-18 185 259)">Geary Boulevard</text>
        </g>
        <text
          x="775"
          y="240"
          fill="var(--map-water-label)"
          font-family="Georgia,serif"
          font-style="italic"
          font-size="19"
          text-anchor="middle"
          transform="rotate(-22 775 240)"
        >
          San Francisco Bay
        </text>
        <text
          x="131"
          y="87"
          fill="var(--map-label)"
          font-family="Arial,sans-serif"
          font-size="10"
          text-anchor="middle"
        >
          PRESIDIO
        </text>
      </svg>
      <button
        v-for="vehicle in vehicles"
        :key="vehicle.id"
        class="map-marker"
        :class="[vehicle.status, { selected: selected === vehicle.id }]"
        :style="{ left: `${vehicle.x}%`, top: `${vehicle.y}%` }"
        :aria-label="`${vehicle.name}, ${vehicle.status}`"
        :aria-pressed="selected === vehicle.id"
        @click="selected = selected === vehicle.id ? null : vehicle.id"
      >
        <UIcon
          :name="
            vehicle.status === 'moving'
              ? 'i-lucide-navigation'
              : vehicle.status === 'offline'
                ? 'i-lucide-wifi-off'
                : 'i-lucide-truck'
          "
        />
      </button>
    </div>
    <div class="map-location-chip"><UIcon name="i-lucide-map-pin" />San Francisco, CA</div>
    <div class="map-controls">
      <button
        aria-label="Zoom in"
        :disabled="zoom >= 1.6"
        @click="zoom = Math.min(1.6, zoom + 0.2)"
      >
        <UIcon name="i-lucide-plus" /></button
      ><button aria-label="Zoom out" :disabled="zoom <= 1" @click="zoom = Math.max(1, zoom - 0.2)">
        <UIcon name="i-lucide-minus" /></button
      ><span /><button aria-label="Reset map view" @click="resetView">
        <UIcon name="i-lucide-locate-fixed" /></button
      ><button aria-label="Toggle map legend" :aria-pressed="layers" @click="layers = !layers">
        <UIcon name="i-lucide-layers-2" />
      </button>
    </div>
    <div v-if="current" class="map-vehicle-popup">
      <span class="vehicle-icon"><UIcon name="i-lucide-truck" /></span>
      <div>
        <strong
          >{{ current.name }}<span>{{ current.plate }}</span></strong
        ><small
          ><span class="moving-indicator" />{{ current.status }}<span>·</span
          >{{ current.speed }} km/h</small
        >
      </div>
      <NuxtLink :to="`/vehicles/${current.id}`" :aria-label="`View ${current.name}`"
        ><UIcon name="i-lucide-arrow-up-right"
      /></NuxtLink>
    </div>
    <div v-if="layers" class="map-legend">
      <span><i class="moving" />Moving</span><span><i class="idle" />Idle</span
      ><span><i class="parked" />Parked</span><span><i class="offline" />Offline</span>
    </div>
    <span class="map-attribution">Illustrated preview map · Not for navigation</span>
  </div>
</template>
