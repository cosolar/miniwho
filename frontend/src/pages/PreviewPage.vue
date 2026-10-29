<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Check, CircleCheck, CopyDocument, Delete, Document, Download, InfoFilled, Link,
  Lock, Printer, Setting, Share, View, Warning
} from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import PaperFrame from '../components/PaperFrame.vue'
import { configOf, templateByCode } from '../data/templatePresets'
import { pdfApi, resumeApi, shareApi } from '../api'
import { notifyError } from '../api/client'
import { useTemplates } from '../composables/useTemplates'
import { copyText, daysLeft, saveBlob, timeAgo } from '../utils/format'

const route = useRoute()
const router = useRouter()
const resumeId = Number(route.params.id)

const loading = ref(true)
const loadError = ref('')
const resume = ref(null)
const { official, community, all: templates, refresh: refreshTemplates, ensure: ensureTemplate } = useTemplates()
const style = reactive({
  filter: 'standard',
  fontSizeStep: 0,
  spacingStep: 0,
  showBrandFooter: true,
  pageMargin: { custom: false, top: 15, right: 15, bottom: 12, left: 15 }
})
const styleState = ref('saved')
const styleSavedAt = ref(Date.now())
const printDialog = ref(false)
const printing = ref(false)
const pdfStatus = ref(null)
const pageCount = ref(1)
const generating = ref(false)
const shareDialog = ref(false)
const shares = ref([])
const sharesLoading = ref(false)
const shareCreating = ref(false)
const activeShare = ref(null)
const shareForm = reactive({
  token: '',
  password: '',
  expiresInDays: 30,
  allowDownload: true,
  hiddenFields: ['phone', 'email']
})

let styleTimer = 0
let styleDirty = false
let styleSaving = false
let styleAgain = false
let stylePromise = Promise.resolve()
let originalTitle = ''

const template = computed(() => templateByCode(resume.value?.templateCode, templates.value))
const styleData = computed(() => ({ ...style }))
const displayShare = computed(() => activeShare.value || shares.value[0] || null)
const currentShareUrl = computed(() => displayShare.value ? shareUrlOf(displayShare.value) : '')
const shareCount = computed(() => shares.value.length)
// 链接名是访客地址的一部分，规则要和后端 CUSTOM_TOKEN_PATTERN 保持一致。
const SHARE_TOKEN_PATTERN = /^[A-Za-z0-9_-]{4,40}$/
const shareTokenHint = computed(() => {
  const alias = shareForm.token.trim()
  if (!alias) return '留空则使用随机令牌；也可以自己定一个更短的，例如 haojianli。'
  if (!SHARE_TOKEN_PATTERN.test(alias)) return '链接名只能用字母、数字、- 和 _，长度 4-40 个字符。'
  if (displayShare.value && alias === displayShare.value.token) {
    return '与当前链接同名：新设置会直接覆盖它，浏览量重新计算。'
  }
  return `访客将打开 ${window.location.origin}/share/${alias}`
})
// 别人导入的模板不在官方区，也不在你的 /mine 里；这时纸面会退回默认版式，必须说清楚。
const templateMissing = computed(() => Boolean(resume.value?.templateCode)
  && !templates.value.some((tpl) => tpl.code === resume.value.templateCode))
const styleStatusText = computed(() => {
  if (styleState.value === 'saving') return '正在保存样式'
  if (styleState.value === 'error') return '样式保存失败'
  return `样式已保存 · ${timeAgo(styleSavedAt.value)}`
})

const LAYOUT_LABELS = {
  minimal: '单栏留白',
  academic: '学术细线',
  sidebar: '双栏',
  band: '色带',
  center: '居中页眉',
  timeline: '时间轴',
  profile: '头像单栏',
  ribbon: '色带旗标',
  classic: '经典',
  rail: '通栏照片',
  photoheader: '照片页眉',
  formal: '传统单栏'
}

const FILTERS = [
  { value: 'standard', label: '原色', hint: '纯白纸面' },
  { value: 'soft', label: '暖纸', hint: '柔和米白' },
  { value: 'mono', label: '黑白', hint: '灰度打印' }
]

const HIDDEN_FIELDS = [
  { value: 'phone', label: '手机号' },
  { value: 'email', label: '邮箱' },
  { value: 'city', label: '所在城市' },
  { value: 'website', label: '个人主页' }
]

function markStyleChanged() {
  styleDirty = true
  styleState.value = 'saving'
  clearTimeout(styleTimer)
  styleTimer = setTimeout(runStyleSave, 350)
}

async function persistStyle() {
  const snapshot = {
    templateCode: resume.value.templateCode,
    filter: style.filter,
    fontSizeStep: style.fontSizeStep,
    spacingStep: style.spacingStep,
    showBrandFooter: style.showBrandFooter,
    pageMargin: { ...style.pageMargin }
  }
  styleDirty = false
  const detail = await resumeApi.saveStyle(resumeId, snapshot)
  if (detail?.templateName) {
    resume.value.templateName = detail.templateName
    resume.value.templateCode = detail.templateCode
  }
}

