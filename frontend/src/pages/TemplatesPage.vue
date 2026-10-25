<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Search, User, View, Check } from '@element-plus/icons-vue'
import TemplateThumb from '../components/TemplateThumb.vue'
import PaperFrame from '../components/PaperFrame.vue'
import { TEMPLATE_CATEGORIES } from '../data/templatePresets'
import { useAuthStore } from '../stores/auth'
import { useTemplates } from '../composables/useTemplates'
import { useSampleResume } from '../composables/useSampleResume'
import { resumeApi, templateApi } from '../api'
import { notifyError } from '../api/client'
import { saveBlob } from '../utils/format'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const { official, library: templates, refresh: refreshTemplates } = useTemplates()
const { content: sampleContent, avatar: sampleAvatar } = useSampleResume()
const category = ref('全部')
const keyword = ref('')
const dialogVisible = ref(false)
const selectedTemplate = ref(null)
const language = ref('zh')
const useSample = ref(true)
const creating = ref(false)

// 社区模板的 meta.category 是自由文本，筛选栏要跟着实际存在的分类长。
const categories = computed(() => {
  const set = new Set(TEMPLATE_CATEGORIES)
  templates.value.forEach((template) => {
    if (template.category) set.add(template.category)
  })
  return [...set]
})

const officialCount = computed(() => official.value.length)
const communityCount = computed(() => templates.value.length - official.value.length)

// 别人分享的包只有在作者开放导出时才能下载；自己的包始终能下载。
function canExport(template) {
  if (template.source !== 'imported') return true
  return template.isMine || template.exportable !== false
}

const filtered = computed(() => templates.value.filter((template) => {
  const inCategory = category.value === '全部' || template.category === category.value
  const text = `${template.name}${template.category}${template.description}${(template.tags || []).join('')}`.toLowerCase()
  const inKeyword = !keyword.value.trim() || text.includes(keyword.value.trim().toLowerCase())
  return inCategory && inKeyword
}))

function openTemplate(template) {
  selectedTemplate.value = template
  language.value = 'zh'
  useSample.value = true
  dialogVisible.value = true
}

function useTemplate(template) {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { mode: 'register', redirect: `/templates?use=${template.code}` } })
    return
  }
  openTemplate(template)
}

watch(() => route.query.use, (code) => {
  if (!code || !auth.isLoggedIn) return
  const template = templates.value.find((item) => item.code === code)
  if (template) openTemplate(template)
  const query = { ...route.query }
  delete query.use
  router.replace({ path: '/templates', query })
})

watch(language, (value) => {
  if (value === 'en') useSample.value = false
})

async function createResume() {
  if (!selectedTemplate.value) return
  creating.value = true
  try {
    const detail = await resumeApi.create({
      title: useSample.value ? '示例简历 · 请替换为你的内容' : '',
      language: language.value,
      templateCode: selectedTemplate.value.code,
      useSample: language.value === 'zh' && useSample.value
    })
    ElMessage.success('简历已创建，开始填写吧')
    dialogVisible.value = false
    router.push(`/resumes/${detail.id}`)
  } catch (error) {
    notifyError(error, '创建简历失败')
  } finally {
    creating.value = false
  }
}

function countOf(cat) {
  if (cat === '全部') return templates.value.length
  return templates.value.filter((item) => item.category === cat).length
}

async function exportPackage(template) {
  try {
    const blob = await templateApi.exportPackage(template.code)
    saveBlob(blob, `${template.code}-${template.version || '1.0.0'}.json`)
    ElMessage.success('模板包已下载，改完可以重新导入')
  } catch (error) {
    notifyError(error, '导出模板包失败')
  }
}

onMounted(async () => {
  await refreshTemplates()
  const code = route.query.use
  if (code && auth.isLoggedIn) {
    const template = templates.value.find((item) => item.code === code)
    if (template) openTemplate(template)
  }
})
</script>

