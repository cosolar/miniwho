<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight, CopyDocument, InfoFilled, Lock, Printer, View, Warning
} from '@element-plus/icons-vue'
import BrandLogo from '../components/BrandLogo.vue'
import PaperFrame from '../components/PaperFrame.vue'
import { shareApi } from '../api'
import { copyText, formatDateTime, timeAgo } from '../utils/format'

const route = useRoute()
const router = useRouter()
const token = String(route.params.token || '')

const loading = ref(true)
const share = ref(null)
const passwordRequired = ref(false)
const gone = ref(false)
const goneTitle = ref('链接已失效')
const goneText = ref('这份分享可能已被撤销或过期。')
const fatalError = ref('')
const password = ref('')
const passwordError = ref('')
const unlocking = ref(false)
const printDialog = ref(false)
const printing = ref(false)

const template = computed(() => {
  if (!share.value) return null
  return {
    code: share.value.templateCode,
    name: share.value.template?.name || share.value.templateCode,
    category: share.value.template?.category || '',
    config: share.value.templateConfig || {}
  }
})
const styleData = computed(() => {
  if (!share.value) return {}
  return { ...(share.value.style || {}), showBrandFooter: share.value.brandFooter !== false }
})

function handleFailure(error) {
  const code = error?.code
  if (code === 'SHARE_PASSWORD_REQUIRED' || code === 'SHARE_PASSWORD_INVALID') {
    passwordRequired.value = true
    if (code === 'SHARE_PASSWORD_INVALID') passwordError.value = error.message || '访问密码不正确'
    return
  }
  if (code === 'SHARE_EXPIRED') {
    gone.value = true
    goneTitle.value = '链接已过期'
    goneText.value = '分享者设置的查看期限已结束。可以请对方重新生成一条分享链接。'
    return
  }
  if (code === 'SHARE_REVOKED') {
    gone.value = true
    goneTitle.value = '链接已失效'
    goneText.value = '分享者已撤销这条链接，或这份简历已被删除。'
    return
  }
  fatalError.value = error?.message || '页面加载失败，请稍后重试'
}

async function load() {
  loading.value = true
  fatalError.value = ''
  try {
    const data = await shareApi.publicGet(token)
    applyShare(data)
  } catch (error) {
    handleFailure(error)
  } finally {
    loading.value = false
  }
}

function applyShare(data) {
  share.value = data
  passwordRequired.value = false
  document.title = `${data.title} · 极简简历`
}

async function unlock() {
  if (unlocking.value) return
  if (!password.value) {
    passwordError.value = '请输入访问密码'
    return
  }
  unlocking.value = true
  passwordError.value = ''
  try {
    const data = await shareApi.unlock(token, password.value)
    applyShare(data)
    password.value = ''
    ElMessage.success('已解锁，欢迎查看')
  } catch (error) {
    handleFailure(error)
  } finally {
    unlocking.value = false
  }
}

async function copyLink() {
  try {
    await copyText(window.location.href)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('浏览器限制复制，请手动复制地址栏')
  }
}

