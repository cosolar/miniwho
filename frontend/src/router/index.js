import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/',
    component: () => import('../layouts/PublicLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('../pages/HomePage.vue'), meta: { title: '把履历写清楚' } },
      { path: 'templates', name: 'templates', component: () => import('../pages/TemplatesPage.vue'), meta: { title: '模板中心' } }
    ]
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../pages/LoginPage.vue'),
    meta: { title: '登录 / 注册', guestOnly: true }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('../pages/DashboardPage.vue'),
    meta: { title: '我的简历', requiresAuth: true }
  },
  {
    path: '/resumes/:id(\\d+)',
    name: 'workbench',
    component: () => import('../pages/WorkbenchPage.vue'),
    meta: { title: '编辑简历', requiresAuth: true }
  },
  {
    path: '/resumes/:id(\\d+)/preview',
    name: 'preview',
    component: () => import('../pages/PreviewPage.vue'),
    meta: { title: '预览与下载', requiresAuth: true }
  },
  {
    path: '/resumes/:id(\\d+)/report',
    name: 'report',
    component: () => import('../pages/ReportPage.vue'),
    meta: { title: '简历诊断', requiresAuth: true }
  },
  {
    path: '/my-templates',
    name: 'my-templates',
    component: () => import('../pages/MyTemplatesPage.vue'),
    meta: { title: '我的模板', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../pages/ProfilePage.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../pages/AdminPage.vue'),
    meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/share/:token',
    name: 'share',
    component: () => import('../pages/SharePage.vue'),
    meta: { title: '简历分享' }
  },
  // /s/ 是 1.0.3 及更早版本发出的地址，已经在别人的邮件和聊天窗口里，保留一跳转发。
  { path: '/s/:token', redirect: (to) => `/share/${to.params.token}` },
  {
    path: '/export/:token',
    name: 'export',
    component: () => import('../pages/ExportPage.vue'),
    meta: { title: '简历导出' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../pages/NotFoundPage.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth', top: 84 }
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (auth.token && !auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      // The request interceptor already clears an invalid session.
    }
  }
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { path: '/dashboard' }
  }
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return { path: '/dashboard' }
  }
  document.title = to.meta.title ? `${to.meta.title} · 极简简历 MiniWho` : '极简简历 MiniWho'
  return true
})

export default router
