<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../../api/http'

const loading = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const form = ref({ word: '', level: 'block' })

async function load() {
  loading.value = true
  try {
    const resp = await http.get('/api/admin/sensitive-words')
    list.value = resp.data.data || []
  } catch (e) {
    ElMessage.error(e?.message || '加载敏感词失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { word: '', level: 'block' }
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = { word: row.word, level: row.level || 'block' }
  dialogVisible.value = true
}

async function submit() {
  try {
    if (!form.value.word.trim()) {
      ElMessage.warning('请输入敏感词')
      return
    }
    const payload = { word: form.value.word.trim(), level: form.value.level || 'block' }
    if (editingId.value) {
      await http.put(`/api/admin/sensitive-words/${editingId.value}`, payload)
    } else {
      await http.post('/api/admin/sensitive-words', payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除敏感词「${row.word}」吗？`, '提示', { type: 'warning' })
    await http.delete(`/api/admin/sensitive-words/${row.id}`)
    ElMessage.success('删除成功')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="wrap" v-loading="loading">
    <el-card>
      <template #header>
        <div class="header">
          <span>敏感词库</span>
          <el-button type="primary" @click="openCreate">新增敏感词</el-button>
        </div>
      </template>
      <el-table :data="list" style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="word" label="敏感词" min-width="180" />
        <el-table-column prop="level" label="级别" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑敏感词' : '新增敏感词'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="敏感词">
          <el-input v-model="form.word" placeholder="请输入敏感词" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.level" style="width: 100%">
            <el-option label="block（拦截）" value="block" />
            <el-option label="review（人工复审）" value="review" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wrap { display: flex; flex-direction: column; gap: 12px; }
.header { display: flex; justify-content: space-between; align-items: center; }
</style>
