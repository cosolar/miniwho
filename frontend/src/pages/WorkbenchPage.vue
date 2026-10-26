<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import {
  Aim, ArrowLeft, Check, CircleCheck, Coffee, Cpu, DataAnalysis, Delete, Document, EditPen,
  Flag, Hide, Medal, MoreFilled, Notebook, Paperclip, Picture, Plus, Postcard, Rank, Reading,
  Refresh, School, Star, Suitcase, Tools, Trophy, User, View, Warning
} from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import PaperFrame from '../components/PaperFrame.vue'
import SectionEditor from '../components/SectionEditor.vue'
import ScoreRing from '../components/ScoreRing.vue'
import AvatarPanel from '../components/AvatarPanel.vue'
import {
  OPTIONAL_MODULES, createBlankSection, createItem, isCustomType, isItemBlank, metaOf, pruneSection,
  sectionCountText, sectionHasContent, sectionLines, validateSection
} from '../data/sectionCatalog'
import { templateByCode } from '../data/templatePresets'
import { adminApi, attachmentApi, resumeApi } from '../api'
import { notifyError } from '../api/client'
import { useTemplates } from '../composables/useTemplates'
import { useAuthStore } from '../stores/auth'
import { formatFileSize, timeAgo } from '../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const ICONS = {
  User, Aim, School, Suitcase, Cpu, Flag, Tools, DataAnalysis, Reading,
  Picture, Star, Notebook, Trophy, Medal, Postcard, Coffee, EditPen
}
const UNHIDEABLE = ['basic_info', 'education']
const OPTIONAL_TYPES = new Set(OPTIONAL_MODULES.map((item) => item.type))

const resumeId = Number(route.params.id)
const loading = ref(true)
const loadError = ref('')
const resume = ref(null)
const attachments = ref([])
const avatar = ref(null)
const { all: templates, refresh: loadTemplates, ensure: ensureTemplate } = useTemplates()
const pageCount = ref(1)
const navSections = ref([])
const drafts = reactive({})
const savingTypes = reactive({})
const activeType = ref('')
const prefsSaving = ref(false)
const moduleDialog = ref(false)
const addingType = ref('')
const attachmentDrawer = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)
const fileInput = ref(null)
const restoreCandidate = ref(null)
const titleEditing = ref(false)
const titleDraft = ref('')
const titleInput = ref(null)

let prefsPromise = null
let prefsAgain = false
let draftTimer = 0
let suppressGuard = false

const draftKey = computed(() => `miniwho.drafts.${resumeId}`)
const template = computed(() => templateByCode(resume.value?.templateCode, templates.value))
const styleData = computed(() => resume.value?.style || {})
const scoreGrade = computed(() => {
  const score = resume.value?.score || 0
  if (score >= 90) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '待提升'
  return '需打磨'
})

const sectionMap = computed(() => {
  const map = {}
  for (const section of resume.value?.content?.sections || []) map[section.type] = section
  return map
})

const previewContent = computed(() => {
  if (!resume.value) return { sections: [] }
  const sections = navSections.value.map((section, index) => {
    const source = drafts[section.type] || section
    return { ...JSON.parse(JSON.stringify(source)), visible: section.visible !== false, order: index }
  })
  return { ...resume.value.content, sections }
})

const dirtyCount = computed(() => Object.keys(drafts).filter((type) => isDraftDirty(type)).length)
const hasUnsaved = computed(() => dirtyCount.value > 0)
const isSaving = computed(() => prefsSaving.value || Object.values(savingTypes).some(Boolean))
const saveStateLabel = computed(() => {
  if (isSaving.value) return '正在保存'
  if (hasUnsaved.value) return `${dirtyCount.value} 个模块未保存`
  return '所有更改已写入云端'
})

const missingSuggestions = computed(() => {
  if (!resume.value) return []
  const core = ['basic_info', 'job_intention', 'education', 'work_experience', 'skills_summary', 'self_evaluation']
  return navSections.value
    .filter((section) => section.visible !== false && core.includes(section.type) && !sectionHasContent(section))
    .slice(0, 3)
    .map((section) => ({ type: section.type, label: metaOf(section.type).label }))
})

const customCount = computed(() => (resume.value?.content?.sections || []).filter((section) => isCustomType(section.type)).length)
const moduleOptions = computed(() => OPTIONAL_MODULES.filter((item) => {
  if (item.type === 'custom') return customCount.value < 5
  return !sectionMap.value[item.type]
}))
const canUpload = computed(() => attachments.value.length < 3 && !uploading.value)

function isOptionalType(type) {
  return OPTIONAL_TYPES.has(type) || isCustomType(type)
}

function iconOf(type) {
  return ICONS[metaOf(type).icon] || Document
}

function isHideable(type) {
  return !UNHIDEABLE.includes(type) && metaOf(type).hideable !== false
}

function summaryLines(section) {
  return sectionLines(section).filter(Boolean).slice(0, 3)
}

function isDraftDirty(type) {
  const draft = drafts[type]
  if (!draft) return false
  const source = sectionMap.value[type]
  if (!source) return true
  const normalize = (section) => {
    const copy = JSON.parse(JSON.stringify(pruneSection(section)))
    delete copy.order
    delete copy.visible
    return JSON.stringify(copy)
  }
  return normalize(draft) !== normalize(source)
}

function applyDetail(detail) {
  resume.value = detail
  rebuildNav()
  for (const type of Object.keys(drafts)) {
    if (!sectionMap.value[type]) delete drafts[type]
  }
}

function rebuildNav() {
  const sections = resume.value?.content?.sections || []
  navSections.value = [...sections].sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
}

function loadRecovery() {
  restoreCandidate.value = null
  try {
    const raw = localStorage.getItem(draftKey.value)
    if (!raw) return
    const data = JSON.parse(raw)
    if (!data?.drafts || !Object.keys(data.drafts).length) {
      localStorage.removeItem(draftKey.value)
      return
    }
    if (data.version !== resume.value.contentVersion) {
      localStorage.removeItem(draftKey.value)
      return
    }
    const valid = {}
    for (const [type, draft] of Object.entries(data.drafts)) {
      if (sectionMap.value[type] && draft) valid[type] = draft
    }
    if (!Object.keys(valid).length) {
      localStorage.removeItem(draftKey.value)
      return
    }
    restoreCandidate.value = { ...data, drafts: valid }
  } catch {
    localStorage.removeItem(draftKey.value)
  }
}

