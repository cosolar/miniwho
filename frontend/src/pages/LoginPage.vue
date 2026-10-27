<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, ArrowRight, Check, Message } from '@element-plus/icons-vue'
import BrandLogo from '../components/BrandLogo.vue'
import { useAuthStore } from '../stores/auth'
import { apiUrl, notifyError } from '../api/client'
import { authApi } from '../api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref(null)
const mode = ref('login')
const loading = ref(false)
const agreed = ref(false)
const isDev = import.meta.env.DEV

/** 后台开关与实际通道就绪状态共同决定这一页渲染哪些入口。 */
const config = reactive({ usernameRegister: false, emailRegister: false, githubLogin: false })
const registerKind = ref('username')
const codeSending = ref(false)
const countdown = ref(0)
let countdownTimer = null

const form = reactive({
  username: '',
  email: '',
  code: '',
  password: '',
  confirm: '',
  nickname: ''
})

const isRegister = computed(() => mode.value === 'register')
const isEmailRegister = computed(() => isRegister.value && registerKind.value === 'email')
const canRegister = computed(() => config.usernameRegister || config.emailRegister)
const heading = computed(() => {
  if (!isRegister.value) return '欢迎回来。'
  return isEmailRegister.value ? '用邮箱开一个账号。' : '开始写一份像样的简历。'
})
const subheading = computed(() => {
  if (!isRegister.value) return '继续编辑你的简历，所有内容都还在原来的位置。'
  if (isEmailRegister.value) return '先向邮箱发送验证码，验证通过后自动创建账号并生成第一份简历。'
  return '创建账号后会自动生成第一份空白简历，随时可以开始填写。'
})

const rules = computed(() => ({
  username: isEmailRegister.value
    ? []
    : [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        ...(isRegister.value ? [{ pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '3-20 位字母、数字或下划线', trigger: 'blur' }] : [])
      ],
  email: isEmailRegister.value
    ? [
        { required: true, message: '请输入邮箱', trigger: 'blur' },
        { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
      ]
    : [],
  code: isEmailRegister.value
    ? [{ required: true, pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }]
    : [],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    ...(isRegister.value ? [{ min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' }] : [])
  ],
  confirm: isRegister.value
    ? [
        { required: true, message: '请再次输入密码', trigger: 'blur' },
        { validator: (rule, value, callback) => value === form.password ? callback() : callback(new Error('两次输入的密码不一致')), trigger: 'blur' }
      ]
    : []
}))

const redirect = computed(() => {
  const target = route.query.redirect
  if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) return target
  return '/dashboard'
})

function switchMode(next) {
  if (next === 'register' && !canRegister.value) return
  mode.value = next
  formRef.value?.clearValidate()
  router.replace({ path: '/login', query: { ...route.query, mode: next === 'register' ? 'register' : undefined } })
}

function switchKind(next) {
  registerKind.value = next
  formRef.value?.clearValidate()
}

function startCountdown(seconds) {
  countdown.value = seconds
  clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(countdownTimer)
  }, 1000)
}

async function sendCode() {
  try {
    await formRef.value.validateField('email')
  } catch {
    return
  }
  codeSending.value = true
  try {
    await authApi.emailCode(form.email.trim())
    ElMessage.success('验证码已发送，10 分钟内有效')
    startCountdown(60)
  } catch (error) {
    notifyError(error, '验证码发送失败')
  } finally {
    codeSending.value = false
  }
}

/** 整页跳转：授权页必须由浏览器直接导航，令牌经回调后从 URL 片段取回。 */
function githubLogin() {
  loading.value = true
  window.location.assign(apiUrl('/auth/oauth/github'))
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (isRegister.value && !agreed.value) {
    ElMessage.warning('请先阅读并同意用户协议与隐私说明')
    return
  }
  loading.value = true
  try {
    if (isRegister.value) {
      if (isEmailRegister.value) {
        await auth.registerWithEmail({
          email: form.email.trim(),
          password: form.password,
          code: form.code.trim(),
          nickname: form.nickname.trim()
        })
      } else {
        await auth.register({
          username: form.username.trim(),
          password: form.password,
          nickname: form.nickname.trim()
        })
      }
      ElMessage.success('账号已创建，你的第一份简历准备好了')
    } else {
      await auth.login({ username: form.username.trim(), password: form.password })
      ElMessage.success('欢迎回来')
    }
    router.push(redirect.value)
  } catch (error) {
    notifyError(error, isRegister.value ? '注册失败' : '登录失败')
  } finally {
    loading.value = false
  }
}

