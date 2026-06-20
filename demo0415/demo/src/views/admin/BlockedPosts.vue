<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'

const loading = ref(false)
const list = ref([])
const keyword = ref('')

// ===== 搜索过滤 =====
const filteredList = computed(() => {
  let items = list.value || []
  const k = keyword.value.trim().toLowerCase()
  if (k) {
    items = items.filter((row) => {
      const text = [
        row.title,
        row.content,
        row.auditReason,
        row.moderationHitWords,
        row.moderationFilteredText
      ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase()
      return text.includes(k)
    })
  }
  return items
})

// ===== 加载拦截列表（status=2） =====
async function load() {
  loading.value = true
  try {
    const resp = await http.get('/api/admin/post-moderation/blocked', {
      params: { status: 2 }
    })
    const data = resp.data?.data
    list.value = Array.isArray(data) ? data : data?.content || []
  } catch (e) {
    ElMessage.error(e?.message || '加载拦截列表失败')
  } finally {
    loading.value = false
  }
}

// ===== 管理员审核 =====
async function review(row, allowed) {
  try {
    const action = allowed ? '放行' : '驳回'
    const { value } = await ElMessageBox.prompt(`请输入${action}原因`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: allowed ? '例如：审核通过' : '例如：内容违规',
    })
    await http.post(`/api/admin/post-moderation/${row.id}/decision`, {
      allowed,
      reason: value
    })
    ElMessage.success('处理成功')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '处理失败')
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="blocked-wrap">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span class="title">拦截管理</span>
            <span class="sub">被自动拦截的帖子列表</span>
          </div>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="toolbar">
        <el-input
            v-model="keyword"
            clearable
            placeholder="搜索标题、内容、命中词"
            class="search-input"
        />
      </div>

      <!-- 表格 -->
      <el-table :data="filteredList" style="width: 100%" size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="danger" size="small">自动拦截</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="moderationHitWords" label="命中词" width="140" show-overflow-tooltip />
        <el-table-column prop="auditReason" label="拦截原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="moderationCheckedAt" label="检查时间" width="160" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="review(row, true)">放行</el-button>
            <el-button link type="danger" size="small" @click="review(row, false)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.blocked-wrap {
  padding: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: 700;
}

.sub {
  margin-left: 12px;
  color: #8b8b8b;
  font-size: 13px;
}

.toolbar {
  margin-bottom: 14px;
}

.search-input {
  max-width: 360px;
}
</style>