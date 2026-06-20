<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const items = ref([])
const reason = ref('')
const formula = ref({})
const profile = ref(null)
const tabs = ref([])
const weightKey = 'guess-like-weights'
const defaultWeights = {
  cosineWeight: 100,
  distanceWeight: 1.0,
  heatWeight: 1.0,
  tagMatchWeight: 8.0,
  categoryMatchWeight: 4.0,
  imageBonus: 1.0,
}
const form = reactive({ ...defaultWeights })

function loadDefaultWeights() {
  Object.assign(form, { ...defaultWeights })
}

function resetToDefault() {
  loadDefaultWeights()
  localStorage.removeItem(weightKey)
  load()
}

function loadSavedWeights() {
  try {
    const saved = localStorage.getItem(weightKey)
    if (!saved) {
      loadDefaultWeights()
      return
    }
    const parsed = JSON.parse(saved)
    Object.assign(form, {
      cosineWeight: Number(parsed.cosineWeight ?? defaultWeights.cosineWeight),
      distanceWeight: Number(parsed.distanceWeight ?? defaultWeights.distanceWeight),
      heatWeight: Number(parsed.heatWeight ?? defaultWeights.heatWeight),
      tagMatchWeight: Number(parsed.tagMatchWeight ?? defaultWeights.tagMatchWeight),
      categoryMatchWeight: Number(parsed.categoryMatchWeight ?? defaultWeights.categoryMatchWeight),
      imageBonus: Number(parsed.imageBonus ?? defaultWeights.imageBonus),
    })
  } catch (e) {
    console.warn('加载猜你喜欢权重失败', e)
    loadDefaultWeights()
  }
}

function saveWeights() {
  localStorage.setItem(weightKey, JSON.stringify({
    cosineWeight: form.cosineWeight,
    distanceWeight: form.distanceWeight,
    heatWeight: form.heatWeight,
    tagMatchWeight: form.tagMatchWeight,
    categoryMatchWeight: form.categoryMatchWeight,
    imageBonus: form.imageBonus,
  }))
}

const formulaText = computed(() => {
  const w = formula.value || {}
  return `总分 = 相似分(${Number(w.cosineWeight || form.cosineWeight).toFixed(2)} × cosine) + 标签命中分(${Number(w.tagMatchWeight || form.tagMatchWeight).toFixed(2)} × 命中次数) + 分类命中分(${Number(w.categoryMatchWeight || form.categoryMatchWeight).toFixed(2)} × 命中次数) + 距离分(${Number(w.distanceWeight || form.distanceWeight).toFixed(2)} × 距离阶梯分) + 热度分(${Number(w.heatWeight || form.heatWeight).toFixed(2)} × 热度值) + 图片加分(${Number(w.imageBonus || form.imageBonus).toFixed(2)})`
})

async function load() {
  loading.value = true
  try {
    const resp = await http.get('/api/recommendations/guess-like', {
      params: {
        limit: 20,
        cosineWeight: form.cosineWeight,
        distanceWeight: form.distanceWeight,
        heatWeight: form.heatWeight,
        tagMatchWeight: form.tagMatchWeight,
        categoryMatchWeight: form.categoryMatchWeight,
        imageBonus: form.imageBonus,
      },
    })
    const data = resp.data.data || {}
    items.value = data.items || []
    reason.value = data.reason || '基于画像标签、帖子标签余弦相似度、距离和热度综合推荐'
    formula.value = data.formula || {}
    profile.value = data.profile || null
    tabs.value = data.tabs || []
  } catch (e) {
    ElMessage.error(e?.message || '加载猜你喜欢失败')
  } finally {
    loading.value = false
  }
}

function openPost(id) {
  router.push(`/app/post/${id}`)
}

function onWeightChange() {
  saveWeights()
  load()
}

onMounted(() => {
  loadSavedWeights()
  load()
})

watch(form, saveWeights, { deep: true })
</script>

