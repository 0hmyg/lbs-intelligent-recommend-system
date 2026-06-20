<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api/http'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const query = ref('')
const answer = ref('')
const reason = ref('')
const items = ref([])
const prompt = ref(null)
const aiResponse = ref(null)
const raw = ref('')

async function fetchRecommend() {
  loading.value = true
  try {
    const resp = await http.get('/api/recommendations/chat', {
      params: {
        q: query.value || undefined,
        limit: 10,
      },
    })
    const data = resp.data.data || {}
    answer.value = data.answer || ''
    reason.value = data.reason || ''
    items.value = data.posts || []
    prompt.value = data.prompt || null
    aiResponse.value = data.aiResponse || null
    raw.value = data.raw || ''
  } catch (e) {
    ElMessage.error(e?.message || '推荐失败')
  } finally {
    loading.value = false
  }
}

function openPost(id) {
  router.push(`/app/post/${id}`)
}

onMounted(fetchRecommend)
</script>

<template>
  <div class="assistant-page">
    <el-card class="assistant-card">
      <template #header>
        <div class="header-wrap">
          <div>
            <div class="title-main">智能帖子助手</div>
            <div class="subtitle">输入你的需求，我会结合历史行为和帖子内容帮你回答并推荐帖子</div>
          </div>
        </div>
      </template>

      <div class="search-shell">
        <el-input
          v-model="query"
          class="search-input"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="比如：帮我找二手猫用品、校园活动、求助帖子"
          @keyup.ctrl.enter="fetchRecommend"
        />
        <el-button class="send-btn" type="primary" :loading="loading" @click="fetchRecommend">发送</el-button>
      </div>

      <div class="conversation">
        <div v-if="query" class="bubble user-bubble">
          <div class="bubble-label">你</div>
          <div class="bubble-text">{{ query }}</div>
        </div>

        <div v-if="answer" class="bubble ai-bubble">
          <div class="bubble-label">AI 助手</div>
          <div class="bubble-text">{{ answer }}</div>
          <div v-if="reason" class="bubble-reason">{{ reason }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="assistant-card results-card">
      <template #header>
        <div class="results-header">
          <span>推荐帖子</span>
          <span class="count">{{ items.length }} 条</span>
        </div>
      </template>

      <el-skeleton v-if="loading && items.length === 0" :rows="5" animated />
      <el-empty v-else-if="!loading && items.length === 0" description="暂无推荐结果" />

      <div v-else class="post-list">
        <div v-for="p in items" :key="p.id" class="post-card" @click="openPost(p.id)">
          <div class="post-main">
            <div class="post-title">{{ p.title }}</div>
            <div class="post-desc">{{ p.content }}</div>
            <div class="post-meta">
              <el-tag size="small">{{ p.category }}</el-tag>
              <span>{{ p.locationName || '未知位置' }}</span>
            </div>
          </div>
          <div class="post-arrow">›</div>
        </div>
      </div>
    </el-card>

    <details class="debug" v-if="prompt || aiResponse || raw">
      <summary>调试信息</summary>
      <pre v-if="prompt">PROMPT: {{ JSON.stringify(prompt, null, 2) }}</pre>
      <pre v-if="aiResponse">AI_RESPONSE: {{ JSON.stringify(aiResponse, null, 2) }}</pre>
      <pre v-if="raw">RAW: {{ raw }}</pre>
    </details>
  </div>
</template>

<style scoped>
.assistant-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.assistant-card {
  border-radius: 16px;
}
.header-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title-main {
  font-size: 18px;
  font-weight: 800;
}
.subtitle {
  color: #8b93a7;
  font-size: 12px;
  margin-top: 4px;
}
.search-shell {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.search-input :deep(textarea) {
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}
.send-btn {
  align-self: flex-end;
  border-radius: 999px;
  padding: 10px 18px;
}
.conversation {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.bubble {
  max-width: 92%;
  padding: 14px 16px;
  border-radius: 16px;
  line-height: 1.75;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}
.user-bubble {
  margin-left: auto;
  background: linear-gradient(135deg, #409eff 0%, #6aa7ff 100%);
  color: #fff;
  border-bottom-right-radius: 6px;
}
.ai-bubble {
  margin-right: auto;
  background: #ffffff;
  border: 1px solid #edf2f7;
  border-bottom-left-radius: 6px;
}
.bubble-label {
  font-size: 12px;
  opacity: 0.75;
  margin-bottom: 6px;
}
.bubble-text {
  font-size: 14px;
}
.bubble-reason {
  margin-top: 10px;
  font-size: 12px;
  color: #64748b;
}
.results-card {
  border-radius: 16px;
}
.results-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.count {
  color: #8b93a7;
  font-size: 12px;
}
.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.post-card {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #edf2f7;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.post-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.08);
}
.post-main {
  flex: 1;
  min-width: 0;
}
.post-title {
  font-weight: 800;
  margin-bottom: 6px;
  color: #1f2937;
}
.post-desc {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.post-meta {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  color: #94a3b8;
  font-size: 12px;
}
.post-arrow {
  display: flex;
  align-items: center;
  color: #cbd5e1;
  font-size: 28px;
  padding-right: 4px;
}
.debug {
  padding: 10px;
  background: #fafafa;
  border: 1px dashed #ddd;
  border-radius: 8px;
}
.debug pre {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