function restoreDrafts() {
  if (!restoreCandidate.value) return
  for (const type of Object.keys(drafts)) delete drafts[type]
  Object.assign(drafts, restoreCandidate.value.drafts)
  restoreCandidate.value = null
  ElMessage.success('已恢复上次未保存的编辑，请确认后保存')
  const first = Object.keys(drafts)[0]
  if (first) nextTick(() => scrollToSection(first))
}

function discardRecovery() {
  restoreCandidate.value = null
  localStorage.removeItem(draftKey.value)
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await resumeApi.workbench(resumeId)
    resume.value = data.resume
    attachments.value = data.attachments || []
    avatar.value = normalizeAvatar(data.resume?.avatar)
    rebuildNav()
    loadRecovery()
    await loadTemplates()
    // 简历在用的模板可能已被作者取消分享，按编码兜底取一次配置，纸面才不会退回默认版式。
    await ensureTemplate(data.resume?.templateCode)
    refreshSampleFlag()
    loading.value = false
    const focus = route.query.section
    if (focus && sectionMap.value[focus]) {
      await nextTick()
      openEditor(focus)
    }
  } catch (error) {
    loadError.value = error?.message || '加载简历失败'
    if (error?.code !== 'UNAUTHORIZED') notifyError(error, '加载简历失败')
  } finally {
    loading.value = false
  }
}

async function refreshWorkbench(silent = true) {
  const data = await resumeApi.workbench(resumeId)
  resume.value = data.resume
  attachments.value = data.attachments || []
  avatar.value = normalizeAvatar(data.resume?.avatar)
  rebuildNav()
  for (const type of Object.keys(drafts)) {
    if (!sectionMap.value[type]) delete drafts[type]
  }
  if (!silent) ElMessage.success('已刷新')
}

function normalizeAvatar(node) {
  if (!node) return { present: false, shape: 'rect' }
  return {
    present: node.present !== false && Boolean(node.url),
    shape: node.shape === 'circle' ? 'circle' : 'rect',
    url: node.url || '',
    sizeBytes: node.sizeBytes,
    updatedAt: node.updatedAt
  }
}

function onAvatarUpdate(vo) {
  avatar.value = normalizeAvatar(vo)
}

function sectionEl(type) {
  return document.getElementById(`section-${type}`)
}

function scrollToSection(type) {
  activeType.value = type
  const el = sectionEl(type)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function openEditor(type) {
  const source = sectionMap.value[type]
  if (!source) return
  if (!drafts[type]) {
    const clone = JSON.parse(JSON.stringify(source))
    clone.meta = clone.meta || {}
    clone.items = clone.items || []
    if (!clone.items.length) clone.items.push(createItem(type))
    if (type === 'job_intention' && !clone.meta.displayMode) clone.meta.displayMode = 'title_only'
    drafts[type] = clone
  }
  scrollToSection(type)
}

function cancelEditor(type) {
  delete drafts[type]
  persistDraftsSoon()
}

function handleSectionCommand(command, section) {
  if (command === 'rename') renameModule(section)
  if (command === 'remove') removeModule(section)
}

async function renameModule(section) {
  try {
    const { value } = await ElMessageBox.prompt('模块标题会直接显示在简历上。', '重命名模块', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: section.title,
      inputValidator: (text) => {
        if (!text?.trim()) return '模块标题不能为空'
        if (text.trim().length > 20) return '模块标题不能超过 20 个字符'
        return true
      }
    })
    section.title = value.trim()
    await schedulePrefs()
    await refreshWorkbench(true)
    ElMessage.success('模块标题已更新')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(error, '重命名失败')
  }
}

async function saveSection(type) {
  const draft = drafts[type]
  if (!draft || !resume.value) return
  const error = validateSection(draft)
  if (error) {
    ElMessage.warning(error)
    return
  }
  const source = sectionMap.value[type]
  const payload = pruneSection(JSON.parse(JSON.stringify(draft)))
  payload.id = source.id
  payload.title = source.title
  payload.order = source.order
  payload.visible = source.visible
  savingTypes[type] = true
  try {
    const result = await resumeApi.saveSection(resumeId, type, payload, resume.value.contentVersion)
    resume.value.contentVersion = result.contentVersion
    resume.value.completeness = result.completeness
    resume.value.score = result.score
    delete drafts[type]
    await refreshWorkbench(true)
    ElMessage.success(`「${source.title}」已保存`)
  } catch (error) {
    if (error?.code === 'RESUME_VERSION_CONFLICT') {
      await handleConflict()
      return
    }
    notifyError(error, '保存失败，请稍后重试')
  } finally {
    savingTypes[type] = false
  }
}

async function handleConflict() {
  let keep = true
  try {
    await ElMessageBox.confirm(
      '这份简历已在其他窗口或设备保存了新版本。保留当前编辑后重新加载，再保存时会以你的版本覆盖对应模块；放弃则丢弃本次未保存的修改。',
      '内容版本冲突',
      { confirmButtonText: '保留编辑并刷新', cancelButtonText: '放弃本地修改', type: 'warning' }
    )
  } catch {
    keep = false
  }
  if (!keep) {
    for (const type of Object.keys(drafts)) delete drafts[type]
    localStorage.removeItem(draftKey.value)
  }
  try {
    await refreshWorkbench(true)
    ElMessage.success(keep ? '已加载最新版本，请确认后重新保存' : '已加载最新版本')
  } catch (error) {
    notifyError(error, '刷新失败，请手动刷新页面')
  }
}

async function toggleVisible(section) {
  if (!isHideable(section.type)) return
  if (section.visible === false) {
    section.visible = true
  } else {
    section.visible = false
  }
  activeType.value = section.type
  try {
    await schedulePrefs()
    ElMessage.success(section.visible === false ? '模块已隐藏，不会出现在简历中' : '模块已显示')
  } catch (error) {
    section.visible = !section.visible
    notifyError(error, '更新显示状态失败')
    refreshWorkbench(true).catch(() => {})
  }
}

async function onOrderChange() {
  try {
    await schedulePrefs()
  } catch (error) {
    notifyError(error, '调整顺序失败')
    refreshWorkbench(true).catch(() => {})
  }
}

async function savePrefsOnce() {
  if (!resume.value) return
  const order = navSections.value.map((section) => section.type)
  const hidden = navSections.value.filter((section) => section.visible === false).map((section) => section.type)
  const customTitles = {}
  for (const section of navSections.value) {
    if (isOptionalType(section.type)) customTitles[section.type] = section.title
  }
  const result = await resumeApi.savePrefs(resumeId, {
    order,
    hidden,
    customTitles,
    contentVersion: resume.value.contentVersion
  })
  resume.value.contentVersion = result.contentVersion
  resume.value.completeness = result.completeness
  resume.value.score = result.score
  resume.value.updatedAt = result.savedAt
}