<template>
  <div class="templates-page">
    <section class="templates-hero">
      <div class="container">
        <div class="templates-hero__inner">
          <div>
            <div class="eyebrow">TEMPLATE LIBRARY</div>
            <h1 class="display">从一个舒服的版式开始。</h1>
            <p>{{ templates.length }} 套模板，全部免费。官方版式随应用发版，社区版式由本站用户分享；每一份预览都由真实结构化内容渲染，不是示意图。选好后可以随时切换，内容不会丢；允许导出的版式还能下载成模板包，改一改再分享。</p>
          </div>
          <div class="templates-hero__meta">
            <span class="mono">ALL FREE / OPEN SOURCE</span>
            <strong>{{ templates.length }}</strong>
            <small>套版式 · 官方 {{ officialCount }} / 社区 {{ communityCount }}</small>
          </div>
        </div>
      </div>
    </section>

    <section class="templates-body">
      <div class="container">
        <div class="filter-bar">
          <div class="category-pills">
            <button
              v-for="cat in categories"
              :key="cat"
              type="button"
              :class="{ active: category === cat }"
              @click="category = cat"
            >
              {{ cat }}
              <span>{{ countOf(cat) }}</span>
            </button>
          </div>
          <div class="filter-search">
            <el-input v-model="keyword" placeholder="搜索模板、场景或标签" clearable>
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>
        </div>

        <div v-if="filtered.length" class="template-grid">
          <article v-for="tpl in filtered" :key="tpl.code" class="template-card">
            <button class="template-card__preview" type="button" aria-label="预览模板" @click="openTemplate(tpl)">
              <TemplateThumb :content="sampleContent" :template="tpl" :avatar="sampleAvatar" />
              <span class="template-card__view">
                <el-icon><View /></el-icon> 预览完整版式
              </span>
            </button>
            <div class="template-card__info">
              <div class="template-card__title">
                <h2>{{ tpl.name }}</h2>
                <span class="tag tag--accent">{{ tpl.category }}</span>
                <span v-if="tpl.source !== 'imported'" class="tag">官方</span>
                <span v-else class="tag">
                  <el-icon><User /></el-icon>{{ tpl.author || '未署名' }}
                </span>
              </div>
              <p>{{ tpl.description }}</p>
              <div class="template-card__tags">
                <span v-for="tag in tpl.tags" :key="tag">{{ tag }}</span>
              </div>
              <div class="template-card__actions">
                <button class="use-btn" type="button" @click="useTemplate(tpl)">
                  使用此模板
                  <span>→</span>
                </button>
                <button class="preview-link" type="button" @click="openTemplate(tpl)">预览</button>
                <button v-if="canExport(tpl)" class="preview-link" type="button" @click="exportPackage(tpl)">导出模板包</button>
                <span v-else class="preview-link is-disabled" title="作者没有开放这个模板包的导出">仅可使用</span>
              </div>
            </div>
          </article>
        </div>

        <el-empty v-else description="没有找到匹配的模板，换个关键词试试">
          <el-button @click="keyword = ''; category = '全部'">清除筛选</el-button>
        </el-empty>

        <div class="template-note">
          <span class="template-note__mark serif">“</span>
          <p>
            模板是一套套可以分享的「模板包」：只描述版式令牌与净化过的样式，不含任何脚本。
            想用自己的版式，到 <router-link to="/my-templates">我的模板</router-link> 导入模板包，
            再决定要不要分享到这里；分享时还能设置别人是否可以把包下载走。
          </p>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      class="template-dialog"
      width="980px"
      align-center
      destroy-on-close
    >
      <template #header>
        <div v-if="selectedTemplate" class="dialog-title">
          <div>
            <span class="mono">TEMPLATE / {{ selectedTemplate.code.toUpperCase() }}</span>
            <h2>{{ selectedTemplate.name }}</h2>
          </div>
          <span class="tag tag--accent">{{ selectedTemplate.category }} · 免费</span>
        </div>
      </template>

      <div v-if="selectedTemplate" class="dialog-layout">
        <div class="dialog-preview">
          <PaperFrame :content="sampleContent" :template="selectedTemplate" :avatar="sampleAvatar" :style-data="{ showBrandFooter: true }" />
        </div>
        <div class="dialog-side">
          <p class="dialog-desc">{{ selectedTemplate.description }}</p>
          <div class="dialog-tags">
            <span v-for="tag in selectedTemplate.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>

          <div class="choice-block">
            <label>简历语言</label>
            <el-radio-group v-model="language" class="choice-group">
              <el-radio-button label="zh" value="zh">中文</el-radio-button>
              <el-radio-button label="en" value="en">English</el-radio-button>
            </el-radio-group>
            <p v-if="language === 'en'" class="choice-tip">英文模板使用英文模块标题，示例内容仅提供中文版本。</p>
          </div>

          <div class="choice-block">
            <label>初始内容</label>
            <div class="content-options">
              <button
                type="button"
                :class="{ active: useSample }"
                :disabled="language === 'en'"
                @click="useSample = true"
              >
                <span class="option-check"><el-icon v-if="useSample"><Check /></el-icon></span>
                <strong>使用示例内容</strong>
                <small>一份完整的虚构简历，便于快速体验，记得替换成你的履历。</small>
              </button>
              <button type="button" :class="{ active: !useSample }" @click="useSample = false">
                <span class="option-check"><el-icon v-if="!useSample"><Check /></el-icon></span>
                <strong>从空白开始</strong>
                <small>只保留基本信息等必要模块，由你一点点填写。</small>
              </button>
            </div>
          </div>

          <div class="dialog-summary">
            <span>版式</span><strong>{{ selectedTemplate.config?.layout || 'minimal' }}</strong>
            <span>字体</span><strong>{{ selectedTemplate.config?.font === 'serif' ? '衬线' : '无衬线' }}</strong>
            <span>价格</span><strong class="free">永久免费</strong>
          </div>

          <el-button type="primary" size="large" class="dialog-cta" :loading="creating" @click="createResume">
            使用「{{ selectedTemplate.name }}」开始
          </el-button>
          <p class="dialog-hint">创建后仍可随时在预览页切换模板，内容与模块顺序不会改变。</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.templates-page {
  min-height: 100vh;
}

