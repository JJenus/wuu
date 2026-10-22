import type * as Leaflet from 'leaflet'
import type {} from 'leaflet.markercluster'
import {
  circleEdge,
  defaultCenter,
  tileConfiguration,
  validCoordinate,
  type Coordinate,
  type TileConfiguration,
} from '../domain/geographic'
import type { MapScene } from '../domain/scene'
import type { Geometry } from '../../geofences/domain/geofence'

type Events = {
  select: (id: string | null) => void
  navigate: () => void
  geometry: (value: Geometry) => void
  drawn: () => void
  editStart: () => void
  editEnd: () => void
}
// Engine lifecycle and raster transport live here, not in UI components.
export function useGeographicMap(
  container: Ref<HTMLElement | undefined>,
  scene: () => MapScene,
  config: TileConfiguration,
  events: Events,
) {
  const ready = ref(false),
    error = ref(''),
    tileStatus = ref<'loading' | 'ready' | 'error'>('loading')
  const view = ref({ lat: defaultCenter.lat, lng: defaultCenter.lng, zoom: 12 })
  let L: typeof Leaflet,
    map: Leaflet.Map | undefined,
    tiles: Leaflet.TileLayer | undefined,
    observer: ResizeObserver | undefined,
    disposed = false,
    initializing = false,
    tileTimer: ReturnType<typeof setTimeout> | undefined
  let options: TileConfiguration,
    hadTileError = false,
    manualFollow = false,
    draggingHandle = false
  const markers = new Map<string, Leaflet.Marker>(),
    handles = new Map<string, Leaflet.Marker>()
  const markerButtons = new Map<string, HTMLButtonElement>()
  const markerLabels = new WeakMap<Leaflet.Marker, string>()
  let clusters: Leaflet.MarkerClusterGroup
  let vectors: Leaflet.LayerGroup,
    geometryLayer: Leaflet.LayerGroup,
    searchMarker: Leaflet.CircleMarker | undefined
  const plain = (value: Coordinate): Leaflet.LatLngTuple => [value.lat, value.lng]
  function manual() {
    manualFollow = true
    events.navigate()
  }
  function viewChanged() {
    if (map) {
      const center = map.getCenter()
      view.value = { lat: center.lat, lng: center.lng, zoom: map.getZoom() }
    }
  }
  function fit(points: readonly Coordinate[]) {
    if (!map) return
    const valid = points.filter((p) => validCoordinate(p) && Math.abs(p.lat) <= 85.0511)
    if (!valid.length) {
      reset()
      return
    }
    map.fitBounds(L.latLngBounds(valid.map(plain)), {
      paddingTopLeft: [50, 85],
      paddingBottomRight: [55, 115],
      maxZoom: 15,
      animate: false,
    })
  }
  function reset() {
    map?.setView(plain(defaultCenter), 12, { animate: false })
  }
  function zoom(delta: number) {
    manual()
    map?.setZoom((map?.getZoom() ?? 12) + delta, { animate: false })
  }
  function focus(point: Coordinate, zoomLevel = 15) {
    if (validCoordinate(point) && Math.abs(point.lat) <= 85.0511) {
      manual()
      map?.setView(plain(point), zoomLevel, { animate: false })
    }
  }
  function fitContent() {
    manual()
    fit([
      ...scene().markers.flatMap((m) => (m.position ? [m.position] : [])),
      ...(scene().route ?? []),
    ])
  }
  function fitGeometry() {
    if (!map) return
    const g = scene().geometry
    if (
      g?.type === 'circle' &&
      validCoordinate(g.center) &&
      Number.isFinite(g.radiusMeters) &&
      g.radiusMeters > 0
    ) {
      const circle = L.circle(plain(g.center), { radius: g.radiusMeters }).addTo(map)
      const bounds = circle.getBounds()
      circle.remove()
      map.fitBounds(bounds, {
        padding: [55, 55],
        maxZoom: 16,
        animate: false,
      })
    } else if (g?.type === 'polygon') fit(g.vertices)
    else reset()
  }
  function attachTiles() {
    if (!map) return
    tiles?.off()
    tiles?.remove()
    clearTimeout(tileTimer)
    hadTileError = false
    tileStatus.value = 'loading'
    tiles = L.tileLayer(options.url, {
      maxZoom: options.maxZoom,
      minZoom: 2,
      noWrap: true,
      updateWhenIdle: true,
      keepBuffer: 2,
      detectRetina: false,
    })
    tiles.on('loading', () => {
      hadTileError = false
      tileStatus.value = 'loading'
      clearTimeout(tileTimer)
      tileTimer = setTimeout(() => {
        tileStatus.value = 'error'
      }, 15000)
    })
    tiles.on('tileerror', () => {
      hadTileError = true
      tileStatus.value = 'error'
    })
    tiles.on('load', () => {
      clearTimeout(tileTimer)
      tileStatus.value = hadTileError ? 'error' : 'ready'
    })
    tiles.addTo(map)
  }
  function updateMarkers() {
    if (!map) return
    const state = scene(),
      ids = new Set<string>()
    for (const item of state.markers) {
      if (!validCoordinate(item.position) || Math.abs(item.position.lat) > 85.0511) continue
      ids.add(item.id)
      let marker = markers.get(item.id)
      if (!marker) {
        const button = document.createElement('button')
        button.type = 'button'
        button.className = 'geo-vehicle-marker'
        const symbol = document.createElement('span')
        symbol.className = 'geo-marker-symbol'
        symbol.setAttribute('aria-hidden', 'true')
        button.append(symbol)
        const label = document.createElement('span')
        label.className = 'geo-marker-label'
        button.append(label)
        marker = L.marker(plain(item.position), {
          icon: L.divIcon({
            html: button,
            className: 'geo-marker-shell',
            iconSize: [44, 44],
            iconAnchor: [22, 22],
          }),
          keyboard: false,
          riseOnHover: true,
          bubblingMouseEvents: false,
        })
        L.DomEvent.disableClickPropagation(button)
        const id = item.id
        button.addEventListener('click', () => events.select(scene().selectedId === id ? null : id))
        button.addEventListener('keydown', (event) => {
          event.stopPropagation()
          if (event.key === 'Escape') events.select(null)
        })
        markers.set(item.id, marker)
        markerButtons.set(item.id, button)
        clusters.addLayer(marker)
      }
      markerLabels.set(marker, item.label)
      marker.setLatLng(plain(item.position))
      const button = markerButtons.get(item.id)
      if (button) {
        const selected = state.selectedId === item.id
        button.className = `geo-vehicle-marker ${item.status}${selected ? ' is-selected' : ''}`
        button.setAttribute('aria-label', `${item.label}, ${item.status}`)
        button.setAttribute('aria-pressed', String(selected))
        button.dataset.lat = String(item.position.lat)
        button.dataset.lng = String(item.position.lng)
        button.querySelector('.geo-marker-label')!.textContent = item.label
        button.querySelector('.geo-marker-symbol')!.textContent =
          item.status === 'moving'
            ? '➤'
            : item.status === 'parked'
              ? 'P'
              : item.status === 'offline'
                ? '×'
                : 'Ⅱ'
        button.classList.toggle('show-label', !!state.showLabels)
        marker.setZIndexOffset(selected ? 1000 : 0)
      }
    }
    for (const [id, marker] of markers)
      if (!ids.has(id)) {
        clusters.removeLayer(marker)
        markers.delete(id)
        markerButtons.delete(id)
      }
    clusters.refreshClusters()
    const current = state.markers.find((m) => m.id === state.selectedId)
    if (
      state.follow &&
      !manualFollow &&
      current?.position &&
      validCoordinate(current.position) &&
      Math.abs(current.position.lat) <= 85.0511
    )
      map.panTo(plain(current.position), { animate: false })
  }
  function updateRoutes() {
    if (!map) return
    vectors.clearLayers()
    const state = scene()
    if (state.showRoute !== false) {
      for (const [points, className, color, weight, dashArray] of [
        [state.route, 'geo-route-planned', '#64748b', 4, '7 7'],
        [state.trail, 'geo-route-traveled', '#0b796f', 5, undefined],
      ] as const) {
        if (!points?.length) continue
        // Invalid samples split paths; never draw a bridge across missing positions.
        let segment: Leaflet.LatLngTuple[] = []
        const flush = () => {
          if (segment.length > 1)
            L.polyline(segment, { className, color, weight, dashArray, interactive: false }).addTo(
              vectors,
            )
          segment = []
        }
        for (const p of points) {
          if (validCoordinate(p) && Math.abs(p.lat) <= 85.0511) segment.push(plain(p))
          else flush()
        }
        flush()
      }
      const route = state.route
      if (route?.length)
        for (const p of [route[0], route.at(-1)])
          if (p && validCoordinate(p))
            L.circleMarker(plain(p), {
              radius: 6,
              color: '#0b796f',
              fillColor: '#fff',
              fillOpacity: 1,
              weight: 2,
              interactive: false,
              className: 'geo-route-endpoint',
            }).addTo(vectors)
    }
  }
  function updateSearch() {
    if (!map) return
    searchMarker?.remove()
    searchMarker = undefined
    const p = scene().searchPosition
    if (validCoordinate(p))
      searchMarker = L.circleMarker(plain(p), {
        radius: 10,
        color: '#2563eb',
        fillOpacity: 0.25,
        weight: 3,
        className: 'search-location-marker',
        interactive: false,
      }).addTo(map)
  }
  function changeHandle(kind: string, point: Coordinate) {
    const g = scene().geometry
    if (!g || !map) return
    if (g.type === 'circle')
      events.geometry(
        kind === 'center'
          ? { ...g, center: point }
          : {
              ...g,
              radiusMeters: Math.max(
                50,
                Math.min(5000, Math.round(map.distance(plain(g.center), plain(point)))),
              ),
            },
      )
    else
      events.geometry({
        ...g,
        vertices: g.vertices.map((p, i) => (String(i) === kind ? point : p)),
      })
  }
  function nudge(event: KeyboardEvent, kind: string) {
    if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) return
    event.preventDefault()
    event.stopPropagation()
    const g = scene().geometry
    if (!g) return
    const p = g.type === 'circle' ? g.center : g.vertices[Number(kind)]
    if (!p) return
    if (g.type === 'circle' && kind === 'radius')
      events.geometry({
        ...g,
        radiusMeters: Math.max(
          50,
          Math.min(
            5000,
            g.radiusMeters + (['ArrowUp', 'ArrowRight'].includes(event.key) ? 10 : -10),
          ),
        ),
      })
    else {
      const step = event.shiftKey ? 0.001 : 0.0001
      changeHandle(kind, {
        lat: p.lat + (event.key === 'ArrowUp' ? step : event.key === 'ArrowDown' ? -step : 0),
        lng: p.lng + (event.key === 'ArrowRight' ? step : event.key === 'ArrowLeft' ? -step : 0),
      })
    }
  }
  function updateGeometry() {
    if (!map) return
    geometryLayer.clearLayers()
    const state = scene(),
      g = state.geometry,
      style = {
        color: '#0b796f',
        weight: 3,
        fillOpacity: 0.14,
        className: 'geofence-boundary',
        interactive: false,
      }
    const desired = new Map<string, { point: Coordinate; label: string }>()
    if (
      g?.type === 'circle' &&
      validCoordinate(g.center) &&
      Number.isFinite(g.radiusMeters) &&
      g.radiusMeters > 0
    ) {
      L.circle(plain(g.center), { ...style, radius: g.radiusMeters }).addTo(geometryLayer)
      desired.set('center', { point: g.center, label: 'Move circle center' })
      desired.set('radius', { point: circleEdge(g.center, g.radiusMeters), label: 'Resize circle' })
    }
    if (g?.type === 'polygon') {
      const points = g.vertices.filter(validCoordinate)
      if (points.length > 1)
        (points.length > 2
          ? L.polygon(points.map(plain), style)
          : L.polyline(points.map(plain), style)
        ).addTo(geometryLayer)
      g.vertices.forEach((point, i) => {
        if (validCoordinate(point)) desired.set(String(i), { point, label: `Move vertex ${i + 1}` })
      })
    }
    if (!state.editable) desired.clear()
    for (const [kind, handle] of handles)
      if (!desired.has(kind)) {
        handle.remove()
        handles.delete(kind)
      }
    for (const [kind, { point, label }] of desired) {
      let handle = handles.get(kind)
      if (!handle) {
        const button = document.createElement('button')
        button.type = 'button'
        button.className = 'geo-edit-handle'
        button.setAttribute('aria-label', label)
        button.textContent =
          kind === 'center' ? '＋' : kind === 'radius' ? '↔' : String(Number(kind) + 1)
        L.DomEvent.disableClickPropagation(button)
        handle = L.marker(plain(point), {
          icon: L.divIcon({
            html: button,
            className: 'geo-handle-shell',
            iconSize: [36, 36],
            iconAnchor: [18, 18],
          }),
          draggable: true,
          keyboard: false,
          zIndexOffset: 2000,
        }).addTo(map)
        const marker = handle
        button.addEventListener('keydown', (event) => nudge(event, kind))
        marker.on('dragstart', () => {
          draggingHandle = true
          events.editStart()
        })
        marker.on('drag', () => {
          const p = marker.getLatLng()
          changeHandle(kind, { lat: p.lat, lng: p.lng })
        })
        marker.on('dragend', () => {
          draggingHandle = false
          events.editEnd()
          updateGeometry()
        })
        handles.set(kind, handle)
      }
      if (!draggingHandle) handle.setLatLng(plain(point))
    }
    const drawing = state.editable && (state.tool === 'circle' || state.tool === 'polygon')
    if (drawing) {
      map.dragging.disable()
      map.doubleClickZoom.disable()
    } else {
      map.dragging.enable()
      map.doubleClickZoom.enable()
    }
    map.getContainer().classList.toggle('is-drawing', !!drawing)
  }
  async function initialize() {
    if (disposed || !container.value || map || initializing) return
    initializing = true
    error.value = ''
    try {
      options = tileConfiguration(config)
      const module = await import('leaflet')
      if (disposed || !container.value) return
      L = module.default ?? module
      await import('leaflet.markercluster')
      if (disposed || !container.value) return
      map = L.map(container.value, {
        center: plain(defaultCenter),
        zoom: 12,
        minZoom: 2,
        maxZoom: options.maxZoom,
        zoomControl: false,
        attributionControl: false,
        scrollWheelZoom: false,
        touchZoom: true,
        keyboard: true,
        inertia: false,
        zoomAnimation: false,
        fadeAnimation: false,
        markerZoomAnimation: false,
        worldCopyJump: true,
      })
      clusters = L.markerClusterGroup({
        maxClusterRadius: 64,
        showCoverageOnHover: false,
        animate: false,
        spiderfyOnMaxZoom: true,
        spiderfyDistanceMultiplier: 1.6,
        removeOutsideVisibleBounds: true,
        iconCreateFunction: (cluster) => {
          cluster.options.keyboard = false
          const selected = cluster
            .getAllChildMarkers()
            .some((marker) => markers.get(scene().selectedId ?? '') === marker)
          const button = document.createElement('button')
          button.type = 'button'
          button.className = `geo-cluster-button${selected ? ' is-selected' : ''}`
          button.textContent = String(cluster.getChildCount())
          button.setAttribute(
            'aria-label',
            `${cluster.getChildCount()} vehicles. Zoom or expand group: ${cluster
              .getAllChildMarkers()
              .slice(0, 10)
              .map((marker) => markerLabels.get(marker) ?? 'Vehicle')
              .join(', ')}`,
          )
          button.addEventListener('keydown', (event) => event.stopPropagation())
          return L.divIcon({
            html: button,
            className: 'geo-cluster-shell',
            iconSize: [44, 44],
            iconAnchor: [22, 22],
          })
        },
      }).addTo(map)
      vectors = L.layerGroup().addTo(map)
      geometryLayer = L.layerGroup().addTo(map)
      L.control.scale({ imperial: false, position: 'bottomleft' }).addTo(map)
      const attribution = L.control
        .attribution({ prefix: false, position: 'bottomright' })
        .addTo(map)
      // Text and link are locally constructed; provider HTML is never injected.
      const a = document.createElement('a')
      a.href = options.attributionUrl
      a.textContent = options.attribution
      a.target = '_blank'
      a.rel = 'noopener noreferrer'
      attribution.getContainer()?.append('© ', a)
      map.on('moveend zoomend', viewChanged)
      map.on('dragstart', manual)
      map.on('click', (event: Leaflet.LeafletMouseEvent) => {
        const s = scene()
        if (!s.editable) return
        const point = { lat: event.latlng.lat, lng: event.latlng.lng }
        if (s.tool === 'circle') {
          events.geometry({
            type: 'circle',
            center: point,
            radiusMeters: s.geometry?.type === 'circle' ? s.geometry.radiusMeters : 500,
          })
          events.drawn()
        } else if (s.tool === 'polygon') {
          const vertices = s.geometry?.type === 'polygon' ? s.geometry.vertices : []
          if (vertices.length < 50)
            events.geometry({ type: 'polygon', vertices: [...vertices, point] })
        }
      })
      const node = container.value
      node.addEventListener('focus', () => map?.scrollWheelZoom.enable())
      node.addEventListener('blur', () => map?.scrollWheelZoom.disable())
      node.addEventListener(
        'wheel',
        () => {
          if (map?.scrollWheelZoom.enabled()) manual()
        },
        { passive: true },
      )
      node.addEventListener(
        'touchstart',
        (event) => {
          if (event.touches.length > 1) manual()
        },
        { passive: true },
      )
      node.addEventListener('dblclick', manual)
      node.addEventListener('keydown', (event) => {
        if (event.target !== node) return
        if (
          ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', '+', '=', '-', 'Home'].includes(
            event.key,
          )
        )
          manual()
        if (event.key === 'Home') {
          event.preventDefault()
          reset()
        }
      })
      observer = new ResizeObserver(() =>
        map?.invalidateSize({ pan: false, debounceMoveend: true }),
      )
      observer.observe(node)
      attachTiles()
      ready.value = true
      updateMarkers()
      updateRoutes()
      updateGeometry()
      updateSearch()
      if (scene().geometry) fitGeometry()
      else if (scene().markers.length) fitContent()
      manualFollow = false
      viewChanged()
    } catch {
      map?.remove()
      map = undefined
      ready.value = false
      error.value =
        'Unable to initialize the geographic map. Check the map provider configuration and retry.'
    } finally {
      initializing = false
    }
  }
  watch(
    () => [scene().markers, scene().selectedId, scene().showLabels, scene().follow],
    updateMarkers,
    { deep: true },
  )
  watch(
    () => scene().follow,
    () => {
      manualFollow = false
      updateMarkers()
    },
  )
  watch(
    () => scene().selectedId,
    (id) => {
      const marker = id ? markers.get(id) : undefined
      if (map && marker)
        clusters.zoomToShowLayer(marker, () => {
          map?.panInside(marker.getLatLng(), { padding: [70, 100], animate: false })
        })
    },
  )
  watch(() => [scene().route, scene().trail, scene().showRoute], updateRoutes, { deep: true })
  watch(() => scene().searchPosition, updateSearch, { deep: true })
  watch(() => [scene().geometry, scene().editable, scene().tool], updateGeometry, { deep: true })
  onMounted(initialize)
  onBeforeUnmount(() => {
    disposed = true
    clearTimeout(tileTimer)
    observer?.disconnect()
    tiles?.off()
    map?.remove()
    markers.clear()
    handles.clear()
    markerButtons.clear()
  })
  return {
    ready,
    error,
    tileStatus,
    view,
    initialize,
    retryTiles: attachTiles,
    zoom,
    reset,
    focus,
    fitContent,
    fitGeometry,
  }
}