function fillDemo() {
  mode.value = 'login'
  form.username = 'demo'
  form.password = 'demo123'
  agreed.value = true
}

async function loadConfig() {
  try {
    const data = await authApi.config()
    config.usernameRegister = Boolean(data.usernameRegister)
    config.emailRegister = Boolean(data.emailRegister)
    config.githubLogin = Boolean(data.githubLogin)
    if (!config.usernameRegister && config.emailRegister) registerKind.value = 'email'
  } catch {
    // 读不到开关时退回「用户名注册可用」：宁可让后端回一句明确的「未开放注册」，
    // 也不要因为一次请求失败就把注册入口整个藏起来。
    config.usernameRegister = true
  }
}

/** GitHub 回调落在 /login#satoken=xxx，失败落 #oauth_error=1；令牌读完就不能再留在地址栏里。 */
async function consumeOAuthHandoff() {
  const hash = typeof route.hash === 'string' ? route.hash : ''
  if (hash.startsWith('#satoken=')) {
    const token = decodeURIComponent(hash.slice('#satoken='.length))
    const target = redirect.value
    loading.value = true
    try {
      await auth.adoptToken(token)
      ElMessage.success('已通过 GitHub 登录')
      // 成功路径只发一条导航：跳转离开 /login 顺带抹掉片段。若先 replace 回登录页再 push，
      // 那条没走完的 /login 导航会被 guestOnly 拦下并弹回首页，?redirect= 就丢了。
      await router.replace(target)
      return
    } catch {
      auth.clear()
      clearHandoffHash()
      ElMessage.error('GitHub 登录状态已失效，请重试')
    } finally {
      loading.value = false
    }
  }
  if (hash.includes('oauth_error')) {
    clearHandoffHash()
    ElMessage.error('GitHub 登录未完成，请重试或用账号登录')
  }
}

/**
 * 失败时留在登录页，只能自己把回调片段抹掉——令牌和 state 不该留在地址栏、历史记录里。
 * 这里绕过 router.replace：目标只是去掉片段的路径，路由会当成同地址导航直接放弃，浏览器地址栏不动。
 */
function clearHandoffHash() {
  history.replaceState(history.state, '', location.pathname + location.search)
}

onBeforeUnmount(() => clearInterval(countdownTimer))

onMounted(async () => {
  await loadConfig()
  if (route.query.mode === 'register' && canRegister.value) mode.value = 'register'
  await consumeOAuthHandoff()
})
</script>

