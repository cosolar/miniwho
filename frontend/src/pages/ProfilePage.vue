<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Calendar, ChatDotSquare, Check, Document, InfoFilled, Key, Lock, SwitchButton, TrendCharts, User
} from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import { useAuthStore } from '../stores/auth'
import { feedbackApi, resumeApi } from '../api'
import { notifyError } from '../api/client'
import { formatDate } from '../utils/format'

const router = useRouter()
const auth = useAuthStore()

const profileRef = ref(null)
const passwordRef = ref(null)
const feedbackRef = ref(null)
const profileSaving = ref(false)
const passwordSaving = ref(false)
const feedbackSaving = ref(false)
const resumes = ref([])
const statsLoading = ref(true)

const profile = reactive({
  nickname: auth.user?.nickname || '',
  email: auth.user?.email || ''
})

const password = reactive({
  oldPassword: '',
  newPassword: '',
  confirm: ''
})

const feedback = reactive({
  content: '',
  contact: ''
})

const profileRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 50, message: '昵称不能超过 50 个字符', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 100, message: '邮箱不能超过 100 个字符', trigger: 'blur' }
  ]
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) =>
        value === password.newPassword ? callback() : callback(new Error('两次输入的新密码不一致')),
      trigger: 'blur'
    }
  ]
}

const feedbackRules = {
  content: [
    { required: true, message: '请填写反馈内容', trigger: 'blur' },
    { max: 2000, message: '反馈内容不能超过 2000 字', trigger: 'blur' }
  ],
  contact: [{ max: 200, message: '联系方式不能超过 200 个字符', trigger: 'blur' }]
}

const displayName = computed(() => auth.user?.nickname || auth.user?.username || '我')
const avatarText = computed(() => displayName.value.slice(0, 1))
const isAdmin = computed(() => auth.user?.role === 'admin')
const resumeCount = computed(() => resumes.value.length)
const bestScore = computed(() => Math.max(0, ...resumes.value.map((item) => Number(item.score) || 0)))
const reviewedCount = computed(() => resumes.value.filter((item) => Number(item.score) > 0).length)
const accountStatus = computed(() => (auth.user?.status === 1 ? '正常' : '已冻结'))

async function loadStats() {
  statsLoading.value = true
  try {
    resumes.value = await resumeApi.list()
  } catch (error) {
    notifyError(error, '加载简历数据失败')
  } finally {
    statsLoading.value = false
  }
}

async function saveProfile() {
  try {
    await profileRef.value.validate()
  } catch {
    return
  }
  profileSaving.value = true
  try {
    await auth.updateProfile({
      nickname: profile.nickname.trim(),
      email: profile.email.trim()
    })
    ElMessage.success('个人资料已保存')
  } catch (error) {
    notifyError(error, '保存个人资料失败')
  } finally {
    profileSaving.value = false
  }
}

async function savePassword() {
  try {
    await passwordRef.value.validate()
  } catch {
    return
  }
  if (password.newPassword === password.oldPassword) {
    ElMessage.warning('新密码不能与原密码相同')
    return
  }
  passwordSaving.value = true
  try {
    await auth.changePassword({
      oldPassword: password.oldPassword,
      newPassword: password.newPassword
    })
    // 服务端改完密码会作废该账号全部登录态，这里同步退出到登录页。
    auth.clear()
    ElMessage.success('密码已更新，请重新登录')
    router.push('/login')
  } catch (error) {
    notifyError(error, '修改密码失败')
  } finally {
    passwordSaving.value = false
  }
}

async function submitFeedback() {
  try {
    await feedbackRef.value.validate()
  } catch {
    return
  }
  feedbackSaving.value = true
  try {
    await feedbackApi.submit({
      content: feedback.content.trim(),
      contact: feedback.contact.trim()
    })
    ElMessage.success('反馈已提交，感谢你的建议')
    feedbackRef.value.resetFields()
  } catch (error) {
    notifyError(error, '提交反馈失败')
  } finally {
    feedbackSaving.value = false
  }
}

