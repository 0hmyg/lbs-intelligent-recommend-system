<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { http } from '../../api/http'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const fileList = ref([])

const form = reactive({
  title: '',
  content: '',
  category: 'help',
  auditMode: 'auto',
})

const categories = [
  { label: '求助', value: 'help' },
  { label: '闲置', value: 'secondhand' },
  { label: '活动', value: 'activity' },
  { label: '问答', value: 'qa' },
]

function beforeUpload(file) {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

function onChange(uploadFile, uploadFiles) {
  fileList.value = uploadFiles.slice(-9)
}

function onRemove(uploadFile, uploadFiles) {
  fileList.value = uploadFiles
}

async function submit() {
  loading.value = true
  try {
    const formData = new FormData()
    formData.append('data', new Blob([JSON.stringify({
      title: form.title,
      content: form.content,
      category: form.category,
      auditMode: form.auditMode,
    })], { type: 'application/json' }))
    fileList.value.forEach((item) => {
      if (item.raw) formData.append('images', item.raw)
    })
    const resp = await http.post('/api/posts', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    ElMessage.success('已发布（待审核）')
    router.push(`/app/post/${resp.data.data.id}`)
  } catch (e) {
    ElMessage.error(e?.message || '发布失败（可能未绑定位置）')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-card>
    <template #header>发布信息</template>
    <el-form label-position="top">
      <el-form-item label="类型">
        <el-select v-model="form.category" style="width: 220px">
          <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="审核方式">
        <el-radio-group v-model="form.auditMode">
          <el-radio label="auto">自动审核</el-radio>
          <el-radio label="manual">人工审核</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="form.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="内容">
        <el-input v-model="form.content" type="textarea" :rows="6" />
      </el-form-item>
      <el-form-item label="图片上传">
        <el-upload
          v-model:file-list="fileList"
          action="#"
          list-type="picture-card"
          :auto-upload="false"
          :before-upload="beforeUpload"
          :on-remove="onRemove"
          multiple
          accept="image/*"
        >
          <el-icon><Plus /></el-icon>
        </el-upload>
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="submit">提交</el-button>
    </el-form>
  </el-card>
</template>