function schedulePrefs() {
  if (prefsPromise) {
    prefsAgain = true
    return prefsPromise
  }
  prefsSaving.value = true
  prefsPromise = (async () => {
    do {
      prefsAgain = false
      await savePrefsOnce()
    } while (prefsAgain)
  })()
  prefsPromise
    .catch((error) => {
      if (error?.code === 'RESUME_VERSION_CONFLICT') handleConflict()
    })
    .finally(() => {
      prefsSaving.value = false
      prefsPromise = null
    })
  return prefsPromise
}

async function flushPrefs() {
  if (prefsPromise) {
    try {
      await prefsPromise
    } catch {
      // The caller retries with a refreshed version.
    }
  }
}

async function addModule(option) {
  if (addingType.value) return
  let customTitle = ''
  let sectionType = option.type
  if (option.type === 'custom') {
    if (customCount.value >= 5) {
      ElMessage.warning('自定义模块最多添加 5 个')
      return
    }
    try {
      const { value } = await ElMessageBox.prompt('给这个模块起一个标题，例如「志愿服务」。', '添加自定义模块', {
        confirmButtonText: '添加',
        cancelButtonText: '取消',
        inputValue: '自定义模块',
        inputValidator: (text) => {
          if (!text?.trim()) return '请输入模块标题'
          if (text.trim().length > 20) return '模块标题不能超过 20 个字符'
          return true
        }
      })
      customTitle = value.trim()
    } catch {
      return
    }
    const usedTypes = new Set((resume.value?.content?.sections || []).map((section) => section.type))
    const slot = [1, 2, 3, 4, 5].find((index) => !usedTypes.has(`custom_${index}`))
    if (!slot) {
      ElMessage.warning('自定义模块最多添加 5 个')
      return
    }
    sectionType = `custom_${slot}`
  }
  await flushPrefs()
  addingType.value = option.type
  try {
    const section = createBlankSection(sectionType, { title: customTitle })
    const detail = await resumeApi.addModule(resumeId, section, resume.value.contentVersion)
    applyDetail(detail)
    moduleDialog.value = false
    await nextTick()
    openEditor(sectionType)
    ElMessage.success('模块已添加，填写后记得保存')
  } catch (error) {
    if (error?.code === 'RESUME_VERSION_CONFLICT') {
      await handleConflict()
    } else {
      notifyError(error, '添加模块失败')
    }
  } finally {
    addingType.value = ''
  }
}

async function removeModule(section) {
  try {
    await ElMessageBox.confirm(
      `移除「${section.title}」会删除其中已填写的内容，且无法恢复。隐藏模块则只是不展示在简历上。`,
      '移除模块',
      { confirmButtonText: '确认移除', cancelButtonText: '改为隐藏', type: 'warning', distinguishCancelAndClose: true }
    )
    await flushPrefs()
    const detail = await resumeApi.removeModule(resumeId, section.type, resume.value.contentVersion)
    delete drafts[section.type]
    applyDetail(detail)
    ElMessage.success('模块已移除')
  } catch (error) {
    if (error === 'cancel') {
      if (isHideable(section.type) && section.visible !== false) toggleVisible(section)
      return
    }
    if (error !== 'close') {
      if (error?.code === 'RESUME_VERSION_CONFLICT') await handleConflict()
      else notifyError(error, '移除模块失败')
    }
  }
}

function startTitleEdit() {
  titleDraft.value = resume.value.title
  titleEditing.value = true
  nextTick(() => titleInput.value?.focus())
}

async function commitTitle() {
  if (!titleEditing.value || !resume.value) return
  titleEditing.value = false
  const title = titleDraft.value.trim()
  if (!title || title === resume.value.title) return
  if (title.length > 50) {
    ElMessage.warning('简历名称不能超过 50 个字符')
    return
  }
  try {
    const detail = await resumeApi.rename(resumeId, title)
    resume.value.title = detail.title
    ElMessage.success('简历名称已更新')
  } catch (error) {
    notifyError(error, '重命名失败')
  }
}

function previewPage(query = {}) {
  router.push({ path: `/resumes/${resumeId}/preview`, query })
}

function reportPage() {
  router.push(`/resumes/${resumeId}/report`)
}

async function duplicateResume() {
  try {
    const detail = await resumeApi.duplicate(resumeId)
    ElMessage.success('已创建副本')
    router.push(`/resumes/${detail.id}`)
  } catch (error) {
    notifyError(error, '复制失败')
  }
}