<template>
  <div class="auth-page">
    <aside class="auth-aside">
      <div class="auth-aside__grain" />
      <div class="auth-aside__top">
        <BrandLogo light />
        <router-link to="/" class="back-home">返回首页 ↗</router-link>
      </div>

      <div class="auth-aside__content">
        <span class="auth-aside__label mono">A QUIET PLACE TO WRITE</span>
        <blockquote class="serif">
          “简历不是履历的堆叠，<br />而是你选择让世界<br />先看见哪一部分。”
        </blockquote>
        <p>结构化填写，A4 排版，可控分享。<br />没有会员墙，也没有算法替你编故事。</p>
      </div>

      <div class="auth-paper" aria-hidden="true">
        <div class="auth-paper__head">
          <span class="auth-paper__name serif">你的名字</span>
          <span class="auth-paper__role">目标岗位 · 城市</span>
        </div>
        <div class="auth-paper__line" v-for="n in 3" :key="n">
          <span class="auth-paper__dot" />
          <span class="auth-paper__bar" :style="{ width: `${88 - n * 13}%` }" />
        </div>
        <div class="auth-paper__section" v-for="n in 3" :key="`s${n}`">
          <span class="auth-paper__title" :style="{ width: `${22 + n * 4}%` }" />
          <span class="auth-paper__text" />
          <span class="auth-paper__text auth-paper__text--short" />
        </div>
        <span class="auth-paper__stamp">MINIWHO</span>
      </div>

      <div class="auth-aside__bottom">
        <span>Apache License 2.0</span>
        <span>数据默认保存在本机</span>
        <span>无广告 · 无第三方追踪</span>
      </div>
    </aside>

    <main class="auth-main">
      <div class="auth-card">
        <div v-if="canRegister" class="auth-tabs" role="tablist">
          <button type="button" role="tab" :class="{ active: !isRegister }" @click="switchMode('login')">登录</button>
          <button type="button" role="tab" :class="{ active: isRegister }" @click="switchMode('register')">注册</button>
          <span class="auth-tabs__indicator" :class="{ right: isRegister }" />
        </div>

        <div class="auth-heading">
          <h1 class="serif">{{ heading }}</h1>
          <p>{{ subheading }}</p>
        </div>

        <div v-if="isRegister && config.usernameRegister && config.emailRegister" class="kind-switch">
          <button type="button" :class="{ active: registerKind === 'username' }" @click="switchKind('username')">用户名</button>
          <button type="button" :class="{ active: registerKind === 'email' }" @click="switchKind('email')">邮箱</button>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form" @submit.prevent="submit">
          <el-form-item v-if="!isEmailRegister" :label="isRegister ? '用户名' : '账号'" prop="username">
            <el-input
              v-model="form.username"
              size="large"
              :placeholder="isRegister ? '3-20 位字母、数字或下划线' : '用户名或邮箱'"
              autocomplete="username"
            >
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <template v-if="isEmailRegister">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" size="large" placeholder="用于接收验证码" autocomplete="email">
                <template #prefix><el-icon><Message /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item label="验证码" prop="code">
              <el-input v-model="form.code" size="large" maxlength="6" placeholder="6 位数字" autocomplete="one-time-code" @keyup.enter="submit">
                <template #append>
                  <el-button :disabled="codeSending || countdown > 0" @click="sendCode">
                    {{ countdown > 0 ? `${countdown} 秒` : codeSending ? '发送中' : '发送验证码' }}
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
          </template>

          <el-form-item v-if="isRegister" label="昵称（选填）" prop="nickname">
            <el-input v-model="form.nickname" size="large" placeholder="希望我们怎么称呼你" maxlength="50" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="至少 6 位"
              :autocomplete="isRegister ? 'new-password' : 'current-password'"
              @keyup.enter="submit"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item v-if="isRegister" label="确认密码" prop="confirm">
            <el-input
              v-model="form.confirm"
              size="large"
              type="password"
              show-password
              placeholder="再输入一次密码"
              autocomplete="new-password"
              @keyup.enter="submit"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <div v-if="isRegister" class="agreement">
            <el-checkbox v-model="agreed">
              我已阅读并同意
              <a href="#" @click.prevent>用户协议</a>
              与
              <a href="#" @click.prevent>隐私说明</a>
            </el-checkbox>
          </div>

          <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="submit">
            {{ isRegister ? '创建账号' : '登录' }}
            <el-icon v-if="!loading"><ArrowRight /></el-icon>
          </el-button>
        </el-form>

        <div v-if="config.githubLogin" class="oauth-block">
          <div class="oauth-divider"><span>或</span></div>
          <button type="button" class="oauth-btn" :disabled="loading" @click="githubLogin">
            <svg viewBox="0 0 16 16" width="15" height="15" fill="currentColor" aria-hidden="true">
              <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.91 2.33.69.07-.52.28-.91.51-1.12-1.78-.2-3.47-.88-3.47-3.89 0-.86.31-1.56.82-2.11-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.55.82 1.25.82 2.11 0 3.02-1.7 3.7-3.47 3.9.27.24.51.7.51 1.42 0 1.03-.01 1.86-.01 2.11 0 .21.14.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z" />
            </svg>
            使用 GitHub 登录
          </button>
          <p class="oauth-note">首次登录会自动创建账号，之后用同一个 GitHub 账号即可继续。</p>
        </div>

        <div v-if="isDev" class="demo-hint">
          <span class="demo-hint__icon"><el-icon><Check /></el-icon></span>
          <p>本地演示账号 <code>demo</code> / <code>demo123</code>，附带一份完整示例简历。</p>
          <button type="button" @click="fillDemo">一键填入</button>
        </div>

        <div class="auth-switch">
          <template v-if="isRegister">
            已经有账号了？
            <button type="button" @click="switchMode('login')">直接登录</button>
          </template>
          <template v-else-if="canRegister">
            第一次来？
            <button type="button" @click="switchMode('register')">免费创建一个账号</button>
          </template>
          <template v-else>
            当前部署未开放注册，如需账号请联系管理员开通。
          </template>
        </div>

        <p class="auth-fineprint">
          本项目为开源演示，示例数据均为虚构。请勿在公开演示环境中填写真实身份证号等敏感信息。
        </p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  grid-template-columns: minmax(380px, 0.9fr) minmax(480px, 1.1fr);
  min-height: 100vh;
  background: var(--canvas);
}