function runStyleSave() {
  clearTimeout(styleTimer)
  if (styleSaving) {
    styleAgain = true
    return stylePromise
  }
  styleSaving = true
  styleState.value = 'saving'
  stylePromise = (async () => {
    do {
      styleAgain = false
      await persistStyle()
    } while (styleAgain)
  })()
  stylePromise
    .then(() => {
      styleState.value = 'saved'
      styleSavedAt.value = Date.now()
    })
    .catch((error) => {
      styleState.value = 'error'
      notifyError(error, '样式保存失败')
    })
    .finally(() => {
      styleSaving = false
    })
  return stylePromise
}

async function flushStyle() {
  clearTimeout(styleTimer)
  if (styleSaving) {
    try {
      await stylePromise
    } catch {
      return
    }
  }
  if (styleDirty) {
    try {
      await runStyleSave()
    } catch {
      // The error state is shown in the toolbar.
    }
  }
}

function chooseTemplate(tpl) {
  if (!resume.value || resume.value.templateCode === tpl.code) return
  resume.value.templateCode = tpl.code
  resume.value.templateName = tpl.name
  markStyleChanged()
}

function chooseFilter(value) {
  if (style.filter === value) return
  style.filter = value
  markStyleChanged()
}
function stepFont(delta) {
  const next = Math.max(-2, Math.min(2, style.fontSizeStep + delta))
  if (next === style.fontSizeStep) return
  style.fontSizeStep = next
  markStyleChanged()
}

function stepSpacing(delta) {
  const next = Math.max(-2, Math.min(2, style.spacingStep + delta))
  if (next === style.spacingStep) return
  style.spacingStep = next
  markStyleChanged()
}

function toggleFooter() {
  style.showBrandFooter = !style.showBrandFooter
  markStyleChanged()
}

const MARGIN_SIDES = [
  { key: 'top', label: '上' },
  { key: 'right', label: '右' },
  { key: 'bottom', label: '下' },
  { key: 'left', label: '左' }
]

function toggleMarginCustom(value) {
  style.pageMargin.custom = !!value
  markStyleChanged()
}

function setMargin(side, value) {
  const next = Math.max(0, Math.min(40, Math.round(Number(value) || 0)))
  if (next === style.pageMargin[side]) return
  style.pageMargin[side] = next
  markStyleChanged()
}

/** 激光/喷墨打印机普遍有 5mm 死区，低于这个值要提醒可能被裁掉。 */
const marginTooTight = computed(() =>
  style.pageMargin.custom && MARGIN_SIDES.some(({ key }) => style.pageMargin[key] < 5))

function openPrint() {
  printDialog.value = true
}

async function startPrint() {
  printing.value = true
  try {
    await flushStyle()
    printDialog.value = false
    await nextTick()
    originalTitle = document.title
    document.title = `${resume.value.title} · 极简简历`
    const restore = () => {
      document.title = originalTitle
      window.removeEventListener('afterprint', restore)
    }
    window.addEventListener('afterprint', restore)
    setTimeout(() => {
      window.print()
      setTimeout(restore, 1200)
    }, 160)
  } finally {
    printing.value = false
  }
}

async function loadPdfStatus() {
  try {
    pdfStatus.value = await pdfApi.status()
  } catch {
    pdfStatus.value = null
  }
}

async function generatePdf() {
  if (generating.value) return
  if (!pdfStatus.value?.available) {
    openPrint()
    return
  }
  generating.value = true
  try {
    await flushStyle()
    const blob = await pdfApi.download(resumeId, {
      templateCode: resume.value.templateCode,
      filter: style.filter,
      fontSizeStep: style.fontSizeStep,
      spacingStep: style.spacingStep,
      showBrandFooter: style.showBrandFooter
    })
    savePdfBlob(blob)
    ElMessage.success('PDF 已生成，开始下载')
  } catch (error) {
    if (error?.code === 'PDF_ENGINE_UNAVAILABLE') {
      pdfStatus.value = { available: false }
      ElMessage.warning('服务端 PDF 暂不可用，已切换到浏览器打印')
      openPrint()
      return
    }
    notifyError(error, 'PDF 生成失败，可改用浏览器打印')
  } finally {
    generating.value = false
  }
}

function savePdfBlob(blob) {
  saveBlob(blob, `${resume.value.title || '极简简历'}.pdf`)
}

async function loadShares() {
  sharesLoading.value = true
  try {
    shares.value = await shareApi.list(resumeId)
    if (activeShare.value) {
      const fresh = shares.value.find((item) => item.id === activeShare.value.id)
      activeShare.value = fresh || null
    }
  } catch (error) {
    notifyError(error, '加载分享链接失败')
  } finally {
    sharesLoading.value = false
  }
}

async function openShare() {
  shareDialog.value = true
  await loadShares()
}

function shareUrlOf(share) {
  return `${window.location.origin}/share/${share.token}`
}

function shortToken(token) {
  return token.length > 12 ? `${token.slice(0, 12)}…` : token
}

async function copyShare(share) {
  if (!share) return
  try {
    await copyText(shareUrlOf(share))
    ElMessage.success('分享链接已复制')
  } catch {
    ElMessage.warning('浏览器限制复制，请手动选中链接')
  }
}

function openSharePage(share) {
  if (!share) return
  window.open(shareUrlOf(share), '_blank', 'noopener')
}