async function startPrint() {
  printing.value = true
  try {
    printDialog.value = false
    await nextTick()
    setTimeout(() => window.print(), 160)
  } finally {
    printing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="share-page">
    <header class="share-topbar no-print">
      <router-link to="/" class="share-brand">
        <BrandLogo small />
      </router-link>
      <div class="share-topbar__right">
        <button type="button" class="copy-link" @click="copyLink">
          <el-icon><CopyDocument /></el-icon>
          复制链接
        </button>
        <router-link to="/login?mode=register" class="share-cta">
          免费做一份简历
          <el-icon><ArrowRight /></el-icon>
        </router-link>
      </div>
    </header>

    <main v-if="loading" class="share-loading">
      <div class="share-loading__sheet"></div>
      <p>正在打开简历…</p>
    </main>

    <main v-else-if="passwordRequired" class="share-gate">
      <div class="gate-card">
        <div class="gate-mark"><el-icon><Lock /></el-icon></div>
        <span class="mono">PRIVATE RESUME</span>
        <h1>这份简历受密码保护</h1>
        <p>请输入分享者提供的访问密码。密码只用于本次查看，不会被保存。</p>
        <form class="gate-form" @submit.prevent="unlock">
          <el-input
            v-model="password"
            type="password"
            show-password
            size="large"
            maxlength="20"
            placeholder="请输入访问密码"
            :class="{ 'is-error': passwordError }"
            @input="passwordError = ''"
          />
          <p v-if="passwordError" class="gate-error">{{ passwordError }}</p>
          <el-button type="primary" size="large" native-type="submit" :loading="unlocking" class="gate-submit">
            查看简历
          </el-button>
        </form>
        <div class="gate-foot">
          <el-icon><Lock /></el-icon>
          <span>分享链接由「极简简历」安全提供</span>
        </div>
      </div>
      <p class="gate-aside">即使是公开链接，作者也可以隐藏手机号、邮箱等敏感信息。</p>
    </main>

    <main v-else-if="gone" class="share-gone">
      <div class="gone-card">
        <div class="gone-icon"><el-icon><Warning /></el-icon></div>
        <span class="mono">LINK UNAVAILABLE</span>
        <h1>{{ goneTitle }}</h1>
        <p>{{ goneText }}</p>
        <div class="gone-actions">
          <el-button @click="router.push('/')">回到首页</el-button>
          <el-button type="primary" @click="router.push('/login?mode=register')">创建我的简历</el-button>
        </div>
      </div>
    </main>

    <main v-else-if="fatalError" class="share-gone">
      <div class="gone-card">
        <div class="gone-icon"><el-icon><Warning /></el-icon></div>
        <span class="mono">SOMETHING WENT WRONG</span>
        <h1>暂时无法打开</h1>
        <p>{{ fatalError }}</p>
        <el-button type="primary" @click="load">重新加载</el-button>
      </div>
    </main>

    <main v-else-if="share" class="share-content">
      <div class="share-meta no-print">
        <div class="share-meta__owner">
          <span class="owner-avatar">{{ (share.ownerName || '求').slice(0, 1) }}</span>
          <div>
            <strong>{{ share.title }}</strong>
            <p>由 {{ share.ownerName || '一位求职者' }} 分享 · {{ timeAgo(share.updatedAt) }}更新</p>
          </div>
        </div>
        <div class="share-meta__actions">
          <span v-if="share.expiresAt" class="expiry-chip">{{ formatDateTime(share.expiresAt) }} 前有效</span>
          <span v-else class="expiry-chip">永久有效</span>
          <el-button v-if="share.allowDownload" plain size="small" @click="printDialog = true">
            <el-icon><Printer /></el-icon>
            保存为 PDF
          </el-button>
          <span v-else class="view-only">
            <el-icon><View /></el-icon>
            作者已关闭下载
          </span>
        </div>
      </div>

      <div class="share-stage">
        <div class="share-paper-wrap">
          <PaperFrame
            :content="share.content"
            :template="template"
            :style-data="styleData"
            :avatar="share.avatar"
          />
        </div>
      </div>

      <div class="share-note no-print">
        <el-icon><InfoFilled /></el-icon>
        <p>这是一条私密分享链接。手机号、邮箱等字段可能已被作者隐藏；请勿将链接转发给无关的人。</p>
      </div>

      <footer class="share-footer no-print">
        <div class="share-footer__brand">
          <BrandLogo small />
          <span>让每一段履历被清楚看见</span>
        </div>
        <p>由极简简历 MiniWho 提供 · 开源、免费、可自托管</p>
      </footer>
    </main>

    <el-dialog v-model="printDialog" width="460px" align-center class="share-print-dialog">
      <template #header>
        <h2 class="share-print-title">保存这份简历</h2>
      </template>
      <ol class="share-print-steps">
        <li>在打印窗口的目标打印机中选择「另存为 PDF」。</li>
        <li>纸张选择 A4，边距选择「无」，缩放保持 100%。</li>
        <li>在更多设置中勾选「背景图形」，关闭「页眉和页脚」。</li>
      </ol>
      <div class="share-print-tip">
        <el-icon><InfoFilled /></el-icon>
        不同浏览器的选项名称可能略有不同。
      </div>
      <template #footer>
        <el-button @click="printDialog = false">取消</el-button>
        <el-button type="primary" :loading="printing" @click="startPrint">打开打印窗口</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  background:
    radial-gradient(1000px 600px at 50% -20%, rgba(194, 160, 100, 0.12), transparent 60%),
    radial-gradient(800px 500px at 100% 20%, rgba(10, 125, 118, 0.06), transparent 55%),
    var(--canvas);
}

.share-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  width: min(1180px, calc(100% - 48px));
  margin: 0 auto;
  padding: 20px 0;
}

.share-brand {
  display: inline-flex;
  color: var(--ink);
}

.share-topbar__right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.copy-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: none;
  background: none;
  color: var(--muted);
  font-family: var(--sans);
  font-size: 11px;
  cursor: pointer;
}

.copy-link:hover {
  color: var(--accent);
}

.share-cta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 14px;
  border: 1px solid var(--ink);
  border-radius: 7px;
  color: var(--ink);
  font-size: 11px;
  transition: all 0.2s ease;
}

.share-cta:hover {
  background: var(--ink);
  color: #f7f4ec;
}

.share-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 80px 20px 120px;
}