.templates-hero {
  padding: 72px 0 54px;
  border-bottom: 1px solid var(--line);
}

.templates-hero__inner {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 60px;
}

.templates-hero h1 {
  margin: 18px 0 16px;
  font-size: clamp(36px, 4.4vw, 58px);
}

.templates-hero p {
  max-width: 580px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.95;
}

.templates-hero__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
  gap: 2px;
  color: var(--muted);
}

.templates-hero__meta .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.15em;
}

.templates-hero__meta strong {
  font-family: var(--serif);
  font-size: 66px;
  font-weight: 500;
  line-height: 1;
  color: var(--accent);
}

.templates-hero__meta small {
  font-size: 10px;
}

.templates-body {
  padding: 36px 0 100px;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 38px;
}

.category-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-pills button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 254, 251, 0.5);
  color: var(--muted);
  font-family: var(--sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.category-pills button span {
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9px;
}

.category-pills button:hover {
  border-color: var(--line-strong);
  color: var(--ink);
}

.category-pills button.active {
  border-color: var(--accent);
  background: var(--accent);
  color: #f6f9f6;
}

.category-pills button.active span {
  color: rgba(246, 249, 246, 0.65);
}

.filter-search {
  width: 260px;
  flex-shrink: 0;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 40px 26px;
}

.template-card {
  min-width: 0;
}

.template-card__preview {
  position: relative;
  display: block;
  width: 100%;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
}

.template-card__view {
  position: absolute;
  left: 50%;
  bottom: 18px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(25, 34, 30, 0.82);
  color: #f6f3ea;
  font-size: 10px;
  letter-spacing: 0.03em;
  white-space: nowrap;
  opacity: 0;
  transform: translate(-50%, 6px);
  transition: opacity 0.25s ease, transform 0.25s ease;
  backdrop-filter: blur(4px);
}

.template-card__preview:hover .template-card__view {
  opacity: 1;
  transform: translate(-50%, 0);
}

.template-card__info {
  padding: 16px 4px 0;
}

.template-card__title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.template-card__title h2 {
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 600;
}

.template-card__info > p {
  min-height: 40px;
  margin-top: 7px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.75;
}

.template-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 12px;
}

.template-card__tags span {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.03em;
}

.template-card__tags span + span::before {
  content: "·";
  margin-right: 5px;
}

.template-card__actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
}

.use-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 0;
  border: none;
  border-bottom: 1px solid var(--accent);
  background: none;
  color: var(--accent);
  font-family: var(--sans);
  font-size: 11px;
  cursor: pointer;
}

