<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import { useAuthStore } from '../../stores/auth'
import AMapLoader from '@amap/amap-jsapi-loader'

const auth = useAuthStore()
const loading = ref(false)
const geoLoading = ref(false)
const mapLoading = ref(true)
const mapError = ref('')
const mapRef = ref()
let map
let marker
let markerClickInited = false
let regeoSeq = 0
let regeoTimer = null

const form = reactive({
  locationName: auth.user?.locationName || '',
  lng: auth.user?.lng ?? null,
  lat: auth.user?.lat ?? null,
})

async function refreshMe() {
  const resp = await http.get('/api/auth/me')
  auth.setAuth(auth.token, resp.data.data)
  form.lng = auth.user?.lng ?? null
  form.lat = auth.user?.lat ?? null
  if (!Number.isFinite(Number(form.lng)) || !Number.isFinite(Number(form.lat))) {
    form.locationName = auth.user?.locationName || ''
  }
}

function updateMap(lng, lat) {
  if (!map || !marker) return
  map.setCenter([lng, lat])
  marker.setPosition([lng, lat])
}

function scheduleFillByRegeo(lng, lat) {
  if (regeoTimer) clearTimeout(regeoTimer)
  const seq = ++regeoSeq
  regeoTimer = setTimeout(() => fillByRegeo(lng, lat, seq), 300)
}

function updateCoords(lng, lat) {
  const nextLng = Number(lng)
  const nextLat = Number(lat)
  if (!Number.isFinite(nextLng) || !Number.isFinite(nextLat)) return
  form.lng = nextLng
  form.lat = nextLat
  updateMap(nextLng, nextLat)
  scheduleFillByRegeo(nextLng, nextLat)
}

async function initMap() {
  const key = import.meta.env.VITE_AMAP_KEY
  if (!key) {
    mapLoading.value = false
    mapError.value = '未配置 VITE_AMAP_KEY'
    return
  }

  try {
    mapLoading.value = true
    mapError.value = ''
    await AMapLoader.load({
      key,
      version: '2.0',
      plugins: ['AMap.Geocoder'],
    })
    await nextTick()

    const container = mapRef.value
    if (!container) {
      throw new Error('Map container div not exist')
    }

    const initialLng = Number.isFinite(Number(form.lng)) ? Number(form.lng) : 116.397428
    const initialLat = Number.isFinite(Number(form.lat)) ? Number(form.lat) : 39.90923

    map = new window.AMap.Map(container, {
      zoom: 12,
      center: [initialLng, initialLat],
    })

    marker = new window.AMap.Marker({
      position: [initialLng, initialLat],
      map,
      draggable: true,
    })

    map.on('click', (e) => {
      const lng = Number(e.lnglat.getLng().toFixed(6))
      const lat = Number(e.lnglat.getLat().toFixed(6))
      updateCoords(lng, lat)
    })

    marker.on('dragend', (e) => {
      const lng = Number(e.lnglat.getLng().toFixed(6))
      const lat = Number(e.lnglat.getLat().toFixed(6))
      updateCoords(lng, lat)
    })

    markerClickInited = true
    scheduleFillByRegeo(initialLng, initialLat)
  } catch (e) {
    mapError.value = e?.message || '高德地图初始化失败，请检查 VITE_AMAP_KEY'
    ElMessage.warning(mapError.value)
  } finally {
    mapLoading.value = false
  }
}

async function fillByRegeo(lng, lat, seq) {
  try {
    const resp = await http.get('/api/geo/regeo', {
      params: { lng, lat, extensions: 'base' },
    })
    if (seq !== regeoSeq) return
    const regeocode = resp.data?.data?.regeocode
    const finalName = regeocode?.formatted_address || resp.data?.data?.locationName || ''
    if (finalName) {
      form.locationName = finalName
    }
  } catch (e) {
    if (seq === regeoSeq) {
      ElMessage.error(e?.message || '逆地理编码失败')
    }
  }
}

function useCurrentLocation() {
  if (!navigator.geolocation) {
    ElMessage.error('当前浏览器不支持定位')
    return
  }
  geoLoading.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      const lng = Number(position.coords.longitude.toFixed(6))
      const lat = Number(position.coords.latitude.toFixed(6))
      updateCoords(lng, lat)
      geoLoading.value = false
      ElMessage.success('已使用当前位置')
    },
    (err) => {
      geoLoading.value = false
      ElMessage.error(`定位失败：${err?.message || '未知错误'}`)
    },
    { enableHighAccuracy: true, timeout: 10000 },
  )
}

async function bind() {
  loading.value = true
  try {
    await http.post('/api/users/me/location', {
      locationName: form.locationName,
      lng: Number(form.lng),
      lat: Number(form.lat),
    })
    await refreshMe()
    ElMessage.success('绑定成功')
  } catch (e) {
    ElMessage.error(e?.message || '绑定失败')
  } finally {
    loading.value = false
  }
}

onMounted(refreshMe)
onMounted(initMap)
</script>

<template>
  <el-card v-loading="mapLoading">
    <template #header>绑定位置</template>
    <el-alert type="info" show-icon :closable="false" style="margin-bottom: 12px">
      点击地图、拖动标记或使用当前位置，系统会根据实时坐标自动生成位置名称。
    </el-alert>

    <el-form label-position="top">
      <el-form-item>
        <el-button :loading="geoLoading" @click="useCurrentLocation">当前位置</el-button>
      </el-form-item>
    </el-form>

    <div class="mapWrap">
      <div ref="mapRef" class="mapBox"></div>
      <div v-if="mapLoading" class="mapState">地图加载中...</div>
      <el-alert v-else-if="mapError" type="error" show-icon :closable="false" class="mapError">
        {{ mapError }}
      </el-alert>
    </div>

    <el-form label-position="top">
      <el-form-item label="位置名称">
        <el-input v-model="form.locationName" placeholder="将根据经纬度自动生成" readonly />
      </el-form-item>
      <div class="grid">
        <el-form-item label="经度 lng">
          <el-input-number v-model="form.lng" :precision="6" :step="0.000001" style="width: 100%" @change="updateCoords(form.lng, form.lat)" />
        </el-form-item>
        <el-form-item label="纬度 lat">
          <el-input-number v-model="form.lat" :precision="6" :step="0.000001" style="width: 100%" @change="updateCoords(form.lng, form.lat)" />
        </el-form-item>
      </div>
      <el-button type="primary" :loading="loading" @click="bind">保存</el-button>
      <el-button @click="refreshMe">刷新</el-button>
    </el-form>
  </el-card>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.mapWrap {
  position: relative;
  width: 100%;
  height: 300px;
  margin-bottom: 12px;
}
.mapBox {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  overflow: hidden;
}
.mapState {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(245, 247, 250, 0.9);
  color: #666;
}
.mapError {
  position: absolute;
  inset: 0;
  align-items: center;
}
</style>