.share-loading__sheet {
  width: min(560px, 80vw);
  height: 70vh;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: linear-gradient(110deg, #fffefb 30%, #f3eee3 50%, #fffefb 70%);
  background-size: 200% 100%;
  box-shadow: var(--shadow);
  animation: shimmer 1.8s linear infinite;
}

@keyframes shimmer {
  to { background-position: -200% 0; }
}

.share-loading p {
  color: var(--faint);
  font-size: 11px;
}

.share-gate,
.share-gone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 100px);
  padding: 40px 20px 90px;
}

.gate-card,
.gone-card {
  width: min(440px, 100%);
  padding: 38px 36px 28px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--paper);
  box-shadow: var(--shadow-lg);
  text-align: center;
}

.gate-mark,
.gone-icon {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  margin: 0 auto 18px;
  border: 1px solid rgba(10, 125, 118, 0.2);
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 22px;
}

.gone-icon {
  border-color: rgba(176, 95, 60, 0.22);
  background: var(--copper-soft);
  color: var(--copper);
}

.gate-card .mono,
.gone-card .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.18em;
}

.gate-card h1,
.gone-card h1 {
  margin: 10px 0 8px;
  font-family: var(--serif);
  font-size: 25px;
  font-weight: 600;
}

.gate-card > p,
.gone-card > p {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.8;
}

.gate-form {
  margin-top: 22px;
}

.gate-error {
  margin-top: 7px;
  color: var(--danger);
  font-size: 10px;
  text-align: left;
}

.gate-submit {
  width: 100%;
  margin-top: 12px;
}

.gate-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
  color: var(--faint);
  font-size: 9px;
}

.gate-aside {
  max-width: 380px;
  margin-top: 16px;
  color: var(--faint);
  font-size: 9px;
  line-height: 1.7;
  text-align: center;
}

.gone-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 22px;
}

.gone-card > :deep(.el-button) {
  margin-top: 22px;
}

.share-content {
  width: min(1040px, calc(100% - 48px));
  margin: 0 auto;
  padding-bottom: 50px;
}

.share-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  padding: 14px 2px 20px;
}

.share-meta__owner {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.owner-avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--ink);
  color: #f6f3ea;
  font-family: var(--serif);
  font-size: 13px;
}

.share-meta__owner strong {
  display: block;
  overflow: hidden;
  font-family: var(--serif);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-meta__owner p {
  margin-top: 1px;
  color: var(--faint);
  font-size: 9px;
}

.share-meta__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.expiry-chip {
  padding: 4px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  font-size: 8px;
}

.view-only {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--faint);
  font-size: 9px;
}

.share-meta__actions :deep(.el-button) {
  height: 30px;
  font-size: 10px;
}

.share-stage {
  padding: 34px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background:
    radial-gradient(700px 500px at 50% -10%, rgba(10, 125, 118, 0.07), transparent 70%),
    linear-gradient(180deg, var(--canvas-deep), #e8e2d5);
}

.share-paper-wrap {
  width: min(794px, 100%);
  margin: 0 auto;
  filter: drop-shadow(0 28px 50px rgba(25, 34, 30, 0.18));
}

.share-paper-wrap :deep(.paper-scaler__paper) {
  box-shadow: none;
}

.share-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
  color: var(--faint);
}

.share-note .el-icon {
  font-size: 11px;
  flex-shrink: 0;
}

.share-note p {
  font-size: 9px;
  line-height: 1.7;
}

.share-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 50px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}

.share-footer__brand {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--muted);
  font-size: 10px;
}

.share-footer p {
  color: var(--faint);
  font-size: 9px;
}

.share-print-title {
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 600;
}

.share-print-steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}

.share-print-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  padding: 9px 11px;
  border-radius: 6px;
  background: var(--paper-deep);
  color: var(--faint);
  font-size: 9px;
}

@media print {
  .share-page {
    background: #fff;
  }

  .share-content {
    width: 210mm;
    margin: 0;
    padding: 0;
  }

  .share-stage {
    padding: 0;
    border: none;
    border-radius: 0;
    background: #fff;
  }

  .share-paper-wrap {
    width: 210mm;
    filter: none;
  }
}

@media (max-width: 760px) {
  .share-topbar {
    width: calc(100% - 32px);
    padding: 14px 0;
  }

  .copy-link {
    display: none;
  }

  .share-cta {
    padding: 7px 10px;
    font-size: 10px;
  }

  .share-content {
    width: calc(100% - 24px);
  }

  .share-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    padding-top: 6px;
  }

  .share-meta__actions {
    width: 100%;
    justify-content: space-between;
  }

  .share-stage {
    padding: 10px;
    border-radius: 9px;
  }

  .share-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .gate-card,
  .gone-card {
    padding: 30px 22px 24px;
  }
}
</style>
