<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight, ChatDotSquare, Check, Close, DataBoard, Delete, Document, Download, FullScreen, Grid, Odometer, Refresh,
  ScaleToOriginal, Search, Setting, Share, User, UserFilled, View
} from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import PaperFrame from '../components/PaperFrame.vue'
import { useAuthStore } from '../stores/auth'
import { adminApi, templateApi } from '../api'
import { notifyError } from '../api/client'
import { applyContentWidth } from '../utils/appearance'
import { formatDate, formatDateTime, formatFileSize, saveBlob } from '../utils/format'
import { queryUsers, userColumnWidths } from '../utils/userTable'
import { templateByCode } from '../data/templatePresets'
import { useTemplates } from '../composables/useTemplates'

const auth = useAuthStore()

const activeTab = ref('overview')
const loading = ref(true)
const usersLoading = ref(false)
const templatesLoading = ref(false)
const feedbackLoading = ref(false)
const stats = ref(null)
const system = ref(null)
const users = ref([])
const resettingPasswordId = ref(null)
const userResumesDialog = ref(false)
const userResumesLoading = ref(false)
const userResumesTarget = ref(null)
const userResumes = ref([])
/** 弹窗右侧预览：选中的那份简历与它的详情（列表只带摘要，纸面要完整内容）。 */
const userResumeId = ref(null)
const userResumeDetail = ref(null)
const userResumeLoading = ref(false)
const userResumeError = ref('')
const userResumePages = ref(1)
/** 弹窗放大 / 还原；放大后预览区跟着长高，「整页」视图才有发挥空间。 */
const userResumesMaxed = ref(false)
/** 预览缩放方式：width 铺满宽度（长简历向下滚），page 让一整页进画面。 */
const previewFit = ref('width')
const previewStageRef = ref(null)
/** 预览区可用高度（已扣内边距），交给 PaperFrame 算「整页」比例。 */
const previewStageHeight = ref(0)
let previewStageObserver = null
const { all: previewTemplates, refresh: refreshPreviewTemplates, ensure: ensurePreviewTemplate } = useTemplates()
/** 连点两份简历时，只有最后一次请求的结果能写回面板。 */
let userResumeRequest = 0

const previewTemplate = computed(() => templateByCode(userResumeDetail.value?.templateCode, previewTemplates.value))
/** 简历记录的模板可能已下架或被作者取消分享，纸面仍要照原样渲染，只是给一句提示。 */
const previewTemplateMissing = computed(() => Boolean(userResumeDetail.value?.templateCode)
  && !previewTemplates.value.some((tpl) => tpl.code === userResumeDetail.value.templateCode))
const previewStyle = computed(() => {
  const style = userResumeDetail.value?.style || {}
  return {
    filter: style.filter || 'standard',
    fontSizeStep: Number(style.fontSizeStep || 0),
    spacingStep: Number(style.spacingStep || 0),
    showBrandFooter: style.showBrandFooter !== false,
    pageMargin: {
      custom: !!style.pageMargin?.custom,
      top: Number(style.pageMargin?.top ?? 15),
      right: Number(style.pageMargin?.right ?? 15),
      bottom: Number(style.pageMargin?.bottom ?? 12),
      left: Number(style.pageMargin?.left ?? 15)
    }
  }
})
const templates = ref([])
const feedback = ref([])
const sample = ref(null)
const keyword = ref('')
const feedbackFilter = ref('all')
const replyDialog = ref(false)
const replySaving = ref(false)
const replyTarget = ref(null)
const replyText = ref('')

const settingsLoading = ref(false)
const settingsSaving = ref(false)
const channelSaving = ref('')
const authSettings = ref(null)
const authForm = reactive({ usernameRegister: false, emailRegister: false, githubLogin: false })
const mailForm = reactive({
  host: '', port: '', username: '', password: '', from: '', fromName: '', ssl: true, starttls: false
})
const githubForm = reactive({ clientId: '', clientSecret: '', publicBaseUrl: '' })

const siteConfig = ref(null)
const siteWidth = ref(1200)
const widthSaving = ref(false)

const tabs = computed(() => [
  { key: 'overview', label: '运营概览', icon: DataBoard },
  { key: 'users', label: '用户管理', icon: UserFilled, count: users.value.length },
  { key: 'templates', label: '模板管理', icon: Grid, count: templates.value.length },
  { key: 'feedback', label: '意见反馈', icon: ChatDotSquare, count: pendingFeedback.value.length },
  { key: 'recycle', label: '回收站', icon: Delete, count: deletedResumes.value.length },
  { key: 'audit', label: '操作日志', icon: Document },
  { key: 'site', label: '站点设置', icon: Setting }
])

// ── 回收站：用户删除的简历，管理员确认后物理清除 ──
const deletedResumes = ref([])
const deletedLoading = ref(false)
const purgeDeleting = ref(false)

// ── 操作日志：账号级与敏感操作的审计流水 ──
const auditRows = ref([])
const auditLoading = ref(false)
const auditPage = ref(1)
const auditPageSize = ref(30)
const auditTotal = ref(0)
const auditToday = ref(0)
const auditAction = ref('')
const auditActor = ref('')
const auditRetention = ref(90)
const auditSaving = ref(false)
const AUDIT_ACTIONS = [
  { value: '', label: '全部动作' },
  { value: 'auth.login', label: '登录成功' },
  { value: 'auth.login_failed', label: '登录失败' },
  { value: 'auth.register', label: '注册' },
  { value: 'auth.password_change', label: '修改/重置密码' },
  { value: 'account.deletion_request', label: '申请注销' },
  { value: 'account.deletion_cancel', label: '撤销注销' },
  { value: 'account.deletion_purge', label: '注销清除（系统）' },
  { value: 'resume.create', label: '新建简历' },
  { value: 'resume.delete', label: '删除简历' },
  { value: 'resume.purge', label: '清除简历' },
  { value: 'share.create', label: '创建分享' },
  { value: 'share.revoke', label: '撤销分享' },
  { value: 'resume.pdf_export', label: '导出 PDF' },
  { value: 'template.import', label: '导入模板' },
  { value: 'template.share', label: '分享模板' },
  { value: 'admin.user.status', label: '管理员：冻结/解封' },
  { value: 'admin.user.password_reset', label: '管理员：重置密码' },
  { value: 'admin.template.status', label: '管理员：模板上下架' },
  { value: 'admin.template.meta', label: '管理员：模板元信息' },
  { value: 'admin.resume.purge', label: '管理员：清除简历' },
  { value: 'admin.audit.purge', label: '管理员：清理日志' }
]

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(Number(value))
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function deletionDaysLeft(requestedAt) {
  if (!requestedAt) return 0
  const deadline = Number(requestedAt) + 7 * 86400000
  return Math.max(0, Math.ceil((deadline - Date.now()) / 86400000))
}

async function loadDeletedResumes() {
  deletedLoading.value = true
  try {
    deletedResumes.value = await adminApi.deletedResumes()
  } catch (error) {
    notifyError(error, '加载回收站失败')
  } finally {
    deletedLoading.value = false
  }
}

