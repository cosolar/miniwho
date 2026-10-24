<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
const year = new Date().getFullYear()
</script>

<template>
  <footer class="site-footer">
    <div class="container">
      <div class="footer-grid">
        <div class="footer-brand">
          <router-link to="/" class="footer-brand__logo" aria-label="极简简历首页">
            <img src="/logo-lockup.png" alt="极简简历 who.minims.cn" width="720" height="217" />
          </router-link>
          <p>把简历做成一件清晰、体面、可以放心交出去的作品。开源、免费，数据由你掌控。</p>
          <span class="license">MIT License · 可自托管</span>
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
          <span>前端：Vue 3 + Element Plus</span>
          <span>后端：Spring Boot 3 + SQLite/MySQL</span>
          <span>不依赖第三方 AI 服务</span>
        </div>
      </div>
      <div class="footer-bottom">
        <span>© {{ year }} 极简简历 MiniWho · <a class="footer-domain" href="https://who.minims.cn" rel="noopener">who.minims.cn</a></span>
        <span class="footer-note">与牛客及任何商业简历产品无关 · 示例数据均为虚构</span>
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