async function deleteResume() {
  try {
    await ElMessageBox.confirm(
      `删除「${resume.value.title}」后，分享链接会立即失效，且无法恢复。`,
      '删除简历',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await resumeApi.remove(resumeId)
    suppressGuard = true
    ElMessage.success('简历已删除')
    router.push('/dashboard')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(error, '删除失败')
  }
}

function handleTopCommand(command) {
  if (command === 'share') previewPage({ share: '1' })
  if (command === 'duplicate') duplicateResume()
  if (command === 'sample') toggleSample()
  if (command === 'delete') deleteResume()
}

const sampleActive = ref(false)

async function refreshSampleFlag() {
  if (!auth.isAdmin) return
  try {
    const info = await adminApi.sample()
    sampleActive.value = info?.resumeId === resumeId
  } catch {
    sampleActive.value = false
  }
}

/** 示例简历会挂到无鉴权接口上公开，所以开启前必须二次确认。 */
async function toggleSample() {
  const turningOn = !sampleActive.value
  if (turningOn) {
    try {
      await ElMessageBox.confirm(
        '这份简历的内容会公开显示在首页和模板中心，不登录也能看到。确认要把它作为示例吗？',
        '设为首页示例',
        { type: 'warning', confirmButtonText: '确认公开', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  try {
    await adminApi.setSample(resumeId, turningOn)
    sampleActive.value = turningOn
    ElMessage.success(turningOn ? '已设为首页示例，首页与模板中心随后会读到这份内容' : '已取消首页示例')
  } catch (error) {
    notifyError(error, '设置失败')
  }
}

function persistDraftsSoon() {
  clearTimeout(draftTimer)
  draftTimer = setTimeout(persistDrafts, 500)
}

function persistDrafts() {
  if (!resume.value) return
  const dirty = {}
  for (const type of Object.keys(drafts)) {
    if (isDraftDirty(type)) dirty[type] = JSON.parse(JSON.stringify(drafts[type]))
  }
  if (Object.keys(dirty).length) {
    localStorage.setItem(draftKey.value, JSON.stringify({
      version: resume.value.contentVersion,
      savedAt: Date.now(),
      drafts: dirty
    }))
  } else {
    localStorage.removeItem(draftKey.value)
  }
}

function beforeUnload(event) {
  if (!hasUnsaved.value) return
  event.preventDefault()
  event.returnValue = ''
}

function onFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('单个附件不能超过 10MB')
    return
  }
  uploadFile(file)
}

async function uploadFile(file) {
  if (!canUpload.value) {
    ElMessage.warning('附件最多保留 3 个，请先删除旧文件')
    return
  }
  uploading.value = true
  uploadPercent.value = 0
  try {
    await attachmentApi.upload(resumeId, file, (value) => { uploadPercent.value = value })
    await refreshWorkbench(true)
    ElMessage.success('附件已上传')
  } catch (error) {
    notifyError(error, '上传失败')
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}

async function openAttachment(attachment) {
  const tab = window.open('about:blank', '_blank')
  try {
    const blob = await attachmentApi.fetchBlob(attachment.id)
    const url = URL.createObjectURL(blob)
    if (tab && !tab.closed) {
      tab.location.href = url
    } else {
      window.open(url, '_blank', 'noopener')
    }
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (error) {
    if (tab && !tab.closed) tab.close()
    notifyError(error, '读取附件失败')
  }
}

async function deleteAttachment(attachment) {
  try {
    await ElMessageBox.confirm(`确认删除附件「${attachment.fileName}」？`, '删除附件', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    await attachmentApi.remove(attachment.id)
    attachments.value = attachments.value.filter((item) => item.id !== attachment.id)
    ElMessage.success('附件已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(error, '删除附件失败')
  }
}

watch(drafts, persistDraftsSoon, { deep: true })
watch(() => route.query.section, (type) => {
  if (type && sectionMap.value[type]) openEditor(type)
})

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  clearTimeout(draftTimer)
})

onBeforeRouteLeave(async () => {
  if (suppressGuard || !hasUnsaved.value) return true
  try {
    await ElMessageBox.confirm(
      '还有模块没有保存，离开后这些修改会保留为本地草稿，下次打开可恢复。',
      '离开编辑页？',
      { confirmButtonText: '保存草稿并离开', cancelButtonText: '继续编辑', type: 'warning' }
    )
    persistDrafts()
    return true
  } catch {
    return false
  }
})
</script>

<template>
  <AppLayout>
    <div v-if="loading" class="wb-loading">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-else-if="loadError" class="wb-error">
      <div class="wb-error__icon"><el-icon><Warning /></el-icon></div>
      <h1>无法打开这份简历</h1>
      <p>{{ loadError }}</p>
      <div class="wb-error__actions">
        <el-button @click="router.push('/dashboard')">返回我的简历</el-button>
        <el-button type="primary" @click="load">重试</el-button>
      </div>
    </div>

    <div v-else-if="resume" class="workbench">
      <div class="workbench-inner">
        <div v-if="restoreCandidate" class="recovery-banner">
          <div class="recovery-banner__mark"><el-icon><Refresh /></el-icon></div>
          <div class="recovery-banner__text">
            <strong>检测到上次未保存的编辑</strong>
            <span>{{ timeAgo(restoreCandidate.savedAt) }}的本地草稿，包含 {{ Object.keys(restoreCandidate.drafts).length }} 个模块。</span>
          </div>
          <div class="recovery-banner__actions">
            <button type="button" class="text-btn" @click="discardRecovery">丢弃</button>
            <el-button size="small" type="primary" @click="restoreDrafts">恢复编辑</el-button>
          </div>
        </div>

        <header class="wb-top">
          <router-link to="/dashboard" class="wb-back">
            <el-icon><ArrowLeft /></el-icon>
            我的简历
          </router-link>

          <div class="wb-title">
            <el-input
              v-if="titleEditing"
              ref="titleInput"
              v-model="titleDraft"
              class="title-input"
              maxlength="50"
              @keyup.enter="commitTitle"
              @blur="commitTitle"
            />
            <h1 v-else @click="startTitleEdit">
              {{ resume.title }}
              <el-icon class="title-pen"><EditPen /></el-icon>
            </h1>
            <p>
              <span>{{ resume.templateName || template.name }}</span>
              <span>{{ timeAgo(resume.updatedAt) }}更新</span>
              <span class="save-state" :class="{ dirty: hasUnsaved, saving: isSaving }">
                <i></i>{{ saveStateLabel }}
              </span>
            </p>
          </div>

          <div class="wb-actions">
            <el-button plain @click="attachmentDrawer = true">
              <el-icon><Paperclip /></el-icon>
              附件
              <span v-if="attachments.length" class="btn-count">{{ attachments.length }}</span>
            </el-button>
            <el-button plain @click="previewPage()">预览 / 导出</el-button>
            <el-button type="primary" @click="reportPage()">诊断报告</el-button>
            <el-dropdown trigger="click" @command="handleTopCommand">
              <button class="more-menu" type="button" aria-label="更多操作"><el-icon><MoreFilled /></el-icon></button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="share">分享设置</el-dropdown-item>
                  <el-dropdown-item command="duplicate">创建副本</el-dropdown-item>
                  <el-dropdown-item v-if="auth.isAdmin" command="sample">
                    {{ sampleActive ? '取消首页示例' : '设为首页示例' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除简历</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </header>

        <div class="wb-grid">
          <aside class="wb-nav">
            <div class="nav-head">
              <span>模块导航</span>
              <small>{{ navSections.filter((s) => s.visible !== false).length }} / {{ navSections.length }}</small>
            </div>
            <draggable
              v-model="navSections"
              item-key="type"
              handle=".drag-handle"
              class="nav-list"
              ghost-class="nav-ghost"
              :animation="180"
              @end="onOrderChange"
            >
              <template #item="{ element }">
                <div
                  class="nav-row"
                  :class="{ active: activeType === element.type, hidden: element.visible === false, dirty: drafts[element.type] && isDraftDirty(element.type) }"
                  @click="openEditor(element.type)"
                >
                  <span class="drag-handle" title="拖拽排序"><el-icon><Rank /></el-icon></span>
                  <span class="nav-icon"><el-icon><component :is="iconOf(element.type)" /></el-icon></span>
                  <span class="nav-label">{{ element.title }}</span>
                  <span v-if="element.visible === false" class="nav-hidden"><el-icon><Hide /></el-icon></span>
                  <i v-else-if="drafts[element.type] && isDraftDirty(element.type)" class="dirty-dot"></i>
                </div>
              </template>
            </draggable>

            <div class="nav-progress">
              <div class="nav-progress__head">
                <span>完整度</span>
                <strong>{{ resume.completeness || 0 }}%</strong>
              </div>
              <div class="nav-progress__track">
                <span :style="{ width: `${resume.completeness || 0}%` }"></span>
              </div>
              <p>拖动模块可以调整简历上的顺序。</p>
            </div>

            <button class="nav-add" type="button" @click="moduleDialog = true">
              <el-icon><Plus /></el-icon>
              模块库
              <span>{{ moduleOptions.length }}</span>
            </button>
          </aside>

          <main class="wb-editor">
            <div class="editor-intro">
              <div>
                <span class="mono">EDITOR</span>
                <h2>逐块填写，随时保存</h2>
              </div>
              <p>点击「编辑」展开模块。每次保存只更新当前模块，不会影响其他内容。</p>
            </div>

            <section
              v-for="section in navSections"
              :id="`section-${section.type}`"
              :key="section.type"
              class="section-card"
              :class="{
                'is-active': activeType === section.type,
                'is-hidden': section.visible === false,
                'is-editing': Boolean(drafts[section.type])
              }"
            >
              <header class="section-card__head">
                <div class="section-card__title">
                  <span class="section-icon"><el-icon><component :is="iconOf(section.type)" /></el-icon></span>
                  <div>
                    <div class="section-name-row">
                      <h3>{{ section.title }}</h3>
                      <span v-if="section.visible === false" class="mini-tag">未展示</span>
                      <span v-if="drafts[section.type] && isDraftDirty(section.type)" class="mini-tag mini-tag--dirty">未保存</span>
                      <span v-else-if="sectionHasContent(section)" class="mini-tag mini-tag--done">
                        <el-icon><Check /></el-icon>
                      </span>
                    </div>
                    <p>{{ metaOf(section.type).hint }}</p>
                  </div>
                </div>

                <div class="section-actions">
                  <span class="section-count">{{ drafts[section.type] ? '编辑中' : sectionCountText(section) }}</span>
                  <button
                    v-if="isHideable(section.type)"
                    class="icon-btn"
                    type="button"
                    :title="section.visible === false ? '显示在简历中' : '从简历中隐藏'"
                    @click="toggleVisible(section)"
                  >
                    <el-icon><View v-if="section.visible === false" /><Hide v-else /></el-icon>
                  </button>
                  <el-dropdown v-if="isOptionalType(section.type)" trigger="click" @command="(command) => handleSectionCommand(command, section)">
                    <button class="icon-btn" type="button" aria-label="模块操作"><el-icon><MoreFilled /></el-icon></button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="rename">重命名模块</el-dropdown-item>
                        <el-dropdown-item command="remove" divided>移除模块</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <button
                    v-if="!drafts[section.type]"
                    class="edit-btn"
                    type="button"
                    @click="openEditor(section.type)"
                  >
                    <el-icon><EditPen /></el-icon>
                    编辑
                  </button>
                </div>
              </header>

              <AvatarPanel
                v-if="section.type === 'basic_info'"
                class="section-card__avatar"
                :resume-id="resumeId"
                :avatar="avatar"
                @update="onAvatarUpdate"
              />

              <div v-if="drafts[section.type]" class="section-card__editor">
                <SectionEditor
                  :draft="drafts[section.type]"
                  :saving="Boolean(savingTypes[section.type])"
                  :resume-id="resumeId"
                  :template-layout="template?.config?.layout || ''"
                  embedded
                  @save="saveSection(section.type)"
                  @cancel="cancelEditor(section.type)"
                />
              </div>

              <div v-else class="section-card__body" @click="openEditor(section.type)">
                <template v-if="summaryLines(section).length">
                  <p v-for="(line, index) in summaryLines(section)" :key="index" class="summary-line">{{ line }}</p>
                  <span class="summary-more" v-if="section.items?.length > 1 && !metaOf(section.type).single">
                    共 {{ section.items.filter((item) => !isItemBlank(item)).length }} 条内容
                  </span>
                </template>
                <div v-else class="summary-empty">
                  <span>{{ metaOf(section.type).emptyHint }}</span>
                  <strong>开始填写 →</strong>
                </div>
              </div>
            </section>

            <div class="editor-end">
              <el-icon><CircleCheck /></el-icon>
              <div>
                <strong>所有内容都只属于你</strong>
                <p>简历默认私密。只有在你主动创建分享链接后，别人才能按设置查看。</p>
              </div>
              <el-button plain size="small" @click="previewPage()">看看完整效果</el-button>
            </div>
          </main>

          <aside class="wb-side">
            <div class="side-score">
              <div class="side-score__top">
                <ScoreRing :score="resume.score || 0" :size="76" :grade="scoreGrade" label="诊断分" />
                <div>
                  <span class="mono">HEALTH CHECK</span>
                  <h3>简历诊断</h3>
                  <p>基于结构、表达与量化的规则评估，不依赖任何外部 AI 服务。</p>
                </div>
              </div>
              <div v-if="missingSuggestions.length" class="side-score__list">
                <button v-for="item in missingSuggestions" :key="item.type" type="button" @click="openEditor(item.type)">
                  <span>{{ item.label }}还未填写</span>
                  <el-icon><EditPen /></el-icon>
                </button>
              </div>
              <div v-else class="side-score__ok">
                <el-icon><Check /></el-icon>
                核心信息已齐全，可以生成报告了
              </div>
              <button class="side-link" type="button" @click="reportPage()">
                查看完整诊断报告
                <span>→</span>
              </button>
            </div>

            <div class="side-preview">
              <div class="side-preview__head">
                <span>{{ template.name }} · 共 {{ pageCount }} 页实时预览</span>
                <button type="button" @click="previewPage()">放大</button>
              </div>
              <div class="side-preview__stage">
                <PaperFrame
                  :content="previewContent"
                  :template="template"
                  :style-data="styleData"
                  :avatar="avatar"
                  @pages="(count) => (pageCount = count)"
                />
              </div>
              <div class="side-preview__foot">
                <span>A4 · 210 × 297mm</span>
                <button type="button" @click="previewPage()">换模板</button>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>

    <el-dialog v-model="moduleDialog" width="620px" align-center class="module-dialog">
      <template #header>
        <div class="module-dialog__head">
          <span class="mono">MODULE LIBRARY</span>
          <h2>添加模块</h2>
          <p>按岗位需要补充内容，模块可以随时隐藏或移除。</p>
        </div>
      </template>

      <div v-if="moduleOptions.length" class="module-options">
        <button
          v-for="option in moduleOptions"
          :key="option.type"
          type="button"
          class="module-option"
          :disabled="Boolean(addingType)"
          @click="addModule(option)"
        >
          <span class="module-option__icon"><el-icon><component :is="iconOf(option.type)" /></el-icon></span>
          <span class="module-option__text">
            <strong>{{ metaOf(option.type).label }}</strong>
            <small>{{ option.description }}</small>
          </span>
          <span class="module-option__plus">
            <el-icon v-if="addingType === option.type" class="is-loading"><Refresh /></el-icon>
            <el-icon v-else><Plus /></el-icon>
          </span>
        </button>
      </div>
      <el-empty v-else description="所有可选模块都已添加" :image-size="80" />

      <p class="module-dialog__hint">新增模块会出现在简历末尾，可在左侧拖拽调整顺序。</p>
    </el-dialog>

    <el-drawer v-model="attachmentDrawer" size="440px" title="附件简历" class="attachment-drawer">
      <div class="attachment-body">
        <p class="attachment-info">
          可以上传 PDF、Word 或图片版本，最多 3 个、单个不超过 10MB。附件仅自己可见，不会出现在公开分享页。
        </p>

        <input
          ref="fileInput"
          type="file"
          accept=".jpg,.jpeg,.png,.pdf,.doc,.docx,.txt"
          class="file-input"
          @change="onFileChange"
        />
        <button
          type="button"
          class="upload-zone"
          :disabled="!canUpload"
          @click="fileInput?.click()"
        >
          <el-icon class="upload-icon"><Paperclip /></el-icon>
          <strong>{{ attachments.length >= 3 ? '附件数量已达上限' : '点击选择文件' }}</strong>
          <span>支持 PDF / DOC / DOCX / JPG / PNG / TXT</span>
          <div v-if="uploading" class="upload-progress">
            <span :style="{ width: `${uploadPercent}%` }"></span>
          </div>
        </button>

        <div v-if="attachments.length" class="attachment-list">
          <article v-for="attachment in attachments" :key="attachment.id" class="attachment-item">
            <span class="attachment-icon"><el-icon><Document /></el-icon></span>
            <div class="attachment-meta">
              <strong>{{ attachment.fileName }}</strong>
              <small>{{ formatFileSize(attachment.sizeBytes) }} · {{ timeAgo(attachment.createdAt) }}</small>
            </div>
            <button class="icon-btn" type="button" title="打开" @click="openAttachment(attachment)">
              <el-icon><View /></el-icon>
            </button>
            <button class="icon-btn danger" type="button" title="删除" @click="deleteAttachment(attachment)">
              <el-icon><Delete /></el-icon>
            </button>
          </article>
        </div>
        <div v-else class="attachment-empty">还没有上传附件</div>
      </div>
    </el-drawer>
  </AppLayout>
</template>

<style scoped>
.wb-loading {
  width: min(1200px, calc(100% - 64px));
  margin: 0 auto;
  padding: 70px 0;
}

.wb-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120px 24px;
  text-align: center;
}

.wb-error__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 16px;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--copper);
  font-size: 20px;
}

.wb-error h1 {
  font-family: var(--serif);
  font-size: 24px;
  font-weight: 600;
}

.wb-error p {
  margin: 8px 0 22px;
  color: var(--muted);
  font-size: 12px;
}

.wb-error__actions {
  display: flex;
  gap: 10px;
}

.workbench {
  padding: 18px 0 70px;
}

.workbench-inner {
  width: min(1280px, calc(100% - 48px));
  margin: 0 auto;
}

.recovery-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid rgba(176, 95, 60, 0.22);
  border-radius: 10px;
  background: #fdf5ee;
}

.recovery-banner__mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--copper-soft);
  color: var(--copper);
  flex-shrink: 0;
}

