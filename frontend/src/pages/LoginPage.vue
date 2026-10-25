<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, ArrowRight, Check } from '@element-plus/icons-vue'
import BrandLogo from '../components/BrandLogo.vue'
import { useAuthStore } from '../stores/auth'
import { notifyError } from '../api/client'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref(null)
const mode = ref('login')
const loading = ref(false)
const agreed = ref(false)
const isDev = import.meta.env.DEV

const form = reactive({
  username: '',
  password: '',
  confirm: '',
  nickname: ''
})

const isRegister = computed(() => mode.value === 'register')

const rules = computed(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    ...(isRegister.value ? [{ pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '3-20 位字母、数字或下划线', trigger: 'blur' }] : [])
  ],
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
  mode.value = next
  formRef.value?.clearValidate()
  router.replace({ path: '/login', query: { ...route.query, mode: next === 'register' ? 'register' : undefined } })
}

function fillDemo() {
  mode.value = 'login'
  form.username = 'demo'
  form.password = 'demo123'
  agreed.value = true
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
      await auth.register({
        username: form.username.trim(),
        password: form.password,
        nickname: form.nickname.trim()
      })
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

onMounted(() => {
  if (route.query.mode === 'register') mode.value = 'register'
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
        <div class="auth-tabs" role="tablist">
          <button type="button" role="tab" :class="{ active: !isRegister }" @click="switchMode('login')">登录</button>
          <button type="button" role="tab" :class="{ active: isRegister }" @click="switchMode('register')">注册</button>
          <span class="auth-tabs__indicator" :class="{ right: isRegister }" />
        </div>

        <div class="auth-heading">
          <h1 class="serif">{{ isRegister ? '开始写一份像样的简历。' : '欢迎回来。' }}</h1>
          <p>{{ isRegister ? '创建账号后会自动生成第一份空白简历，随时可以开始填写。' : '继续编辑你的简历，所有内容都还在原来的位置。' }}</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" placeholder="3-20 位字母、数字或下划线" autocomplete="username">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

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
          <template v-else>
            第一次来？
            <button type="button" @click="switchMode('register')">免费创建一个账号</button>
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