.auth-aside {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  min-height: 100vh;
  padding: 34px 42px;
  background:
    radial-gradient(700px 500px at 0% 0%, rgba(199, 164, 101, 0.15), transparent 60%),
    #16231e;
  color: #f3efe5;
}

.auth-aside__grain {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(243, 239, 229, 0.16) 0.6px, transparent 0.6px);
  background-size: 5px 5px;
  opacity: 0.24;
  pointer-events: none;
}

.auth-aside__top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.back-home {
  color: rgba(243, 239, 229, 0.55);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.back-home:hover {
  color: #f3efe5;
}

.auth-aside__content {
  position: relative;
  z-index: 2;
  max-width: 470px;
  margin: 40px 0;
}

.auth-aside__label {
  color: rgba(243, 239, 229, 0.38);
  font-size: 8px;
  letter-spacing: 0.2em;
}

.auth-aside__content blockquote {
  margin: 22px 0 24px;
  color: #f7f3e9;
  font-size: clamp(25px, 2.6vw, 35px);
  font-weight: 500;
  line-height: 1.6;
  letter-spacing: 0.01em;
}

.auth-aside__content > p {
  color: rgba(243, 239, 229, 0.5);
  font-size: 12px;
  line-height: 1.95;
}

.auth-paper {
  position: absolute;
  right: -70px;
  bottom: 96px;
  z-index: 1;
  width: 300px;
  height: 370px;
  padding: 28px 24px;
  border-radius: 3px;
  background: #f8f4ea;
  box-shadow: 0 30px 70px -30px rgba(0, 0, 0, 0.6);
  transform: rotate(-8deg);
  opacity: 0.94;
}

.auth-paper__head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 13px;
  border-bottom: 1px solid rgba(31, 39, 35, 0.16);
}

.auth-paper__name {
  color: #1c2722;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.auth-paper__role {
  color: #6d7871;
  font-size: 8px;
}

.auth-paper__line {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 11px;
}

.auth-paper__dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #2f6f62;
}

.auth-paper__bar {
  height: 3px;
  border-radius: 2px;
  background: #dfd9cc;
}

.auth-paper__section {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 22px;
}

.auth-paper__title {
  height: 6px;
  border-radius: 1px;
  background: #2f6f62;
  opacity: 0.8;
}

.auth-paper__text {
  height: 3px;
  border-radius: 2px;
  background: #e3ddd0;
}

.auth-paper__text--short {
  width: 72%;
}

.auth-paper__stamp {
  position: absolute;
  right: 22px;
  bottom: 18px;
  color: rgba(31, 39, 35, 0.18);
  font-family: var(--mono);
  font-size: 7px;
  letter-spacing: 0.18em;
}

.auth-aside__bottom {
  position: relative;
  z-index: 3;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding-top: 18px;
  border-top: 1px solid rgba(243, 239, 229, 0.12);
  color: rgba(243, 239, 229, 0.34);
  font-size: 9px;
  letter-spacing: 0.06em;
}