<template>
  <div class="guess-page" v-loading="loading">
    <el-card class="hero-card">
      <template #header>
        <div class="hero-head">
          <div>
            <div class="title">猜你喜欢</div>
            <div class="subtitle">根据你的画像标签、帖子标签、距离和热度综合推荐</div>
          </div>
          <div class="hero-actions">
            <el-button @click="resetToDefault">恢复默认参数</el-button>
            <el-button type="primary" plain @click="load">刷新推荐</el-button>
          </div>
        </div>
      </template>

      <div class="reason">{{ reason }}</div>

      <div class="algo-box">
        <div class="algo-title">推荐公式</div>
        <div class="algo-text">{{ formulaText }}</div>
      </div>

      <div class="weight-panel">
        <div class="weight-title">可调整权重</div>
        <div class="weight-grid">
          <div class="weight-item"><span>相似分权重</span><el-input-number v-model="form.cosineWeight" :min="0" :step="5" @change="onWeightChange" /></div>
          <div class="weight-item"><span>距离权重</span><el-input-number v-model="form.distanceWeight" :min="0" :step="0.1" @change="onWeightChange" /></div>
          <div class="weight-item"><span>热度权重</span><el-input-number v-model="form.heatWeight" :min="0" :step="0.1" @change="onWeightChange" /></div>
          <div class="weight-item"><span>标签命中权重</span><el-input-number v-model="form.tagMatchWeight" :min="0" :step="0.5" @change="onWeightChange" /></div>
          <div class="weight-item"><span>分类命中权重</span><el-input-number v-model="form.categoryMatchWeight" :min="0" :step="0.5" @change="onWeightChange" /></div>
          <div class="weight-item"><span>图片加分</span><el-input-number v-model="form.imageBonus" :min="0" :step="0.1" @change="onWeightChange" /></div>
        </div>
        <div class="weight-note">说明：权重修改后会立即重新请求推荐；恢复默认参数会清除本地保存并回到系统默认值。</div>
      </div>

      <div v-if="tabs.length" class="tag-row">
        <el-tag v-for="t in tabs" :key="t" type="info" effect="light">{{ t }}</el-tag>
      </div>
    </el-card>

    <el-card class="list-card">
      <template #header>
        <div class="list-head">
          <span>推荐帖子</span>
          <span class="count">{{ items.length }} 条</span>
        </div>
      </template>

      <el-empty v-if="!loading && items.length === 0" description="暂无推荐结果" />

      <div v-else class="post-list">
        <div v-for="p in items" :key="p.id" class="post-card" @click="openPost(p.id)">
          <div class="post-main">
            <div class="post-title">{{ p.title }}</div>
            <div class="post-desc">{{ p.content }}</div>
            <div class="post-meta">
              <el-tag size="small">{{ p.category }}</el-tag>
              <span>{{ p.locationName || '未知位置' }}</span>
              <span v-if="p.distanceMeters != null">距离 {{ Number(p.distanceMeters).toFixed(0) }}m</span>
            </div>
            <div class="score-row">
              <el-tag type="success" effect="light">相似度 {{ Number(p.cosine || 0).toFixed(4) }}</el-tag>
              <el-tag type="warning" effect="light">相似分 {{ Number(p.cosineScore || 0).toFixed(2) }}</el-tag>
              <el-tag type="info" effect="light">距离分 {{ Number(p.distanceScore || 0).toFixed(2) }}</el-tag>
              <el-tag type="danger" effect="light">热度分 {{ Number(p.heatScore || 0).toFixed(2) }}</el-tag>
              <el-tag type="primary" effect="light">总分 {{ Number(p.finalScore || p.score || 0).toFixed(2) }}</el-tag>
            </div>
          </div>
          <div class="arrow">›</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.guess-page { display:flex; flex-direction:column; gap:12px; }
.hero-card, .list-card { border-radius:16px; }
.hero-head, .list-head { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.hero-actions { display:flex; gap:8px; }
.title { font-size:18px; font-weight:800; }
.subtitle { margin-top:4px; color:#8b93a7; font-size:12px; }
.reason { color:#475569; line-height:1.7; }
.algo-box { margin-top: 12px; padding: 12px 14px; background: linear-gradient(135deg, #f8fbff 0%, #ffffff 100%); border: 1px solid #e6eefb; border-radius: 12px; }
.algo-title { font-size: 13px; font-weight: 800; color: #1f2937; margin-bottom: 6px; }
.algo-text { color: #64748b; font-size: 13px; line-height: 1.7; }
.weight-panel { margin-top: 12px; padding: 12px 14px; border: 1px solid #e6eefb; border-radius: 12px; background: #fff; }
.weight-title { font-size: 13px; font-weight: 800; color: #1f2937; margin-bottom: 10px; }
.weight-grid { display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.weight-item { display:flex; flex-direction:column; gap:6px; font-size:12px; color:#475569; }
.weight-note { margin-top: 10px; font-size: 12px; color: #8b93a7; line-height: 1.6; }
.tag-row { margin-top:12px; display:flex; gap:8px; flex-wrap:wrap; }
.post-list { display:flex; flex-direction:column; gap:12px; }
.post-card { display:flex; justify-content:space-between; gap:12px; padding:14px; border:1px solid #edf2f7; border-radius:14px; background:#fff; cursor:pointer; transition:transform .15s ease, box-shadow .15s ease; }
.post-card:hover { transform:translateY(-1px); box-shadow:0 10px 26px rgba(15,23,42,.08); }
.post-main { flex:1; min-width:0; }
.post-title { font-weight:800; margin-bottom:6px; color:#1f2937; }
.post-desc { color:#64748b; font-size:13px; line-height:1.6; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
.post-meta { margin-top:10px; display:flex; gap:8px; align-items:center; flex-wrap:wrap; color:#94a3b8; font-size:12px; }
.score-row { margin-top: 10px; display:flex; gap:8px; flex-wrap:wrap; }
.arrow { display:flex; align-items:center; color:#cbd5e1; font-size:28px; padding-right:4px; }
.count { color:#8b93a7; font-size:12px; }
@media (max-width: 960px) { .weight-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 640px) { .weight-grid { grid-template-columns: 1fr; } }
</style>