async function createShare() {
  if (shareCreating.value) return
  const alias = shareForm.token.trim()
  if (alias && !SHARE_TOKEN_PATTERN.test(alias)) {
    ElMessage.warning('链接名只能用字母、数字、- 和 _，长度 4-40 个字符')
    return
  }
  const password = shareForm.password.trim()
  if (password && (password.length < 4 || password.length > 20)) {
    ElMessage.warning('访问密码长度需为 4-20 个字符')
    return
  }
  shareCreating.value = true
  try {
    const created = await shareApi.create(resumeId, {
      token: alias || null,
      password: password || null,
      expiresInDays: Number(shareForm.expiresInDays),
      allowDownload: shareForm.allowDownload,
      hiddenFields: shareForm.hiddenFields
    })
    activeShare.value = created
    shareForm.token = ''
    shareForm.password = ''
    await loadShares()
    ElMessage.success('分享链接已创建')
  } catch (error) {
    notifyError(error, '创建分享链接失败')
  } finally {
    shareCreating.value = false
  }
}

async function revokeShare(share) {
  try {
    await ElMessageBox.confirm(
      '撤销后，该链接会立即失效，已经发出的链接也无法再打开。',
      '撤销分享链接',
      { confirmButtonText: '确认撤销', cancelButtonText: '取消', type: 'warning' }
    )
    await shareApi.revoke(share.id)
    if (activeShare.value?.id === share.id) activeShare.value = null
    shares.value = shares.value.filter((item) => item.id !== share.id)
    ElMessage.success('分享链接已撤销')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(error, '撤销失败')
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const detail = await resumeApi.detail(resumeId)
    resume.value = detail
    Object.assign(style, {
      filter: detail.style?.filter || 'standard',
      fontSizeStep: Number(detail.style?.fontSizeStep || 0),
      spacingStep: Number(detail.style?.spacingStep || 0),
      showBrandFooter: detail.style?.showBrandFooter !== false,
      pageMargin: {
        custom: !!detail.style?.pageMargin?.custom,
        top: Number(detail.style?.pageMargin?.top ?? 15),
        right: Number(detail.style?.pageMargin?.right ?? 15),
        bottom: Number(detail.style?.pageMargin?.bottom ?? 12),
        left: Number(detail.style?.pageMargin?.left ?? 15)
      }
    })
    styleSavedAt.value = detail.updatedAt || Date.now()
    await refreshTemplates()
    // 别人分享的模板被取消分享后仍要按原样渲染，所以按编码单独兜底取一次配置。
    await ensureTemplate(detail.templateCode)
    loadShares()
    loadPdfStatus()
    if (route.query.share === '1') await openShare()
  } catch (error) {
    loadError.value = error?.message || '加载简历失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

onBeforeUnmount(() => {
  clearTimeout(styleTimer)
})

onBeforeRouteLeave(async () => {
  await flushStyle()
  return true
})
</script>

<template>
  <AppLayout>
    <div v-if="loading" class="preview-loading">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-else-if="loadError" class="preview-error">
      <el-icon class="preview-error__icon"><Warning /></el-icon>
      <h1>预览加载失败</h1>
      <p>{{ loadError }}</p>
      <el-button type="primary" @click="router.push('/dashboard')">返回我的简历</el-button>
    </div>

    <div v-else-if="resume" class="preview-page">
      <header class="preview-toolbar no-print">
        <div class="preview-toolbar__left">
          <router-link :to="`/resumes/${resumeId}`" class="back-link">
            <el-icon><ArrowLeft /></el-icon>
            返回编辑
          </router-link>
          <div class="preview-title">
            <h1>{{ resume.title }}</h1>
            <p>
              <span>{{ template.name }} · {{ resume.language === 'en' ? 'English' : '中文' }}</span>
              <span class="style-state" :class="styleState"><i></i>{{ styleStatusText }}</span>
            </p>
          </div>
        </div>
        <div class="preview-toolbar__actions">
          <el-button plain @click="openShare">
            <el-icon><Share /></el-icon>
            分享
            <span v-if="shareCount" class="share-count">{{ shareCount }}</span>
          </el-button>
          <el-button plain @click="openPrint">
            <el-icon><Printer /></el-icon>
            浏览器打印
          </el-button>
          <el-button type="primary" :loading="generating" @click="generatePdf">
            <el-icon><Download /></el-icon>
            {{ generating ? '正在生成 PDF' : '下载 PDF' }}
          </el-button>
        </div>
      </header>

      <div class="preview-layout">
        <section class="preview-stage-area">
          <div class="stage-meta no-print">
            <span><i class="meta-dot"></i> A4 实际尺寸预览</span>
            <span>共 {{ pageCount }} 页 · 虚线为实际分页位置</span>
          </div>
          <div class="preview-stage">
            <div class="preview-stage__inner">
              <PaperFrame
                :content="resume.content"
                :template="template"
                :style-data="styleData"
                :avatar="resume.avatar"
                :padding="0"
                @pages="(count) => (pageCount = count)"
              />
            </div>
          </div>
          <div class="stage-foot no-print">
            <span>210 × 297 mm · {{ template.name }}模板</span>
            <span>导出 PDF 与预览按同一处分页</span>
          </div>
        </section>

        <aside class="preview-controls no-print">
          <div class="controls-head">
            <div>
              <span class="mono">STYLE</span>
              <h2>样式设置</h2>
            </div>
            <el-icon class="controls-head__icon"><Setting /></el-icon>
          </div>

          <div class="control-group">
            <div class="control-label">
              <span>官方模板</span>
              <small>全部免费</small>
            </div>
            <p v-if="templateMissing" class="template-missing">
              <el-icon><Warning /></el-icon>
              <span>这份简历记录的模板「{{ resume.templateCode }}」已下架或不属于本站，纸面暂按默认版式渲染。选中下方任一模板即可恢复。</span>
            </p>
            <div class="template-grid">
              <button
                v-for="tpl in official"
                :key="tpl.code"
                type="button"
                class="template-chip"
                :class="{ active: resume.templateCode === tpl.code }"
                :style="{ '--chip-accent': configOf(tpl).accent }"
                @click="chooseTemplate(tpl)"
              >
                <span class="chip-paper" :class="`chip-${configOf(tpl).layout}`">
                  <i class="chip-bar"></i>
                  <i></i><i></i>
                </span>
                <span class="chip-text">
                  <strong>{{ tpl.name }}</strong>
                  <small>{{ LAYOUT_LABELS[configOf(tpl).layout] || '单栏' }}</small>
                </span>
                <el-icon v-if="resume.templateCode === tpl.code" class="chip-check"><Check /></el-icon>
              </button>
            </div>

            <template v-if="community.length">
              <div class="control-label control-label--sub">
                <span>社区模板</span>
                <small>{{ community.length }} 套可选用</small>
              </div>
              <div class="template-grid">
                <button
                  v-for="tpl in community"
                  :key="tpl.code"
                  type="button"
                  class="template-chip"
                  :class="{ active: resume.templateCode === tpl.code }"
                  :style="{ '--chip-accent': configOf(tpl).accent }"
                  @click="chooseTemplate(tpl)"
                >
                  <span class="chip-paper" :class="`chip-${configOf(tpl).layout}`">
                    <i class="chip-bar"></i>
                    <i></i><i></i>
                  </span>
                  <span class="chip-text">
                    <strong>{{ tpl.name }}</strong>
                    <small>{{ tpl.isMine ? '我的 · ' : '' }}{{ tpl.author || '未署名' }} · v{{ tpl.version || '1.0.0' }}</small>
                  </span>
                  <el-icon v-if="resume.templateCode === tpl.code" class="chip-check"><Check /></el-icon>
                </button>
              </div>
            </template>

            <p class="package-note">
              导入别人的模板包、或把自己调的版式分享到模板中心，都在
              <router-link to="/my-templates">我的模板</router-link>里完成。
            </p>
          </div>

          <div class="control-group">
            <div class="control-label">
              <span>纸面滤镜</span>
              <small>打印同样生效</small>
            </div>
            <div class="filter-options">
              <button
                v-for="item in FILTERS"
                :key="item.value"
                type="button"
                :class="{ active: style.filter === item.value }"
                @click="chooseFilter(item.value)"
              >
                <span class="filter-dot" :class="`dot-${item.value}`"></span>
                <strong>{{ item.label }}</strong>
                <small>{{ item.hint }}</small>
              </button>
            </div>
          </div>

          <div class="control-group">
            <div class="control-label">
              <span>正文字号</span>
              <small>{{ style.fontSizeStep > 0 ? `+${style.fontSizeStep}` : style.fontSizeStep }}</small>
            </div>
            <div class="stepper">
              <button type="button" :disabled="style.fontSizeStep <= -2" @click="stepFont(-1)">A−</button>
              <div class="stepper-value">
                <span>{{ style.fontSizeStep === 0 ? '标准' : style.fontSizeStep > 0 ? '偏大' : '偏小' }}</span>
                <i><b :style="{ width: `${((style.fontSizeStep + 2) / 4) * 100}%` }"></b></i>
              </div>
              <button type="button" :disabled="style.fontSizeStep >= 2" @click="stepFont(1)">A+</button>
            </div>
          </div>

          <div class="control-group">
            <div class="control-label">
              <span>行距与段落</span>
              <small>{{ style.spacingStep > 0 ? `+${style.spacingStep}` : style.spacingStep }}</small>
            </div>
            <div class="stepper">
              <button type="button" :disabled="style.spacingStep <= -2" @click="stepSpacing(-1)">紧</button>
              <div class="stepper-value">
                <span>{{ style.spacingStep === 0 ? '标准' : style.spacingStep > 0 ? '宽松' : '紧凑' }}</span>
                <i><b :style="{ width: `${((style.spacingStep + 2) / 4) * 100}%` }"></b></i>
              </div>
              <button type="button" :disabled="style.spacingStep >= 2" @click="stepSpacing(1)">松</button>
            </div>
          </div>

          <div class="control-group">
            <div class="control-label">
              <span>页边距</span>
              <small>{{ style.pageMargin.custom ? '自定义' : '跟随模板' }}</small>
            </div>
            <div class="margin-row">
              <el-switch size="small" :model-value="style.pageMargin.custom" @change="toggleMarginCustom" />
              <span class="margin-row__hint">
                {{ style.pageMargin.custom ? '按下面的值排版' : '用这套模板自带的留白' }}
              </span>
            </div>
            <div v-if="style.pageMargin.custom" class="margin-grid">
              <label v-for="side in MARGIN_SIDES" :key="side.key" class="margin-field">
                <span>{{ side.label }}</span>
                <input
                  type="number"
                  min="0"
                  max="40"
                  step="1"
                  :value="style.pageMargin[side.key]"
                  @change="setMargin(side.key, $event.target.value)"
                />
                <em>mm</em>
              </label>
            </div>
            <p v-if="marginTooTight" class="margin-warn">
              <el-icon><Warning /></el-icon>
              <span>小于 5mm 的边距多数打印机打不出来，导出没问题、实印可能被裁掉。</span>
            </p>
          </div>

          <div class="control-switch">
            <div>
              <strong>页脚品牌标识</strong>
              <small>在页面底部显示「极简简历」字样</small>
            </div>
            <el-switch :model-value="style.showBrandFooter" @change="toggleFooter" />
          </div>

          <div class="controls-note">
            <el-icon><InfoFilled /></el-icon>
            <p>样式修改会自动保存。内容超出一页时按 A4 自动分页，纸面上的虚线就是导出 PDF 的换页位置。</p>
          </div>
        </aside>
      </div>
    </div>

    <el-dialog v-model="printDialog" width="480px" align-center class="print-dialog">
      <template #header>
        <div class="print-dialog__head">
          <span class="print-dialog__icon"><el-icon><Document /></el-icon></span>
          <div>
            <h2>导出为 PDF</h2>
            <p>使用浏览器打印，文件只保存在你的设备上。</p>
          </div>
        </div>
      </template>

      <ol class="print-steps">
        <li><span>01</span><p>在打印窗口中，将目标打印机选择为「另存为 PDF」。</p></li>
        <li><span>02</span><p>纸张选择 <strong>A4</strong>，边距选择 <strong>无</strong>，缩放保持 <strong>100%</strong>。</p></li>
        <li><span>03</span><p>展开「更多设置」，勾选 <strong>背景图形</strong>，关闭 <strong>页眉和页脚</strong>。</p></li>
        <li><span>04</span><p>点击保存，即可得到可投递的 PDF 文件。</p></li>
      </ol>

      <div class="print-tip">
        <el-icon><InfoFilled /></el-icon>
        <p>不同浏览器的选项名称略有差异。若分页不理想，可回到编辑页精简内容或调小字号。</p>
      </div>

      <template #footer>
        <el-button @click="printDialog = false">取消</el-button>
        <el-button type="primary" :loading="printing" @click="startPrint">
          <el-icon><Printer /></el-icon>
          打开打印窗口
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shareDialog" width="580px" align-center class="share-dialog">
      <template #header>
        <div class="share-dialog__head">
          <span class="mono">SHARE</span>
          <h2>分享这份简历</h2>
          <p>链接默认 30 天后过期，可设置访问密码与隐藏字段。</p>
        </div>
      </template>

      <div v-if="displayShare" class="share-active">
        <div class="share-active__head">
          <span class="active-badge"><i></i>当前有效链接</span>
          <span class="share-views"><el-icon><View /></el-icon>{{ displayShare.viewCount || 0 }} 次浏览</span>
        </div>
        <div class="share-link-row">
          <span class="share-link-icon"><el-icon><Link /></el-icon></span>
          <code>{{ currentShareUrl }}</code>
          <button type="button" class="copy-btn" @click="copyShare(displayShare)">
            <el-icon><CopyDocument /></el-icon>
            复制
          </button>
        </div>
        <div class="share-meta-line">
          <span>{{ daysLeft(displayShare.expiresAt) }}</span>
          <span>{{ displayShare.hasPassword ? '需要密码' : '无访问密码' }}</span>
          <span>{{ displayShare.allowDownload ? '允许下载' : '仅在线查看' }}</span>
          <button type="button" @click="openSharePage(displayShare)">打开页面</button>
          <button type="button" class="danger" @click="revokeShare(displayShare)">撤销</button>
        </div>
      </div>

      <div class="share-form">
        <div class="share-form__title">
          <span>{{ displayShare ? '创建新链接' : '新建分享链接' }}</span>
          <small v-if="displayShare">新链接创建后，旧链接立即失效</small>
        </div>

        <div class="share-field">
          <label>链接名</label>
          <el-input
            v-model="shareForm.token"
            maxlength="40"
            placeholder="选填，自定义链接名"
            spellcheck="false"
            autocomplete="off"
          >
            <template #prefix>
              <span class="share-token-prefix">/share/</span>
            </template>
          </el-input>
          <p :class="{ 'share-token-hint--bad': shareForm.token.trim() && !SHARE_TOKEN_PATTERN.test(shareForm.token.trim()) }">
            {{ shareTokenHint }}
          </p>
        </div>

        <div class="share-field">
          <label>访问密码</label>
          <el-input
            v-model="shareForm.password"
            type="password"
            show-password
            maxlength="20"
            placeholder="选填，4-20 个字符"
          />
          <p>设置密码后，访客需要输入密码才能查看。</p>
        </div>

        <div class="share-grid">
          <div class="share-field">
            <label>有效期</label>
            <el-select v-model="shareForm.expiresInDays">
              <el-option label="7 天" :value="7" />
              <el-option label="30 天" :value="30" />
              <el-option label="90 天" :value="90" />
              <el-option label="永久有效" :value="0" />
            </el-select>
          </div>
          <div class="share-field">
            <label>下载权限</label>
            <div class="download-switch">
              <el-switch v-model="shareForm.allowDownload" />
              <span>{{ shareForm.allowDownload ? '允许打印 / 存为 PDF' : '仅在线查看' }}</span>
            </div>
          </div>
        </div>

        <div class="share-field">
          <label>隐藏字段</label>
          <el-checkbox-group v-model="shareForm.hiddenFields" class="hidden-group">
            <el-checkbox v-for="field in HIDDEN_FIELDS" :key="field.value" :value="field.value">
              {{ field.label }}
            </el-checkbox>
          </el-checkbox-group>
          <p>隐藏的内容不会发送到访客浏览器，而不只是视觉遮挡。</p>
        </div>

        <div class="share-security">
          <el-icon><Lock /></el-icon>
          <p>未自定义时使用 256 位安全随机令牌；自定义链接名全站唯一，被占用时会提示换一个。分享页只显示昵称，不会暴露用户名、邮箱等登录信息。</p>
        </div>

        <el-button type="primary" class="share-create" :loading="shareCreating" @click="createShare">
          {{ displayShare ? '创建新链接并替换旧链接' : '创建分享链接' }}
        </el-button>
      </div>

      <div v-if="shares.length" class="share-history">
        <span class="share-history__label">有效链接记录</span>
        <div v-for="share in shares" :key="share.id" class="share-history__row">
          <el-icon><CircleCheck /></el-icon>
          <span class="history-url">/share/{{ shortToken(share.token) }}</span>
          <span>{{ share.viewCount || 0 }} 次浏览</span>
          <span>{{ daysLeft(share.expiresAt) }}</span>
          <button type="button" @click="copyShare(share)"><el-icon><CopyDocument /></el-icon></button>
          <button type="button" @click="revokeShare(share)"><el-icon><Delete /></el-icon></button>
        </div>
      </div>
    </el-dialog>
  </AppLayout>
</template>

<style scoped>
.preview-loading {
  width: min(calc(var(--content-width) + 80px), calc(100% - 48px));
  margin: 0 auto;
  padding: 60px 0;
}

.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120px 24px;
  text-align: center;
}

.preview-error__icon {
  margin-bottom: 14px;
  color: var(--copper);
  font-size: 32px;
}

.preview-error h1 {
  font-family: var(--serif);
  font-size: 24px;
  font-weight: 600;
}

.preview-error p {
  margin: 8px 0 20px;
  color: var(--muted);
  font-size: 12px;
}

.preview-page {
  padding-bottom: 70px;
}

.preview-toolbar {
  position: sticky;
  top: 62px;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  /* 左右让出与 .preview-layout 相同的边距（外壳宽度 = 内容宽度 + 80px），
     这样「返回编辑」和右侧按钮组分别与下方舞台、样式面板的外沿对齐。 */
  padding: 12px max(24px, calc((100% - var(--content-width) - 80px) / 2));
  border-bottom: 1px solid var(--line);
  background: rgba(244, 241, 233, 0.9);
  backdrop-filter: blur(14px);
}

.preview-toolbar__left {
  display: flex;
  align-items: center;
  gap: 18px;
  flex: 1;
  min-width: 0;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  color: var(--muted);
  font-size: 11px;
}

.back-link:hover {
  color: var(--accent);
}

.preview-title {
  min-width: 0;
  padding-left: 18px;
  border-left: 1px solid var(--line-strong);
}

.preview-title h1 {
  overflow: hidden;
  font-family: var(--serif);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-title p {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 2px;
  color: var(--faint);
  font-size: 9px;
}

.preview-title p > span + span::before {
  content: "·";
  margin-right: 9px;
}

.style-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.style-state i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--success);
}