.recovery-banner__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1px;
}

.recovery-banner__text strong {
  font-size: 12px;
  font-weight: 600;
}

.recovery-banner__text span {
  color: var(--muted);
  font-size: 10px;
}

.recovery-banner__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.text-btn {
  padding: 0;
  border: none;
  background: none;
  color: var(--muted);
  font-family: var(--sans);
  font-size: 11px;
  cursor: pointer;
}

.text-btn:hover {
  color: var(--danger);
}

.wb-top {
  display: flex;
  align-items: flex-start;
  gap: 22px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--line);
}

.wb-back {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  margin-top: 10px;
  color: var(--muted);
  font-size: 11px;
}

.wb-back:hover {
  color: var(--accent);
}

.wb-title {
  flex: 1;
  min-width: 0;
}

.wb-title h1 {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  font-family: var(--serif);
  font-size: clamp(22px, 2.4vw, 30px);
  font-weight: 600;
  line-height: 1.3;
  cursor: text;
}

.title-pen {
  color: var(--faint);
  font-size: 13px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.wb-title h1:hover .title-pen {
  opacity: 1;
}

.title-input {
  max-width: 460px;
}

.wb-title p {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 5px;
  color: var(--faint);
  font-size: 10px;
}

.wb-title p > span + span::before {
  content: "·";
  margin-right: 9px;
}

.save-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.save-state i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--success);
}

