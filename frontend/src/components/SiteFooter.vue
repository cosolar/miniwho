<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { statsApi } from '../api'

const router = useRouter()
const year = new Date().getFullYear()

const GITHUB_URL = 'https://github.com/cosolar/miniwho'
const VISITOR_KEY = 'miniwho.visitor'
/** 页脚统计只做展示，请求失败就保持空白，不打扰浏览。 */
const stats = ref(null)
const visitsText = computed(() => (stats.value ? stats.value.visits.toLocaleString('zh-CN') : '—'))

function visitorId() {
  let id = localStorage.getItem(VISITOR_KEY)
  if (!id) {
    id = globalThis.crypto?.randomUUID?.() || `v-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    localStorage.setItem(VISITOR_KEY, id)
  }
  return id
}

function apply(data) {
  if (data && typeof data.visits === 'number') stats.value = data
}

async function ping() {
  try {
    apply(await statsApi.ping(visitorId()))
  } catch {
    // 统计是装饰信息，失败静默。
  }
}

/**
 * 每次挂载都上报一次访问，由服务端按访客 id 做 6 小时去重。
 * 不在前端记「本会话已计」，否则第一次上报失败后这个会话就再也不会计数了。
 */
async function enterSite() {
  try {
    apply(await statsApi.visit(visitorId()))
  } catch {
    // 忽略：页脚不因统计接口失败而报错。
  }
}

let timer = 0
function onVisibility() {
  if (document.visibilityState === 'visible') ping()
}

onMounted(() => {
  enterSite()
  timer = window.setInterval(() => {
    if (document.visibilityState === 'visible') ping()
  }, 60000)
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  window.clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<template>
  <footer class="site-footer">
    <div class="container">
      <div class="footer-grid">
        <div class="footer-brand">
          <router-link to="/" class="footer-brand__logo" aria-label="极简简历首页">
            <img src="/logo-lockup.png" alt="极简简历 who.minims.cn" width="720" height="217" />
          </router-link>
          <p>把简历做成一件清晰、体面、可以放心交出去的作品。全部功能免费，没有会员与付费模板。</p>
          <a class="license" :href="`${GITHUB_URL}/blob/main/LICENSE`" target="_blank" rel="noopener">Apache-2.0 · 可自托管</a>
        </div>
        <div class="footer-col">
          <h4>产品</h4>
          <router-link to="/templates">模板中心</router-link>
          <router-link to="/dashboard">我的简历</router-link>
          <button @click="router.push('/login?mode=register')">免费注册</button>
        </div>
        <div class="footer-col">
          <h4>帮助</h4>
          <a href="/#how-it-works">使用方式</a>
          <a href="/#faq">常见问题</a>
          <a href="/#privacy">隐私设计</a>
        </div>
        <div class="footer-col">
          <h4>开源</h4>
          <a class="footer-github" :href="GITHUB_URL" target="_blank" rel="noopener">
            <svg class="footer-github__mark" viewBox="0 0 16 16" aria-hidden="true">
              <path
                fill="currentColor"
                d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z"
              />
            </svg>
            GitHub 仓库
          </a>
          <span>前端：Vue 3 + Element Plus</span>
          <span>后端：Spring Boot 3 + SQLite/MySQL</span>
          <span>不依赖第三方 AI 服务</span>
          <div v-if="stats" class="footer-stats">
            <span class="footer-stats__live"><i></i>{{ stats.online }} 人在线</span>
            <span class="footer-stats__sep" aria-hidden="true"></span>
            <span>{{ visitsText }} 次访问</span>
          </div>
        </div>
      </div>
      <div class="footer-bottom">
        <span>© {{ year }} 极简简历 MiniWho · <a class="footer-domain" href="https://who.minims.cn" rel="noopener">who.minims.cn</a></span>
        <span class="footer-note">开源自部署 · 数据由你掌控</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  padding: 64px 0 28px;
  border-top: 1px solid var(--line);
  background: #eeeade;
}

.footer-grid {
  display: grid;
  grid-template-columns: 1.8fr repeat(3, 1fr);
  gap: 48px;
}

.footer-brand__logo {
  display: inline-block;
  width: min(258px, 100%);
}

.footer-brand__logo img {
  display: block;
  width: 100%;
  height: auto;
}

.footer-brand p {
  max-width: 330px;
  margin: 20px 0 18px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.license {
  display: inline-flex;
  padding: 3px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  color: var(--muted);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  text-decoration: none;
  transition: color 0.2s ease, border-color 0.2s ease;
}

.license:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.footer-github {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.footer-github__mark {
  width: 13px;
  height: 13px;
  flex: none;
}

.footer-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
  color: var(--faint);
  font-family: var(--mono);
  font-size: 10.5px;
  line-height: 1.6;
  font-variant-numeric: tabular-nums;
}

.footer-stats span {
  cursor: default;
}

.footer-stats__live {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--muted);
}

.footer-stats__live i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 3px rgba(10, 125, 118, 0.12);
}

.footer-stats__sep {
  width: 1px;
  height: 9px;
  background: var(--line-strong);
}

.footer-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.footer-col h4 {
  margin-bottom: 8px;
  color: var(--ink);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.footer-col a,
.footer-col button,
.footer-col span {
  color: var(--muted);
  font-family: inherit;
  font-size: 12px;
  line-height: 1.7;
  text-align: left;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  transition: color 0.2s ease;
}

.footer-col a:hover,
.footer-col button:hover {
  color: var(--accent);
}

.footer-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 54px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
  color: var(--faint);
  font-size: 11px;
}

.footer-note {
  text-align: right;
}

.footer-domain {
  color: inherit;
  font-family: var(--mono);
  letter-spacing: 0.04em;
  text-decoration: none;
  border-bottom: 1px solid var(--line-strong);
  transition: color 0.2s ease, border-color 0.2s ease;
}

.footer-domain:hover {
  color: var(--accent);
  border-color: var(--accent);
}

@media (max-width: 900px) {
  .footer-grid {
    grid-template-columns: 1fr 1fr;
    gap: 34px;
  }
}

@media (max-width: 560px) {
  .site-footer {
    padding-top: 44px;
  }

  .footer-grid {
    grid-template-columns: 1fr;
    gap: 26px;
  }

  .footer-bottom {
    flex-direction: column;
    align-items: flex-start;
    margin-top: 32px;
  }

  .footer-note {
    text-align: left;
  }
}
</style>