.style-state.saving i {
  background: var(--gold);
}

.style-state.error {
  color: var(--danger);
}

.style-state.error i {
  background: var(--danger);
}

.preview-toolbar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.preview-toolbar__actions :deep(.el-button) {
  height: 36px;
  font-size: 12px;
}

.share-count {
  margin-left: 3px;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9px;
}

.preview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 312px;
  gap: 26px;
  align-items: start;
  width: min(calc(var(--content-width) + 80px), calc(100% - 48px));
  margin: 22px auto 0;
}

.stage-meta,
.stage-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 4px 8px;
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.04em;
}

.stage-meta span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
}

.meta-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--success);
}

.stage-foot {
  padding: 8px 4px 0;
}

.preview-stage {
  padding: 30px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background:
    radial-gradient(600px 420px at 50% -10%, rgba(10, 125, 118, 0.07), transparent 70%),
    linear-gradient(180deg, var(--canvas-deep), #e7e1d4);
}

.preview-stage__inner {
  width: min(794px, 100%);
  margin: 0 auto;
  filter: drop-shadow(0 24px 48px rgba(25, 34, 30, 0.16));
}

.preview-stage__inner :deep(.paper-scaler__paper) {
  box-shadow: none;
}

.preview-controls {
  position: sticky;
  top: 138px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--paper);
  max-height: calc(100vh - 156px);
  overflow-y: auto;
}