.save-state.dirty {
  color: var(--copper);
}

.save-state.dirty i {
  background: var(--copper);
}

.save-state.saving i {
  background: var(--gold);
  animation: pulse 1s ease infinite;
}

@keyframes pulse {
  50% { opacity: 0.3; }
}

.wb-actions {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-shrink: 0;
  margin-top: 4px;
}

.wb-actions :deep(.el-button) {
  height: 34px;
  padding: 0 13px;
  font-size: 12px;
}

.btn-count {
  margin-left: 3px;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9px;
}

.more-menu {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}

.more-menu:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.wb-grid {
  display: grid;
  grid-template-columns: 198px minmax(0, 1fr) 330px;
  gap: 22px;
  align-items: start;
  margin-top: 24px;
}

.wb-nav {
  position: sticky;
  top: 82px;
  padding: 14px 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(255, 254, 251, 0.7);
}

.nav-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 10px;
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.nav-head small {
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9px;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: calc(100vh - 350px);
  overflow-y: auto;
}

.nav-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 7px 7px 3px;
  border: 1px solid transparent;
  border-radius: 7px;
  color: var(--muted);
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.nav-row:hover {
  background: var(--paper-deep);
  color: var(--ink);
}

.nav-row.active {
  border-color: rgba(10, 125, 118, 0.18);
  background: var(--accent-soft);
  color: var(--accent);
}

