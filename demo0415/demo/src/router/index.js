import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const Login = () => import('../views/Login.vue')
const Register = () => import('../views/Register.vue')
const AppLayout = () => import('../views/app/AppLayout.vue')
const Feed = () => import('../views/app/Feed.vue')
const Publish = () => import('../views/app/Publish.vue')
const Location = () => import('../views/app/Location.vue')
const Me = () => import('../views/app/Me.vue')
const PostDetail = () => import('../views/app/PostDetail.vue')
const SmartRecommend = () => import('../views/app/SmartRecommend.vue')
const GuessLike = () => import('../views/app/GuessLike.vue')

const AdminLayout = () => import('../views/admin/AdminLayout.vue')
const Audit = () => import('../views/admin/Audit.vue')
const Dashboard = () => import('../views/admin/Dashboard.vue')
const BehaviorLogs = () => import('../views/admin/BehaviorLogs.vue')
const BlockedPosts = () => import('../views/admin/BlockedPosts.vue')
const SensitiveWords = () => import('../views/admin/SensitiveWords.vue')
const UserProfiles = () => import('../views/admin/UserProfiles.vue')

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/app/feed' },
    { path: '/login', component: Login },
    { path: '/register', component: Register },

    {
      path: '/app',
      component: AppLayout,
      children: [
        { path: 'feed', component: Feed },
        { path: 'publish', component: Publish },
        { path: 'location', component: Location },
        { path: 'me', component: Me },
        { path: 'guess-like', component: GuessLike },
        { path: 'smart', component: SmartRecommend },
        { path: 'post/:id', component: PostDetail, props: true },
      ],
    },
    {
      path: '/admin',
      component: AdminLayout,
      children: [
        { path: 'audit', component: Audit },
        { path: 'dashboard', component: Dashboard },
        { path: 'behavior-logs', component: BehaviorLogs },
        { path: 'sensitive-words', component: SensitiveWords },
        { path: 'user-profiles', component: UserProfiles },
        { path: 'blocked-posts', component: BlockedPosts },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path === '/login' || to.path === '/register') return true
  if (!auth.isLoggedIn) return '/login'
  if (to.path.startsWith('/admin') && auth.role !== 'admin') return '/app/feed'
  return true
})
