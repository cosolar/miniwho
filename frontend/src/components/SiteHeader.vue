<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Menu, Close } from '@element-plus/icons-vue'
import BrandLogo from './BrandLogo.vue'
import UserAvatar from './UserAvatar.vue'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)

/** 头像位没设图时退回昵称首字，和顶栏原来的字母圆一致。 */
const letter = computed(() => (auth.user?.nickname || auth.user?.username || '我').slice(0, 1))

const SECTION_OFFSET = 84
const HOME_SECTIONS = ['how-it-works', 'privacy']
const activeAnchor = ref('')
let spyPaused = false
let settleTimer = 0

const activeKey = computed(() => {
  if (route.path !== '/') return ''
  return activeAnchor.value || 'home'
})

function resumeSpy() {
  spyPaused = false
  measureAnchor()
}

function measureAnchor() {
  if (spyPaused || route.path !== '/') return
  let current = ''
  for (const id of HOME_SECTIONS) {
    const el = document.getElementById(id)
    if (el && el.getBoundingClientRect().top <= SECTION_OFFSET + 8) current = id
  }
  activeAnchor.value = current
}

function onScroll() {
  if (!spyPaused) {
    measureAnchor()
    return
  }
  // 点击或路由跳转引发的滚动途中不干扰高亮，停下后再接管。
  clearTimeout(settleTimer)
  settleTimer = setTimeout(resumeSpy, 140)
}

function spyTo(id) {
  activeAnchor.value = id
  spyPaused = true
  clearTimeout(settleTimer)
  settleTimer = setTimeout(resumeSpy, 1500)
}

async function goSection(id) {
  mobileOpen.value = false
  if (route.path !== '/') {
    spyTo(id)
    await router.push({ path: '/', hash: `#${id}` })
    return
  }
  const el = document.getElementById(id)
  if (!el) return
  spyTo(id)
  window.scrollTo({
    top: window.scrollY + el.getBoundingClientRect().top - SECTION_OFFSET,
    behavior: 'smooth'
  })
}

watch(
  () => route.fullPath,
  async () => {
    mobileOpen.value = false
    if (route.path !== '/') {
      activeAnchor.value = ''
      return
    }
    await nextTick()
    const id = route.hash.slice(1)
    if (HOME_SECTIONS.includes(id)) spyTo(id)
    else measureAnchor()
  }
)

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onScroll)
  measureAnchor()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onScroll)
  clearTimeout(settleTimer)
})

function go(path) {
  mobileOpen.value = false
  router.push(path)
}

async function logout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}
</script>

