<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'
import dayjs from 'dayjs'
import { useRoute } from 'vue-router'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

function resolveImageUrl(url) {
  if (!url) return ''
  if (url.startsWith('/uploads/') || url.startsWith('uploads/')) {
    return `${API_BASE_URL}${url.startsWith('/') ? '' : '/'}${url}`
  }
  return ''
}

const route = useRoute()
const id = Number(route.params.id)

const loading = ref(false)
const post = ref(null)
const comments = ref([])
const commentForm = reactive({ content: '' })
const liking = ref(false)
const liked = ref(false)
const canLike = ref(true)
const appealing = ref(false)

async function load() {
  loading.value = true
  try {
    const resp = await http.get(`/api/posts/${id}`)
    post.value = resp.data.data
    await http.post(`/api/posts/${id}/view`)
    const likeResp = await http.get(`/api/posts/${id}/like-status`)
    liked.value = !!likeResp.data.data?.liked
    canLike.value = !!likeResp.data.data?.canLike
    await loadComments()
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadComments() {
  const resp = await http.get(`/api/posts/${id}/comments`, { params: { page: 0, size: 50 } })
  comments.value = resp.data.data.content || []
}

async function submitComment() {
  if (!commentForm.content.trim()) return
  try {
    await http.post(`/api/posts/${id}/comments`, { content: commentForm.content })
    commentForm.content = ''
    ElMessage.success('已评论')
    await loadComments()
    // 刷新详情统计
    const resp = await http.get(`/api/posts/${id}`)
    post.value = resp.data.data
  } catch (e) {
    ElMessage.error(e?.message || '评论失败')
  }
}

async function toggleLike() {
  if (!canLike.value) return
  liking.value = true
  try {
    if (liked.value) {
      await http.delete(`/api/posts/${id}/like`)
      ElMessage.success('已取消点赞')
      post.value.likeCount = Math.max(0, (post.value.likeCount || 0) - 1)
      liked.value = false
    } else {
      await http.post(`/api/posts/${id}/like`)
      ElMessage.success('已点赞')
      post.value.likeCount = (post.value.likeCount || 0) + 1
      liked.value = true
    }
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    liking.value = false
  }
}

async function submitAppeal() {
  try {
    const { value } = await ElMessageBox.prompt('请输入申述原因', '帖子申述', {
      confirmButtonText: '提交申述',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：内容没有违规，请复核',
      inputPattern: /.+/,
      inputErrorMessage: '申述原因不能为空',
    })
    appealing.value = true
    await http.post(`/api/posts/${id}/appeal`, { reason: value })
    ElMessage.success('申述已提交，等待管理员复核')
    const resp = await http.get(`/api/posts/${id}`)
    post.value = resp.data.data
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '申述失败')
  } finally {
    appealing.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-skeleton v-if="loading && !post" :rows="8" animated />

  <template v-else-if="post">
    <el-card>
      <img
        v-if="post.images && post.images.length"
        class="cover"
        :src="resolveImageUrl(post.images[0])"
        alt="帖子封面图"
      />
      <div class="title">{{ post.title }}</div>
      <div class="meta">
        <el-tag size="small">{{ post.category }}</el-tag>
        <span class="muted">{{ post.locationName || '未知位置' }}</span>
        <span class="muted">{{ dayjs(post.createdAt).format('YYYY-MM-DD HH:mm') }}</span>
      </div>
      <div class="content">{{ post.content }}</div>
      <div class="counts">
        <span>👁 {{ post.viewCount || 0 }}</span>
        <span>👍 {{ post.likeCount || 0 }}</span>
        <span>💬 {{ post.commentCount || 0 }}</span>
      </div>
      <div class="actions">
        <el-button
          class="like-btn"
          :class="{ liked }"
          :disabled="!canLike"
          :loading="liking"
          @click="toggleLike"
        >
          {{ liked ? '已喜欢' : '喜欢' }}
        </el-button>
        <el-button
          v-if="post.isAudited === 2"
          type="warning"
          :loading="appealing"
          @click="submitAppeal"
        >
          申述
        </el-button>
      </div>
      <el-alert v-if="post.isAudited === 0" type="warning" :closable="false" show-icon style="margin-top: 10px">
        该帖子正在审核中，通过后才会出现在附近/热门列表
      </el-alert>
      <el-alert v-else-if="post.isAudited === 2" type="error" :closable="false" show-icon style="margin-top: 10px">
        已拦截：{{ post.auditReason || '无' }}
      </el-alert>
      <el-alert v-else-if="post.isAudited === 3" type="info" :closable="false" show-icon style="margin-top: 10px">
        申述中：{{ post.auditReason || '等待管理员复核' }}
      </el-alert>
    </el-card>

    <el-card style="margin-top: 10px">
      <template #header>评论</template>
      <div class="commentBox">
        <el-input v-model="commentForm.content" placeholder="写点什么…" />
        <el-button type="primary" @click="submitComment">发送</el-button>
      </div>
      <el-empty v-if="comments.length === 0" description="暂无评论" />
      <div v-else class="commentList">
        <div v-for="c in comments" :key="c.id" class="commentItem">
          <div class="cmeta">
            <span class="muted">#{{ c.id }} 用户{{ c.userId }}</span>
            <span class="muted">{{ dayjs(c.createdAt).format('MM-DD HH:mm') }}</span>
          </div>
          <div>{{ c.content }}</div>
        </div>
      </div>
    </el-card>
  </template>
</template>

<style scoped>
.cover {
  width: 100%;
  height: 260px;
  object-fit: cover;
  border-radius: 12px;
  margin-bottom: 10px;
  background: #f3f4f6;
}
.title {
  font-weight: 800;
  font-size: 16px;
  margin-bottom: 6px;
}
.meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}
.muted {
  color: #888;
  font-size: 12px;
}
.content {
  white-space: pre-wrap;
  line-height: 1.7;
}
.counts {
  display: flex;
  gap: 12px;
  margin-top: 10px;
  color: #555;
  font-size: 12px;
}
.actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.like-btn {
  transition: all 0.2s ease;
}
.like-btn::before {
  content: '♥';
  display: inline-block;
  margin-right: 6px;
  transform: translateY(-1px);
}
.like-btn.liked {
  opacity: 0.55;
  filter: grayscale(0.25);
}
.commentBox {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}
.commentList {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.commentItem {
  padding: 10px;
  border: 1px solid #eee;
  border-radius: 8px;
  background: #fff;
}
.cmeta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
</style>