async function logout() {
  try {
    await ElMessageBox.confirm('退出后仍可使用同一账号重新登录。', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}

onMounted(() => {
  if (auth.user) {
    profile.nickname = auth.user.nickname || ''
    profile.email = auth.user.email || ''
  }
  loadStats()
})
</script>

<template>
  <AppLayout>
    <div class="profile-page">
      <div class="profile-shell">
        <header class="profile-hero">
          <div class="profile-id">
            <span class="profile-avatar">{{ avatarText }}</span>
            <div>
              <div class="profile-name-row">
                <h1 class="display">{{ displayName }}</h1>
                <span class="role-chip" :class="{ 'is-admin': isAdmin }">{{ isAdmin ? '管理员' : '普通用户' }}</span>
              </div>
              <p class="profile-handle">@{{ auth.user?.username }}</p>
              <div class="profile-meta">
                <span><el-icon><Calendar /></el-icon> {{ formatDate(auth.user?.createdAt) }} 加入</span>
                <span><el-icon><Document /></el-icon> {{ resumeCount }} 份简历</span>
              </div>
            </div>
          </div>
          <div class="profile-hero__actions">
            <el-button v-if="isAdmin" @click="router.push('/admin')">管理后台</el-button>
            <el-button type="primary" @click="router.push('/dashboard')">返回我的简历</el-button>
          </div>
        </header>

        <div class="profile-grid">
          <div class="profile-main">
            <section class="surface profile-card">
              <header class="profile-card__head">
                <div>
                  <span class="card-kicker">Profile</span>
                  <h2>个人资料</h2>
                  <p>昵称会显示在页面右上角，邮箱仅用于登录后的账号联系。</p>
                </div>
                <span class="card-icon"><el-icon><User /></el-icon></span>
              </header>
              <el-form ref="profileRef" :model="profile" :rules="profileRules" label-position="top" @submit.prevent>
                <div class="form-grid">
                  <el-form-item label="用户名">
                    <el-input :model-value="auth.user?.username" disabled>
                      <template #prefix><el-icon><User /></el-icon></template>
                    </el-input>
                    <span class="field-note">用户名是登录凭证，注册后暂不支持修改。</span>
                  </el-form-item>
                  <el-form-item label="昵称" prop="nickname">
                    <el-input v-model="profile.nickname" maxlength="50" placeholder="怎么称呼你" />
                  </el-form-item>
                </div>
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="profile.email" maxlength="100" placeholder="name@example.com（选填）">
                    <template #prefix><el-icon><InfoFilled /></el-icon></template>
                  </el-input>
                </el-form-item>
                <div class="form-actions">
                  <span class="save-hint">修改后立即生效，不影响简历内容。</span>
                  <el-button type="primary" :loading="profileSaving" @click="saveProfile">
                    <el-icon v-if="!profileSaving"><Check /></el-icon>
                    保存资料
                  </el-button>
                </div>
              </el-form>
            </section>

            <section class="surface profile-card">
              <header class="profile-card__head">
                <div>
                  <span class="card-kicker">Security</span>
                  <h2>修改密码</h2>
                  <p>建议使用与其他网站不同的密码，至少 6 位。修改后当前登录仍然有效。</p>
                </div>
                <span class="card-icon"><el-icon><Lock /></el-icon></span>
              </header>
              <el-form ref="passwordRef" :model="password" :rules="passwordRules" label-position="top" @submit.prevent>
                <el-form-item label="原密码" prop="oldPassword">
                  <el-input v-model="password.oldPassword" type="password" show-password autocomplete="current-password" placeholder="输入当前密码">
                    <template #prefix><el-icon><Key /></el-icon></template>
                  </el-input>
                </el-form-item>
                <div class="form-grid">
                  <el-form-item label="新密码" prop="newPassword">
                    <el-input v-model="password.newPassword" type="password" show-password autocomplete="new-password" placeholder="6-32 位新密码" />
                  </el-form-item>
                  <el-form-item label="确认新密码" prop="confirm">
                    <el-input v-model="password.confirm" type="password" show-password autocomplete="new-password" placeholder="再次输入新密码" />
                  </el-form-item>
                </div>
                <div class="form-actions">
                  <span class="save-hint">忘记密码时，请联系部署管理员重置。</span>
                  <el-button type="primary" plain :loading="passwordSaving" @click="savePassword">
                    更新密码
                  </el-button>
                </div>
              </el-form>
            </section>

            <section class="surface profile-card">
              <header class="profile-card__head">
                <div>
                  <span class="card-kicker">Feedback</span>
                  <h2>意见反馈</h2>
                  <p>遇到问题或想要新功能，可以在这里告诉部署管理员。</p>
                </div>
                <span class="card-icon"><el-icon><ChatDotSquare /></el-icon></span>
              </header>
              <el-form ref="feedbackRef" :model="feedback" :rules="feedbackRules" label-position="top" @submit.prevent>
                <el-form-item label="反馈内容" prop="content">
                  <el-input
                    v-model="feedback.content"
                    type="textarea"
                    :rows="4"
                    maxlength="2000"
                    show-word-limit
                    placeholder="描述你遇到的问题、复现步骤，或希望增加的功能。"
                  />
                </el-form-item>
                <el-form-item label="联系方式（选填）" prop="contact">
                  <el-input v-model="feedback.contact" maxlength="200" placeholder="邮箱或其他联系方式，方便管理员回复" />
                </el-form-item>
                <div class="form-actions">
                  <span class="save-hint">反馈由管理员在后台查看，提交后不可自行编辑。</span>
                  <el-button type="primary" plain :loading="feedbackSaving" @click="submitFeedback">
                    提交反馈
                  </el-button>
                </div>
              </el-form>
            </section>
          </div>

          <aside class="profile-aside">
            <section class="surface account-card">
              <span class="card-kicker">Overview</span>
              <h2>账号概览</h2>
              <dl>
                <div>
                  <dt>简历总数</dt>
                  <dd>{{ statsLoading ? '—' : resumeCount }}</dd>
                </div>
                <div>
                  <dt>已诊断</dt>
                  <dd>{{ statsLoading ? '—' : reviewedCount }}</dd>
                </div>
                <div>
                  <dt>最高得分</dt>
                  <dd>{{ statsLoading ? '—' : bestScore }}</dd>
                </div>
                <div>
                  <dt>账号状态</dt>
                  <dd class="is-status"><span class="status-dot"></span>{{ accountStatus }}</dd>
                </div>
              </dl>
              <el-button class="account-btn" @click="router.push('/dashboard')">
                <el-icon><TrendCharts /></el-icon>
                查看我的简历
              </el-button>
            </section>

            <section class="surface privacy-card">
              <span class="card-kicker">Privacy</span>
              <h2>数据与隐私</h2>
              <ul>
                <li><span>01</span>简历内容保存在你自己部署的数据库中。</li>
                <li><span>02</span>分享链接默认隐藏手机号与邮箱，可随时撤回。</li>
                <li><span>03</span>诊断评分在服务端按固定规则计算，不调用外部 AI。</li>
              </ul>
            </section>

            <button class="logout-card" type="button" @click="logout">
              <el-icon><SwitchButton /></el-icon>
              <span>退出当前账号</span>
            </button>
          </aside>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.profile-page {
  padding: 36px 0 72px;
  background:
    radial-gradient(800px 380px at 88% -12%, rgba(10, 125, 118, 0.07), transparent 60%),
    transparent;
}

.profile-shell {
  width: min(1060px, calc(100% - 56px));
  margin: 0 auto;
}

.profile-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 32px;
  padding: 4px 2px;
}

.profile-id {
  display: flex;
  align-items: center;
  gap: 20px;
}

.profile-avatar {
  display: grid;
  place-items: center;
  width: 74px;
  height: 74px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--ink);
  color: #f8f5ed;
  font-family: var(--serif);
  font-size: 28px;
}

