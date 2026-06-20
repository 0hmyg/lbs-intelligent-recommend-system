<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

async function onSubmit() {
  loading.value = true
  try {
    const resp = await http.post('/api/auth/login', form)
    const { token, user } = resp.data.data
    auth.setAuth(token, user)
    ElMessage.success('登录成功')
    router.replace(user?.role === 'admin' ? '/admin/audit' : '/app/feed')
  } catch (e) {
    ElMessage.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="card">
      <div class="title">位置论坛系统</div>
      <el-form @submit.prevent="onSubmit" label-position="top">
        <el-form-item label="账号">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="onSubmit">登录</el-button>
        <div class="tip">
          还没有账号？<el-link type="primary" @click="router.push('/register')">去注册</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0b1220;
}
.card {
  width: 380px;
  background: #ffffff;
  border-radius: 12px;
  padding: 22px 22px 16px;
}
.title {
  font-weight: 700;
  font-size: 18px;
  margin-bottom: 14px;
}
.tip {
  margin-top: 10px;
  color: #666;
  font-size: 12px;
}
</style>