.nav-row.hidden {
  opacity: 0.55;
}

.nav-row.dirty .nav-label {
  font-weight: 600;
}

.drag-handle {
  display: grid;
  place-items: center;
  width: 16px;
  height: 20px;
  color: var(--faint);
  font-size: 11px;
  cursor: grab;
}

.drag-handle:active {
  cursor: grabbing;
}

.nav-icon {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 5px;
  background: rgba(25, 34, 30, 0.04);
  font-size: 11px;
  flex-shrink: 0;
}

.nav-label {
  flex: 1;
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-hidden {
  color: var(--faint);
  font-size: 10px;
}

.dirty-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--copper);
}

.nav-ghost {
  opacity: 0.4;
  background: var(--accent-soft);
}

.nav-progress {
  margin-top: 12px;
  padding: 12px 8px 10px;
  border-top: 1px solid var(--line);
}

.nav-progress__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--muted);
  font-size: 9px;
}

.nav-progress__head strong {
  color: var(--ink-soft);
  font-family: var(--mono);
  font-weight: 500;
}

.nav-progress__track {
  height: 3px;
  margin-top: 7px;
  overflow: hidden;
  border-radius: 3px;
  background: var(--canvas-deep);
}

.nav-progress__track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
  transition: width 0.4s ease;
}

.nav-progress p {
  margin-top: 8px;
  color: var(--faint);
  font-size: 9px;
  line-height: 1.6;
}

.nav-add {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  margin-top: 8px;
  padding: 9px 10px;
  border: 1px dashed var(--line-strong);
  border-radius: 7px;
  background: none;
  color: var(--muted);
  font-family: var(--sans);
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-add:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--paper);
}

.nav-add span {
  margin-left: auto;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9px;
}

.wb-editor {
  min-width: 0;
}

.editor-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
  padding: 0 2px;
}

.editor-intro .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.editor-intro h2 {
  margin-top: 3px;
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 600;
}

.editor-intro p {
  max-width: 330px;
  color: var(--faint);
  font-size: 10px;
  line-height: 1.7;
  text-align: right;
}

.section-card {
  margin-bottom: 10px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--paper);
  scroll-margin-top: 82px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.section-card.is-active {
  border-color: rgba(10, 125, 118, 0.32);
}

.section-card.is-editing {
  border-color: rgba(10, 125, 118, 0.42);
  box-shadow: 0 20px 44px -34px rgba(25, 34, 30, 0.4);
}

.section-card.is-hidden {
  background: rgba(255, 254, 251, 0.52);
}

.section-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
}

.section-card__title {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  min-width: 0;
}

.section-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper-deep);
  color: var(--muted);
  font-size: 14px;
}

.section-card.is-editing .section-icon {
  border-color: rgba(10, 125, 118, 0.2);
  background: var(--accent-soft);
  color: var(--accent);
}

.section-name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.section-name-row h3 {
  font-family: var(--serif);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.mini-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--canvas-deep);
  color: var(--faint);
  font-size: 8px;
  letter-spacing: 0.04em;
}

.mini-tag--dirty {
  background: var(--copper-soft);
  color: var(--copper);
}

.mini-tag--done {
  background: var(--accent-soft);
  color: var(--accent);
}

.section-card__title p {
  max-width: 440px;
  margin-top: 2px;
  color: var(--faint);
  font-size: 10px;
  line-height: 1.65;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
  padding-top: 2px;
}

.section-count {
  margin-right: 5px;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9px;
  white-space: nowrap;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  background: none;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.icon-btn:hover {
  border-color: var(--line);
  background: var(--paper-deep);
  color: var(--ink);
}

.icon-btn.danger:hover {
  border-color: rgba(181, 72, 63, 0.25);
  background: #fbeeec;
  color: var(--danger);
}

.edit-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 3px;
  padding: 5px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: transparent;
  color: var(--ink-soft);
  font-family: var(--sans);
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.edit-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.section-card__avatar {
  margin: 2px 16px 14px 57px;
}

.section-card__body {
  padding: 0 16px 15px 57px;
  cursor: pointer;
}

.summary-line {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.7;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.summary-line + .summary-line {
  margin-top: 2px;
}

.summary-more {
  display: inline-block;
  margin-top: 5px;
  color: var(--faint);
  font-size: 9px;
}

.summary-empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 11px 13px;
  border: 1px dashed var(--line-strong);
  border-radius: 7px;
  color: var(--faint);
  font-size: 10px;
}

.summary-empty strong {
  flex-shrink: 0;
  color: var(--accent);
  font-size: 10px;
  font-weight: 500;
}

.section-card__editor {
  padding: 0 16px 16px;
}

.section-card__editor :deep(.section-editor) {
  margin-top: 0;
  padding: 14px 14px 12px;
  border-top: 1px dashed var(--line);
  border-radius: 0 0 8px 8px;
  background: var(--paper-deep);
}

.editor-end {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: rgba(255, 254, 251, 0.5);
}

.editor-end > .el-icon {
  color: var(--success);
  font-size: 18px;
}

.editor-end > div {
  flex: 1;
}

.editor-end strong {
  font-size: 11px;
  font-weight: 600;
}

.editor-end p {
  margin-top: 2px;
  color: var(--faint);
  font-size: 9px;
}

.wb-side {
  position: sticky;
  top: 82px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: calc(100vh - 96px);
  overflow-y: auto;
  scrollbar-width: none;
}

.wb-side::-webkit-scrollbar {
  display: none;
}

.side-score {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--paper);
}

.side-score__top {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.side-score__top .mono {
  color: var(--faint);
  font-size: 8px;
  letter-spacing: 0.14em;
}

.side-score__top h3 {
  margin-top: 2px;
  font-family: var(--serif);
  font-size: 16px;
  font-weight: 600;
}

.side-score__top p {
  margin-top: 3px;
  color: var(--faint);
  font-size: 9px;
  line-height: 1.6;
}

.side-score__top :deep(.score-ring__inner) {
  background: var(--paper);
}

.side-score__top :deep(.score-ring strong) {
  font-size: 23px;
}

.side-score__top :deep(.score-ring span) {
  font-size: 8px;
}

.side-score__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 13px;
  padding-top: 11px;
  border-top: 1px solid var(--line);
}

.side-score__list button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 4px 0;
  border: none;
  background: none;
  color: var(--muted);
  font-family: var(--sans);
  font-size: 10px;
  text-align: left;
  cursor: pointer;
}

.side-score__list button:hover {
  color: var(--accent);
}

.side-score__list .el-icon {
  font-size: 10px;
}