.profile-name-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.profile-name-row h1 {
  font-size: clamp(26px, 3vw, 34px);
}

.role-chip {
  padding: 2px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  background: var(--paper);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.role-chip.is-admin {
  border-color: rgba(10, 125, 118, 0.2);
  color: var(--accent);
  background: var(--accent-soft);
}

.profile-handle {
  margin-top: 2px;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 11px;
}

.profile-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 8px;
  color: var(--muted);
  font-size: 11px;
}

.profile-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.profile-hero__actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 22px;
  align-items: start;
}

.profile-main {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.profile-card {
  padding: 28px 30px 26px;
}

.profile-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.card-kicker {
  color: var(--accent);
  font-family: var(--mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.profile-card__head h2,
.account-card h2,
.privacy-card h2 {
  margin-top: 5px;
  font-family: var(--serif);
  font-size: 21px;
  font-weight: 600;
}

.profile-card__head p {
  max-width: 460px;
  margin-top: 6px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.75;
}

.card-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--accent);
  background: var(--paper-deep);
  font-size: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

.field-note {
  margin-top: 5px;
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.6;
}

.form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 6px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.save-hint {
  color: var(--faint);
  font-size: 11px;
}

.profile-aside {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.account-card,
.privacy-card {
  padding: 24px;
}

.account-card dl {
  margin: 18px 0 0;
}

.account-card dl > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 10px 0;
  border-top: 1px solid var(--line);
}

.account-card dl > div:first-child {
  border-top: none;
  padding-top: 0;
}

.account-card dt {
  color: var(--muted);
  font-size: 12px;
}

.account-card dd {
  margin: 0;
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 600;
}

.account-card dd.is-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--sans);
  font-size: 12px;
  font-weight: 500;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success);
}

.account-btn {
  width: 100%;
  margin-top: 18px;
}

.privacy-card ul {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.privacy-card li {
  display: flex;
  gap: 9px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.75;
}

.privacy-card li span {
  flex-shrink: 0;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9px;
  padding-top: 2px;
}

.logout-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 14px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius);
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logout-card:hover {
  border-color: var(--copper);
  color: var(--copper);
  background: var(--copper-soft);
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }

  .profile-aside {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .logout-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 700px) {
  .profile-page {
    padding: 22px 0 48px;
  }

  .profile-shell {
    width: calc(100% - 28px);
  }

  .profile-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 18px;
    margin-bottom: 22px;
  }

  .profile-hero__actions {
    width: 100%;
  }

  .profile-hero__actions .el-button {
    flex: 1;
  }

  .profile-aside {
    grid-template-columns: 1fr;
  }

  .profile-card {
    padding: 22px 18px;
  }

  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .form-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .save-hint {
    order: 2;
    text-align: center;
  }
}
</style>
