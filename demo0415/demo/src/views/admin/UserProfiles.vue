<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const manualDialogVisible = ref(false)
const batchDialogVisible = ref(false)
const dialogTitle = ref('')
const records = ref([])
const manualUserId = ref(null)

const form = reactive({ userId: null, tagText: '{\n  "鱼缸": 0.8\n}' })
const manualForm = reactive({ viewLimit: 50, likeLimit: 20, commentLimit: 10, shareLimit: 5, oldProfileWeight: 0.3 })
const batchForm = reactive({ minActions: 5, sinceHours: 24, viewLimit: 50, likeLimit: 20, commentLimit: 10, shareLimit: 5, oldProfileWeight: 0.3 })

function resetForm() {
  form.userId = null
  form.tagText = '{\n  "鱼缸": 0.8\n}'
}

function openCreate() {
  resetForm()
  dialogTitle.value = '新增用户画像'
  dialogVisible.value = true
}

function openEdit(row) {
  form.userId = row.userId
  form.tagText = JSON.stringify(row.tagVector || {}, null, 2)
  dialogTitle.value = `编辑用户画像 - 用户 ${row.userId}`
  dialogVisible.value = true
}

function openManualUpdate(row) {
  manualUserId.value = row.userId
  manualDialogVisible.value = true
}

function openBatchUpdate() {
  batchDialogVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const resp = await http.get('/api/admin/user-profiles')
    records.value = resp.data.data?.records || []
  } catch (e) {
    ElMessage.error(e?.message || '加载用户画像失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  let tagVector
  try {
    tagVector = JSON.parse(form.tagText || '{}')
  } catch (e) {
    ElMessage.error('标签向量 JSON 格式不正确')
    return
  }
  saving.value = true
  try {
    await http.post('/api/admin/user-profiles', { userId: form.userId, tagVector })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function submitManualUpdate() {
  try {
    await http.post(`/api/admin/user-profiles/${manualUserId.value}/manual-update`, manualForm)
    ElMessage.success('手动更新已提交')
    manualDialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e?.message || '手动更新失败')
  }
}

async function submitBatchUpdate() {
  try {
    await http.post('/api/admin/user-profiles/batch-update', batchForm)
    ElMessage.success('批量更新已提交')
    batchDialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e?.message || '批量更新失败')
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除用户 ${row.userId} 的画像？`, '提示', { type: 'warning' })
    await http.delete(`/api/admin/user-profiles/${row.userId}`)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="toolbar">
      <div class="toolbar-left">
        <h2>用户画像管理</h2>
        <p>支持注册默认创建、手动更新与定时批量更新</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="openBatchUpdate">批量更新画像</el-button>
        <el-button type="primary" @click="openCreate">新增画像</el-button>
      </div>
    </div>

    <el-card class="table-card">
      <el-table :data="records" stripe border style="width: 100%" :max-height="600">
        <el-table-column prop="userId" label="用户ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" width="120" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" width="120" show-overflow-tooltip />
        <el-table-column label="标签向量" min-width="200">
          <template #default="scope">
            <div class="json-wrapper">
              <pre class="json">{{ JSON.stringify(scope.row.tagVector || {}, null, 2) }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="150" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="success" size="small" @click="openManualUpdate(scope.row)">更新</el-button>
            <el-button link type="danger" size="small" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑画像 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <el-form label-width="90px" class="dialog-form">
        <el-form-item label="用户ID">
          <el-input v-model="form.userId" type="number" placeholder="请输入用户ID" style="width: 240px" />
        </el-form-item>
        <el-form-item label="标签向量">
          <el-input v-model="form.tagText" type="textarea" :rows="10" placeholder='例如 {"鱼缸": 0.8, "宠物": 0.6}' class="json-textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 手动更新画像 -->
    <el-dialog v-model="manualDialogVisible" title="手动更新画像" width="460px">
      <el-form label-width="140px">
        <el-form-item label="view_limit"><el-input-number v-model="manualForm.viewLimit" :min="1" size="small" /></el-form-item>
        <el-form-item label="like_limit"><el-input-number v-model="manualForm.likeLimit" :min="1" size="small" /></el-form-item>
        <el-form-item label="comment_limit"><el-input-number v-model="manualForm.commentLimit" :min="1" size="small" /></el-form-item>
        <el-form-item label="share_limit"><el-input-number v-model="manualForm.shareLimit" :min="1" size="small" /></el-form-item>
        <el-form-item label="old_profile_weight"><el-input-number v-model="manualForm.oldProfileWeight" :min="0" :max="1" :step="0.1" size="small" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitManualUpdate">提交更新</el-button>
      </template>
    </el-dialog>

    <!-- 批量更新画像 -->
    <el-dialog v-model="batchDialogVisible" title="批量更新画像" width="460px">
      <el-form label-width="140px">
        <el-form-item label="min_actions"><el-input-number v-model="batchForm.minActions" :min="1" size="small" /></el-form-item>
        <el-form-item label="since_hours"><el-input-number v-model="batchForm.sinceHours" :min="1" size="small" /></el-form-item>
        <el-form-item label="view_limit"><el-input-number v-model="batchForm.viewLimit" :min="1" size="small" /></el-form-item>
        <el-form-item label="like_limit"><el-input-number v-model="batchForm.likeLimit" :min="1" size="small" /></el-form-item>
        <el-form-item label="comment_limit"><el-input-number v-model="batchForm.commentLimit" :min="1" size="small" /></el-form-item>
        <el-form-item label="share_limit"><el-input-number v-model="batchForm.shareLimit" :min="1" size="small" /></el-form-item>
        <el-form-item label="old_profile_weight"><el-input-number v-model="batchForm.oldProfileWeight" :min="0" :max="1" :step="0.1" size="small" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBatchUpdate">提交批量更新</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; padding: 0; }

.toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.toolbar-left h2 { margin: 0; font-size: 18px; font-weight: 600; color: #303133; }
.toolbar-left p { margin: 4px 0 0; color: #909399; font-size: 13px; }
.toolbar-actions { display: flex; gap: 8px; }

.table-card { flex: 1; }
.json-wrapper { max-height: 80px; overflow-y: auto; }
.json { margin: 0; white-space: pre-wrap; word-break: break-all; font-size: 12px; background: #f5f7fa; padding: 6px 10px; border-radius: 4px; line-height: 1.5; font-family: 'Courier New', monospace; color: #606266; }

.dialog-form { padding: 8px 0; }
.json-textarea :deep(textarea) { font-family: 'Courier New', monospace; font-size: 13px; line-height: 1.5; }

@media (max-width: 768px) {
  .toolbar { flex-direction: column; align-items: flex-start; }
  .toolbar-actions { width: 100%; }
}
</style>