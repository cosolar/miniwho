<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Calendar, ChatDotSquare, Check, Delete, Document, Download, InfoFilled, Key, Lock, SwitchButton, TrendCharts, User
} from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import { useAuthStore } from '../stores/auth'
import { accountApi, feedbackApi, resumeApi } from '../api'
import { notifyError } from '../api/client'
import { formatDate, formatDateTime, formatFileSize, saveBlob } from '../utils/format'

const router = useRouter()
const auth = useAuthStore()

const profileRef = ref(null)
const passwordRef = ref(null)
const feedbackRef = ref(null)
const profileSaving = ref(false)
const passwordSaving = ref(false)
const feedbackSaving = ref(false)
const exporting = ref(false)
const deletionBusy = ref(false)
const codeSending = ref(false)
const resumes = ref([])
const statsLoading = ref(true)
const storage = ref(null)

/** 上限由部署者的 MINIWHO_STORAGE_QUOTA_MB 决定；0 表示没设限，这时只显示已用量。 */
const storageUsed = computed(() => formatFileSize(storage.value?.usedBytes || 0))
const storageQuota = computed(() => (storage.value?.quotaBytes > 0 ? formatFileSize(storage.value.quotaBytes) : ''))
const storagePercent = computed(() => {
  const used = storage.value?.usedBytes || 0
  const quota = storage.value?.quotaBytes || 0
  return quota > 0 ? Math.min(100, Math.round((used / quota) * 100)) : 0
})

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
/** GitHub 自动建号的账号没有可用密码；老缓存里没这个字段时按「有密码」处理。 */
const hasPassword = computed(() => auth.user?.hasPassword !== false)
const emailVerified = computed(() => Boolean(auth.user?.emailVerified))
/** 服务端落库的邮箱：换绑校验只对「和它不一样」的地址生效，清空不算换绑。 */
const savedEmail = ref(auth.user?.email || '')
const emailChanged = computed(() => {
  const next = profile.email.trim().toLowerCase()
  return next !== '' && next !== savedEmail.value.toLowerCase()
})
const emailCode = ref('')
const emailCodeSending = ref(false)
const emailCodeCountdown = ref(0)
let emailCodeTimer = null
const resumeCount = computed(() => resumes.value.length)
const bestScore = computed(() => Math.max(0, ...resumes.value.map((item) => Number(item.score) || 0)))
const reviewedCount = computed(() => resumes.value.filter((item) => Number(item.score) > 0).length)
const accountStatus = computed(() => {
  // 注销中的账号照常可用，但状态行要说清楚：不然用户以为已经删干净了。
  if (auth.user?.deletionScheduledAt) return '注销中'
  return auth.user?.status === 1 ? '正常' : '已冻结'
})

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

/**
 * 磁盘用量只是参考信息：接口要扫目录，所以不进 /auth/me。
 * 读失败就把这一行留空，不该为它弹一个错误。
 */
async function loadStorage() {
  try {
    storage.value = await accountApi.storage()
  } catch {
    storage.value = null
  }
}

async function saveProfile() {
  try {
    await profileRef.value.validate()
  } catch {
    return
  }
  if (emailChanged.value && !/^\d{6}$/.test(emailCode.value)) {
    ElMessage.warning('更换邮箱需要先向新邮箱获取验证码')
    return
  }
  profileSaving.value = true
  try {
    await auth.updateProfile({
      nickname: profile.nickname.trim(),
      email: profile.email.trim(),
      code: emailChanged.value ? emailCode.value : undefined
    })
    savedEmail.value = auth.user?.email || ''
    emailCode.value = ''
    ElMessage.success('个人资料已保存')
  } catch (error) {
    notifyError(error, '保存个人资料失败')
  } finally {
    profileSaving.value = false
  }
}

/** 验证码发给「新」地址；倒计时与后端的 60 秒重发限制保持一致，别让按钮比服务端先放开。 */
async function sendChangeEmailCode() {
  if (emailCodeSending.value || emailCodeCountdown.value > 0) return
  emailCodeSending.value = true
  try {
    await accountApi.changeEmailCode({ email: profile.email.trim() })
    ElMessage.success('验证码已发送，10 分钟内有效')
    startEmailCodeCountdown()
  } catch (error) {
    notifyError(error, '验证码发送失败')
  } finally {
    emailCodeSending.value = false
  }
}

