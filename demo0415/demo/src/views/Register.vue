<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  username: '',
  nickname: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

const usernameOk = computed(() => /^[a-zA-Z0-9_@.\-]{3,50}$/.test(form.username.trim()))
const phoneOk = computed(() => !form.phone.trim() || /^1\d{10}$/.test(form.phone.trim()))

async function onSubmit() {
  const username = form.username.trim()
  const nickname = form.nickname.trim()
  const phone = form.phone.trim()

  if (!username) return ElMessage.warning('请输入账号')
  if (!usernameOk.value) return ElMessage.warning('账号需 3-50 位，仅可包含字母、数字及 _ @ . -')
  if (!nickname) return ElMessage.warning('请输入昵称')
  if (nickname.length > 50) return ElMessage.warning('昵称不能超过 50 位')
  if (!phoneOk.value) return ElMessage.warning('手机号格式不正确')
  if (form.password.length < 6) return ElMessage.warning('密码至少 6 位')
  if (form.password !== form.confirmPassword) return ElMessage.warning('两次密码不一致')

  loading.value = true
  try {
    await http.post('/api/auth/register', {
      username,
      nickname,
      phone,
      password: form.password,
      confirmPassword: form.confirmPassword,
    })
    ElMessage.success('注册成功，请登录')
    router.replace('/login')
  } catch (e) {
    ElMessage.error(e?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="card">
      <div class="title">注册账号</div>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="账号">
          <el-input v-model="form.username" maxlength="50" placeholder="3-50 位，字母/数字/_/@/./-" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="50" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号（可选）">
          <el-input v-model="form.phone" maxlength="11" placeholder="11位手机号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="onSubmit">注册</el-button>
        <div class="tip">已有账号？<el-link type="primary" @click="router.push('/login')">去登录</el-link></div>
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
  width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.18);
}
.title {
  font-weight: 700;
  margin-bottom: 10px;
}
.tip {
  margin-top: 10px;
  color: #666;
  font-size: 12px;
}
</style>