.controls-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.controls-head .mono {
  color: var(--faint);
  font-size: 8px;
  letter-spacing: 0.16em;
}

.controls-head h2 {
  margin-top: 2px;
  font-family: var(--serif);
  font-size: 18px;
  font-weight: 600;
}

.controls-head__icon {
  margin-top: 4px;
  color: var(--faint);
  font-size: 15px;
}

.control-group {
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}

.control-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 9px;
  color: var(--ink-soft);
  font-size: 11px;
}

.control-label small {
  color: var(--faint);
  font-size: 8px;
  letter-spacing: 0.03em;
}

.template-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.template-chip {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--paper);
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}

.template-chip:hover {
  border-color: var(--line-strong);
}

.template-chip.active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.chip-paper {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 24px;
  height: 30px;
  padding: 5px 4px;
  flex-shrink: 0;
  border: 1px solid var(--line);
  border-radius: 1px;
  background: #fff;
}

.chip-paper i {
  display: block;
  width: 100%;
  height: 1.5px;
  background: #ded8cc;
}

.chip-paper .chip-bar {
  width: 60%;
  height: 2px;
  margin-bottom: 2px;
  background: var(--chip-accent);
}

.chip-band .chip-bar {
  width: 100%;
  height: 7px;
  margin: -5px -4px 3px;
  padding: 0;
}

