<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Collection, Document, Grid, QuestionFilled, User, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BrandLogo from '../components/BrandLogo.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { useAuthStore } from '../stores/auth'
import { formatDateTime } from '../utils/format'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

/** 工作台和预览是沉浸式编辑页，手机底部那排 tab 会把 A4 版面挤没，所以在路由上标了 immersive。 */
const immersive = computed(() => Boolean(route.meta.immersive))
/** 宽限期里的账号还能用，但每一页都要看得见「正在被清除」这件事，不然取消入口就藏太深了。 */
const deletionScheduledAt = computed(() => auth.user?.deletionScheduledAt || null)
/** 没设头像时的兜底：昵称首字，和顶栏原来的字母圆一致。 */
const letter = computed(() => (auth.user?.nickname || auth.user?.username || '我').slice(0, 1))

async function logout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}

async function handleCommand(command) {
  if (command === 'logout') {
    await ElMessageBox.confirm('退出后仍可使用同一账号重新登录。', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(logout).catch(() => {})
    return
  }
  router.push(command)
}
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--immersive': immersive }">
    <header class="app-header no-print">
      <div class="app-header__inner">
        <BrandLogo small />
        <nav class="app-nav">
          <router-link to="/dashboard" active-class="is-active">我的简历</router-link>
          <router-link to="/templates" active-class="is-active">模板中心</router-link>
          <router-link to="/my-templates" active-class="is-active">我的模板</router-link>
        </nav>
        <div class="app-actions">
          <router-link to="/#faq" class="help-link">
            <el-icon><QuestionFilled /></el-icon>
            <span>帮助</span>
          </router-link>
          <el-dropdown trigger="click" @command="handleCommand">
            <button class="app-user" type="button">
              <UserAvatar class="app-avatar" :text="letter" />
              <span class="app-name">{{ auth.user?.nickname || auth.user?.username }}</span>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/dashboard">我的简历</el-dropdown-item>
                <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                <el-dropdown-item v-if="auth.isAdmin" command="/admin">管理后台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>
    <div v-if="deletionScheduledAt" class="deletion-strip no-print">
      <el-icon><WarningFilled /></el-icon>
      <span>该账号已申请注销，将于 {{ formatDateTime(deletionScheduledAt) }} 永久清除。</span>
      <button type="button" @click="router.push('/profile')">去取消</button>
    </div>
    <main class="app-main">
      <slot />
    </main>
    <nav v-if="!immersive" class="app-tabbar no-print" aria-label="主要导航">
      <router-link to="/dashboard" class="app-tab" active-class="is-active">
        <el-icon><Document /></el-icon>
        <span>简历</span>
      </router-link>
      <router-link to="/templates" class="app-tab" active-class="is-active">
        <el-icon><Grid /></el-icon>
        <span>模板</span>
      </router-link>
      <router-link to="/my-templates" class="app-tab" active-class="is-active">
        <el-icon><Collection /></el-icon>
        <span>我的模板</span>
      </router-link>
      <router-link to="/profile" class="app-tab" active-class="is-active">
        <el-icon><User /></el-icon>
        <span>我的</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background:
    radial-gradient(1000px 500px at 90% -20%, rgba(194, 160, 100, 0.08), transparent 55%),
    var(--canvas);
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 90;
  border-bottom: 1px solid var(--line);
  background: rgba(251, 250, 246, 0.9);
  backdrop-filter: blur(16px) saturate(1.2);
}

.app-header__inner {
  display: flex;
  align-items: center;
  gap: 34px;
  width: min(calc(var(--content-width) + 80px), calc(100% - 48px));
  height: 62px;
  margin: 0 auto;
}

.app-nav {
  display: flex;
  align-items: center;
  gap: 24px;
  flex: 1;
}

.app-nav a {
  position: relative;
  padding: 8px 0;
  color: var(--muted);
  font-size: 13px;
  transition: color 0.2s ease;
}

.app-nav a:hover,
.app-nav a.is-active {
  color: var(--ink);
}

.app-nav a.is-active::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  border-radius: 2px;
  background: var(--accent);
}

.app-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.help-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
}

.help-link:hover {
  color: var(--accent);
}

.app-user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 3px 4px 3px 3px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: none;
  color: var(--ink);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.app-user:hover {
  border-color: var(--line);
  background: var(--paper);
}

.app-avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--ink);
  color: #f7f4ec;
  font-family: var(--serif);
  font-size: 13px;
}

.app-name {
  max-width: 100px;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.caret {
  color: var(--faint);
  font-size: 10px;
}

.app-main {
  min-height: calc(100vh - 62px);
}

.app-tabbar {
  display: none;
}

.deletion-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 9px 24px;
  background: #fdf1ee;
  border-bottom: 1px solid #e9d5d1;
  color: var(--danger);
  font-size: 12px;
  line-height: 1.6;
}

.deletion-strip button {
  padding: 2px 10px;
  border: 1px solid #e0bfb9;
  border-radius: 999px;
  background: none;
  color: var(--danger);
  cursor: pointer;
  font-size: 11.5px;
}

.deletion-strip button:hover {
  background: #f7e2dd;
}

@media (max-width: 700px) {
  .app-header__inner {
    width: calc(100% - 32px);
    gap: 12px;
    height: 56px;
  }

  .app-nav {
    display: none;
  }

  .app-actions {
    gap: 12px;
    margin-left: auto;
  }

  .app-user {
    padding: 2px 6px 2px 2px;
    border-color: var(--line);
    background: var(--paper);
  }

  .app-name {
    display: none;
  }

  .app-main {
    padding-bottom: calc(62px + env(safe-area-inset-bottom, 0px));
  }

  .app-shell--immersive .app-main {
    padding-bottom: 0;
  }

  .app-tabbar {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 85;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    padding: 5px 6px calc(5px + env(safe-area-inset-bottom, 0px));
    border-top: 1px solid var(--line);
    background: rgba(251, 250, 246, 0.96);
    backdrop-filter: blur(18px) saturate(1.2);
  }

  .app-tab {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    padding: 5px 0 3px;
    color: var(--muted);
    font-size: 10px;
    line-height: 1;
    transition: color 0.2s ease;
  }

  .app-tab .el-icon {
    font-size: 19px;
  }

  .app-tab.is-active {
    color: var(--accent);
  }

  .deletion-strip {
    flex-wrap: wrap;
    padding: 9px 16px;
    line-height: 1.7;
  }
}
</style>
