<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'
import dayjs from 'dayjs'

const loading = ref(false)
const items = ref([])
const appealItems = ref([])
const preview = ref(null)
const activeTab = ref('audit')

async function load() {
  loading.value = true
  try {
    const [auditResp, appealResp] = await Promise.all([
      http.get('/api/admin/audits', { params: { page: 0, size: 50 } }),
      http.get('/api/admin/post-moderation/blocked', { params: { page: 0, size: 50, status: 3 } }),
    ])
    items.value = auditResp.data.data.content || []
    appealItems.value = appealResp.data.data.content || []
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// ===== 人工审核 - 查看详情 =====
async function openDetail(row) {
  try {
    const resp = await http.get(`/api/admin/audits/${row.id}`)
    preview.value = resp.data.data
  } catch (e) {
    ElMessage.error(e?.message || '加载详情失败')
  }
}

// ===== 申述管理 - 查看详情 =====
async function openAppealDetail(row) {
  try {
    const resp = await http.get(`/api/admin/post-moderation/${row.id}`)
    preview.value = resp.data.data
  } catch (e) {
    // 如果申述接口没有详情接口，直接用当前行数据
    preview.value = { ...row }
  }
}

async function approve(id) {
  try {
    await http.post(`/api/admin/audits/${id}/approve`)
    ElMessage.success('已通过')
    await load()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function reject(id) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '原因不能为空',
    })
    await http.post(`/api/admin/audits/${id}/reject`, { reason: value })
    ElMessage.success('已驳回')
    await load()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '操作失败')
  }
}

async function reviewAppeal(row, allowed) {
  try {
    const { value } = await ElMessageBox.prompt(
        allowed ? '请输入放行原因' : '请输入驳回原因',
        '申述管理',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputPlaceholder: allowed ? '例如：申述成立，内容合规' : '例如：申述理由不足，维持拦截',
        }
    )
    await http.post(`/api/admin/post-moderation/${row.id}/appeal-decision`, {
      allowed,
      reason: value,
    })
    ElMessage.success('处理成功')
    await load()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '处理失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="audit-wrap">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span class="title">内容审核</span>
            <span class="sub">人工审核与申述管理</span>
          </div>
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 人工审核 -->
        <el-tab-pane label="人工审核" name="audit">
          <el-empty v-if="!loading && items.length === 0" description="暂无待审核帖子" />
          <el-table v-else :data="items" size="small" v-loading="loading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
            <el-table-column label="发布时间" width="150">
              <template #default="{ row }">
                {{ dayjs(row.createdAt).format('MM-DD HH:mm') }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
                <el-button link type="success" size="small" @click="approve(row.id)">通过</el-button>
                <el-button link type="danger" size="small" @click="reject(row.id)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 申述管理 -->
        <el-tab-pane label="申述管理" name="appeal">
          <el-empty v-if="!loading && appealItems.length === 0" description="暂无申述帖子" />
          <el-table v-else :data="appealItems" size="small" v-loading="loading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
            <el-table-column prop="moderationHitWords" label="命中词" width="120" show-overflow-tooltip />
            <el-table-column prop="auditReason" label="申述原因" min-width="150" show-overflow-tooltip />
            <el-table-column label="发布时间" width="150">
              <template #default="{ row }">
                {{ dayjs(row.createdAt).format('MM-DD HH:mm') }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openAppealDetail(row)">详情</el-button>
                <el-button link type="success" size="small" @click="reviewAppeal(row, true)">放行</el-button>
                <el-button link type="danger" size="small" @click="reviewAppeal(row, false)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 帖子详情弹窗 -->
    <el-dialog
        :model-value="!!preview"
        title="帖子详情"
        width="700px"
        @update:model-value="(visible) => { if (!visible) preview = null }"
    >
      <template v-if="preview">
        <div class="detail-title">{{ preview.title }}</div>
        <div class="detail-meta">
          <el-tag size="small" type="info">{{ preview.category }}</el-tag>
          <span class="meta-location">{{ preview.locationName || '未知位置' }}</span>
          <span class="meta-time">{{ dayjs(preview.createdAt).format('YYYY-MM-DD HH:mm') }}</span>
        </div>

        <div class="detail-content">{{ preview.content }}</div>

        <!-- 审核信息（申述帖子显示） -->
        <div v-if="preview.moderationHitWords" class="detail-moderation">
          <div class="mod-item">
            <span class="mod-label">命中词：</span>
            <span class="mod-value danger">{{ preview.moderationHitWords }}</span>
          </div>
          <div class="mod-item">
            <span class="mod-label">拦截原因：</span>
            <span class="mod-value">{{ preview.auditReason || '-' }}</span>
          </div>
          <div v-if="preview.moderationFilteredText" class="mod-item">
            <span class="mod-label">过滤后文本：</span>
            <span class="mod-value filtered">{{ preview.moderationFilteredText }}</span>
          </div>
        </div>

        <div v-if="preview.images && preview.images.length" class="image-grid">
          <img
              v-for="img in preview.images"
              :key="img"
              class="detail-img"
              :src="img.startsWith('/uploads/') ? `http://localhost:8080${img}` : img"
          />
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.audit-wrap {
  padding: 16px;
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 16px;
  font-weight: 700;
}
.sub {
  margin-left: 10px;
  color: #8b8b8b;
  font-size: 12px;
}

/* 详情弹窗 */
.detail-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 10px;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  color: #909399;
  font-size: 13px;
}
.meta-location::before {
  content: '📍 ';
}
.meta-time {
  margin-left: auto;
}
.detail-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
  background: #f5f7fa;
  padding: 14px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  min-height: 60px;
}

/* 审核信息 */
.detail-moderation {
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
}
.mod-item {
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.6;
}
.mod-item:last-child {
  margin-bottom: 0;
}
.mod-label {
  color: #909399;
}
.mod-value {
  color: #303133;
}
.mod-value.danger {
  color: #f56c6c;
  font-weight: 600;
}
.mod-value.filtered {
  color: #e6a23c;
  font-style: italic;
}

/* 图片 */
.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}
.detail-img {
  width: 100%;
  height: 130px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}
</style>