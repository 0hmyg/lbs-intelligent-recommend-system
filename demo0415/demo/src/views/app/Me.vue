<script setup>
import { computed, onMounted, ref } from 'vue'
import { http } from '../../api/http'
import { useAuthStore } from '../../stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'

const auth = useAuthStore()
const user = computed(() => auth.user)
const router = useRouter()

const loading = ref(false)
const postLoading = ref(false)
const postStatus = ref('all')
const posts = ref([])

async function refresh() {
  const resp = await http.get('/api/auth/me')
  auth.setAuth(auth.token, resp.data.data)
}

async function loadMyPosts() {
  postLoading.value = true
  try {
    const resp = await http.get('/api/posts/my', {
      params: { status: postStatus.value, page: 0, size: 50 },
    })
    posts.value = resp.data.data?.content || []
  } catch (e) {
    ElMessage.error(e?.message || '加载我的帖子失败')
  } finally {
    postLoading.value = false
  }
}

function statusText(row) {
  if (row.deletedAt) return '已删除'
  if (row.isAudited === 0) return '人工待审'
  if (row.isAudited === 1) return '已通过'
  if (row.isAudited === 2) return '自动拦截'
  if (row.isAudited === 3) return '申述中'
  if (row.isAudited === 4) return '人工驳回'
  return '未知'
}

function statusType(row) {
  if (row.deletedAt) return 'info'
  if (row.isAudited === 0) return 'warning'
  if (row.isAudited === 1) return 'success'
  if (row.isAudited === 2) return 'danger'    // 自动拦截 - 红色
  if (row.isAudited === 3) return 'warning'   // 申述中 - 橙色
  if (row.isAudited === 4) return 'danger'    // 人工驳回 - 红色
  return ''
}

async function removePost(id) {
  try {
    await ElMessageBox.confirm('确定删除该帖子？删除后不会在公开列表显示。', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await http.delete(`/api/posts/${id}`)
    ElMessage.success('删除成功')
    await loadMyPosts()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '删除失败')
  }
}

function toDetail(id) {
  router.push(`/app/post/${id}`)
}

onMounted(async () => {
  await refresh()
  await loadMyPosts()
})
</script>

<template>
  <el-card style="margin-bottom: 10px">
    <template #header>我的信息</template>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="ID">{{ user?.id }}</el-descriptions-item>
      <el-descriptions-item label="账号">{{ user?.username }}</el-descriptions-item>
      <el-descriptions-item label="昵称">{{ user?.nickname }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ user?.role }}</el-descriptions-item>
      <el-descriptions-item label="位置">{{ user?.locationName || '未绑定' }}</el-descriptions-item>
      <el-descriptions-item label="经纬度">
        <span v-if="user?.lng != null">{{ user?.lng }}, {{ user?.lat }}</span>
        <span v-else>未绑定</span>
      </el-descriptions-item>
    </el-descriptions>
    <div style="margin-top: 10px">
      <el-button @click="refresh">刷新</el-button>
    </div>
  </el-card>

  <el-card>
    <template #header>
      <div class="headRow">
        <span>我的帖子</span>
        <div>
          <el-select v-model="postStatus" style="width: 140px; margin-right: 8px" @change="loadMyPosts">
            <el-option label="全部" value="all" />
            <el-option label="待审核" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已删除" value="deleted" />
          </el-select>
          <el-button @click="loadMyPosts">刷新</el-button>
        </div>
      </div>
    </template>

    <el-table :data="posts" v-loading="postLoading" empty-text="暂无帖子">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row)">{{ statusText(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="150">
        <template #default="{ row }">
          {{ dayjs(row.createdAt).format('MM-DD HH:mm') }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button link type="primary" @click="toDetail(row.id)">查看</el-button>
          <el-button v-if="!row.deletedAt" link type="danger" @click="removePost(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.headRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>