.auth-main {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 48px 32px;
}

.auth-card {
  width: min(420px, 100%);
}

.auth-tabs {
  position: relative;
  display: flex;
  gap: 26px;
  margin-bottom: 38px;
  border-bottom: 1px solid var(--line);
}

.auth-tabs button {
  padding: 0 2px 12px;
  border: none;
  background: none;
  color: var(--faint);
  font-family: var(--sans);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.auth-tabs button.active {
  color: var(--ink);
}

.auth-tabs__indicator {
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 28px;
  height: 2px;
  background: var(--accent);
  transition: transform 0.25s ease, width 0.25s ease;
}

.auth-tabs__indicator.right {
  width: 28px;
  transform: translateX(54px);
}

.kind-switch {
  display: inline-flex;
  gap: 2px;
  margin-top: 22px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--paper-deep);
}

.kind-switch button {
  min-width: 76px;
  padding: 6px 14px;
  border: none;
  border-radius: 999px;
  background: none;
  color: var(--muted);
  font-family: var(--sans);
  font-size: 11px;
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.kind-switch button.active {
  background: var(--paper);
  color: var(--ink);
  box-shadow: 0 1px 4px rgba(31, 39, 35, 0.12);
}

.oauth-block {
  margin-top: 26px;
}

.oauth-divider {
  position: relative;
  text-align: center;
}

.oauth-divider::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--line);
}

.oauth-divider span {
  position: relative;
  padding: 0 12px;
  background: var(--canvas);
  color: var(--faint);
  font-size: 10px;
}

.oauth-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 44px;
  margin-top: 18px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: var(--paper);
  color: var(--ink-soft);
  font-family: var(--sans);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, opacity 0.2s ease;
}

.oauth-btn:hover:not(:disabled) {
  border-color: var(--ink-soft);
  background: var(--paper-deep);
}

.oauth-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.oauth-note {
  margin-top: 10px;
  color: var(--faint);
  font-size: 10px;
  line-height: 1.7;
  text-align: center;
}

.auth-heading h1 {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.4;
}

.auth-heading p {
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}

.auth-form {
  margin-top: 30px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-form :deep(.el-form-item__label) {
  padding-bottom: 5px;
  font-size: 12px;
}

.auth-form :deep(.el-input__prefix) {
  color: var(--faint);
}

.agreement {
  margin: -2px 0 16px;
}

.agreement :deep(.el-checkbox__label) {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
  white-space: normal;
}

.agreement a {
  color: var(--accent);
}

.submit-btn {
  width: 100%;
  height: 46px;
  margin-top: 6px;
}

.demo-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 22px;
  padding: 10px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: 8px;
  background: var(--paper-deep);
}

.demo-hint__icon {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 10px;
}

.demo-hint p {
  flex: 1;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.6;
}

.demo-hint code {
  padding: 1px 4px;
  border-radius: 3px;
  background: var(--paper);
  color: var(--ink-soft);
  font-family: var(--mono);
  font-size: 9px;
}

.demo-hint button {
  border: none;
  background: none;
  color: var(--accent);
  font-family: var(--sans);
  font-size: 10px;
  white-space: nowrap;
  cursor: pointer;
}

.auth-switch {
  margin-top: 24px;
  color: var(--muted);
  font-size: 11px;
  text-align: center;
}

.auth-switch button {
  border: none;
  background: none;
  color: var(--accent);
  font-family: var(--sans);
  font-size: 11px;
  cursor: pointer;
}

.auth-switch button:hover {
  text-decoration: underline;
}

.auth-fineprint {
  margin-top: 30px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
  color: var(--faint);
  font-size: 9px;
  line-height: 1.75;
  text-align: center;
}

@media (max-width: 980px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-aside {
    display: none;
  }

  .auth-main {
    padding: 40px 24px;
  }
}

@media (max-width: 520px) {
  .auth-main {
    padding: 28px 20px;
  }

  .auth-tabs {
    margin-bottom: 28px;
  }

  .auth-heading h1 {
    font-size: 26px;
  }
}
</style>