function startEmailCodeCountdown() {
  emailCodeCountdown.value = 60
  emailCodeTimer = setInterval(() => {
    emailCodeCountdown.value -= 1
    if (emailCodeCountdown.value <= 0) {
      clearInterval(emailCodeTimer)
      emailCodeTimer = null
    }
  }, 1000)
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

/** 文件名用本地时间戳，和后端 zip 里的 manifest 时间能对上。 */
function exportStamp(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
    + `-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
}

async function exportAccount() {
  exporting.value = true
  try {
    const blob = await accountApi.exportData()
    // blob 响应拿不到 Content-Disposition，文件名只能在前端拼一份；
    // username 是用户自己起的，先把 Windows/Linux 都不允许的路径字符替掉。
    const owner = (auth.user?.username || 'account').replace(/[\\/:*?"<>|]/g, '_')
    saveBlob(blob, `miniwho-${owner}-${exportStamp(new Date())}.zip`)
    ElMessage.success('导出完成，浏览器已开始下载')
  } catch (error) {
    notifyError(error, '导出失败')
  } finally {
    exporting.value = false
  }
}

const deletionForm = reactive({ password: '', code: '' })

/** 后端算好的实际清除时间；宽限期天数只在服务层有一处定义，前端不自己加。 */
const deletionScheduledAt = computed(() => auth.user?.deletionScheduledAt || null)

/** 拉一次 /auth/me 把注销状态写回登录态，卡片与状态行会一起换。 */
async function refreshDeletionState() {
  try {
    await auth.fetchMe()
  } catch {
    // 刷新失败不打紧：申请结果本身已经成功，最坏是这次进页面还显示旧状态。
  }
}

async function sendDeletionCode() {
  codeSending.value = true
  try {
    await accountApi.deletionCode()
    ElMessage.success('验证码已发出，十分钟内有效')
  } catch (error) {
    notifyError(error, '验证码发送失败')
  } finally {
    codeSending.value = false
  }
}

async function requestDeletion() {
  try {
    await ElMessageBox.confirm(
      '提交后账号进入宽限期，到期将永久删除全部简历、附件、图片与自行导入的模板，分享链接同时失效。'
        + '期内重新登录可以撤销。确定提交吗？',
      '申请注销账号',
      { confirmButtonText: '确认提交申请', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return
  }
  deletionBusy.value = true
  try {
    await accountApi.requestDeletion(
      hasPassword.value ? { password: deletionForm.password } : { code: deletionForm.code }
    )
    deletionForm.password = ''
    deletionForm.code = ''
    await refreshDeletionState()
    ElMessage.success('注销申请已提交，宽限期内登录即可撤销')
  } catch (error) {
    notifyError(error, '注销申请提交失败')
  } finally {
    deletionBusy.value = false
  }
}

async function cancelDeletion() {
  deletionBusy.value = true
  try {
    await accountApi.cancelDeletion()
    await refreshDeletionState()
    ElMessage.success('已撤销注销申请，账号保持原样')
  } catch (error) {
    notifyError(error, '撤销失败')
  } finally {
    deletionBusy.value = false
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
    savedEmail.value = auth.user.email || ''
  }
  loadStats()
  loadStorage()
})

onBeforeUnmount(() => {
  if (emailCodeTimer) clearInterval(emailCodeTimer)
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
                  <p>昵称会显示在页面右上角；邮箱用于账号联系、GitHub 登录关联与自助找回密码，更换时需要用新邮箱收验证码确认。</p>
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
                  <span v-if="!profile.email" class="field-note">邮箱选填；填写或更换都要用该邮箱收一封验证码确认，验证通过才算绑上。</span>
                  <span v-else-if="emailChanged" class="field-note">邮箱已改动但尚未保存：先点下面的「发送验证码」，新邮箱收到信才算完成换绑。</span>
                  <span v-else class="field-note" :class="{ 'is-verified': emailVerified }">
                    {{ emailVerified
                      ? '该邮箱已通过验证码确认，GitHub 登录时可自动关联到本账号。'
                      : '该邮箱尚未通过验证码确认，GitHub 登录不会把它关联到本账号。' }}
                  </span>
                </el-form-item>
                <el-form-item v-if="emailChanged" label="新邮箱验证码">
                  <div class="deletion-code-row">
                    <el-input v-model="emailCode" maxlength="6" placeholder="6 位邮箱验证码" />
                    <el-button
                      plain
                      :loading="emailCodeSending"
                      :disabled="emailCodeCountdown > 0"
                      @click="sendChangeEmailCode"
                    >
                      {{ emailCodeCountdown > 0 ? `${emailCodeCountdown} 秒` : '发送验证码' }}
                    </el-button>
                  </div>
                  <span class="field-note">验证码发给上面填的新邮箱地址；换绑完成后邮箱状态回到「已验证」，自助找回密码不受影响。</span>
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
                  <h2>{{ hasPassword ? '修改密码' : '登录方式' }}</h2>
                  <p>
                    {{ hasPassword
                      ? '建议使用与其他网站不同的密码，至少 6 位。修改后当前登录仍然有效。'
                      : '该账号由第三方登录自动创建，没有可用密码。需要密码时请联系管理员在后台设置；绑定了已验证邮箱后也可以在登录页用「忘记密码」自助设置。' }}
                  </p>
                </div>
                <span class="card-icon"><el-icon><Lock /></el-icon></span>
              </header>
              <el-form v-if="hasPassword" ref="passwordRef" :model="password" :rules="passwordRules" label-position="top" @submit.prevent>
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
                  <span class="save-hint">忘记密码可在登录页用邮箱验证码自助重置；邮箱未验证时联系部署管理员。</span>
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

            <section class="surface profile-card danger-card">
              <header class="profile-card__head">
                <div>
                  <span class="card-kicker">Danger</span>
                  <h2>注销账号</h2>
                  <p>
                    {{ deletionScheduledAt
                      ? '到期后本账号的全部简历、附件、图片与自行导入的模板都会被永久删除，分享链接同时失效，回收站里的内容也一并清除。'
                      : '提交后不会立刻删除：账号进入宽限期，期内随时可以撤销；到期才执行永久删除。' }}
                  </p>
                </div>
                <span class="card-icon"><el-icon><Delete /></el-icon></span>
              </header>

              <template v-if="deletionScheduledAt">
                <p class="deletion-state">
                  该账号已申请注销，将于 <strong>{{ formatDateTime(deletionScheduledAt) }}</strong> 永久清除。
                </p>
                <div class="form-actions">
                  <span class="save-hint">撤销后账号与数据都保持原样。</span>
                  <el-button type="primary" plain :loading="deletionBusy" @click="cancelDeletion">
                    取消注销
                  </el-button>
                </div>
              </template>

              <el-form v-else label-position="top" @submit.prevent>
                <el-form-item v-if="hasPassword" label="确认身份">
                  <el-input
                    v-model="deletionForm.password"
                    type="password"
                    show-password
                    autocomplete="current-password"
                    placeholder="输入当前密码"
                  />
                  <span class="field-note">注销是不可回头的一步，所以要多确认一次密码。</span>
                </el-form-item>
                <el-form-item v-else label="确认身份">
                  <div class="deletion-code-row">
                    <el-input v-model="deletionForm.code" maxlength="6" placeholder="6 位邮箱验证码" />
                    <el-button plain :loading="codeSending" @click="sendDeletionCode">发送验证码</el-button>
                  </div>
                  <span class="field-note">
                    该账号由第三方登录创建、没有密码，改用已验证邮箱 {{ auth.user?.email || '（未绑定）' }} 的验证码确认。
                  </span>
                </el-form-item>
                <div class="form-actions">
                  <span class="save-hint">建议先用「导出我的全部数据」留一份备份。</span>
                  <el-button
                    type="danger"
                    plain
                    :loading="deletionBusy"
                    :disabled="hasPassword ? !deletionForm.password : !deletionForm.code"
                    @click="requestDeletion"
                  >
                    申请注销账号
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
                  <dd class="is-status" :class="{ 'is-pending': deletionScheduledAt }">
                    <span class="status-dot"></span>{{ accountStatus }}
                  </dd>
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
                <li><span>04</span>随时可打包导出全部履历与文件，导出件不含密码。</li>
              </ul>
              <el-button class="export-btn" :loading="exporting" @click="exportAccount">
                <el-icon v-if="!exporting"><Download /></el-icon>
                {{ exporting ? '正在打包，数据多可能要几十秒' : '导出我的全部数据' }}
              </el-button>
              <p class="export-hint">回收站里的简历不在导出范围内，需要先恢复再打包。</p>

              <div v-if="storage" class="storage-meter">
                <div class="storage-meter__head">
                  <span>磁盘用量</span>
                  <strong>
                    {{ storageUsed }}<template v-if="storageQuota"> / {{ storageQuota }}</template>
                  </strong>
                </div>
                <div v-if="storageQuota" class="storage-meter__bar">
                  <i :style="{ width: storagePercent + '%' }"></i>
                </div>
                <p class="storage-meter__hint">
                  {{ storageQuota
                    ? '附件、头像与条目图片都计入，回收站里的简历同样占盘。'
                    : '部署者没有为单个账号设置存储上限。' }}
                </p>
              </div>
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

/* 注销卡用同一套卡片结构，只在颜色上把「不可回头」这件事标出来。 */
.danger-card {
  border-color: #e9d5d1;
}

.danger-card .card-kicker {
  color: var(--danger);
}

.danger-card .card-icon {
  border-color: #e9d5d1;
  color: var(--danger);
}

.deletion-state {
  margin: 0;
  padding: 13px 15px;
  border: 1px solid #e9d5d1;
  border-radius: 10px;
  background: #fdf6f4;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.75;
}

.deletion-state strong {
  color: var(--danger);
  font-family: var(--mono);
  font-weight: 600;
}

.deletion-code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.deletion-code-row :deep(.el-input) {
  flex: 1;
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

.field-note.is-verified {
  color: var(--accent);
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

/* 注销中的账号照常可用，但状态点不能继续显示「一切正常」的绿。 */
dd.is-pending .status-dot {
  background: var(--danger);
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

.export-btn {
  width: 100%;
  margin-top: 18px;
}

.export-hint {
  margin: 10px 0 0;
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.7;
}

.storage-meter {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
}

.storage-meter__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  color: var(--muted);
  font-size: 11px;
}

.storage-meter__head strong {
  color: var(--ink);
  font-family: var(--mono);
  font-size: 11.5px;
  font-weight: 500;
}

.storage-meter__bar {
  height: 4px;
  margin-top: 8px;
  border-radius: 999px;
  background: var(--line);
  overflow: hidden;
}

.storage-meter__bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--accent);
  transition: width 0.3s ease;
}

.storage-meter__hint {
  margin: 8px 0 0;
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.7;
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