.use-btn span {
  transition: transform 0.2s ease;
}

.use-btn:hover span {
  transform: translateX(3px);
}

.preview-link {
  border: none;
  background: none;
  color: var(--faint);
  font-family: var(--sans);
  font-size: 11px;
  cursor: pointer;
}

.preview-link:hover {
  color: var(--ink);
}

.preview-link.is-disabled {
  color: var(--faint);
  font-style: italic;
  cursor: help;
}

.preview-link.is-disabled:hover {
  color: var(--faint);
}

.template-note {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  max-width: 760px;
  margin: 80px auto 0;
  padding-top: 28px;
  border-top: 1px solid var(--line);
}

.template-note__mark {
  color: var(--gold);
  font-size: 30px;
  line-height: 0.9;
}

.template-note p {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.9;
}

.template-note a {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
}

:global(.template-dialog) {
  border-radius: 16px;
}

:global(.template-dialog .el-dialog__body) {
  padding-top: 6px;
}

.dialog-title {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.dialog-title .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.dialog-title h2 {
  margin-top: 6px;
  font-family: var(--serif);
  font-size: 26px;
  font-weight: 600;
}

.dialog-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
  gap: 30px;
}

.dialog-preview {
  max-height: 68vh;
  overflow-y: auto;
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--canvas-deep);
}

.dialog-side {
  display: flex;
  flex-direction: column;
  padding: 4px 0;
}

.dialog-desc {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.85;
}

.dialog-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 12px;
}

.choice-block {
  margin-top: 24px;
}

.choice-block > label {
  display: block;
  margin-bottom: 9px;
  color: var(--ink-soft);
  font-size: 12px;
}

.choice-group {
  width: 100%;
}

.choice-tip {
  margin-top: 7px;
  color: var(--faint);
  font-size: 10px;
  line-height: 1.6;
}

.content-options {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.content-options button {
  display: grid;
  grid-template-columns: 18px 1fr;
  grid-template-rows: auto auto;
  gap: 2px 9px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--paper);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.content-options button:hover:not(:disabled) {
  border-color: var(--line-strong);
}

.content-options button.active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.content-options button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.option-check {
  grid-row: 1 / 3;
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  margin-top: 1px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  color: #fff;
  font-size: 10px;
}

.content-options button.active .option-check {
  border-color: var(--accent);
  background: var(--accent);
}

.content-options strong {
  color: var(--ink);
  font-size: 12px;
  font-weight: 600;
}

.content-options small {
  color: var(--muted);
  font-size: 10px;
  line-height: 1.55;
}

.dialog-summary {
  display: grid;
  grid-template-columns: auto 1fr auto 1fr;
  gap: 8px 14px;
  margin-top: 24px;
  padding: 13px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  font-size: 10px;
}

.dialog-summary span {
  color: var(--faint);
}

.dialog-summary strong {
  color: var(--ink-soft);
  font-weight: 500;
  text-align: right;
}

.dialog-summary .free {
  color: var(--accent);
}

.dialog-cta {
  width: 100%;
  margin-top: 22px;
}

.dialog-hint {
  margin-top: 10px;
  color: var(--faint);
  font-size: 9px;
  line-height: 1.6;
  text-align: center;
}

@media (max-width: 1050px) {
  .template-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dialog-layout {
    grid-template-columns: 1fr;
  }

  .dialog-preview {
    max-height: 46vh;
  }
}

@media (max-width: 800px) {
  .templates-hero__inner {
    flex-direction: column;
    align-items: flex-start;
    gap: 28px;
  }

  .templates-hero__meta {
    align-items: flex-start;
    flex-direction: row;
    align-items: baseline;
    gap: 10px;
  }

  .templates-hero__meta strong {
    font-size: 38px;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-search {
    width: 100%;
  }
}

@media (max-width: 620px) {
  .templates-hero {
    padding: 50px 0 38px;
  }

  .template-grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .templates-body {
    padding-bottom: 70px;
  }

  .template-note {
    margin-top: 50px;
  }

  :global(.template-dialog) {
    width: calc(100% - 24px) !important;
    margin: 12px auto;
  }

  .dialog-preview {
    padding: 12px;
  }
}
</style>