async function purgeDeleted(rows) {
  const list = Array.isArray(rows) ? rows : [rows]
  if (!list.length) return
  try {
    await ElMessageBox.confirm(
      `将物理清除 ${list.length} 份简历，附件、头像与分享记录一并删除，且不可恢复。`,
      '确认清除',
      { type: 'warning', confirmButtonText: '确认清除', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  purgeDeleting.value = true
  try {
    const result = await adminApi.purgeResumes(list.map((row) => row.id))
    ElMessage.success(`已清除 ${result?.removed ?? list.length} 份简历`)
    await loadDeletedResumes()
    await loadStats()
  } catch (error) {
    notifyError(error, '清除失败')
  } finally {
    purgeDeleting.value = false
  }
}

async function loadAuditLogs(page = auditPage.value) {
  auditLoading.value = true
  try {
    const data = await adminApi.auditLogs({
      actorId: auditActor.value ? Number(auditActor.value) : undefined,
      action: auditAction.value || undefined,
      page,
      size: auditPageSize.value
    })
    auditRows.value = data.items || []
    auditTotal.value = data.total || 0
    auditToday.value = data.today || 0
    auditPage.value = data.page || page
  } catch (error) {
    notifyError(error, '加载操作日志失败')
  } finally {
    auditLoading.value = false
  }
}

async function loadAuditSettings() {
  try {
    const data = await adminApi.auditSettings()
    auditRetention.value = data.retentionDays
  } catch {
    auditRetention.value = 90
  }
}

async function saveAuditSettings() {
  auditSaving.value = true
  try {
    const data = await adminApi.updateAuditSettings(Number(auditRetention.value))
    auditRetention.value = data.retentionDays
    ElMessage.success(data.retentionDays === 0 ? '已关闭自动清理' : `已设为保留 ${data.retentionDays} 天`)
  } catch (error) {
    notifyError(error, '保存清理策略失败')
  } finally {
    auditSaving.value = false
  }
}

async function purgeAudit(all) {
  try {
    await ElMessageBox.confirm(
      all ? '将清空全部操作日志，不可恢复。' : '将清理 90 天以前的日志，近 90 天的记录保留。',
      all ? '清空全部日志？' : '清理历史日志？',
      { type: 'warning', confirmButtonText: all ? '清空' : '清理', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const before = all ? undefined : Date.now() - 90 * 86400000
    const result = await adminApi.purgeAuditLogs(before)
    ElMessage.success(`已清理 ${result?.removed ?? 0} 条`)
    await loadAuditLogs(1)
  } catch (error) {
    notifyError(error, '清理失败')
  }
}

async function cancelDeletion(row) {
  try {
    await adminApi.cancelUserDeletion(row.id)
    ElMessage.success(`已撤销 @${row.username} 的注销申请`)
    await loadUsers()
  } catch (error) {
    notifyError(error, '撤销失败')
  }
}

async function purgeDeletion(row) {
  try {
    await ElMessageBox.confirm(
      `将跳过宽限期，立即清除 @${row.username} 的账号与全部数据，不可恢复。`,
      '立即清除账号？',
      { type: 'warning', confirmButtonText: '立即清除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await adminApi.purgeUserDeletion(row.id)
    ElMessage.success(`已清除 @${row.username}`)
    await loadUsers()
  } catch (error) {
    notifyError(error, '清除失败')
  }
}

/** 示意块按 1600px 窗口等比缩放：上限宽度刚好铺满，默认 1200px 占四分之三。 */
const DEMO_VIEWPORT = 1600
const widthDemoStyle = computed(() => ({ width: `${Math.min(100, (siteWidth.value / DEMO_VIEWPORT) * 100)}%` }))
const widthGutter = computed(() => Math.max(0, Math.round((DEMO_VIEWPORT - siteWidth.value) / 2)))

const sortState = ref({ prop: 'createdAt', order: 'descending' })
const userPage = ref(1)
const userPageSize = ref(10)

/** 搜索与排序基于全量数据，分页只决定当前展示哪一段。 */
const filteredUsers = computed(() => queryUsers(users.value, keyword.value, sortState.value))
const pagedUsers = computed(() => {
  const start = (userPage.value - 1) * userPageSize.value
  return filteredUsers.value.slice(start, start + userPageSize.value)
})
const userRangeText = computed(() => {
  if (!filteredUsers.value.length) return '暂无用户'
  const start = (userPage.value - 1) * userPageSize.value + 1
  const end = Math.min(userPage.value * userPageSize.value, filteredUsers.value.length)
  return `第 ${start}–${end} 位，共 ${filteredUsers.value.length} 位`
})
const columnWidths = computed(() => userColumnWidths(filteredUsers.value))

const onlineCount = computed(() => users.value.filter((item) => item.online).length)

const pendingFeedback = computed(() => feedback.value.filter((item) => item.status === 0))
const visibleFeedback = computed(() => {
  if (feedbackFilter.value === 'pending') return feedback.value.filter((item) => item.status === 0)
  if (feedbackFilter.value === 'handled') return feedback.value.filter((item) => item.status === 1)
  return feedback.value
})

const overviewCards = computed(() => {
  const data = stats.value || {}
  // 待处理反馈不进这一条：它是「待办」不是「统计」，页签角标与下方待处理面板都能看到。
  return [
    { key: 'users', label: '注册用户', value: data.users, delta: data.newUsers7d, deltaLabel: '近 7 天新增', icon: UserFilled },
    { key: 'resumes', label: '简历总数', value: data.resumes, delta: data.newResumes7d, deltaLabel: '近 7 天创建', icon: Document },
    { key: 'templates', label: '启用模板', value: data.templates, icon: Grid },
    { key: 'reports', label: '诊断报告', value: data.reports, icon: DataBoard },
    { key: 'shares', label: '分享链接', value: data.shares, icon: Share },
    { key: 'storage', label: '上传占用', value: formatFileSize(data.storageBytes || 0), icon: Odometer }
  ]
})

const recentUsers = computed(() => stats.value?.recentUsers || [])

const CHECK_LABELS = {
  database: '数据库',
  storage: '上传目录',
  pdf: 'PDF 渲染',
  mail: '邮件通道',
  disk: '磁盘空间'
}

function checkLabel(name) {
  return CHECK_LABELS[name] || name
}

const systemStatusText = computed(() => {
  const status = system.value?.status
  if (status === 'UP') return '全部正常'
  if (status === 'DEGRADED') return '可用，但有降级项'
  if (status === 'DOWN') return '核心依赖不可用'
  return '未读取'
})

const systemStatusClass = computed(() => {
  const status = system.value?.status
  return status === 'UP' ? 'is-up' : status === 'DOWN' ? 'is-down' : 'is-warn'
})

/** 无主文件只在真的扫出来时才提，否则这行只会让管理员多一个没有的顾虑。 */
const hasOrphans = computed(() => (system.value?.orphanBytes || 0) > 0)

const sampleTemplateName = computed(() => {
  const code = sample.value?.templateCode
  if (!code) return ''
  return templates.value.find((tpl) => tpl.code === code)?.name || code
})

/** 工作台只能改自己名下的简历，跨用户时后台只展示归属，不给编辑入口。 */
const canEditSample = computed(
  () => Boolean(sample.value) && sample.value.ownerId === auth.user?.id
)

async function loadStats() {
  try {
    stats.value = await adminApi.stats()
  } catch (error) {
    notifyError(error, '加载运营数据失败')
  }
}

async function loadUsers() {
  usersLoading.value = true
  try {
    users.value = await adminApi.users()
    userPage.value = 1
  } catch (error) {
    notifyError(error, '加载用户列表失败')
  } finally {
    usersLoading.value = false
  }
}

function onUserSortChange({ prop, order }) {
  sortState.value = { prop: prop || 'createdAt', order: order || 'descending' }
  userPage.value = 1
}

/** 只读查看：后台不提供跨账号编辑，列表里点一份就在右侧看纸面。 */
async function openUserResumes(row) {
  if (!row.resumeCount) return
  userResumesTarget.value = row
  userResumes.value = []
  userResumeId.value = null
  userResumeDetail.value = null
  userResumeError.value = ''
  userResumePages.value = 1
  userResumeLoading.value = false
  userResumeRequest += 1
  userResumesDialog.value = true
  userResumesLoading.value = true
  observePreviewStage()
  try {
    const list = await adminApi.userResumes(row.id)
    // 列表还没回来就被关掉了，别再接着去拉详情。
    if (!userResumesDialog.value) return
    userResumes.value = Array.isArray(list) ? list : []
    // 弹出来就该看见东西：默认选中第一份，用户再点别的就换。
    if (userResumes.value.length) await selectUserResume(userResumes.value[0])
  } catch (error) {
    notifyError(error, '加载用户简历失败')
  } finally {
    userResumesLoading.value = false
  }
}

/** 选中左侧某一份，把纸面内容取回来铺到右侧。 */
async function selectUserResume(resume) {
  if (!resume) return
  userResumeId.value = resume.id
  userResumeLoading.value = true
  userResumeError.value = ''
  const request = ++userResumeRequest
  try {
    const detail = await adminApi.userResume(resume.id)
    if (request !== userResumeRequest) return
    userResumeDetail.value = detail
    userResumePages.value = 1
    await refreshPreviewTemplates()
    // 作者私有导入的模板不在公开目录里，管理员按编码单独取一次配置。
    await ensurePreviewTemplate(detail.templateCode)
  } catch (error) {
    if (request !== userResumeRequest) return
    userResumeDetail.value = null
    userResumeError.value = error?.message || '这份简历加载失败'
  } finally {
    if (request === userResumeRequest) userResumeLoading.value = false
  }
}

/** 预览面板一变高变矮（弹窗放大、窗口缩放），「整页」视图的缩放比例就得重算。 */
function measurePreviewStage() {
  const el = previewStageRef.value
  previewStageHeight.value = el ? Math.max(0, el.clientHeight - 36) : 0
}

function observePreviewStage() {
  nextTick(() => {
    const el = previewStageRef.value
    if (!el) return
    previewStageObserver?.disconnect()
    if (typeof ResizeObserver !== 'undefined') {
      previewStageObserver = new ResizeObserver(measurePreviewStage)
      previewStageObserver.observe(el)
    }
    measurePreviewStage()
  })
}

/** 关闭即清空：下次点开另一个账号不该先闪一下上一个人的简历。 */
watch(userResumesDialog, (open) => {
  if (open) return
  userResumeRequest += 1
  userResumeId.value = null
  userResumeDetail.value = null
  userResumeError.value = ''
  userResumes.value = []
  userResumePages.value = 1
  userResumeLoading.value = false
})

function previewTargetOf(resume) {
  const username = userResumesTarget.value?.username
  return {
    name: 'admin-resume-preview',
    params: { id: resume.id },
    query: username ? { user: username } : {}
  }
}

watch([keyword, userPageSize], () => {
  userPage.value = 1
})

/** 搜索后结果变少时，别把管理员留在一个已经越界的页码上。 */
watch(() => filteredUsers.value.length, (total) => {
  userPage.value = Math.min(userPage.value, Math.max(1, Math.ceil(total / userPageSize.value)))
})

/** 在线状态单独轮询：用户列表接口会扫磁盘，不适合每 60 秒跑一次。 */
async function refreshOnlineUsers() {
  if (activeTab.value !== 'users') return
  try {
    const online = new Set((await adminApi.onlineUsers()) || [])
    for (const user of users.value) {
      user.online = online.has(user.id)
    }
  } catch {
    // 在线状态只是辅助信息，失败时保留上一次结果。
  }
}

let onlineTimer = 0

async function loadTemplates() {
  templatesLoading.value = true
  try {
    templates.value = await adminApi.templates()
  } catch (error) {
    notifyError(error, '加载模板列表失败')
  } finally {
    templatesLoading.value = false
  }
}

async function exportBundle() {
  try {
    const blob = await adminApi.exportTemplates()
    saveBlob(blob, 'miniwho-official-templates.json')
    ElMessage.success('官方模板包已打包导出，迁移部署时逐个导入即可')
  } catch (error) {
    notifyError(error, '导出模板包失败')
  }
}

async function exportOne(row) {
  try {
    const blob = await templateApi.exportPackage(row.code)
    saveBlob(blob, `${row.code}-${row.version || '1.0.0'}.json`)
  } catch (error) {
    notifyError(error, '导出模板包失败')
  }
}

async function loadFeedback() {
  feedbackLoading.value = true
  try {
    feedback.value = await adminApi.feedback()
  } catch (error) {
    notifyError(error, '加载反馈失败')
  } finally {
    feedbackLoading.value = false
  }
}

async function loadSample() {
  try {
    sample.value = await adminApi.sample()
  } catch (error) {
    notifyError(error, '加载示例简历失败')
  }
}

async function clearSample() {
  try {
    await ElMessageBox.confirm(
      '取消后首页与模板中心会退回打包的出厂示例，简历本身不受影响。',
      '取消首页示例',
      { confirmButtonText: '取消示例', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await adminApi.setSample(sample.value.resumeId, false)
    sample.value = null
    ElMessage.success('已取消首页示例')
  } catch (error) {
    notifyError(error, '取消示例失败')
  }
}

/** 后台开关是意愿，SMTP / GitHub 凭据就绪才是事实，前台只渲染两者都成立的入口。 */
const authEffective = computed(() => {
  const data = authSettings.value
  return {
    username: Boolean(data?.usernameRegister),
    email: Boolean(data?.emailRegister && data?.mailConfigured),
    github: Boolean(data?.githubLogin && data?.githubConfigured)
  }
})
const authAllClosed = computed(() =>
  Boolean(authSettings.value) && !Object.values(authEffective.value).some(Boolean)
)

function syncAuthForm(data) {
  authForm.usernameRegister = Boolean(data.usernameRegister)
  authForm.emailRegister = Boolean(data.emailRegister)
  authForm.githubLogin = Boolean(data.githubLogin)
}

/** 通道表单只回填非敏感项；授权码与 Client Secret 永远留空，填了才提交。 */
function syncChannelForm(data) {
  const mail = data.mail || {}
  Object.assign(mailForm, {
    host: mail.host || '',
    port: mail.port || '',
    username: mail.username || '',
    password: '',
    from: mail.from || '',
    fromName: mail.fromName || '',
    ssl: mail.ssl !== false,
    starttls: Boolean(mail.starttls)
  })
  const github = data.github || {}
  Object.assign(githubForm, {
    clientId: github.clientId || '',
    clientSecret: '',
    publicBaseUrl: github.publicBaseUrl || ''
  })
}

async function loadSiteSettings() {
  settingsLoading.value = true
  try {
    const [authData, siteData] = await Promise.all([adminApi.authSettings(), adminApi.siteConfig()])
    authSettings.value = authData
    syncAuthForm(authData)
    syncChannelForm(authData)
    siteConfig.value = siteData
    siteWidth.value = siteData.contentWidth
    applyContentWidth(siteData.contentWidth)
  } catch (error) {
    notifyError(error, '加载站点设置失败')
  } finally {
    settingsLoading.value = false
  }
}

/** 保存后立刻写进 CSS 变量，管理员不用刷新就能在其它标签页看到效果。 */
async function saveContentWidth() {
  if (widthSaving.value) return
  widthSaving.value = true
  try {
    const data = await adminApi.updateSiteConfig({ contentWidth: siteWidth.value })
    siteConfig.value = data
    siteWidth.value = data.contentWidth
    applyContentWidth(data.contentWidth)
    ElMessage.success('内容宽度已保存，前台刷新后按新宽度排版')
  } catch (error) {
    notifyError(error, '保存内容宽度失败')
    await loadSiteSettings()
  } finally {
    widthSaving.value = false
  }
}

async function saveAuthSettings() {
  if (settingsSaving.value) return
  settingsSaving.value = true
  try {
    const data = await adminApi.updateAuthSettings({ ...authForm })
    authSettings.value = data
    syncAuthForm(data)
    ElMessage.success('设置已保存，登录页最多 30 秒后生效')
  } catch (error) {
    notifyError(error, '保存失败')
    await loadSiteSettings()
  } finally {
    settingsSaving.value = false
  }
}

/**
 * 通道配置一次只提交一组：未填的密钥项直接不发，服务端按「不改动」处理。
 * clear=true 时把所有项传空串，等于清空后台值、重新回落到环境变量。
 */
async function saveChannel(group, payload, clear = false) {
  if (channelSaving.value) return
  channelSaving.value = group
  try {
    const data = await adminApi.updateAuthSettings(payload)
    authSettings.value = data
    syncAuthForm(data)
    syncChannelForm(data)
    ElMessage.success(clear ? '已清空后台配置，回落到环境变量' : '已保存，立即生效，无需重启')
  } catch (error) {
    notifyError(error, '保存失败')
    await loadSiteSettings()
  } finally {
    channelSaving.value = ''
  }
}

function saveMailChannel() {
  const payload = {
    mailHost: mailForm.host.trim(),
    mailPort: String(mailForm.port).trim(),
    mailUsername: mailForm.username.trim(),
    mailFrom: mailForm.from.trim(),
    mailFromName: mailForm.fromName.trim(),
    mailSsl: mailForm.ssl,
    mailStarttls: mailForm.starttls
  }
  if (mailForm.password) payload.mailPassword = mailForm.password
  return saveChannel('mail', payload)
}

function saveGithubChannel() {
  const payload = {
    githubClientId: githubForm.clientId.trim(),
    publicBaseUrl: githubForm.publicBaseUrl.trim()
  }
  if (githubForm.clientSecret) payload.githubClientSecret = githubForm.clientSecret
  return saveChannel('github', payload)
}

function clearMailChannel() {
  return saveChannel('mail', {
    mailHost: '', mailPort: '', mailUsername: '', mailPassword: '', mailFrom: '', mailFromName: ''
  }, true)
}

function clearGithubChannel() {
  return saveChannel('github', {
    githubClientId: '', githubClientSecret: '', publicBaseUrl: ''
  }, true)
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('浏览器不允许直接复制，请手动选中')
  }
}

async function loadAll(showLoading = true) {
  if (showLoading) loading.value = true
  await Promise.all([
    loadStats(), loadSystem(), loadUsers(), loadTemplates(), loadFeedback(), loadSample(), loadSiteSettings(),
    loadDeletedResumes(), loadAuditSettings()
  ])
  loading.value = false
}

/**
 * 运行环境是「看一眼」的信息，读失败不该把整个后台变成错误态：
 * 保持 system 为 null，卡片自己会整块不渲染。
 */
async function loadSystem() {
  try {
    system.value = await adminApi.system()
  } catch {
    system.value = null
  }
}

async function toggleUser(row) {
  if (row.id === auth.user?.id) return
  const freezing = row.status === 1
  try {
    await ElMessageBox.confirm(
      freezing
        ? `冻结后「${row.nickname || row.username}」将无法登录，但数据会保留。`
        : `确定要恢复「${row.nickname || row.username}」的登录权限吗？`,
      freezing ? '冻结账号' : '恢复账号',
      {
        confirmButtonText: freezing ? '冻结' : '恢复',
        cancelButtonText: '取消',
        type: freezing ? 'warning' : 'info'
      }
    )
  } catch {
    return
  }
  try {
    await adminApi.updateUserStatus(row.id, freezing ? 2 : 1)
    row.status = freezing ? 2 : 1
    ElMessage.success(freezing ? '账号已冻结' : '账号已恢复')
    loadStats()
    // 冻结会踢掉会话，后端已判定离线；同步一次避免出现「在线 · 已冻结」。
    refreshOnlineUsers()
  } catch (error) {
    notifyError(error, '更新账号状态失败')
  }
}

/**
 * 重置密码：后端生成 8 位随机密码。有已验证邮箱就自动发送；没有则直接返回密码给管理员转告。
 * 发送期间锁住按钮，避免连点产生两封密码不同的邮件。
 */
async function resetUserPassword(row) {
  if (row.id === auth.user?.id) {
    ElMessage.info('改自己的密码请用个人中心的「修改密码」')
    return
  }
  const deliverable = Boolean(row.email && row.emailVerified)
  const deliveryHint = deliverable
    ? `发送到 ${row.email}`
    : '直接显示给你（该账号没有已验证邮箱）'
  try {
    await ElMessageBox.confirm(
      `将为「${row.nickname || row.username}」生成 8 位随机密码并${deliveryHint}。重置后该账号已登录的会话会全部退出，确认重置吗？`,
      '重置密码',
      {
        confirmButtonText: deliverable ? '重置并发送' : '重置并显示',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  resettingPasswordId.value = row.id
  try {
    const result = await adminApi.resetUserPassword(row.id)
    if (result?.delivery === 'manual') {
      ElMessageBox.alert(
        `该账号没有已验证邮箱，无法发送邮件。<br><br>新密码：<b style="font-family:monospace;font-size:20px;color:#0a7d76;">${result.password}</b><br><br>请通过安全渠道转告本人，并提醒登录后尽快修改。`,
        '临时密码',
        { confirmButtonText: '我已记下', type: 'warning', dangerouslyUseHTMLString: true }
      ).catch(() => {})
    } else {
      ElMessage.success(`新密码已发送到 ${row.email}，该账号需要重新登录`)
    }
  } catch (error) {
    notifyError(error, '重置密码失败')
  } finally {
    resettingPasswordId.value = null
  }
}

async function toggleTemplate(row) {
  const enabling = row.status !== 1
  try {
    await ElMessageBox.confirm(
      enabling ? `启用「${row.name}」后，用户可以在模板中心选择它。` : `停用「${row.name}」后，新简历将不能选择该模板，已有简历不受影响。`,
      enabling ? '启用模板' : '停用模板',
      {
        confirmButtonText: enabling ? '启用' : '停用',
        cancelButtonText: '取消',
        type: enabling ? 'info' : 'warning'
      }
    )
  } catch {
    return
  }
  try {
    await adminApi.updateTemplateStatus(row.code, enabling ? 1 : 2)
    row.status = enabling ? 1 : 2
    ElMessage.success(enabling ? '模板已启用' : '模板已停用')
    loadStats()
  } catch (error) {
    notifyError(error, '更新模板状态失败')
  }
}

/** 修改社区模板的基础信息：官方模板随发版下发，不在可编辑范围（按钮只对导入模板出现）。 */
const metaDialog = reactive({
  visible: false,
  saving: false,
  code: '',
  name: '',
  description: '',
  category: '',
  tags: ''
})

function openMetaDialog(item) {
  metaDialog.code = item.code
  metaDialog.name = item.name || ''
  metaDialog.description = item.description || ''
  metaDialog.category = item.category || ''
  // 标签编辑框里放库里存的原始标签（含与分类重复的），保存时原样回写。
  metaDialog.tags = String(item.tags || '').split(',').map((tag) => tag.trim()).filter(Boolean).join(', ')
  metaDialog.visible = true
}

async function saveMeta() {
  const name = metaDialog.name.trim()
  if (!name) {
    ElMessage.warning('请填写模板名称')
    return
  }
  metaDialog.saving = true
  try {
    await adminApi.updateTemplateMeta(metaDialog.code, {
      name,
      description: metaDialog.description.trim(),
      category: metaDialog.category.trim(),
      tags: metaDialog.tags.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean)
    })
    ElMessage.success('模板信息已更新')
    metaDialog.visible = false
    await loadTemplates()
  } catch (error) {
    notifyError(error, '保存模板信息失败')
  } finally {
    metaDialog.saving = false
  }
}

function openReply(item) {
  replyTarget.value = item
  replyText.value = item.reply || ''
  replyDialog.value = true
}

async function submitReply() {
  if (!replyTarget.value || replySaving.value) return
  const text = replyText.value.trim()
  if (!text) {
    ElMessage.warning('请填写回复内容')
    return
  }
  replySaving.value = true
  try {
    await adminApi.updateFeedback(replyTarget.value.id, { status: 1, reply: text })
    ElMessage.success('回复已保存，反馈已标记为已处理')
    replyDialog.value = false
    await Promise.all([loadFeedback(), loadStats()])
  } catch (error) {
    notifyError(error, '保存回复失败')
  } finally {
    replySaving.value = false
  }
}

function switchTab(key) {
  activeTab.value = key
  if (key === 'overview') loadStats()
  if (key === 'users') refreshOnlineUsers()
  if (key === 'recycle') loadDeletedResumes()
  if (key === 'audit') {
    loadAuditSettings()
    loadAuditLogs(1)
  }
}

function templateTags(row) {
  const tags = String(row.tags || '').split(',').map((item) => item.trim()).filter(Boolean)
  return tags.filter((tag, index) => tag !== row.category && tags.indexOf(tag) === index)
}

function feedbackPreview(item) {
  return item.content?.length > 120 ? `${item.content.slice(0, 120)}…` : item.content
}

onMounted(() => {
  loadAll()
  onlineTimer = window.setInterval(refreshOnlineUsers, 60_000)
})

onBeforeUnmount(() => {
  window.clearInterval(onlineTimer)
  previewStageObserver?.disconnect()
})
</script>

<template>
  <AppLayout>
    <div class="admin-page">
      <div class="admin-shell">
        <header class="admin-hero">
          <div>
            <span class="eyebrow">Admin console</span>
            <h1 class="display">管理后台</h1>
            <p>查看站点运营数据、管理用户与模板，并处理用户反馈。当前操作仅对本部署实例生效。</p>
          </div>
          <div class="admin-hero__actions">
            <el-button :loading="loading" @click="loadAll()">
              <el-icon v-if="!loading"><Refresh /></el-icon>
              刷新数据
            </el-button>
            <el-button type="primary" @click="$router.push('/dashboard')">返回前台</el-button>
          </div>
        </header>

        <nav class="admin-tabs" aria-label="后台模块">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="admin-tab"
            :class="{ 'is-active': activeTab === tab.key }"
            @click="switchTab(tab.key)"
          >
            <el-icon><component :is="tab.icon" /></el-icon>
            <span>{{ tab.label }}</span>
            <span v-if="tab.count !== undefined" class="tab-count">{{ tab.count }}</span>
          </button>
        </nav>

        <el-skeleton v-if="loading" :rows="8" animated class="admin-skeleton" />

        <template v-else>
          <section v-show="activeTab === 'overview'" class="admin-section">
            <div class="stat-grid">
              <article v-for="card in overviewCards" :key="card.key" class="stat-card surface">
                <div class="stat-card__top">
                  <span>{{ card.label }}</span>
                  <span class="stat-icon"><el-icon><component :is="card.icon" /></el-icon></span>
                </div>
                <strong class="stat-value">{{ card.value ?? 0 }}</strong>
                <p v-if="card.delta !== undefined" class="stat-delta">
                  <span>+{{ card.delta ?? 0 }}</span>
                  {{ card.deltaLabel }}
                </p>
                <p v-else class="stat-delta">当前累计</p>
              </article>
            </div>

            <section class="surface sample-card">
              <header class="panel-head sample-card__head">
                <div>
                  <span class="card-kicker">Sample resume</span>
                  <h2>首页示例简历</h2>
                </div>
                <div class="sample-card__actions">
                  <button
                    v-if="canEditSample"
                    class="text-link"
                    type="button"
                    @click="$router.push(`/resumes/${sample.resumeId}`)"
                  >
                    去工作台编辑 →
                  </button>
                  <button v-if="sample" class="sample-card__clear" type="button" @click="clearSample">
                    取消示例
                  </button>
                </div>
              </header>
              <p class="sample-card__hint">
                首页纸面、模板中心缩略图与放大预览都读这一份，改一次全站同步；无需再动打包的示例数据。
              </p>
              <div v-if="sample" class="sample-card__row">
                <strong>{{ sample.title || '未命名简历' }}</strong>
                <span>{{ sampleTemplateName }}</span>
                <span>归属 {{ sample.owner || '匿名' }}</span>
                <span>更新于 {{ formatDateTime(sample.updatedAt) }}</span>
              </div>
              <p v-else class="sample-card__empty">
                当前未设置，前台使用打包的出厂示例。在任意简历的工作台右上角菜单点「设为首页示例」即可接管，
                注意该接口无鉴权，示例内容不登录也能看到。
              </p>
            </section>

            <div class="overview-grid">
              <section class="surface overview-card">
                <header class="panel-head">
                  <div>
                    <span class="card-kicker">New users</span>
                    <h2>最近注册</h2>
                  </div>
                  <button class="text-link" type="button" @click="switchTab('users')">全部用户 →</button>
                </header>
                <ul class="recent-users">
                  <li v-for="user in recentUsers" :key="user.id">
                    <span class="recent-avatar">{{ (user.nickname || user.username || '?').slice(0, 1) }}</span>
                    <div>
                      <strong>{{ user.nickname || user.username }}</strong>
                      <span>@{{ user.username }} · {{ formatDate(user.createdAt) }}</span>
                    </div>
                    <span class="role-tag" :class="{ 'is-admin': user.role === 'admin' }">
                      {{ user.role === 'admin' ? '管理员' : '用户' }}
                    </span>
                  </li>
                  <li v-if="!recentUsers.length" class="recent-empty">暂无注册用户</li>
                </ul>
              </section>

              <section class="surface overview-card">
                <header class="panel-head">
                  <div>
                    <span class="card-kicker">Feedback</span>
                    <h2>待处理反馈</h2>
                  </div>
                  <button class="text-link" type="button" @click="switchTab('feedback')">处理全部 →</button>
                </header>
                <ul v-if="pendingFeedback.length" class="pending-list">
                  <li v-for="item in pendingFeedback.slice(0, 3)" :key="item.id">
                    <p>{{ feedbackPreview(item) }}</p>
                    <div>
                      <span>{{ item.contact || '未留联系方式' }} · {{ formatDate(item.createdAt) }}</span>
                      <button class="mini-action" type="button" @click="openReply(item)">回复</button>
                    </div>
                  </li>
                </ul>
                <div v-else class="pending-empty">
                  <el-icon><ChatDotSquare /></el-icon>
                  <p>所有反馈都已处理</p>
                </div>
              </section>
            </div>

            <section v-if="system" class="surface system-card">
              <header class="panel-head">
                <div>
                  <span class="card-kicker">Runtime</span>
                  <h2>运行环境</h2>
                </div>
                <span class="system-status" :class="systemStatusClass">{{ systemStatusText }}</span>
              </header>
              <ul class="system-checks">
                <li v-for="check in system.checks" :key="check.name">
                  <el-icon class="is-ok" v-if="check.ok"><Check /></el-icon>
                  <el-icon class="is-bad" v-else><Close /></el-icon>
                  <strong>{{ checkLabel(check.name) }}</strong>
                  <span>{{ check.detail }}</span>
                </li>
              </ul>
              <dl class="system-numbers">
                <div>
                  <dt>上传目录</dt>
                  <dd>{{ system.uploadDir }}</dd>
                </div>
                <div>
                  <dt>磁盘剩余</dt>
                  <dd>{{ formatFileSize(system.freeBytes) }}</dd>
                </div>
                <div>
                  <dt>附件合计</dt>
                  <dd>{{ formatFileSize(system.storageBytes) }}</dd>
                </div>
                <div>
                  <dt>单账号上限</dt>
                  <dd>{{ system.quotaBytes > 0 ? formatFileSize(system.quotaBytes) : '未设置' }}</dd>
                </div>
              </dl>
              <p v-if="hasOrphans" class="system-orphan">
                另有 {{ formatFileSize(system.orphanBytes) }} 文件找不到对应简历（多为删除链路的残留），
                可以直接在上传目录里按目录名核对后手动清理。
              </p>
            </section>

            <section class="surface admin-note">
              <el-icon><View /></el-icon>
              <div>
                <strong>部署提示</strong>
                <p>管理员账号由后端启动参数创建（demo 环境为 admin / admin123）。上线前请修改默认密码，并在反向代理层开启 HTTPS。</p>
              </div>
            </section>
          </section>

          <section v-show="activeTab === 'users'" class="admin-section">
            <div class="table-toolbar">
              <div>
                <span class="card-kicker">Users</span>
                <h2>用户管理 <small>{{ users.length }} 位 · {{ onlineCount }} 人在线</small></h2>
              </div>
              <el-input v-model="keyword" class="user-search" placeholder="搜索用户名、昵称或邮箱" clearable>
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
            </div>

            <div class="surface table-card user-table-card" v-loading="usersLoading">
              <el-table
                :data="pagedUsers"
                row-key="id"
                style="width: 100%"
                :default-sort="{ prop: 'createdAt', order: 'descending' }"
                @sort-change="onUserSortChange"
              >
                <el-table-column prop="username" label="用户" :min-width="columnWidths.user" sortable="custom" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div class="table-user">
                      <span class="table-avatar">
                        {{ (row.nickname || row.username || '?').slice(0, 1) }}
                        <i v-if="row.online" class="table-avatar__online" title="最近 5 分钟内有活动"></i>
                      </span>
                      <div>
                        <strong>{{ row.nickname || row.username }}</strong>
                        <span class="table-user__handle">@{{ row.username }}</span>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="email" label="邮箱" :min-width="columnWidths.email" sortable="custom" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div v-if="row.email" class="cell-email">
                      <span class="cell-muted">{{ row.email }}</span>
                      <span class="verify-tag" :class="row.emailVerified ? 'is-ok' : 'is-no'">
                        {{ row.emailVerified ? '已验证' : '未验证' }}
                      </span>
                    </div>
                    <span v-else class="cell-muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column prop="role" label="角色" :min-width="columnWidths.role" sortable="custom">
                  <template #default="{ row }">
                    <span class="role-tag" :class="{ 'is-admin': row.role === 'admin' }">
                      {{ row.role === 'admin' ? '管理员' : '普通用户' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="resumeCount" label="简历" :min-width="columnWidths.resumeCount" align="center" sortable="custom">
                  <template #default="{ row }">
                    <button
                      v-if="row.resumeCount"
                      type="button"
                      class="resume-count resume-count--link"
                      title="查看该用户已制作的简历"
                      @click="openUserResumes(row)"
                    >
                      <b>{{ row.resumeCount }}</b><small>份</small>
                    </button>
                    <span v-else class="resume-count is-empty"><b>0</b><small>份</small></span>
                  </template>
                </el-table-column>
                <el-table-column prop="storageBytes" label="存储" :min-width="columnWidths.storage" align="right" sortable="custom">
                  <template #default="{ row }">
                    <span class="cell-muted cell-mono">{{ formatFileSize(row.storageBytes || 0) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="注册时间" :min-width="columnWidths.createdAt" sortable="custom">
                  <template #default="{ row }">
                    <span class="cell-muted">{{ formatDate(row.createdAt) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="online" label="状态" :min-width="columnWidths.status" sortable="custom">
                  <template #default="{ row }">
                    <div class="user-state">
                      <span
                        class="status-tag"
                        :class="row.online ? 'is-on' : 'is-off'"
                        :title="row.online ? '最近 5 分钟内有活动' : '最近 5 分钟内没有活动'"
                      >
                        <i></i>{{ row.online ? '在线' : '离线' }}
                      </span>
                      <span v-if="row.status !== 1" class="frozen-tag">已冻结</span>
                      <span
                        v-if="row.deletionRequestedAt"
                        class="frozen-tag is-deletion"
                        :title="`${formatTime(row.deletionRequestedAt)} 申请注销`"
                      >
                        注销中 · 剩 {{ deletionDaysLeft(row.deletionRequestedAt) }} 天
                      </span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" :min-width="columnWidths.actions" align="right">
                  <template #default="{ row }">
                    <div class="row-actions">
                      <el-button size="small" link :loading="resettingPasswordId === row.id" :disabled="row.id === auth.user?.id" @click="resetUserPassword(row)">
                        重置密码
                      </el-button>
                      <span class="row-actions__sep" aria-hidden="true"></span>
                      <el-button
                        size="small"
                        :type="row.status === 1 ? 'danger' : 'primary'"
                        link
                        :disabled="row.id === auth.user?.id"
                        @click="toggleUser(row)"
                      >
                        {{ row.id === auth.user?.id ? '当前账号' : (row.status === 1 ? '冻结' : '恢复') }}
                      </el-button>
                      <template v-if="row.deletionRequestedAt">
                        <span class="row-actions__sep" aria-hidden="true"></span>
                        <el-button size="small" link @click="cancelDeletion(row)">撤销注销</el-button>
                        <el-button size="small" link type="danger" @click="purgeDeletion(row)">立即清除</el-button>
                      </template>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="!filteredUsers.length" class="table-empty">没有匹配的用户</div>
              <div v-else class="user-pager">
                <span class="user-pager__summary">{{ userRangeText }}</span>
                <el-pagination
                  v-model:current-page="userPage"
                  v-model:page-size="userPageSize"
                  :page-sizes="[10, 20, 50]"
                  :total="filteredUsers.length"
                  layout="sizes, prev, pager, next, jumper"
                  background
                  size="small"
                />
              </div>
            </div>
          </section>

          <section v-show="activeTab === 'templates'" class="admin-section">
            <div class="table-toolbar">
              <div>
                <span class="card-kicker">Templates</span>
                <h2>模板管理 <small>{{ templates.length }} 套</small></h2>
              </div>
              <p class="toolbar-hint">
                停用模板不会影响已创建的简历。分享与导出权限只能由作者设置，后台仅可查看。
                <el-button size="small" link type="primary" @click="exportBundle">
                  <el-icon><Download /></el-icon> 导出全部官方模板包
                </el-button>
              </p>
            </div>

            <div class="template-admin-grid" v-loading="templatesLoading">
              <article v-for="item in templates" :key="item.code" class="template-admin-card surface">
                <header>
                  <div>
                    <strong>{{ item.name }}</strong>
                    <span class="mono-code">{{ item.code }}</span>
                  </div>
                  <span class="status-tag" :class="item.status === 1 ? 'is-on' : 'is-off'">
                    <i></i>{{ item.status === 1 ? '启用' : '停用' }}
                  </span>
                </header>
                <p>{{ item.description || '未填写模板描述' }}</p>
                <div class="template-tags">
                  <span class="tag">{{ item.source === 'imported' ? `${item.author || '未署名'} 导入` : '官方' }}</span>
                  <span v-if="item.source === 'imported' && item.metaEdited" class="tag">信息已定制</span>
                  <span v-if="item.source === 'imported'" class="tag">
                    {{ item.visibility === 'public'
                      ? (item.exportable ? '已分享 · 可导出' : '已分享 · 仅选用')
                      : '仅作者可见' }}
                  </span>
                  <span v-if="item.version" class="tag">v{{ item.version }}</span>
                  <span v-if="item.category" class="tag">{{ item.category }}</span>
                  <span v-for="tag in templateTags(item)" :key="tag" class="tag">{{ tag }}</span>
                </div>
                <footer>
                  <span>{{ item.usageCount ?? 0 }} 份简历在用</span>
                  <span class="footer-actions">
                    <el-button v-if="item.source === 'imported'" size="small" link @click="openMetaDialog(item)">
                      编辑信息
                    </el-button>
                    <el-button size="small" link @click="exportOne(item)">导出包</el-button>
                    <el-button size="small" :type="item.status === 1 ? 'danger' : 'primary'" link @click="toggleTemplate(item)">
                      {{ item.status === 1 ? '停用' : '启用' }}
                    </el-button>
                  </span>
                </footer>
              </article>
            </div>
          </section>

          <section v-show="activeTab === 'feedback'" class="admin-section">
            <div class="table-toolbar">
              <div>
                <span class="card-kicker">Feedback</span>
                <h2>意见反馈 <small>{{ feedback.length }} 条</small></h2>
              </div>
              <el-radio-group v-model="feedbackFilter" size="small">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="pending">待处理 {{ pendingFeedback.length }}</el-radio-button>
                <el-radio-button value="handled">已处理</el-radio-button>
              </el-radio-group>
            </div>

            <div v-if="feedbackLoading" class="surface feedback-loading"><el-skeleton :rows="4" animated /></div>
            <div v-else-if="!visibleFeedback.length" class="surface feedback-empty">
              <el-icon><ChatDotSquare /></el-icon>
              <p>这个分类下暂时没有反馈</p>
            </div>
            <ul v-else class="feedback-list">
              <li v-for="item in visibleFeedback" :key="item.id" class="surface feedback-item">
                <header>
                  <div class="feedback-meta">
                    <span class="status-tag" :class="item.status === 0 ? 'is-warn' : 'is-on'">
                      <i></i>{{ item.status === 0 ? '待处理' : '已处理' }}
                    </span>
                    <span>{{ formatDateTime(item.createdAt) }}</span>
                    <span v-if="item.contact">联系：{{ item.contact }}</span>
                    <span v-else>未留联系方式</span>
                  </div>
                  <el-button v-if="item.status === 0" size="small" type="primary" plain @click="openReply(item)">回复</el-button>
                  <el-button v-else size="small" link @click="openReply(item)">查看回复</el-button>
                </header>
                <p class="feedback-content">{{ item.content }}</p>
                <div v-if="item.reply" class="feedback-reply">
                  <span>管理员回复</span>
                  <p>{{ item.reply }}</p>
                </div>
              </li>
            </ul>
          </section>

          <section v-show="activeTab === 'recycle'" class="admin-section">
            <div class="table-toolbar">
              <div>
                <span class="card-kicker">Recycle bin</span>
                <h2>回收站 <small>{{ deletedResumes.length }} 份已删除简历</small></h2>
              </div>
              <div class="row-actions">
                <el-button :loading="deletedLoading" @click="loadDeletedResumes">
                  <el-icon v-if="!deletedLoading"><Refresh /></el-icon>
                  刷新
                </el-button>
                <el-button
                  type="danger"
                  plain
                  :disabled="!deletedResumes.length"
                  :loading="purgeDeleting"
                  @click="purgeDeleted(deletedResumes)"
                >
                  全部彻底删除
                </el-button>
              </div>
            </div>

            <div class="surface table-card" v-loading="deletedLoading">
              <el-table :data="deletedResumes" row-key="id" style="width: 100%">
                <el-table-column label="简历" min-width="240" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div class="table-user">
                      <div>
                        <strong>{{ row.title || '（未命名简历）' }}</strong>
                        <span class="table-user__handle">#{{ row.id }} · {{ row.templateCode }}</span>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="属主" min-width="180">
                  <template #default="{ row }">
                    <strong>{{ row.nickname || row.username || '—' }}</strong>
                    <span class="table-user__handle">@{{ row.username }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="删除时间" min-width="150">
                  <template #default="{ row }">{{ formatTime(row.deletedAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="right">
                  <template #default="{ row }">
                    <el-button size="small" link type="danger" :loading="purgeDeleting" @click="purgeDeleted(row)">
                      彻底删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="!deletedResumes.length" class="table-empty">回收站是空的</div>
              <p class="recycle-note">
                用户删除的简历会先进入这里：用户侧已经看不到，但附件与图片还在。确认无误后再彻底删除，删除后不可恢复。
              </p>
            </div>
          </section>

          <section v-show="activeTab === 'audit'" class="admin-section">
            <div class="table-toolbar">
              <div>
                <span class="card-kicker">Audit log</span>
                <h2>操作日志 <small>今天 {{ auditToday }} 条 · 累计 {{ auditTotal }} 条</small></h2>
              </div>
              <div class="row-actions">
                <el-button @click="purgeAudit(false)">清理 90 天前</el-button>
                <el-button type="danger" plain @click="purgeAudit(true)">清空全部</el-button>
              </div>
            </div>

            <section class="surface audit-policy">
              <div>
                <strong>自动清理策略</strong>
                <p>每天凌晨 3 点按保留天数清理日志；填 0 表示不自动清理，只靠手动清。</p>
              </div>
              <div class="audit-policy__control">
                <span class="cell-muted">保留</span>
                <el-input-number v-model="auditRetention" :min="0" :max="3650" :step="30" size="small" controls-position="right" />
                <span class="cell-muted">天</span>
                <el-button size="small" type="primary" :loading="auditSaving" @click="saveAuditSettings">保存</el-button>
              </div>
            </section>

            <div class="surface table-card" v-loading="auditLoading">
              <div class="audit-filters">
                <el-select v-model="auditAction" size="small" @change="loadAuditLogs(1)">
                  <el-option v-for="item in AUDIT_ACTIONS" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
                <el-input
                  v-model="auditActor"
                  class="user-search"
                  size="small"
                  placeholder="操作者用户 ID"
                  clearable
                  @keyup.enter="loadAuditLogs(1)"
                />
                <el-button size="small" @click="loadAuditLogs(1)">查询</el-button>
              </div>
              <el-table :data="auditRows" row-key="id" style="width: 100%">
                <el-table-column label="时间" width="150">
                  <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作者" min-width="150">
                  <template #default="{ row }">
                    <strong>{{ row.actorName || '—' }}</strong>
                    <span class="table-user__handle">{{ row.actorRole }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="action" label="动作" min-width="180" show-overflow-tooltip />
                <el-table-column label="对象" min-width="120">
                  <template #default="{ row }">
                    <span class="cell-muted">{{ row.targetType }}{{ row.targetId ? ' #' + row.targetId : '' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="detail" label="说明" min-width="220" show-overflow-tooltip />
                <el-table-column prop="ip" label="IP" width="140" />
              </el-table>
              <div v-if="!auditRows.length" class="table-empty">还没有日志</div>
              <div v-else class="user-pager">
                <span class="user-pager__summary">共 {{ auditTotal }} 条</span>
                <el-pagination
                  v-model:current-page="auditPage"
                  :page-size="auditPageSize"
                  :total="auditTotal"
                  layout="prev, pager, next"
                  background
                  size="small"
                  @current-change="loadAuditLogs"
                />
              </div>
            </div>
          </section>

          <section v-show="activeTab === 'site'" class="admin-section">
            <div class="table-toolbar">
              <div>
                <span class="card-kicker">Site settings</span>
                <h2>站点设置 <small>内容宽度与登录方式</small></h2>
              </div>
              <el-button size="small" :loading="settingsLoading" @click="loadSiteSettings">重新读取</el-button>
            </div>

            <section class="surface site-width" v-loading="settingsLoading">
              <header class="panel-head">
                <div>
                  <span class="card-kicker">Layout</span>
                  <h2>内容宽度 <small>页面内容区最大宽度</small></h2>
                </div>
                <strong class="site-width__value">{{ siteWidth }}<span>px</span></strong>
              </header>
              <p class="auth-callback__hint">
                全站页面（首页、模板中心、工作台、预览页、个人中心与本页）都按这个宽度排版：
                窗口更宽时内容不再继续拉伸，窗口变窄时按屏幕自适应，不会出现横向滚动。默认 1200px。
              </p>
              <div class="site-width__control">
                <el-slider
                  v-model="siteWidth"
                  :min="siteConfig?.minWidth || 1100"
                  :max="siteConfig?.maxWidth || 1600"
                  :step="20"
                  :disabled="widthSaving"
                />
                <el-input-number
                  v-model="siteWidth"
                  :min="siteConfig?.minWidth || 1100"
                  :max="siteConfig?.maxWidth || 1600"
                  :step="20"
                  controls-position="right"
                  size="small"
                  :disabled="widthSaving"
                />
              </div>
              <div class="site-width__demo">
                <div class="site-width__demo-frame">
                  <div class="site-width__demo-content" :style="widthDemoStyle">
                    <span>{{ siteWidth }}</span>
                  </div>
                </div>
                <p class="site-width__demo-caption">
                  1600px 窗口下的示意：内容区两侧各留 {{ widthGutter }}px
                </p>
              </div>
              <div class="channel-actions">
                <el-button type="primary" size="small" :loading="widthSaving" @click="saveContentWidth">
                  保存宽度
                </el-button>
                <el-button
                  size="small"
                  text
                  :disabled="widthSaving"
                  @click="siteWidth = siteConfig?.defaultWidth || 1200"
                >
                  恢复默认
                </el-button>
              </div>
            </section>

            <div class="admin-subhead">
              <span class="card-kicker">Authentication</span>
              <h3>认证设置</h3>
            </div>

            <div class="surface auth-panel" v-loading="settingsLoading">
              <p v-if="authAllClosed" class="auth-alert">
                当前没有任何自助注册入口，新用户完全无法开号。需要开号时临时打开「用户名注册」，注册完再关掉。
              </p>

              <div class="auth-row">
                <div class="auth-row__main">
                  <strong>用户名注册</strong>
                  <p>传统用户名 + 密码注册，脚本可以批量开号，公开部署建议保持关闭。</p>
                </div>
                <el-switch v-model="authForm.usernameRegister" :disabled="settingsSaving" @change="saveAuthSettings" />
              </div>

              <div class="auth-row">
                <div class="auth-row__main">
                  <strong>邮箱注册</strong>
                  <p>先发送 6 位验证码，验证通过才建号；一封邮件的成本足以挡住批量注册。同一通道还用于登录页的自助找回密码，注册关掉也不影响用户重置密码。</p>
                  <span class="auth-dep" :class="authSettings?.mailConfigured ? 'is-ok' : 'is-no'">
                    <i></i>
                    {{ authSettings?.mailConfigured
                      ? 'SMTP 通道已就绪'
                      : 'SMTP 未配置：在下方「通道配置」填主机、发件地址与授权码即可' }}
                  </span>
                  <span v-if="authForm.emailRegister && !authSettings?.mailConfigured" class="auth-dep is-no">
                    <i></i>通道未就绪，登录页暂时不会出现该入口
                  </span>
                </div>
                <el-switch v-model="authForm.emailRegister" :disabled="settingsSaving" @change="saveAuthSettings" />
              </div>

              <div class="auth-row">
                <div class="auth-row__main">
                  <strong>GitHub 登录</strong>
                  <p>首次登录自动创建账号；GitHub 返回的已验证邮箱与站内已验证邮箱一致时，自动关联到原账号。</p>
                  <span class="auth-dep" :class="authSettings?.githubConfigured ? 'is-ok' : 'is-no'">
                    <i></i>
                    {{ authSettings?.githubConfigured
                      ? 'GitHub 应用凭据已就绪'
                      : '未配置：在下方「通道配置」填 Client ID / Client Secret / 站点对外地址' }}
                  </span>
                  <span v-if="authForm.githubLogin && !authSettings?.githubConfigured" class="auth-dep is-no">
                    <i></i>通道未就绪，登录页暂时不会出现该入口
                  </span>
                </div>
                <el-switch v-model="authForm.githubLogin" :disabled="settingsSaving" @change="saveAuthSettings" />
              </div>
            </div>

            <section class="surface auth-channels">
              <header class="panel-head">
                <div>
                  <span class="card-kicker">Channels</span>
                  <h2>通道配置 <small>保存后立即生效，不必重启</small></h2>
                </div>
              </header>
              <p class="auth-callback__hint">
                这里填的值存在站点设置里，优先于对应的环境变量；某一项清空后，才重新回落到环境变量。
                授权码与 Client Secret 只写不读，保存后界面上只会显示「已保存」。
              </p>

              <div class="channel-grid">
                <div class="channel-card">
                  <div class="channel-card__head">
                    <strong>邮件通道 SMTP</strong>
                    <span class="auth-dep" :class="authSettings?.mail?.fromAdmin ? 'is-ok' : 'is-muted'">
                      <i></i>{{ authSettings?.mail?.fromAdmin ? '当前由后台配置' : '当前值来自环境变量' }}
                    </span>
                  </div>
                  <div class="channel-fields">
                    <label>
                      <span>SMTP 主机</span>
                      <el-input v-model="mailForm.host" placeholder="smtp.qq.com" />
                    </label>
                    <label>
                      <span>端口</span>
                      <el-input v-model="mailForm.port" placeholder="465" />
                    </label>
                    <label class="is-wide">
                      <span>登录名</span>
                      <el-input v-model="mailForm.username" placeholder="留空则使用发件地址" />
                    </label>
                    <label class="is-wide">
                      <span>授权码</span>
                      <el-input
                        v-model="mailForm.password"
                        type="password"
                        show-password
                        :placeholder="authSettings?.mail?.passwordSet
                          ? '已保存，留空则不修改'
                          : '邮箱授权码，不是邮箱登录密码'"
                      />
                    </label>
                    <label>
                      <span>发件地址</span>
                      <el-input v-model="mailForm.from" placeholder="you@qq.com" />
                    </label>
                    <label>
                      <span>发件署名</span>
                      <el-input v-model="mailForm.fromName" placeholder="极简简历" />
                    </label>
                    <label class="is-switch">
                      <span>隐式 SSL（465 端口）</span>
                      <el-switch v-model="mailForm.ssl" />
                    </label>
                    <label class="is-switch">
                      <span>STARTTLS（587 端口）</span>
                      <el-switch v-model="mailForm.starttls" />
                    </label>
                  </div>
                  <div class="channel-actions">
                    <el-button
                      type="primary"
                      size="small"
                      :loading="channelSaving === 'mail'"
                      @click="saveMailChannel"
                    >
                      保存邮件通道
                    </el-button>
                    <el-button size="small" text :disabled="Boolean(channelSaving)" @click="clearMailChannel">
                      清空并回落到环境变量
                    </el-button>
                  </div>
                </div>

                <div class="channel-card">
                  <div class="channel-card__head">
                    <strong>GitHub OAuth</strong>
                    <span class="auth-dep" :class="authSettings?.github?.fromAdmin ? 'is-ok' : 'is-muted'">
                      <i></i>{{ authSettings?.github?.fromAdmin ? '当前由后台配置' : '当前值来自环境变量' }}
                    </span>
                  </div>
                  <div class="channel-fields">
                    <label class="is-wide">
                      <span>Client ID</span>
                      <el-input v-model="githubForm.clientId" placeholder="Iv1.xxxxxxxxxxxxxxxx" />
                    </label>
                    <label class="is-wide">
                      <span>Client Secret</span>
                      <el-input
                        v-model="githubForm.clientSecret"
                        type="password"
                        show-password
                        :placeholder="authSettings?.github?.secretSet
                          ? '已保存，留空则不修改'
                          : '在 OAuth App 里 Generate new client secret 后填入'"
                      />
                    </label>
                    <label class="is-wide">
                      <span>站点对外地址</span>
                      <el-input v-model="githubForm.publicBaseUrl" placeholder="https://who.example.com" />
                    </label>
                  </div>
                  <p class="channel-note">
                    站点对外地址决定回调地址与登录完成后的跳转目标，必须是浏览器实际访问的那个域名
                    （含 https://，末尾斜杠可省）。
                  </p>
                  <div class="channel-actions">
                    <el-button
                      type="primary"
                      size="small"
                      :loading="channelSaving === 'github'"
                      @click="saveGithubChannel"
                    >
                      保存 GitHub 通道
                    </el-button>
                    <el-button size="small" text :disabled="Boolean(channelSaving)" @click="clearGithubChannel">
                      清空并回落到环境变量
                    </el-button>
                  </div>
                </div>
              </div>
            </section>

            <section class="surface auth-callback">
              <header class="panel-head">
                <div>
                  <span class="card-kicker">OAuth callback</span>
                  <h2>GitHub OAuth 应用</h2>
                </div>
              </header>
              <p class="auth-callback__hint">
                在 GitHub → Settings → Developer settings → OAuth Apps → New OAuth App 建应用，
                Authorization callback URL 填下面这个地址，Homepage URL 填站点首页；拿到 Client ID 与
                Client secret 后填到上方「通道配置」保存即可，不需要重启后端，再打开开关。
              </p>
              <div class="auth-field">
                <span>回调地址</span>
                <code>{{ authSettings?.githubCallbackUrl || '未配置：先在上方「通道配置」填写站点对外地址' }}</code>
                <el-button
                  v-if="authSettings?.githubCallbackUrl"
                  size="small"
                  text
                  type="primary"
                  @click="copyText(authSettings.githubCallbackUrl)"
                >
                  复制
                </el-button>
              </div>
              <div class="auth-field">
                <span>站点公网地址</span>
                <code>{{ authSettings?.publicBaseUrl || '未配置' }}</code>
              </div>
              <p class="auth-callback__note">
                开关保存后立即写库，登录页只读「开关 + 通道就绪」的交集：只配凭据不打开关，或只开关不配凭据，都不会出现入口。
              </p>
            </section>
          </section>
        </template>
      </div>
    </div>

    <el-dialog v-model="metaDialog.visible" title="修改模板基础信息" width="min(560px, 92vw)">
      <p class="meta-dialog-hint">
        名称、描述、分类与标签保存后立即在模板中心生效；作者重新导入模板时会整体还原为包里的信息。
      </p>
      <el-form label-width="64px" @submit.prevent>
        <el-form-item label="名称" required>
          <el-input v-model="metaDialog.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="metaDialog.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="metaDialog.category" maxlength="30" placeholder="如：简约 / 双栏 / 国企" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="metaDialog.tags" placeholder="用逗号分隔，最多 8 个、每个不超过 20 字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metaDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="metaDialog.saving" @click="saveMeta">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="replyDialog" :title="replyTarget?.status === 1 ? '查看 / 修改回复' : '回复反馈'" width="min(520px, 92vw)">
      <div v-if="replyTarget" class="reply-context">
        <span>{{ formatDateTime(replyTarget.createdAt) }} · {{ replyTarget.contact || '未留联系方式' }}</span>
        <p>{{ replyTarget.content }}</p>
      </div>
      <el-input
        v-model="replyText"
        type="textarea"
        :rows="5"
        maxlength="2000"
        show-word-limit
        placeholder="回复会与该条反馈一起保存，便于后续跟进；当前版本用户侧暂不展示回复内容。"
      />
      <template #footer>
        <el-button @click="replyDialog = false">取消</el-button>
        <el-button type="primary" :loading="replySaving" @click="submitReply">保存回复</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="userResumesDialog"
      width="min(1120px, 94vw)"
      :fullscreen="userResumesMaxed"
      align-center
      append-to-body
      :class="['user-resumes-dialog', { 'is-max': userResumesMaxed }]"
    >
      <template #header>
        <div class="user-resumes-dialog__head">
          <span class="table-avatar">
            {{ (userResumesTarget?.nickname || userResumesTarget?.username || '?').slice(0, 1) }}
          </span>
          <div class="user-resumes-dialog__title">
            <span class="mono">USER RESUMES</span>
            <h2>{{ userResumesTarget?.nickname || userResumesTarget?.username }}的简历</h2>
            <p>
              {{ userResumesTarget?.username }}<template v-if="userResumesTarget?.email"> · {{ userResumesTarget.email }}</template>
            </p>
          </div>
          <span v-if="!userResumesLoading" class="user-resumes-dialog__count">{{ userResumes.length }} 份</span>
        </div>
        <!-- 放在右上角、紧挨 Element Plus 自带的关闭按钮：窗口控件该在这一簇里 -->
        <button
          type="button"
          class="user-resumes-dialog__max"
          :title="userResumesMaxed ? '还原窗口' : '放大查看'"
          :aria-label="userResumesMaxed ? '还原窗口' : '放大查看'"
          @click="userResumesMaxed = !userResumesMaxed"
        >
          <el-icon><ScaleToOriginal v-if="userResumesMaxed" /><FullScreen v-else /></el-icon>
        </button>
      </template>

      <div class="user-resumes">
        <div v-loading="userResumesLoading" class="user-resumes__list">
          <ul v-if="userResumes.length">
            <li v-for="resume in userResumes" :key="resume.id">
              <button
                type="button"
                class="user-resume-card"
                :class="{ 'is-active': resume.id === userResumeId }"
                @click="selectUserResume(resume)"
              >
                <span class="user-resume-card__icon"><el-icon><Document /></el-icon></span>
                <span class="user-resume-card__body">
                  <strong>{{ resume.title || '未命名简历' }}</strong>
                  <span>{{ resume.templateName || resume.templateCode }}</span>
                  <span>更新于 {{ formatDateTime(resume.updatedAt) }}</span>
                </span>
                <span class="user-resume-card__meta">
                  <b>{{ resume.completeness ?? 0 }}%</b>
                  <small>完整度</small>
                </span>
              </button>
            </li>
          </ul>
          <div v-else-if="!userResumesLoading" class="table-empty">该用户还没有创建简历</div>
        </div>

        <div class="user-resumes__preview">
          <div class="user-resumes__preview-head">
            <span class="mono">A4 PREVIEW</span>
            <div class="user-resumes__preview-tools">
              <span v-if="userResumeDetail" class="user-resumes__preview-meta">共 {{ userResumePages }} 页</span>
              <div class="fit-switch" role="group" aria-label="预览缩放方式">
                <button
                  type="button"
                  :class="{ 'is-active': previewFit === 'width' }"
                  @click="previewFit = 'width'"
                >适宽</button>
                <button
                  type="button"
                  :class="{ 'is-active': previewFit === 'page' }"
                  @click="previewFit = 'page'"
                >整页</button>
              </div>
            </div>
          </div>
          <div ref="previewStageRef" v-loading="userResumeLoading" class="user-resume-stage">
            <div v-if="userResumeError" class="user-resume-stage__state user-resume-stage__state--error">
              <p>{{ userResumeError }}</p>
              <el-button plain size="small" @click="selectUserResume({ id: userResumeId })">重新加载</el-button>
            </div>
            <div v-else-if="!userResumeDetail" class="user-resume-stage__state">
              <el-icon><Document /></el-icon>
              <p>{{ userResumesLoading ? '正在加载简历…' : userResumes.length ? '正在载入预览…' : '该用户还没有创建简历' }}</p>
            </div>
            <template v-else>
              <p v-if="previewTemplateMissing" class="user-resume-stage__notice">
                模板「{{ userResumeDetail.templateCode }}」已下架或不存在，纸面按默认版式渲染。
              </p>
              <PaperFrame
                :content="userResumeDetail.content"
                :template="previewTemplate"
                :style-data="previewStyle"
                :avatar="userResumeDetail.avatar"
                :padding="0"
                :fit-mode="previewFit"
                :fit-height="previewStageHeight"
                @pages="(count) => (userResumePages = count)"
              />
            </template>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="user-resumes-dialog__foot">
          <router-link v-if="userResumeDetail" class="user-resumes-dialog__full" :to="previewTargetOf(userResumeDetail)">
            在新页面打开
            <el-icon><ArrowRight /></el-icon>
          </router-link>
          <el-button @click="userResumesDialog = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<style scoped>
/* 注销中的账号在用户表里要一眼看出来，和「已冻结」同尺寸但用橙色区分。 */
.frozen-tag.is-deletion {
  border-color: rgba(196, 122, 42, 0.35);
  background: rgba(196, 122, 42, 0.1);
  color: #a3611c;
}

.recycle-note {
  margin: 12px 2px 0;
  color: #8a94a0;
  font-size: 12.5px;
  line-height: 1.6;
}

.audit-policy {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  padding: 16px 18px;
}

.audit-policy strong {
  display: block;
  margin-bottom: 4px;
}

.audit-policy p {
  margin: 0;
  color: #7d8794;
  font-size: 12.5px;
}

.audit-policy__control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.audit-filters .el-select {
  width: 190px;
}


/* 注销中的账号在用户表里要一眼看出来，和「已冻结」同尺寸但用橙色区分。 */
.frozen-tag.is-deletion {
  border-color: rgba(196, 122, 42, 0.35);
  background: rgba(196, 122, 42, 0.1);
  color: #a3611c;
}

.recycle-note {
  margin: 12px 2px 0;
  color: #8a94a0;
  font-size: 12.5px;
  line-height: 1.6;
}

.audit-policy {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  padding: 16px 18px;
}

.audit-policy strong {
  display: block;
  margin-bottom: 4px;
}

.audit-policy p {
  margin: 0;
  color: #7d8794;
  font-size: 12.5px;
}

.audit-policy__control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.audit-filters .el-select {
  width: 190px;
}


/* 注销中的账号在用户表里要一眼看出来，和「已冻结」同尺寸但用橙色区分。 */
.frozen-tag.is-deletion {
  border-color: rgba(196, 122, 42, 0.35);
  background: rgba(196, 122, 42, 0.1);
  color: #a3611c;
}

.recycle-note {
  margin: 12px 2px 0;
  color: #8a94a0;
  font-size: 12.5px;
  line-height: 1.6;
}

.audit-policy {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  padding: 16px 18px;
}

.audit-policy strong {
  display: block;
  margin-bottom: 4px;
}

.audit-policy p {
  margin: 0;
  color: #7d8794;
  font-size: 12.5px;
}

.audit-policy__control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.audit-filters .el-select {
  width: 190px;
}


.admin-page {
  padding: 34px 0 72px;
  background:
    radial-gradient(900px 420px at 10% -10%, rgba(10, 125, 118, 0.06), transparent 62%),
    transparent;
}

.admin-shell {
  width: min(var(--content-width), calc(100% - 56px));
  margin: 0 auto;
}

.admin-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding: 4px 2px 30px;
}

.admin-hero h1 {
  margin-top: 12px;
  font-size: clamp(34px, 4vw, 46px);
}

.admin-hero p {
  max-width: 560px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.85;
}

.admin-hero__actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.admin-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 24px;
  padding: 5px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 254, 251, 0.7);
  width: fit-content;
  max-width: 100%;
  overflow-x: auto;
}

.admin-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.2s ease;
}

.admin-tab:hover {
  color: var(--ink);
  background: var(--paper-deep);
}

.admin-tab.is-active {
  color: #f7faf7;
  background: var(--accent);
  box-shadow: 0 6px 16px -10px rgba(35, 84, 72, 0.8);
}

.tab-count {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(25, 34, 30, 0.08);
  font-family: var(--mono);
  font-size: 9px;
}

.admin-tab.is-active .tab-count {
  background: rgba(255, 255, 255, 0.2);
}

.admin-skeleton {
  padding: 30px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--paper);
}

.admin-section {
  animation: admin-fade 0.3s ease;
}

@keyframes admin-fade {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: none; }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 22px;
}

.stat-card {
  padding: 18px 18px 16px;
}

.stat-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--muted);
  font-size: 11px;
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
}

.stat-value {
  display: block;
  margin-top: 10px;
  font-family: var(--serif);
  font-size: 30px;
  font-weight: 600;
  line-height: 1.1;
}

.stat-delta {
  margin-top: 8px;
  color: var(--faint);
  font-size: 10.5px;
}

.stat-delta span {
  color: var(--accent);
  font-weight: 600;
}

.sample-card {
  margin-bottom: 22px;
  padding: 20px 24px 22px;
}

.sample-card__head {
  margin-bottom: 4px;
}

.sample-card__head h2 {
  font-size: 15px;
}

.sample-card__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sample-card__clear {
  padding: 4px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--danger);
  font-size: 11px;
  cursor: pointer;
}

.sample-card__clear:hover {
  border-color: #e0c0ba;
  background: #f8edeb;
}

.sample-card__hint {
  color: var(--faint);
  font-size: 11px;
  line-height: 1.7;
}

.sample-card__row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 14px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.sample-card__row strong {
  font-family: var(--serif);
  font-size: 14px;
}

.sample-card__row span {
  color: var(--muted);
  font-size: 11px;
}

.sample-card__empty {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 11px;
  line-height: 1.75;
}

.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 22px;
}

.overview-card {
  padding: 26px 28px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.card-kicker {
  color: var(--accent);
  font-family: var(--mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.panel-head h2,
.table-toolbar h2 {
  margin-top: 4px;
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 600;
}

.table-toolbar h2 small {
  margin-left: 6px;
  color: var(--faint);
  font-family: var(--sans);
  font-size: 11px;
  font-weight: 400;
}

.text-link {
  padding: 4px 0;
  border: none;
  background: none;
  color: var(--accent);
  font-size: 11px;
  cursor: pointer;
}

.text-link:hover {
  color: var(--accent-dark);
}

.recent-users {
  margin: 0;
  padding: 0;
  list-style: none;
}

.recent-users li {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 0;
  border-top: 1px solid var(--line);
}

.recent-users li:first-child {
  border-top: none;
}

.recent-avatar,
.table-avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--paper-deep);
  color: var(--ink-soft);
  font-family: var(--serif);
  font-size: 13px;
}

.recent-users li > div {
  flex: 1;
  min-width: 0;
}

.recent-users strong {
  display: block;
  font-size: 12px;
  font-weight: 600;
}

.recent-users li > div span {
  color: var(--faint);
  font-size: 10px;
}

.recent-empty,
.table-empty {
  padding: 24px 0;
  color: var(--faint);
  font-size: 12px;
  text-align: center;
}

.role-tag {
  display: inline-flex;
  padding: 1px 7px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  background: var(--paper);
  font-size: 9.5px;
  white-space: nowrap;
}

.role-tag.is-admin {
  border-color: rgba(10, 125, 118, 0.2);
  color: var(--accent);
  background: var(--accent-soft);
}

.pending-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.pending-list li {
  padding: 12px 0;
  border-top: 1px solid var(--line);
}

.pending-list li:first-child {
  border-top: none;
}

.pending-list p {
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.75;
}

.pending-list li > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 7px;
}

.pending-list li > div span {
  color: var(--faint);
  font-size: 10px;
}

.mini-action {
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--accent);
  font-size: 10px;
  cursor: pointer;
}

.mini-action:hover {
  background: var(--accent-soft);
}

.pending-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 30px 0 20px;
  color: var(--faint);
  font-size: 12px;
}

.pending-empty .el-icon {
  color: var(--accent);
  font-size: 26px;
}

.system-card {
  margin-top: 22px;
  padding: 20px 22px 22px;
}

.system-status {
  padding: 3px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 11px;
  white-space: nowrap;
}

.system-status.is-up {
  color: var(--success);
}

.system-status.is-warn {
  color: var(--accent);
}

.system-status.is-down {
  color: var(--danger);
  border-color: var(--danger);
}

.system-checks {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.system-checks li {
  display: grid;
  grid-template-columns: 16px 72px 1fr;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 11.5px;
}

.system-checks strong {
  color: var(--ink-soft);
  font-weight: 500;
}

.system-checks .is-ok {
  color: var(--success);
}

.system-checks .is-bad {
  color: var(--danger);
}

.system-numbers {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px 20px;
  margin: 18px 0 0;
  padding-top: 15px;
  border-top: 1px dashed var(--line);
}

.system-numbers dt {
  color: var(--faint);
  font-size: 10.5px;
}

.system-numbers dd {
  margin: 4px 0 0;
  color: var(--ink);
  font-family: var(--mono);
  font-size: 11.5px;
  word-break: break-all;
}

.system-orphan {
  margin: 14px 0 0;
  color: var(--danger);
  font-size: 10.5px;
  line-height: 1.7;
}

.admin-note {
  display: flex;
  gap: 12px;
  margin-top: 22px;
  padding: 18px 22px;
  color: var(--muted);
}

.admin-note > .el-icon {
  margin-top: 2px;
  color: var(--accent);
  font-size: 17px;
}

.admin-note strong {
  color: var(--ink-soft);
  font-size: 12px;
}

.admin-note p {
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.75;
}

.table-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.user-search {
  width: 280px;
}

.toolbar-hint {
  color: var(--faint);
  font-size: 11px;
}

.table-card {
  padding: 8px 14px;
  overflow: hidden;
}

.table-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-user strong {
  display: block;
  font-size: 12px;
  font-weight: 600;
}

.table-user__handle {
  color: var(--faint);
  font-family: var(--mono);
  font-size: 10px;
}

.table-avatar {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 10%, var(--paper));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 16%, transparent);
  color: var(--accent-dark);
  font-size: 14px;
  font-weight: 600;
}

.table-avatar__online {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 10px;
  height: 10px;
  border: 2px solid var(--paper);
  border-radius: 50%;
  background: var(--success);
}

.resume-count {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  color: var(--ink-soft);
  font-family: var(--mono);
}

.resume-count b {
  font-size: 13px;
  font-weight: 600;
}

.resume-count small {
  color: var(--faint);
  font-size: 9.5px;
}

.resume-count--link {
  padding: 3px 9px;
  border: 0;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font: inherit;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.resume-count--link b {
  color: inherit;
}

.resume-count--link:hover {
  background: var(--accent);
  color: #fff;
}

.resume-count--link:hover small {
  color: rgba(255, 255, 255, 0.72);
}

.resume-count.is-empty {
  color: var(--faint);
}

:global(.user-resumes-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: 16px;
}

/* 放大后要真的贴到视口边缘：上一条的 max-width 会留出 32px 白边 */
:global(.user-resumes-dialog.is-max) {
  max-width: 100vw;
  border-radius: 0;
}

/* 放大后要真的贴到视口边缘：上一条的 max-width 会留出 32px 白边 */
:global(.user-resumes-dialog.is-max) {
  max-width: 100vw;
  border-radius: 0;
}

/* 右上角并排两个控件（放大 + 关闭），标题与份数要给它们让出位置 */
:global(.user-resumes-dialog .el-dialog__header) {
  padding-right: 96px;
}

/* 右上角并排两个控件（放大 + 关闭），标题与份数要给它们让出位置 */
:global(.user-resumes-dialog .el-dialog__header) {
  padding-right: 96px;
}

.user-resumes-dialog__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-resumes-dialog__title {
  flex: 1;
  min-width: 0;
}

.user-resumes-dialog__title .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.user-resumes-dialog__title h2 {
  margin-top: 4px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 600;
}

.user-resumes-dialog__title p {
  margin-top: 3px;
  overflow-wrap: anywhere;
  color: var(--faint);
  font-size: 10.5px;
}

.user-resumes-dialog__count {
  flex: none;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
}

.user-resumes-dialog__max {
  position: absolute;
  top: 6px;
  right: 42px;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-color-info, #909399);
  font-size: 16px;
  cursor: pointer;
  transition: color 0.16s ease, background 0.16s ease;
}

.user-resumes-dialog__max:hover {
  background: var(--paper-deep);
  color: var(--accent);
}

.user-resumes {
  display: grid;
  grid-template-columns: 296px minmax(0, 1fr);
  gap: 16px;
  height: min(72vh, 700px);
}

.user-resumes__list {
  height: 100%;
  padding-right: 2px;
  overflow-y: auto;
}

.user-resumes__list ul {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.user-resume-card {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--paper);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.user-resume-card:hover {
  border-color: rgba(10, 125, 118, 0.4);
  background: rgba(10, 125, 118, 0.045);
}

.user-resume-card.is-active {
  border-color: var(--accent);
  background: var(--accent-soft);
  box-shadow: inset 3px 0 0 var(--accent);
}

.user-resume-card__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 10px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 16px;
}

.user-resume-card.is-active .user-resume-card__icon {
  background: #fff;
}

.user-resume-card__body {
  flex: 1;
  min-width: 0;
}

.user-resume-card__body strong {
  display: block;
  overflow: hidden;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-resume-card__body span {
  display: block;
  margin-top: 1px;
  overflow: hidden;
  color: var(--faint);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-resume-card__meta {
  flex: none;
  text-align: right;
}

.user-resume-card__meta b {
  display: block;
  color: var(--ink-soft);
  font-family: var(--mono);
  font-size: 12px;
}

.user-resume-card__meta small {
  color: var(--faint);
  font-size: 9px;
}

.user-resumes__preview {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background:
    radial-gradient(600px 320px at 50% -10%, rgba(10, 125, 118, 0.07), transparent 70%),
    linear-gradient(180deg, var(--canvas-deep), #e7e1d4);
}

.user-resumes__preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.55);
  color: var(--faint);
  font-size: 9.5px;
}

.user-resumes__preview-head .mono {
  letter-spacing: 0.16em;
}

.user-resumes__preview-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-resumes__preview-meta {
  color: var(--faint);
  font-size: 9.5px;
}

.fit-switch {
  display: inline-flex;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.fit-switch button {
  padding: 2px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  font-family: var(--sans);
  font-size: 10.5px;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}

.fit-switch button:hover {
  color: var(--ink);
}

.fit-switch button.is-active {
  background: var(--accent);
  color: #fff;
}

.user-resume-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  padding: 18px;
  overflow-y: auto;
}

.user-resume-stage :deep(.paper-scaler__paper) {
  box-shadow: 0 14px 34px -18px rgba(25, 34, 30, 0.42);
}

.user-resume-stage__state {
  display: grid;
  place-items: center;
  gap: 8px;
  height: 100%;
  min-height: 160px;
  padding: 24px;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}

.user-resume-stage__state .el-icon {
  color: var(--faint);
  font-size: 22px;
}

.user-resume-stage__state--error {
  color: var(--danger);
}

.user-resume-stage__notice {
  margin-bottom: 10px;
  padding: 8px 10px;
  border: 1px solid rgba(181, 72, 63, 0.2);
  border-radius: 8px;
  background: #fbeeec;
  color: var(--danger);
  font-size: 11px;
  line-height: 1.6;
}

.user-resumes-dialog__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.user-resumes-dialog__full {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--muted);
  font-size: 11px;
}

.user-resumes-dialog__full:hover {
  color: var(--accent);
}

/* 放大到整屏：列表略宽一点，预览区吃掉剩下所有高度，「整页」视图才有意义 */
.user-resumes-dialog.is-max .user-resumes {
  grid-template-columns: 320px minmax(0, 1fr);
  height: calc(100vh - 210px);
}

@media (max-width: 900px) {
  .user-resumes,
  .user-resumes-dialog.is-max .user-resumes {
    grid-template-columns: 1fr;
    height: auto;
  }

  .user-resumes__list {
    max-height: 190px;
  }

  .user-resumes__preview {
    height: min(56vh, 460px);
  }
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
}

.row-actions :deep(.el-button) {
  margin-left: 0;
  padding: 0 2px;
}

.row-actions__sep {
  width: 1px;
  height: 10px;
  margin: 0 4px;
  background: var(--line-strong);
}

.cell-muted {
  color: var(--muted);
  font-size: 11px;
}

.cell-mono {
  font-family: var(--mono);
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10.5px;
  white-space: nowrap;
}

.status-tag i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.status-tag.is-on {
  color: var(--success);
}

.status-tag.is-off {
  color: var(--faint);
}

.status-tag.is-warn {
  color: #a17c3d;
}

.user-state {
  display: flex;
  align-items: center;
  gap: 6px;
}

.frozen-tag {
  color: #a17c3d;
  font-size: 10px;
  white-space: nowrap;
}

/* 用户表：整张卡片就是一块列表，表头、行和分页各占一层。 */
.table-card.user-table-card {
  padding: 0;
  overflow: hidden;
}

.user-table-card :deep(.el-table) {
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #f7f3ea;
  --el-table-row-hover-bg-color: color-mix(in srgb, var(--accent) 5%, var(--paper));
  --el-table-text-color: var(--ink-soft);
  font-size: 12px;
}

.user-table-card :deep(.el-table th.el-table__cell) {
  height: 44px;
  padding: 0;
  border-bottom: 1px solid var(--line);
  background: #f7f3ea;
  color: var(--faint);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.user-table-card :deep(.el-table td.el-table__cell) {
  height: 62px;
  padding: 0;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 72%, transparent);
}

.user-table-card :deep(.el-table__body tr:last-child td.el-table__cell) {
  border-bottom: none;
}

.user-table-card :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.user-table-card :deep(.el-table .cell) {
  padding: 0 14px;
}

.user-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 18px;
  border-top: 1px solid var(--line);
  background: color-mix(in srgb, var(--paper-deep) 48%, var(--paper));
}

.user-pager__summary {
  color: var(--faint);
  font-size: 11px;
  white-space: nowrap;
}

.user-pager :deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-button-color: var(--muted);
  --el-pagination-hover-color: var(--accent);
}

.user-pager :deep(.el-pagination.is-background .el-pager li),
.user-pager :deep(.el-pagination.is-background .btn-prev),
.user-pager :deep(.el-pagination.is-background .btn-next) {
  border-radius: 7px;
  background: var(--paper-deep);
  color: var(--muted);
}

.user-pager :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: var(--accent);
  color: #fff;
}

.template-admin-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.template-admin-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px;
}

.template-admin-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.template-admin-card header strong {
  display: block;
  font-family: var(--serif);
  font-size: 17px;
  font-weight: 600;
}

.mono-code {
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9.5px;
}

.template-admin-card > p {
  flex: 1;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.75;
}

.meta-dialog-hint {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.75;
}

.template-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.template-admin-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  color: var(--faint);
  font-size: 10px;
}

.feedback-loading {
  padding: 24px;
}

.feedback-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 54px 20px;
  color: var(--faint);
  font-size: 12px;
}

.feedback-empty .el-icon {
  color: var(--accent);
  font-size: 28px;
}

.feedback-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.feedback-item {
  padding: 20px 24px;
}

.feedback-item header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.feedback-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  color: var(--faint);
  font-size: 10.5px;
}

.feedback-content {
  margin-top: 12px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.85;
  white-space: pre-wrap;
}

.feedback-reply {
  margin-top: 12px;
  padding: 10px 14px;
  border-left: 2px solid var(--accent);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--accent-soft);
}