.side-score__ok {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 13px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 10px;
}

.side-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 12px;
  padding: 9px 0 0;
  border: none;
  border-top: 1px solid var(--line);
  background: none;
  color: var(--accent);
  font-family: var(--sans);
  font-size: 10px;
  cursor: pointer;
}

.side-preview {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--paper);
}

.side-preview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 9px;
}

.side-preview__head button {
  padding: 0;
  border: none;
  background: none;
  color: var(--accent);
  font-family: var(--sans);
  font-size: 9px;
  cursor: pointer;
}

.side-preview__stage {
  padding: 12px;
  background:
    radial-gradient(400px 300px at 50% -20%, rgba(10, 125, 118, 0.08), transparent),
    var(--canvas-deep);
}

.side-preview__stage :deep(.paper-scaler__paper) {
  box-shadow: 0 18px 36px -20px rgba(25, 34, 30, 0.38);
}

.side-preview__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid var(--line);
  color: var(--faint);
  font-family: var(--mono);
  font-size: 8px;
}

.side-preview__foot button {
  padding: 0;
  border: none;
  background: none;
  color: var(--muted);
  font-family: var(--sans);
  font-size: 9px;
  cursor: pointer;
}

.side-preview__foot button:hover {
  color: var(--accent);
}

.module-dialog__head .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.module-dialog__head h2 {
  margin-top: 5px;
  font-family: var(--serif);
  font-size: 23px;
  font-weight: 600;
}

.module-dialog__head p {
  margin-top: 4px;
  color: var(--muted);
  font-size: 10px;
}

.module-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.module-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper);
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.module-option:hover:not(:disabled) {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.module-option:disabled {
  opacity: 0.55;
  cursor: wait;
}

.module-option__icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 7px;
  background: var(--paper-deep);
  color: var(--accent);
  font-size: 13px;
}

.module-option__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.module-option__text strong {
  font-size: 11px;
  font-weight: 600;
}

.module-option__text small {
  overflow: hidden;
  color: var(--muted);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-option__plus {
  color: var(--faint);
  font-size: 12px;
}

.module-dialog__hint {
  margin-top: 14px;
  color: var(--faint);
  font-size: 9px;
  text-align: center;
}

.attachment-body {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.attachment-info {
  padding: 10px 12px;
  border-radius: 7px;
  background: var(--paper-deep);
  color: var(--muted);
  font-size: 10px;
  line-height: 1.75;
}

.file-input {
  display: none;
}

.upload-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  margin-top: 14px;
  padding: 22px 16px;
  border: 1px dashed var(--line-strong);
  border-radius: 9px;
  background: var(--paper-deep);
  color: var(--muted);
  font-family: var(--sans);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.upload-zone:hover:not(:disabled) {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.upload-zone:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.upload-icon {
  margin-bottom: 4px;
  color: var(--accent);
  font-size: 20px;
}

.upload-zone strong {
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 500;
}

.upload-zone span {
  color: var(--faint);
  font-size: 9px;
}

.upload-progress {
  width: 70%;
  height: 3px;
  margin-top: 9px;
  overflow: hidden;
  border-radius: 3px;
  background: var(--canvas-deep);
}

.upload-progress span {
  display: block;
  height: 100%;
  background: var(--accent);
  transition: width 0.2s ease;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 18px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 11px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper);
}

.attachment-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 6px;
  background: var(--paper-deep);
  color: var(--muted);
  font-size: 13px;
}

.attachment-meta {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.attachment-meta strong {
  overflow: hidden;
  font-size: 10px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta small {
  color: var(--faint);
  font-size: 9px;
}

.attachment-empty {
  padding: 30px 0;
  color: var(--faint);
  font-size: 10px;
  text-align: center;
}

:global(.attachment-drawer .el-drawer__header) {
  margin-bottom: 8px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
  font-family: var(--serif);
}

:global(.attachment-drawer .el-drawer__body) {
  padding-top: 10px;
}

@media (max-width: 1240px) {
  .wb-grid {
    grid-template-columns: 190px minmax(0, 1fr) 300px;
    gap: 18px;
  }
}

@media (max-width: 1120px) {
  .wb-grid {
    grid-template-columns: 186px minmax(0, 1fr);
  }

  .wb-side {
    display: none;
  }

  .nav-list {
    max-height: none;
  }
}

@media (max-width: 880px) {
  .workbench {
    padding-top: 12px;
  }

  .workbench-inner {
    width: calc(100% - 32px);
  }

  .wb-top {
    flex-wrap: wrap;
    gap: 12px;
  }

  .wb-back {
    width: 100%;
    margin-top: 0;
  }

  .wb-title {
    width: 100%;
    flex: auto;
  }

  .wb-actions {
    width: 100%;
    flex-wrap: wrap;
    margin-top: 0;
  }

  .wb-actions :deep(.el-button) {
    flex: 1;
  }

  .wb-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .wb-grid > * {
    min-width: 0;
  }

  .wb-nav {
    position: static;
    display: flex;
    align-items: stretch;
    gap: 6px;
    padding: 10px;
  }

  .nav-head,
  .nav-progress {
    display: none;
  }

  .nav-list {
    flex: 1;
    min-width: 0;
    flex-direction: row;
    gap: 4px;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .nav-add {
    width: auto;
    margin-top: 0;
    flex-shrink: 0;
    padding: 6px 9px;
    white-space: nowrap;
  }

  .nav-add span {
    display: none;
  }

  .nav-list > * {
    flex-shrink: 0;
  }

  .nav-row {
    padding: 6px 9px;
  }

  .drag-handle,
  .nav-icon,
  .nav-hidden {
    display: none;
  }

  .nav-label {
    font-size: 10px;
  }

  .editor-intro {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
  }

  .editor-intro p {
    text-align: left;
  }

  .section-card__head {
    flex-direction: column;
    gap: 9px;
  }

  .section-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .section-card__body {
    padding-left: 16px;
  }

  .section-card__avatar {
    margin-left: 16px;
  }

  .section-card__title p {
    display: none;
  }

  .recovery-banner {
    flex-wrap: wrap;
  }

  .recovery-banner__actions {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 560px) {
  .wb-actions :deep(.el-button:nth-child(2)) {
    display: none;
  }

  .editor-end {
    flex-wrap: wrap;
  }

  .module-options {
    grid-template-columns: 1fr;
  }

  :global(.module-dialog) {
    width: calc(100% - 24px) !important;
  }

  :global(.attachment-drawer) {
    width: calc(100% - 24px) !important;
  }
}
</style>
