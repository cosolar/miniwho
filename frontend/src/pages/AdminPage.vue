<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotSquare, DataBoard, Document, Download, Grid, Message, Refresh, Search, Share,
  User, UserFilled, View
} from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import { useAuthStore } from '../stores/auth'
import { adminApi, templateApi } from '../api'
import { notifyError } from '../api/client'
import { formatDate, formatDateTime, saveBlob } from '../utils/format'

const auth = useAuthStore()

const activeTab = ref('overview')
const loading = ref(true)
const usersLoading = ref(false)
const templatesLoading = ref(false)
const feedbackLoading = ref(false)
const stats = ref(null)
const users = ref([])
const templates = ref([])
const feedback = ref([])
const sample = ref(null)
const keyword = ref('')
const feedbackFilter = ref('all')
const replyDialog = ref(false)
const replySaving = ref(false)
const replyTarget = ref(null)
const replyText = ref('')

const tabs = computed(() => [
  { key: 'overview', label: '运营概览', icon: DataBoard },
  { key: 'users', label: '用户管理', icon: UserFilled, count: users.value.length },
  { key: 'templates', label: '模板管理', icon: Grid, count: templates.value.length },
  { key: 'feedback', label: '意见反馈', icon: ChatDotSquare, count: pendingFeedback.value.length }
])

const filteredUsers = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return users.value
  return users.value.filter((item) =>
    [item.username, item.nickname, item.email].filter(Boolean).some((value) =>
      String(value).toLowerCase().includes(query)))
})

const pendingFeedback = computed(() => feedback.value.filter((item) => item.status === 0))
const visibleFeedback = computed(() => {
  if (feedbackFilter.value === 'pending') return feedback.value.filter((item) => item.status === 0)
  if (feedbackFilter.value === 'handled') return feedback.value.filter((item) => item.status === 1)
  return feedback.value
})

const overviewCards = computed(() => {
  const data = stats.value || {}
  return [
    { key: 'users', label: '注册用户', value: data.users, delta: data.newUsers7d, deltaLabel: '近 7 天新增', icon: UserFilled },
    { key: 'resumes', label: '简历总数', value: data.resumes, delta: data.newResumes7d, deltaLabel: '近 7 天创建', icon: Document },
    { key: 'templates', label: '启用模板', value: data.templates, icon: Grid },
    { key: 'reports', label: '诊断报告', value: data.reports, icon: DataBoard },
    { key: 'shares', label: '分享链接', value: data.shares, icon: Share },
    { key: 'feedback', label: '待处理反馈', value: data.feedbackPending, icon: Message }
  ]
})

const recentUsers = computed(() => stats.value?.recentUsers || [])

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
  } catch (error) {
    notifyError(error, '加载用户列表失败')
  } finally {
    usersLoading.value = false
  }
}

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

async function loadAll(showLoading = true) {
  if (showLoading) loading.value = true
  await Promise.all([loadStats(), loadUsers(), loadTemplates(), loadFeedback(), loadSample()])
  loading.value = false
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
  } catch (error) {
    notifyError(error, '更新账号状态失败')
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
}

function templateTags(row) {
  const tags = String(row.tags || '').split(',').map((item) => item.trim()).filter(Boolean)
  return tags.filter((tag, index) => tag !== row.category && tags.indexOf(tag) === index)
}

function feedbackPreview(item) {
  return item.content?.length > 120 ? `${item.content.slice(0, 120)}…` : item.content
}

onMounted(() => loadAll())
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
                <p v-else class="stat-delta">{{ card.key === 'feedback' ? '需要管理员回复' : '当前累计' }}</p>
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
                <h2>用户管理 <small>{{ users.length }} 位</small></h2>
              </div>
              <el-input v-model="keyword" class="user-search" placeholder="搜索用户名、昵称或邮箱" clearable>
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
            </div>

            <div class="surface table-card" v-loading="usersLoading">
              <el-table :data="filteredUsers" style="width: 100%">
                <el-table-column label="用户" min-width="158">
                  <template #default="{ row }">
                    <div class="table-user">
                      <span class="table-avatar">{{ (row.nickname || row.username || '?').slice(0, 1) }}</span>
                      <div>
                        <strong>{{ row.nickname || row.username }}</strong>
                        <span>@{{ row.username }}</span>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="email" label="邮箱" min-width="146">
                  <template #default="{ row }">
                    <span class="cell-muted">{{ row.email || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="角色" width="84">
                  <template #default="{ row }">
                    <span class="role-tag" :class="{ 'is-admin': row.role === 'admin' }">
                      {{ row.role === 'admin' ? '管理员' : '普通用户' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="resumeCount" label="简历" width="60" align="center" />
                <el-table-column label="注册时间" width="100">
                  <template #default="{ row }">
                    <span class="cell-muted">{{ formatDate(row.createdAt) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="86">
                  <template #default="{ row }">
                    <span class="status-tag" :class="row.status === 1 ? 'is-on' : 'is-off'">
                      <i></i>{{ row.status === 1 ? '正常' : '已冻结' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="92" align="right">
                  <template #default="{ row }">
                    <el-button
                      size="small"
                      :type="row.status === 1 ? 'danger' : 'primary'"
                      link
                      :disabled="row.id === auth.user?.id"
                      @click="toggleUser(row)"
                    >
                      {{ row.id === auth.user?.id ? '当前账号' : (row.status === 1 ? '冻结' : '恢复') }}
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="!filteredUsers.length" class="table-empty">没有匹配的用户</div>
            </div>
          </section>

          <section v-show="activeTab === 'templates'" class="admin-section">
            <div class="table-toolbar">
              <div>
                <span class="card-kicker">Templates</span>
                <h2>模板管理 <small>{{ templates.length }} 套</small></h2>
              </div>
              <p class="toolbar-hint">
                停用模板不会影响已创建的简历。
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
                  <span v-if="item.version" class="tag">v{{ item.version }}</span>
                  <span v-if="item.category" class="tag">{{ item.category }}</span>
                  <span v-for="tag in templateTags(item)" :key="tag" class="tag">{{ tag }}</span>
                </div>
                <footer>
                  <span>{{ item.usageCount ?? 0 }} 份简历在用</span>
                  <span class="footer-actions">
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
        </template>
      </div>
    </div>

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
  </AppLayout>
</template>

<style scoped>
.admin-page {
  padding: 34px 0 72px;
  background:
    radial-gradient(900px 420px at 10% -10%, rgba(10, 125, 118, 0.06), transparent 62%),
    transparent;
}

.admin-shell {
  width: min(1180px, calc(100% - 56px));
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

.table-user span {
  color: var(--faint);
  font-size: 10px;
}

.cell-muted {
  color: var(--muted);
  font-size: 11px;
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
