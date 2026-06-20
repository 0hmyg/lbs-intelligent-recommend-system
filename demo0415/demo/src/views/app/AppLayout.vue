<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const active = computed(() => route.path)

function go(path) {
  router.push(path)
}

function logout() {
  auth.logout()
  ElMessage.success('已退出')
  router.replace('/login')
}
</script>

<template>
  <div class="layout">
    <div class="topbar">
      <div class="brand">LSP Forum</div>
      <div class="right">
        <el-tag v-if="auth.role === 'admin'" type="warning" style="margin-right: 10px" @click="go('/admin/audit')">
          管理端
        </el-tag>
        <el-button size="small" @click="go('/app/guess-like')">猜你喜欢</el-button>
        <el-button size="small" @click="go('/app/smart')">智能推荐</el-button>
        <el-button size="small" @click="logout">退出</el-button>
      </div>
    </div>

    <div class="tabbar">
      <div class="tab" :class="{ on: active.startsWith('/app/feed') }" @click="go('/app/feed')">附近</div>
      <div class="tab" :class="{ on: active.startsWith('/app/guess-like') }" @click="go('/app/guess-like')">猜你喜欢</div>
      <div class="tab" :class="{ on: active.startsWith('/app/publish') }" @click="go('/app/publish')">发布</div>
      <div class="tab" :class="{ on: active.startsWith('/app/location') }" @click="go('/app/location')">位置</div>
      <div class="tab" :class="{ on: active.startsWith('/app/me') }" @click="go('/app/me')">我的</div>
    </div>

    <div class="content">
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid #eee;
  background: #fff;
  flex: 0 0 auto;
}
.brand {
  font-weight: 700;
}
.tabbar {
  height: 56px;
  background: #fff;
  border-top: 1px solid #eee;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  position: sticky;
  top: 0;
  z-index: 20;
  flex: 0 0 auto;
}
.tab {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  white-space: nowrap;
}
.tab.on {
  color: #409eff;
  font-weight: 700;
}
.content {
  flex: 1;
  padding: 12px;
  background: #f6f7fb;
  overflow: auto;
}
</style>