.feedback-reply span {
  color: var(--accent);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.feedback-reply p {
  margin-top: 3px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.reply-context {
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--paper-deep);
}

.reply-context span {
  color: var(--faint);
  font-size: 10px;
}

.reply-context p {
  margin-top: 5px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.cell-email {
  display: flex;
  align-items: center;
  gap: 7px;
}

.verify-tag {
  padding: 1px 6px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 9px;
  white-space: nowrap;
}

.verify-tag.is-ok {
  border-color: rgba(10, 125, 118, 0.2);
  background: var(--accent-soft);
  color: var(--accent);
}

.verify-tag.is-no {
  color: var(--faint);
}

.auth-panel {
  margin-bottom: 22px;
  padding: 4px 24px;
}

.site-width {
  margin-bottom: 26px;
  padding: 24px 28px;
}

.site-width__value {
  color: var(--ink);
  font-family: var(--mono);
  font-size: 22px;
  font-weight: 500;
}

.site-width__value span {
  margin-left: 3px;
  color: var(--faint);
  font-size: 11px;
}

.site-width__control {
  display: flex;
  align-items: center;
  gap: 22px;
  margin: 4px 0 2px;
}

.site-width__control .el-slider {
  flex: 1;
}

.site-width__demo {
  margin-top: 10px;
}

.site-width__demo-frame {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 74px;
  padding: 0 14px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--paper-deep);
}

.site-width__demo-content {
  display: grid;
  place-items: center;
  height: 42px;
  border-radius: 4px;
  background: var(--accent-soft);
  color: var(--accent-dark);
  font-family: var(--mono);
  font-size: 10.5px;
  transition: width 0.2s ease;
}

.site-width__demo-caption {
  margin-top: 8px;
  color: var(--faint);
  font-size: 10.5px;
}

.site-width .channel-actions {
  margin-top: 16px;
}

.admin-subhead {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.admin-subhead h3 {
  font-family: var(--serif);
  font-size: 16px;
  font-weight: 600;
}

.auth-alert {
  margin: 16px 0 2px;
  padding: 12px 14px;
  border: 1px solid rgba(161, 124, 61, 0.28);
  border-radius: var(--radius-sm);
  background: #fbf5e9;
  color: #8a6a33;
  font-size: 11.5px;
  line-height: 1.75;
}

.auth-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 0;
  border-top: 1px solid var(--line);
}

.auth-row:first-of-type {
  border-top: none;
}

.auth-row__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.auth-row__main strong {
  font-size: 13px;
}

.auth-row__main p {
  max-width: 660px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.8;
}

.auth-dep {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 7px;
  font-size: 10.5px;
  white-space: normal;
}

.auth-dep i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.auth-dep.is-ok {
  color: var(--success);
}

.auth-dep.is-no {
  color: #a17c3d;
}

.auth-dep.is-muted {
  color: var(--faint);
}

.auth-channels {
  margin-bottom: 22px;
  padding: 24px 28px;
}

.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 18px;
  margin-top: 18px;
}