.chip-sidebar {
  padding-left: 9px;
}

.chip-sidebar::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 6px;
  background: color-mix(in srgb, var(--chip-accent) 15%, #fff);
}

.chip-center .chip-bar {
  margin-inline: auto;
}

.chip-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chip-text strong {
  color: var(--ink-soft);
  font-size: 10px;
  font-weight: 500;
}

.chip-text small {
  overflow: hidden;
  color: var(--faint);
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-check {
  position: absolute;
  top: 4px;
  right: 4px;
  color: var(--accent);
  font-size: 9px;
}

.template-missing {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0 0 9px;
  padding: 7px 8px;
  border: 1px solid #e8d5b5;
  border-radius: 6px;
  background: #fbf3e6;
  color: #8a6420;
  font-size: 9px;
  line-height: 1.6;
}

.template-missing .el-icon {
  flex-shrink: 0;
  margin-top: 1px;
  font-size: 11px;
}

.control-label--sub {
  margin-top: 14px;
}

.package-note {
  margin: 10px 0 0;
  color: var(--faint);
  font-size: 11px;
  line-height: 1.6;
}

.package-note a {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
}

.filter-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
}

.filter-options button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 7px 3px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--paper);
  cursor: pointer;
  transition: all 0.18s ease;
}

.filter-options button:hover {
  border-color: var(--line-strong);
}