<template>
  <header class="site-header">
    <div class="site-header__inner container">
      <BrandLogo />
      <nav class="site-nav" aria-label="主导航">
        <router-link to="/" :class="{ 'is-active': activeKey === 'home' }">首页</router-link>
        <router-link to="/templates" active-class="is-active">模板中心</router-link>
        <a
          href="/#how-it-works"
          :class="{ 'is-active': activeKey === 'how-it-works' }"
          @click.prevent="goSection('how-it-works')"
        >使用方式</a>
        <a
          href="/#privacy"
          :class="{ 'is-active': activeKey === 'privacy' }"
          @click.prevent="goSection('privacy')"
        >隐私设计</a>
      </nav>
      <div class="site-actions">
        <template v-if="auth.isLoggedIn">
          <button class="m-btn m-btn--ghost header-cta" @click="go('/dashboard')">进入工作台</button>
          <el-dropdown trigger="click" @command="(cmd) => cmd === 'logout' ? logout() : go(cmd)">
            <button class="avatar-button" type="button" aria-label="账号菜单">
              <UserAvatar class="avatar" :text="letter" />
              <el-icon class="avatar-caret"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ auth.user?.nickname || auth.user?.username }}</el-dropdown-item>
                <el-dropdown-item command="/dashboard" divided>我的简历</el-dropdown-item>
                <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                <el-dropdown-item v-if="auth.isAdmin" command="/admin">管理后台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <button class="login-link" @click="go('/login')">登录</button>
          <button class="m-btn m-btn--primary header-cta" @click="go('/login?mode=register')">免费开始</button>
        </template>
        <button class="mobile-toggle" type="button" :aria-label="mobileOpen ? '关闭菜单' : '打开菜单'" @click="mobileOpen = !mobileOpen">
          <el-icon><Close v-if="mobileOpen" /><Menu v-else /></el-icon>
        </button>
      </div>
    </div>
    <transition name="fade">
      <div v-if="mobileOpen" class="mobile-panel">
        <router-link to="/" :class="{ 'is-active': activeKey === 'home' }">首页</router-link>
        <router-link to="/templates" active-class="is-active">模板中心</router-link>
        <a href="/#how-it-works" :class="{ 'is-active': activeKey === 'how-it-works' }" @click.prevent="goSection('how-it-works')">使用方式</a>
        <a href="/#privacy" :class="{ 'is-active': activeKey === 'privacy' }" @click.prevent="goSection('privacy')">隐私设计</a>
        <template v-if="auth.isLoggedIn">
          <router-link to="/dashboard">我的简历</router-link>
          <router-link to="/profile">个人中心</router-link>
          <button class="mobile-logout" @click="logout">退出登录</button>
        </template>
        <template v-else>
          <router-link to="/login">登录</router-link>
          <router-link to="/login?mode=register" class="mobile-primary">免费注册</router-link>
        </template>
      </div>
    </transition>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 80;
  border-bottom: 1px solid rgba(210, 202, 186, 0.55);
  background: rgba(244, 241, 233, 0.86);
  backdrop-filter: blur(16px) saturate(1.2);
}

.site-header__inner {
  display: flex;
  align-items: center;
  height: var(--header-h);
  gap: 44px;
}

.site-nav {
  display: flex;
  align-items: center;
  gap: 30px;
  flex: 1;
}

.site-nav a {
  position: relative;
  color: var(--ink-soft);
  font-size: 13px;
  letter-spacing: 0.03em;
  transition: color 0.2s ease;
}

.site-nav a::after {
  content: "";
  position: absolute;
  left: 0;
  right: 100%;
  bottom: -7px;
  height: 1px;
  background: var(--accent);
  transition: right 0.25s ease;
}

.site-nav a:hover,
.site-nav a.is-active {
  color: var(--accent);
}

.site-nav a:hover::after,
.site-nav a.is-active::after {
  right: 0;
}

.site-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.login-link {
  padding: 8px 2px;
  border: none;
  background: none;
  color: var(--ink-soft);
  font-size: 13px;
  cursor: pointer;
}

.login-link:hover {
  color: var(--accent);
}

.header-cta {
  height: 40px;
  padding: 0 20px;
  font-size: 13px;
}

.avatar-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
}

.avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--ink);
  color: #f7f4ec;
  font-family: var(--serif);
  font-size: 15px;
}

.avatar-caret {
  color: var(--muted);
  font-size: 11px;
}

.mobile-toggle {
  display: none;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper);
  color: var(--ink);
  font-size: 18px;
  cursor: pointer;
}

.mobile-panel {
  display: none;
  padding: 12px 24px 22px;
  border-top: 1px solid var(--line);
  background: var(--paper);
}

.mobile-panel a,
.mobile-logout {
  display: block;
  padding: 12px 4px;
  border: none;
  border-bottom: 1px solid var(--line);
  background: none;
  color: var(--ink);
  font-family: inherit;
  font-size: 14px;
  text-align: left;
}

.mobile-panel a.is-active {
  color: var(--accent);
}

.mobile-panel .mobile-primary {
  margin-top: 14px;
  border: none;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  text-align: center;
}

@media (max-width: 960px) {
  .site-nav {
    display: none;
  }

  .site-header__inner {
    justify-content: space-between;
  }

  .site-actions {
    gap: 10px;
  }

  .mobile-toggle {
    display: grid;
  }

  .mobile-panel {
    display: block;
  }
}

@media (max-width: 560px) {
  .header-cta {
    display: none;
  }

  .login-link {
    display: none;
  }
}
</style>
