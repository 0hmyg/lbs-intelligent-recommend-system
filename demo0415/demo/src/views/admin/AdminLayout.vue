<script setup>
import { useRouter, useRoute } from 'vue-router'
import { computed } from 'vue'

const router = useRouter()
const route = useRoute()
const active = computed(() => route.path)

function go(path) {
  router.push(path)
}
</script>

<template>
  <div class="layout">
    <div class="sidebar">
      <div class="brand" @click="go('/app/feed')">← 返回用户端</div>
      <div class="menu">
        <div class="item" :class="{ on: active.startsWith('/admin/dashboard') }" @click="go('/admin/dashboard')">数据报表</div>
        <div class="item" :class="{ on: active.startsWith('/admin/behavior-logs') }" @click="go('/admin/behavior-logs')">行为日志</div>
        <div class="item" :class="{ on: active.startsWith('/admin/blocked-posts') }" @click="go('/admin/blocked-posts')">拦截帖子</div>
        <div class="item" :class="{ on: active.startsWith('/admin/sensitive-words') }" @click="go('/admin/sensitive-words')">敏感词库</div>
        <div class="item" :class="{ on: active.startsWith('/admin/user-profiles') }" @click="go('/admin/user-profiles')">用户画像</div>
        <div class="item" :class="{ on: active.startsWith('/admin/audit') }" @click="go('/admin/audit')">内容审核</div>
      </div>
    </div>
    <div class="main">
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 220px 1fr;
}
.sidebar {
  border-right: 1px solid #eee;
  background: #fff;
}
.brand {
  padding: 14px;
  font-weight: 800;
  cursor: pointer;
  border-bottom: 1px solid #eee;
}
.menu {
  padding: 10px;
}
.item {
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  color: #555;
}
.item.on {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 700;
}
.main {
  padding: 12px;
  background: #f6f7fb;
}
</style>