.filter-options button.active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.filter-dot {
  width: 13px;
  height: 13px;
  margin-bottom: 2px;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: #fff;
}

.dot-soft {
  background: #fdfaf2;
}

.dot-mono {
  background: linear-gradient(135deg, #fff 50%, #8d948f 50%);
}

.filter-options strong {
  color: var(--ink-soft);
  font-size: 9px;
  font-weight: 500;
}

.filter-options small {
  color: var(--faint);
  font-size: 7px;
}

.stepper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.stepper > button {
  width: 34px;
  height: 30px;
  flex-shrink: 0;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--paper);
  color: var(--muted);
  font-family: var(--sans);
  font-size: 10px;
  cursor: pointer;
}

.stepper > button:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
}

.stepper > button:disabled {
  opacity: 0.35;
  cursor: default;
}

.stepper-value {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stepper-value span {
  color: var(--muted);
  font-size: 8px;
}

.stepper-value i {
  width: 100%;
  height: 2px;
  overflow: hidden;
  border-radius: 2px;
  background: var(--canvas-deep);
}

.stepper-value b {
  display: block;
  height: 100%;
  background: var(--accent);
  transition: width 0.2s ease;
}

.control-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}

.control-switch strong {
  display: block;
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 500;
}

.control-switch small {
  display: block;
  margin-top: 2px;
  color: var(--faint);
  font-size: 8px;
  line-height: 1.5;
}

/* 页边距：开关一行，四个方向各一个数字框，边距改小不留风险提示的余地。 */
.margin-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.margin-row__hint {
  color: var(--faint);
  font-size: 9px;
}

.margin-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 8px;
  margin-top: 10px;
}

.margin-field {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--faint);
  font-size: 10px;
}

.margin-field input {
  flex: 1;
  min-width: 0;
  padding: 3px 5px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: #fff;
  color: var(--ink);
  font-family: var(--mono);
  font-size: 10.5px;
}

.margin-field input:focus {
  border-color: var(--accent);
  outline: none;
}

.margin-field em {
  font-style: normal;
  font-size: 9px;
}

.margin-warn {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin: 9px 0 0;
  color: var(--danger);
  font-size: 9px;
  line-height: 1.6;
}

.margin-warn .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.controls-note {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 12px;
  padding: 9px 10px;
  border-radius: 6px;
  background: var(--paper-deep);
  color: var(--faint);
}

.controls-note .el-icon {
  margin-top: 1px;
  flex-shrink: 0;
  font-size: 11px;
}

.controls-note p {
  font-size: 8px;
  line-height: 1.65;
}

.print-dialog__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.print-dialog__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 17px;
}

.print-dialog__head h2 {
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 600;
}

.print-dialog__head p {
  margin-top: 2px;
  color: var(--muted);
  font-size: 10px;
}

.print-steps {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 4px 0 0;
  padding: 0;
  list-style: none;
}

