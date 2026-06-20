<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { http } from '../../api/http'

const loading = ref(false)
const filters = ref({
  startDate: '',
  endDate: '',
  actionType: '',
  userId: '',
  postId: '',
})
const summary = ref({
  totalCount: 0,
  activeUsers: 0,
  actionTypeDist: [],
  dailyTrend: [],
  topPosts: [],
  topUsers: [],
})
const page = ref(1)
const size = ref(10)
const total = ref(0)
const records = ref([])

const trendRef = ref()
const typeRef = ref()
const topUsersRef = ref()
const topPostsRef = ref()
let trendChart
let typeChart
let topUsersChart
let topPostsChart

const tablePage = computed(() => page.value)

function initCharts() {
  if (trendRef.value && !trendChart) trendChart = echarts.init(trendRef.value)
  if (typeRef.value && !typeChart) typeChart = echarts.init(typeRef.value)
  if (topUsersRef.value && !topUsersChart) topUsersChart = echarts.init(topUsersRef.value)
  if (topPostsRef.value && !topPostsChart) topPostsChart = echarts.init(topPostsRef.value)
}

function renderCharts() {
  initCharts()
  const trend = summary.value.dailyTrend || []
  trendChart?.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: trend.map((i) => i.day) },
    yAxis: { type: 'value' },
    series: [{ type: 'line', smooth: true, areaStyle: {}, data: trend.map((i) => Number(i.value || 0)) }],
  })

  const actionTypeDist = (summary.value.actionTypeDist || []).map((i) => ({ name: i.name, value: Number(i.value || 0) }))
  typeChart?.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{ type: 'pie', radius: ['40%', '70%'], data: actionTypeDist }],
  })

  const topUsers = (summary.value.topUsers || []).map((i) => ({ name: String(i.name), value: Number(i.value || 0) }))
  topUsersChart?.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: topUsers.map((i) => `用户 ${i.name}`) },
    series: [{ type: 'bar', data: topUsers.map((i) => i.value) }],
  })

  const topPosts = (summary.value.topPosts || []).map((i) => ({ name: String(i.name), value: Number(i.value || 0) }))
  topPostsChart?.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: topPosts.map((i) => `帖子 ${i.name}`) },
    series: [{ type: 'bar', data: topPosts.map((i) => i.value) }],
  })
}

async function load() {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
    }
    Object.entries(filters.value).forEach(([key, value]) => {
      if (value !== '' && value !== null && value !== undefined) params[key] = value
    })
    const overviewParams = { ...params }
    delete overviewParams.page
    delete overviewParams.size
    const [overviewResp, listResp] = await Promise.all([
      http.get('/api/admin/behavior-logs/overview', { params: overviewParams }),
      http.get('/api/admin/behavior-logs', { params }),
    ])
    summary.value = overviewResp.data.data || summary.value
    const listData = listResp.data.data || { records: [], total: 0 }
    records.value = listData.records || []
    total.value = Number(listData.total || 0)
    renderCharts()
  } catch (e) {
    ElMessage.error(e?.message || '加载行为日志失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.value = { startDate: '', endDate: '', actionType: '', userId: '', postId: '' }
  page.value = 1
  load()
}

function onPageChange(val) {
  page.value = val
  load()
}

watch(size, () => {
  page.value = 1
  load()
})

onMounted(load)

onBeforeUnmount(() => {
  trendChart?.dispose()
  typeChart?.dispose()
  topUsersChart?.dispose()
  topPostsChart?.dispose()
})
</script>

<template>
  <div class="wrap" v-loading="loading">
    <el-card class="filter-card">
      <div class="filters">
        <el-input v-model="filters.userId" placeholder="用户ID" clearable size="small" />
        <el-input v-model="filters.postId" placeholder="帖子ID" clearable size="small" />
        <el-select v-model="filters.actionType" placeholder="行为类型" clearable size="small">
          <el-option label="浏览" value="view" />
          <el-option label="点赞" value="like" />
          <el-option label="评论" value="comment" />
          <el-option label="分享" value="share" />
        </el-select>
        <el-date-picker v-model="filters.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" size="small" />
        <el-date-picker v-model="filters.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" size="small" />
        <el-button type="primary" size="small" @click="page = 1; load()">查询</el-button>
        <el-button size="small" @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <div class="kpi">
      <el-card shadow="hover"><div class="k">总行为数</div><div class="v">{{ summary.totalCount }}</div></el-card>
      <el-card shadow="hover"><div class="k">活跃用户</div><div class="v">{{ summary.activeUsers }}</div></el-card>
      <el-card shadow="hover"><div class="k">当前页条数</div><div class="v">{{ records.length }}</div></el-card>
      <el-card shadow="hover"><div class="k">总记录数</div><div class="v">{{ total }}</div></el-card>
    </div>

    <div class="grid2">
      <el-card shadow="hover">
        <template #header><span class="chart-title">近7日行为趋势</span></template>
        <div ref="trendRef" class="chart h260" />
      </el-card>
      <el-card shadow="hover">
        <template #header><span class="chart-title">行为类型分布</span></template>
        <div ref="typeRef" class="chart h260" />
      </el-card>
      <el-card shadow="hover">
        <template #header><span class="chart-title">活跃用户 TOP10</span></template>
        <div ref="topUsersRef" class="chart h240" />
      </el-card>
      <el-card shadow="hover">
        <template #header><span class="chart-title">热门帖子 TOP10</span></template>
        <div ref="topPostsRef" class="chart h240" />
      </el-card>
    </div>

    <el-card class="table-card">
      <template #header><span class="chart-title">行为明细</span></template>
      <el-table :data="records" size="small" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="user_id" label="用户ID" width="120" />
        <el-table-column prop="post_id" label="帖子ID" width="120" />
        <el-table-column prop="action_type" label="行为" width="100" />
        <el-table-column prop="action_time" label="时间" width="180" />
        <el-table-column prop="duration" label="时长(s)" min-width="100" />
      </el-table>
      <div class="pager">
        <el-pagination
            v-model:current-page="tablePage"
            v-model:page-size="size"
            :total="total"
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 20, 50]"
            size="small"
            @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}

.filter-card :deep(.el-card__body) {
  padding: 14px 16px;
}
.filters {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr 1fr auto auto;
  gap: 10px;
  align-items: center;
}

.kpi {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.kpi .el-card {
  text-align: center;
}
.kpi :deep(.el-card__body) {
  padding: 20px 16px;
}
.k {
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
}
.v {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
}
.chart {
  width: 100%;
}
.h260 {
  height: 260px;
}
.h240 {
  height: 240px;
}

.table-card {
  overflow: hidden;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>