.channel-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 20px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--paper-deep);
}

.channel-card__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.channel-card__head strong {
  font-size: 13px;
}

.channel-card__head .auth-dep {
  margin-top: 0;
}

.channel-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
}

.channel-fields label {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.channel-fields label.is-wide {
  grid-column: 1 / -1;
}

.channel-fields label > span {
  color: var(--faint);
  font-size: 10.5px;
  letter-spacing: 0.04em;
}

.channel-fields label.is-switch {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.channel-note {
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.8;
}

.channel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
}

@media (max-width: 720px) {
  .channel-fields {
    grid-template-columns: minmax(0, 1fr);
  }
}

.auth-callback {
  padding: 24px 28px;
}

.auth-callback__hint {
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.85;
}

.auth-field {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--paper-deep);
}

.auth-field span {
  flex-shrink: 0;
  color: var(--faint);
  font-size: 10.5px;
  letter-spacing: 0.04em;
}

.auth-field code {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--ink-soft);
  font-family: var(--mono);
  font-size: 11px;
}

.auth-callback__note {
  margin-top: 14px;
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.8;
}

@media (max-width: 1080px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .template-admin-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .admin-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 18px;
  }
}

@media (max-width: 700px) {
  .admin-page {
    padding: 20px 0 48px;
  }

  .admin-shell {
    width: calc(100% - 28px);
  }

  .admin-hero__actions {
    width: 100%;
  }

  .admin-hero__actions .el-button {
    flex: 1;
  }

  .admin-tabs {
    width: 100%;
    border-radius: var(--radius);
  }

  .admin-tab {
    padding: 8px 11px;
  }

  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-card {
    padding: 14px;
  }

  .stat-value {
    font-size: 25px;
  }

  .overview-card {
    padding: 20px 18px;
  }

  .table-toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .user-search {
    width: 100%;
  }

  .table-card {
    overflow-x: auto;
  }

  .user-table-card {
    overflow: hidden;
  }

  .user-pager {
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 12px;
  }

  .user-pager__summary {
    white-space: normal;
  }

  .user-pager :deep(.el-pagination) {
    justify-content: center;
  }

  .user-pager :deep(.el-pagination__sizes),
  .user-pager :deep(.el-pagination__jump) {
    display: none;
  }

  .template-admin-grid {
    grid-template-columns: 1fr;
  }

  .feedback-item {
    padding: 16px;
  }

  .feedback-item header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