.print-steps li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.print-steps li > span {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 8px;
}

.print-steps p {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}

.print-steps strong {
  color: var(--ink-soft);
  font-weight: 600;
}

.print-tip {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 16px;
  padding: 10px 12px;
  border-radius: 7px;
  background: var(--paper-deep);
  color: var(--faint);
}

.print-tip .el-icon {
  margin-top: 2px;
  flex-shrink: 0;
  font-size: 11px;
}

.print-tip p {
  font-size: 9px;
  line-height: 1.7;
}

.share-dialog__head .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.share-dialog__head h2 {
  margin-top: 4px;
  font-family: var(--serif);
  font-size: 23px;
  font-weight: 600;
}

.share-dialog__head p {
  margin-top: 3px;
  color: var(--muted);
  font-size: 10px;
}

.share-active {
  padding: 12px;
  border: 1px solid rgba(10, 125, 118, 0.2);
  border-radius: 9px;
  background: var(--accent-soft);
}

.share-active__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.active-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--accent);
  font-size: 10px;
  font-weight: 500;
}

.active-badge i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--success);
}

.share-views {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--muted);
  font-size: 9px;
}

.share-link-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
  padding: 7px 8px;
  border: 1px solid rgba(10, 125, 118, 0.16);
  border-radius: 6px;
  background: var(--paper);
}

.share-link-icon {
  color: var(--accent);
  font-size: 12px;
}

.share-link-row code {
  flex: 1;
  overflow: hidden;
  color: var(--ink-soft);
  font-family: var(--mono);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 7px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--paper-deep);
  color: var(--muted);
  font-family: var(--sans);
  font-size: 9px;
  cursor: pointer;
}

.copy-btn:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.share-meta-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 8px;
  color: var(--muted);
  font-size: 8px;
}

.share-meta-line button {
  padding: 0;
  border: none;
  background: none;
  color: var(--accent);
  font-family: var(--sans);
  font-size: 8px;
  cursor: pointer;
}

.share-meta-line button.danger {
  color: var(--danger);
}

.share-form {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.share-form__title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.share-form__title span {
  font-family: var(--serif);
  font-size: 14px;
  font-weight: 600;
}

.share-form__title small {
  color: var(--faint);
  font-size: 8px;
}

.share-field {
  margin-bottom: 12px;
}

.share-field > label {
  display: block;
  margin-bottom: 5px;
  color: var(--ink-soft);
  font-size: 10px;
}

.share-field > p {
  margin-top: 4px;
  color: var(--faint);
  font-size: 8px;
  line-height: 1.6;
}

.share-field > p.share-token-hint--bad {
  color: var(--danger);
}

.share-token-prefix {
  color: var(--faint);
  font-size: 10px;
}

.share-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.download-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
}

.download-switch span {
  color: var(--muted);
  font-size: 9px;
}

.hidden-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
}

.hidden-group :deep(.el-checkbox__label) {
  padding-left: 5px;
  font-size: 10px;
}

.share-security {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 4px 0 14px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--paper-deep);
  color: var(--faint);
}

.share-security .el-icon {
  margin-top: 1px;
  flex-shrink: 0;
  font-size: 11px;
}

.share-security p {
  font-size: 8px;
  line-height: 1.65;
}

.share-create {
  width: 100%;
}

.share-history {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.share-history__label {
  color: var(--faint);
  font-size: 8px;
  letter-spacing: 0.08em;
}

.share-history__row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 7px;
  padding: 6px 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--muted);
  font-size: 8px;
}

.share-history__row > .el-icon {
  color: var(--success);
  font-size: 10px;
}

.history-url {
  flex: 1;
  overflow: hidden;
  font-family: var(--mono);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-history__row button {
  padding: 0;
  border: none;
  background: none;
  color: var(--faint);
  cursor: pointer;
}

.share-history__row button:hover {
  color: var(--accent);
}

@media (max-width: 1120px) {
  .preview-layout {
    grid-template-columns: minmax(0, 1fr) 276px;
    gap: 18px;
  }

  .preview-stage {
    padding: 20px;
  }
}

@media (max-width: 980px) {
  .preview-toolbar {
    padding-inline: 20px;
  }

  .preview-layout {
    grid-template-columns: 1fr;
    width: calc(100% - 40px);
  }

  .preview-controls {
    position: static;
    max-height: none;
  }

  .template-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (max-width: 700px) {
  .preview-toolbar {
    flex-wrap: wrap;
    top: 56px;
    gap: 10px;
  }

  .preview-toolbar__left {
    width: 100%;
    gap: 10px;
  }

  .preview-title {
    padding-left: 10px;
  }

  .preview-title h1 {
    font-size: 15px;
  }

  .preview-title p span:first-child {
    display: none;
  }

  .preview-toolbar__actions {
    width: 100%;
  }

  .preview-toolbar__actions :deep(.el-button) {
    flex: 1;
  }

  .preview-layout {
    width: calc(100% - 24px);
    margin-top: 14px;
  }

  .preview-stage {
    padding: 10px;
  }

  .stage-meta span:last-child,
  .stage-foot span:last-child {
    display: none;
  }

  .template-grid {
    grid-template-columns: 1fr 1fr;
  }

  .share-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .print-steps p,
  .print-steps strong {
    font-size: 10px;
  }
}
</style>
