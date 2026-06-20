<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const mode = ref('hot')
const keyword = ref('')
const loading = ref(false)
const items = ref([])
const page = ref(0)
const size = 10
const finished = ref(false)

const userLocation = computed(() => auth.user?.locationName || '未绑定位置')

async function load(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) {
      page.value = 0
      finished.value = false
      items.value = []
    }
    const resp = await http.get('/api/posts', {
      params: {
        mode: mode.value,
        keyword: keyword.value || undefined,
        page: page.value,
        size,
      },
    })
    const p = resp.data.data
    items.value.push(...(p.content || []))
    if (p.last || (p.content || []).length < size) finished.value = true
    page.value += 1
  } catch (e) {
    const msg = e?.message || ''
    if (msg.includes('绑定位置') || msg.includes('请先绑定') || msg.includes('Bad Request')) {
      ElMessageBox.confirm('你还没有绑定位置，先去绑定后再看内容。', '需要绑定位置', {
        confirmButtonText: '去绑定',
        cancelButtonText: '取消',
        type: 'warning',
      }).then(() => router.push('/app/location')).catch(() => {})
    } else {
      ElMessage.error(msg || '加载失败')
    }
    finished.value = true
  } finally {
    loading.value = false
  }
}

function runSearch() {
  load(true)
}

function clearSearch() {
  keyword.value = ''
  load(true)
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
function resolveImageUrl(url) {
  if (!url) return ''
  if (url.startsWith('/uploads/') || url.startsWith('uploads/')) return `${API_BASE_URL}${url.startsWith('/') ? '' : '/'}${url}`
  return ''
}

function toDetail(id) {
  router.push(`/app/post/${id}`)
}

onMounted(() => load(true))
</script>

<template>
  <div class="page">
    <div class="row">
      <div class="locationTip">当前位置：{{ userLocation }}</div>
      <el-input v-model="keyword" placeholder="搜索帖子标题/内容/分类/位置" style="max-width: 280px" clearable @keyup.enter="runSearch" />
      <el-segmented v-model="mode" :options="[{ label: '附近', value: 'nearby' }, { label: '热门', value: 'hot' } ]" @change="load(true)" />
      <el-button @click="runSearch">搜索</el-button>
      <el-button @click="clearSearch">清空</el-button>
      <el-button @click="load(true)">刷新</el-button>
    </div>

    <el-empty v-if="!loading && items.length === 0" description="暂无内容" />
    <el-skeleton v-if="loading && items.length === 0" :rows="6" animated />

    <div class="list">
      <el-card v-for="p in items" :key="p.id" class="card" shadow="hover" @click="toDetail(p.id)">
        <img v-if="p.images && p.images.length" class="cover" :src="resolveImageUrl(p.images[0])" alt="帖子封面图" />
        <div class="title">{{ p.title }}</div>
        <div class="meta">
          <el-tag size="small">{{ p.category }}</el-tag>
          <span class="muted">{{ p.locationName || '未知位置' }}</span>
          <span v-if="p.distanceMeters != null" class="muted">离你 {{ p.distanceMeters > 1000 ? (p.distanceMeters / 1000).toFixed(2) + ' km' : Math.round(p.distanceMeters) + ' m' }}</span>
          <span class="muted">{{ dayjs(p.createdAt).format('MM-DD HH:mm') }}</span>
        </div>
        <div class="counts">
          <span>👁 {{ p.viewCount || 0 }}</span>
          <span>👍 {{ p.likeCount || 0 }}</span>
          <span>💬 {{ p.commentCount || 0 }}</span>
          <span v-if="p.isAudited === 0" class="audit">待审</span>
          <span v-else-if="p.isAudited === 2" class="audit reject">驳回</span>
        </div>
      </el-card>
    </div>

    <div class="footer">
      <el-button v-if="!finished" :loading="loading" @click="load(false)">加载更多</el-button>
      <div v-else class="muted">没有更多了</div>
    </div>
  </div>
</template>

<style scoped>
.page { display:flex; flex-direction:column; gap:12px; }
.row { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
.locationTip { color:#666; font-size:13px; }
.list { display:flex; flex-direction:column; gap:10px; }
.card { cursor:pointer; }
.cover { width:100%; height:180px; object-fit:cover; border-radius:10px; margin-bottom:10px; background:#f3f4f6; }
.title { font-weight:700; margin-bottom:6px; }
.meta { display:flex; gap:8px; align-items:center; margin-bottom:8px; flex-wrap:wrap; }
.muted { color:#888; font-size:12px; }
.counts { display:flex; gap:12px; color:#555; font-size:12px; flex-wrap:wrap; }
.audit { margin-left:auto; color:#e6a23c; }
.audit.reject { color:#f56c6c; }
.footer { margin-top:14px; display:flex; justify-content:center; }
</style>