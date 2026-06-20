<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import * as echarts from 'echarts'

const loading = ref(false)
const data = ref({
  userCount: 0,
  postCount: 0,
  commentCount: 0,
  actionCount: 0,
  trend7d: [],
  categoryDist: [],
  auditDist: [],
  hotAreas: [],
})

const trendRef = ref()
const categoryRef = ref()
const auditRef = ref()
const areaRef = ref()

let trendChart
let categoryChart
let auditChart
let areaChart

function initCharts() {
  if (trendRef.value && !trendChart) trendChart = echarts.init(trendRef.value)
  if (categoryRef.value && !categoryChart) categoryChart = echarts.init(categoryRef.value)
  if (auditRef.value && !auditChart) auditChart = echarts.init(auditRef.value)
  if (areaRef.value && !areaChart) areaChart = echarts.init(areaRef.value)
}

function renderCharts() {
  initCharts()
  const trend = data.value.trend7d || []
  trendChart?.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['用户', '帖子', '评论'] },
    xAxis: { type: 'category', data: trend.map((i) => i.day) },
    yAxis: { type: 'value' },
    series: [
      { name: '用户', type: 'line', smooth: true, data: trend.map((i) => Number(i.users || 0)) },
      { name: '帖子', type: 'line', smooth: true, data: trend.map((i) => Number(i.posts || 0)) },
      { name: '评论', type: 'line', smooth: true, data: trend.map((i) => Number(i.comments || 0)) },
    ],
  })

  categoryChart?.setOption({
    tooltip: { trigger: 'item' },
    series: [{ type: 'pie', radius: ['35%', '65%'], data: data.value.categoryDist || [] }],
  })

  const auditDist = (data.value.auditDist || []).map((i) => ({
    name: i.name === '0' ? '待审' : i.name === '1' ? '通过' : i.name === '2' ? '驳回' : String(i.name),
    value: Number(i.value || 0),
  }))
  auditChart?.setOption({
    tooltip: { trigger: 'item' },
    series: [{ type: 'pie', radius: '60%', data: auditDist }],
  })

  const hotAreas = data.value.hotAreas || []
  areaChart?.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: hotAreas.map((i) => i.name) },
    series: [{ type: 'bar', data: hotAreas.map((i) => Number(i.value || 0)) }],
  })
}

async function load() {
  loading.value = true
  try {
    const resp = await http.get('/api/admin/dashboard/overview')
    data.value = resp.data.data || data.value
    renderCharts()
  } catch (e) {
    ElMessage.error(e?.message || '加载报表失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="wrap" v-loading="loading">
    <div class="kpi">
      <el-card><div class="k">用户总数</div><div class="v">{{ data.userCount }}</div></el-card>
      <el-card><div class="k">帖子总数</div><div class="v">{{ data.postCount }}</div></el-card>
      <el-card><div class="k">评论总数</div><div class="v">{{ data.commentCount }}</div></el-card>
      <el-card><div class="k">行为日志</div><div class="v">{{ data.actionCount }}</div></el-card>
    </div>
    <div class="grid2">
      <el-card><template #header>近7日趋势</template><div ref="trendRef" class="chart h280" /></el-card>
      <el-card><template #header>分类分布</template><div ref="categoryRef" class="chart h280" /></el-card>
      <el-card><template #header>审核状态</template><div ref="auditRef" class="chart h260" /></el-card>
      <el-card><template #header>热门区域 TOP10</template><div ref="areaRef" class="chart h260" /></el-card>
    </div>
  </div>
</template>

<style scoped>
.kpi { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 10px; }
.k { color: #666; font-size: 12px; margin-bottom: 6px; }
.v { font-size: 26px; font-weight: 700; color: #111; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.chart { width: 100%; }
.h280 { height: 280px; }
.h260 { height: 260px; }
</style>

