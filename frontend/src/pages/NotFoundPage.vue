<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, HomeFilled } from '@element-plus/icons-vue'
import BrandLogo from '../components/BrandLogo.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const primaryLabel = computed(() => (auth.isLoggedIn ? '回到我的简历' : '回到首页'))
const primaryPath = computed(() => (auth.isLoggedIn ? '/dashboard' : '/'))

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push(primaryPath.value)
  }
}
</script>

<template>
  <div class="not-found">
    <header class="not-found__header">
      <BrandLogo small />
      <span class="mono-label">404 / Not found</span>
    </header>

    <main class="not-found__main">
      <div class="not-found__copy">
        <span class="eyebrow">Lost page</span>
        <h1 class="display">这一页<br />没有写进简历</h1>
        <p>你访问的地址不存在，可能已经移动或被删除。检查一下链接，或者从这里重新开始。</p>
        <div class="not-found__actions">
          <el-button type="primary" size="large" @click="router.push(primaryPath)">
            <el-icon><HomeFilled /></el-icon>
            {{ primaryLabel }}
          </el-button>
          <el-button size="large" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回上一页
          </el-button>
        </div>
        <div class="not-found__links">
          <router-link to="/templates">浏览模板</router-link>
          <span></span>
          <router-link to="/#faq">常见问题</router-link>
          <span></span>
          <router-link v-if="!auth.isLoggedIn" to="/login">登录 / 注册</router-link>
        </div>
      </div>

      <div class="not-found__paper" aria-hidden="true">
        <div class="paper-num">4<span>0</span>4</div>
        <div class="paper-line is-wide"></div>
        <div class="paper-line"></div>
        <div class="paper-line is-short"></div>
        <div class="paper-section">
          <span></span>
          <div>
            <i></i>
            <i></i>
            <i class="is-short"></i>
          </div>
        </div>
        <div class="paper-section">
          <span></span>
          <div>
            <i></i>
            <i class="is-short"></i>
          </div>
        </div>
        <div class="paper-stamp">MINIWHO</div>
      </div>
    </main>

    <footer class="not-found__footer">
      <span>极简简历 MiniWho</span>
      <span>开源 · 可自部署 · 数据归你所有</span>
    </footer>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background:
    radial-gradient(900px 520px at 82% 18%, rgba(194, 160, 100, 0.12), transparent 58%),
    radial-gradient(700px 400px at 8% 90%, rgba(10, 125, 118, 0.07), transparent 60%),
    var(--canvas);
}

.not-found__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1160px, calc(100% - 64px));
  height: 72px;
  margin: 0 auto;
}

.mono-label {
  color: var(--faint);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.not-found__main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  align-items: center;
  gap: 72px;
  width: min(1060px, calc(100% - 64px));
  margin: auto;
  padding: 48px 0 64px;
}

.not-found__copy h1 {
  margin-top: 16px;
  font-size: clamp(40px, 5.4vw, 66px);
  line-height: 1.18;
}

.not-found__copy > p {
  max-width: 430px;
  margin-top: 20px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.95;
}

.not-found__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 30px;
}

.not-found__links {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  color: var(--faint);
  font-size: 11px;
}

.not-found__links a {
  color: var(--muted);
  transition: color 0.2s ease;
}

.not-found__links a:hover {
  color: var(--accent);
}

.not-found__links span {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--line-strong);
}

.not-found__paper {
  position: relative;
  width: 100%;
  min-height: 486px;
  padding: 42px 36px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: #fffefb;
  box-shadow: var(--shadow-lg);
  transform: rotate(2.2deg);
  overflow: hidden;
}

.not-found__paper::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.45;
  background-image: radial-gradient(rgba(44, 56, 47, 0.06) 0.7px, transparent 0.7px);
  background-size: 4px 4px;
}

.paper-num {
  color: var(--ink);
  font-family: var(--serif);
  font-size: 112px;
  font-weight: 600;
  letter-spacing: -0.06em;
  line-height: 0.95;
}

.paper-num span {
  color: var(--copper);
}

.paper-line {
  width: 88%;
  height: 5px;
  margin-top: 12px;
  border-radius: 4px;
  background: var(--canvas-deep);
}

.paper-line.is-wide {
  width: 100%;
  height: 7px;
  margin-top: 32px;
  background: var(--ink);
  opacity: 0.75;
}

.paper-line.is-short {
  width: 62%;
}

.paper-section {
  display: flex;
  gap: 14px;
  margin-top: 34px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.paper-section > span {
  width: 7px;
  height: 7px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--accent);
}

.paper-section > div {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.paper-section i {
  display: block;
  width: 100%;
  height: 4px;
  border-radius: 3px;
  background: var(--canvas-deep);
}

.paper-section i.is-short {
  width: 58%;
}

.paper-stamp {
  position: absolute;
  right: 30px;
  bottom: 28px;
  padding: 7px 12px;
  border: 1px solid rgba(176, 95, 60, 0.45);
  border-radius: 3px;
  color: rgba(176, 95, 60, 0.72);
  font-family: var(--mono);
  font-size: 8px;
  letter-spacing: 0.22em;
  transform: rotate(-6deg);
}

.not-found__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1160px, calc(100% - 64px));
  margin: 0 auto;
  padding: 22px 0 28px;
  border-top: 1px solid var(--line);
  color: var(--faint);
  font-size: 10.5px;
}

@media (max-width: 900px) {
  .not-found__main {
    grid-template-columns: 1fr;
    gap: 40px;
    width: min(560px, calc(100% - 40px));
    padding-top: 28px;
  }

  .not-found__paper {
    min-height: 380px;
    padding: 32px 28px;
    transform: rotate(1.2deg);
  }

  .paper-num {
    font-size: 86px;
  }
}

@media (max-width: 560px) {
  .not-found__header,
  .not-found__footer {
    width: calc(100% - 32px);
  }

  .not-found__main {
    width: calc(100% - 32px);
  }

  .not-found__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .not-found__paper {
    display: none;
  }

  .not-found__footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
