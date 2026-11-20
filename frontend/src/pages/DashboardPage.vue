<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight, CopyDocument, Delete, Document, EditPen, MoreFilled, Plus, Search, Select, View
} from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import { resumeApi } from '../api'
import { notifyError } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useTemplates } from '../composables/useTemplates'
import TemplateThumb from '../components/TemplateThumb.vue'
import { useSampleResume } from '../composables/useSampleResume'
import { TEMPLATE_CATEGORIES, configOf, templateByCode } from '../data/templatePresets'
import { greeting, timeAgo } from '../utils/format'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const loading = ref(true)
const resumes = ref([])
const { all: templates, refresh: loadTemplates } = useTemplates()
const filter = ref('all')
const keyword = ref('')

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  title: '',
  language: 'zh',
  templateCode: 'qinghe',
  useSample: true
})
/** 弹窗里的版式预览用真实渲染，和模板中心同一套示例数据。 */
const { content: sampleContent, avatar: sampleAvatar } = useSampleResume()
const createCategory = ref('全部')
/** 只列出当前确实有模板的分类，避免点进去是空的。 */
const createCategories = computed(() => {
  const used = new Set(templates.value.map((tpl) => tpl.category).filter(Boolean))
  return TEMPLATE_CATEGORIES.filter((name) => name === '全部' || used.has(name))
})
const createTemplates = computed(() => (createCategory.value === '全部'
  ? templates.value
  : templates.value.filter((tpl) => tpl.category === createCategory.value)))
const selectedTemplate = computed(() => templateByCode(createForm.templateCode, templates.value))

const displayName = computed(() => auth.user?.nickname || auth.user?.username || '你')

const filteredResumes = computed(() => resumes.value.filter((resume) => {
  if (filter.value === 'complete' && (resume.completeness || 0) < 100) return false
  if (filter.value === 'draft' && (resume.completeness || 0) >= 100) return false
  const text = `${resume.title}${resume.targetJob || ''}${resume.templateName || ''}`.toLowerCase()
  return !keyword.value.trim() || text.includes(keyword.value.trim().toLowerCase())
}))

const stats = computed(() => {
  const list = resumes.value
  const best = list.reduce((max, item) => Math.max(max, item.score || 0), 0)
  const pending = list.filter((item) => (item.completeness || 0) < 100).length
  return [
    { label: '简历总数', value: list.length, suffix: '份', note: '最多可创建 50 份' },
    { label: '最高得分', value: best || '—', suffix: best ? '分' : '', note: '由规则引擎本地评估' },
    { label: '待完善', value: pending, suffix: '份', note: pending ? '补全信息可提升通过率' : '全部信息已完整' }
  ]
})

function accentOf(code) {
  return configOf(templateByCode(code, templates.value)).accent || '#2f6f62'
}

function templateName(code) {
  return templateByCode(code, templates.value).name
}

function filterOf(code) {
  return templateByCode(code, templates.value).config?.layout || 'minimal'
}

// 社区模板来自别人分享，标签要说清来源，免得用户以为是自己的包。
function choiceHint(template) {
  if (template.source !== 'imported') return template.category
  const who = template.author || '未署名'
  return template.isMine ? `我的模板 · ${who}` : `社区 · ${who}`
}

function openCreate(templateCode = 'qinghe') {
  createForm.title = ''
  createForm.language = 'zh'
  createForm.templateCode = templateCode
  createForm.useSample = true
  createVisible.value = true
}

async function createResume() {
  creating.value = true
  try {
    const detail = await resumeApi.create({
      title: createForm.title.trim(),
      language: createForm.language,
      templateCode: createForm.templateCode,
      useSample: createForm.language === 'zh' && createForm.useSample
    })
    createVisible.value = false
    ElMessage.success('简历已创建')
    router.push(`/resumes/${detail.id}`)
  } catch (error) {
    notifyError(error, '创建简历失败')
  } finally {
    creating.value = false
  }
}

async function loadResumes(silent = false) {
  if (!silent) loading.value = true
  try {
    resumes.value = await resumeApi.list()
  } catch (error) {
    notifyError(error, '加载简历失败')
  } finally {
    loading.value = false
  }
}

async function handleCommand(command, resume) {
  if (command === 'preview') {
    router.push(`/resumes/${resume.id}/preview`)
    return
  }
  if (command === 'rename') {
    try {
      const { value } = await ElMessageBox.prompt('给这份简历起一个容易辨认的名字。', '重命名简历', {
        confirmButtonText: '保存',
        cancelButtonText: '取消',
        inputValue: resume.title,
        inputValidator: (text) => {
          if (!text?.trim()) return '简历名称不能为空'
          if (text.trim().length > 50) return '简历名称不能超过 50 个字符'
          return true
        }
      })
      const detail = await resumeApi.rename(resume.id, value.trim())
      resume.title = detail.title
      ElMessage.success('已重命名')
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') notifyError(error, '重命名失败')
    }
    return
  }
  if (command === 'duplicate') {
    try {
      const detail = await resumeApi.duplicate(resume.id)
      ElMessage.success('已创建副本')
      router.push(`/resumes/${detail.id}`)
    } catch (error) {
      notifyError(error, '复制失败')
    }
    return
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm(
        `删除「${resume.title}」后，所有分享链接也会立即失效，且无法恢复。`,
        '删除简历',
        { confirmButtonText: '确认删除', cancelButtonText: '再想想', type: 'warning' }
      )
      await resumeApi.remove(resume.id)
      resumes.value = resumes.value.filter((item) => item.id !== resume.id)
      ElMessage.success('简历已删除')
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') notifyError(error, '删除失败')
    }
  }
}

onMounted(async () => {
  await Promise.all([loadResumes(), loadTemplates()])
  // 从「我的模板」点「用它新建简历」跳过来时，直接带好模板打开新建弹窗。
  const preset = String(route.query.template || '')
  if (preset && templates.value.some((item) => item.code === preset)) {
    openCreate(preset)
    router.replace({ path: '/dashboard' })
  }
})
</script>

<template>
  <AppLayout>
    <div class="dashboard">
      <div class="dashboard-inner">
        <header class="dash-hero">
          <div>
            <div class="eyebrow">WORKSPACE · 我的简历</div>
            <h1 class="display">{{ greeting() }}，{{ displayName }}。</h1>
            <p>把最近的一段履历写清楚，比堆满一页更有力量。所有内容都可以随时修改，模板永久免费。</p>
          </div>
          <div class="dash-hero__actions">
            <el-button plain @click="router.push('/templates')">浏览模板</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate()">新建简历</el-button>
          </div>
        </header>

        <section class="stat-strip">
          <article v-for="item in stats" :key="item.label" class="stat-item">
            <span>{{ item.label }}</span>
            <div class="stat-item__value">
              <strong>{{ item.value }}</strong>
              <small>{{ item.suffix }}</small>
            </div>
            <p>{{ item.note }}</p>
          </article>
          <div class="stat-advice">
            <span class="mono">MINIWHO TIP</span>
            <p>投递前重新生成一次诊断报告，确认没有遗漏的联系方式与时间信息。</p>
          </div>
        </section>

        <section class="resume-section">
          <div class="list-toolbar">
            <div class="list-tabs">
              <button type="button" :class="{ active: filter === 'all' }" @click="filter = 'all'">
                全部 <span>{{ resumes.length }}</span>
              </button>
              <button type="button" :class="{ active: filter === 'complete' }" @click="filter = 'complete'">
                已完成
              </button>
              <button type="button" :class="{ active: filter === 'draft' }" @click="filter = 'draft'">
                待完善
              </button>
            </div>
            <el-input v-model="keyword" class="list-search" placeholder="搜索简历或目标岗位" clearable>
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>

          <div v-loading="loading" class="resume-grid-wrap">
            <div v-if="filteredResumes.length" class="resume-grid">
              <article
                v-for="resume in filteredResumes"
                :key="resume.id"
                class="resume-card"
                :style="{ '--card-accent': accentOf(resume.templateCode) }"
                @click="router.push(`/resumes/${resume.id}`)"
              >
                <div class="resume-card__cover" :class="`cover-${filterOf(resume.templateCode)}`">
                  <div class="cover-stage">
                    <div class="cover-sheet">
                      <span class="cover-rule"></span>
                      <strong>{{ resume.title }}</strong>
                      <small v-if="resume.targetJob">{{ resume.targetJob }}</small>
                      <div class="cover-lines">
                        <i></i><i></i><i></i><i class="short"></i>
                      </div>
                      <span class="cover-mark">MW</span>
                    </div>
                  </div>
                  <div class="resume-card__badges">
                    <span class="template-badge">
                      <i :style="{ background: accentOf(resume.templateCode) }"></i>
                      {{ resume.templateName || templateName(resume.templateCode) }}
                    </span>
                    <span v-if="resume.completeness >= 100" class="done-badge">
                      <el-icon><Select /></el-icon> 信息完整
                    </span>
                  </div>
                </div>

                <div class="resume-card__body">
                  <div class="resume-card__heading">
                    <div class="resume-card__title">
                      <h2>{{ resume.title }}</h2>
                      <p>{{ resume.targetJob || '还没有填写目标岗位' }}</p>
                    </div>
                    <el-tooltip content="更多操作：重命名 / 复制 / 删除" placement="top" :show-after="120">
                      <el-dropdown trigger="click" @command="(command) => handleCommand(command, resume)">
                        <button class="more-btn" type="button" aria-label="更多操作" @click.stop>
                          <el-icon><MoreFilled /></el-icon>
                        </button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="preview" :icon="View">预览与导出</el-dropdown-item>
                            <el-dropdown-item command="rename" :icon="EditPen">重命名</el-dropdown-item>
                            <el-dropdown-item command="duplicate" :icon="CopyDocument">创建副本</el-dropdown-item>
                            <el-dropdown-item command="delete" :icon="Delete" divided>删除</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </el-tooltip>
                  </div>

                  <div class="resume-card__meta">
                    <span>{{ timeAgo(resume.updatedAt) }}更新</span>
                    <span>{{ resume.language === 'en' ? 'English' : '中文' }}</span>
                  </div>

                  <div class="resume-card__progress">
                    <div class="progress-head">
                      <span>完整度</span>
                      <strong>{{ resume.completeness || 0 }}%</strong>
                    </div>
                    <div class="progress-track">
                      <span :style="{ width: `${resume.completeness || 0}%` }"></span>
                    </div>
                  </div>

                  <div class="resume-card__footer">
                    <div class="resume-score" :class="{ low: (resume.score || 0) < 60 }">
                      <span
                        class="score-badge"
                        :style="{ '--score': Math.min(100, Math.max(0, resume.score || 0)) }"
                      >
                        <i>{{ resume.score ?? '—' }}</i>
                      </span>
                      <span class="score-label">诊断分</span>
                    </div>
                    <button class="open-btn" type="button" @click.stop="router.push(`/resumes/${resume.id}`)">
                      继续编辑
                      <el-icon><ArrowRight /></el-icon>
                    </button>
                  </div>
                </div>
              </article>

              <button class="resume-card resume-card--new" type="button" @click="openCreate()">
                <span class="new-plus"><el-icon><Plus /></el-icon></span>
                <strong>新建一份简历</strong>
                <p>针对不同岗位准备不同版本，命中率会更高。</p>
              </button>
            </div>

            <div v-else-if="!loading" class="empty-state">
              <div class="empty-icon"><el-icon><Document /></el-icon></div>
              <template v-if="resumes.length">
                <h2>没有匹配的简历</h2>
                <p>试试换个关键词，或查看全部简历。</p>
                <el-button plain @click="keyword = ''; filter = 'all'">清除筛选</el-button>
              </template>
              <template v-else>
                <h2>从第一份简历开始</h2>
                <p>选择一个喜欢的模板，填写内容后即可导出 PDF。全程免费，没有功能限制。</p>
                <el-button type="primary" :icon="Plus" @click="openCreate()">新建简历</el-button>
              </template>
            </div>
          </div>
        </section>

        <section class="dash-note">
          <div class="dash-note__quote serif">“</div>
          <div>
            <h3>一份简历只讲一个方向</h3>
            <p>面向不同岗位时，复制一份再调整履历顺序与关键词，比用同一份简历投递所有岗位更有效。</p>
          </div>
          <router-link to="/templates" class="link-arrow">看看适合的版式</router-link>
        </section>
      </div>
    </div>

    <el-dialog v-model="createVisible" width="900px" align-center class="create-dialog" destroy-on-close>
      <template #header>
        <div class="create-head">
          <span class="mono">NEW RESUME</span>
          <h2>新建简历</h2>
          <p>先选一个版式，之后随时可以切换。</p>
        </div>
      </template>

      <div class="create-form">
        <div class="create-top">
          <div class="create-top__title">
            <span class="form-label">简历名称（选填）</span>
            <el-input v-model="createForm.title" maxlength="50" show-word-limit placeholder="例如：后端开发 · 社招版" />
          </div>
          <div class="form-block">
            <span class="form-label">语言</span>
            <el-radio-group v-model="createForm.language" size="small">
              <el-radio-button value="zh">中文</el-radio-button>
              <el-radio-button value="en">English</el-radio-button>
            </el-radio-group>
          </div>
          <div class="form-block">
            <span class="form-label">初始内容</span>
            <el-switch
              v-model="createForm.useSample"
              :disabled="createForm.language === 'en'"
              active-text="示例"
              inactive-text="空白"
            />
          </div>
        </div>

        <div class="create-body">
          <div class="create-picker">
            <div class="picker-head">
              <span class="form-label">版式</span>
              <div class="picker-filters">
                <button
                  v-for="name in createCategories"
                  :key="name"
                  type="button"
                  class="filter-chip"
                  :class="{ active: createCategory === name }"
                  @click="createCategory = name"
                >{{ name }}</button>
              </div>
            </div>
            <div class="picker-list">
              <button
                v-for="tpl in createTemplates"
                :key="tpl.code"
                type="button"
                class="picker-item"
                :class="{ active: createForm.templateCode === tpl.code }"
                @click="createForm.templateCode = tpl.code"
              >
                <span class="picker-dot" :style="{ background: configOf(tpl).accent }"></span>
                <span class="picker-name">{{ tpl.name }}</span>
                <span class="picker-cat">{{ choiceHint(tpl) }}</span>
              </button>
            </div>
          </div>

          <div class="create-preview">
            <TemplateThumb :content="sampleContent" :template="selectedTemplate" :avatar="sampleAvatar" />
            <div class="preview-meta">
              <strong>{{ selectedTemplate?.name }}</strong>
              <span>{{ choiceHint(selectedTemplate) }}</span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createResume">创建并开始填写</el-button>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<style scoped>
.dashboard {
  padding: 44px 0 80px;
}

.dashboard-inner {
  width: min(var(--content-width), calc(100% - 64px));
  margin: 0 auto;
}

.dash-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 40px;
  padding-bottom: 32px;
}

.dash-hero h1 {
  margin: 14px 0 10px;
  font-size: clamp(30px, 3.6vw, 44px);
}

.dash-hero p {
  max-width: 620px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.dash-hero__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 0.8fr)) minmax(240px, 1.4fr);
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--line);
}

.stat-item {
  padding: 20px 24px;
  background: var(--paper);
}

.stat-item > span {
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0.06em;
}

.stat-item__value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin: 7px 0 2px;
}

.stat-item__value strong {
  font-family: var(--serif);
  font-size: 34px;
  font-weight: 600;
  line-height: 1;
}

.stat-item__value small {
  color: var(--muted);
  font-size: 11px;
}

.stat-item p {
  color: var(--faint);
  font-size: 10px;
}

.stat-advice {
  padding: 20px 24px;
  background: var(--ink);
  color: #f3f0e6;
}

.stat-advice .mono {
  color: var(--gold);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.stat-advice p {
  margin-top: 8px;
  color: rgba(243, 240, 230, 0.72);
  font-size: 11px;
  line-height: 1.75;
}

.resume-section {
  margin-top: 40px;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.list-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
}

.list-tabs button {
  padding: 7px 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: none;
  color: var(--muted);
  font-family: var(--sans);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.list-tabs button span {
  margin-left: 4px;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 9px;
}

.list-tabs button:hover {
  color: var(--ink);
}

.list-tabs button.active {
  border-color: var(--line);
  background: var(--paper);
  color: var(--ink);
}

.list-search {
  width: 240px;
}

.resume-grid-wrap {
  min-height: 220px;
}

.resume-grid {
  display: grid;
  /* 列数跟着内容宽度走（默认 1200px 三列、1600px 四列）。
     auto-fill 而不是 auto-fit：简历不到一屏时要留出空轨道，卡片不被拉胖。 */
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 22px;
}

.resume-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--paper);
  text-align: left;
  cursor: pointer;
  transition: transform 0.26s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.26s ease, border-color 0.26s ease;
}

.resume-card:hover {
  border-color: color-mix(in srgb, var(--card-accent) 34%, var(--line-strong));
  transform: translateY(-4px);
  box-shadow: 0 18px 40px -26px rgba(25, 34, 30, 0.4), var(--shadow-sm);
}

/* 封面：模板主色做一层很淡的光晕，再叠一点点阵，纸面才有质感 */
.resume-card__cover {
  position: relative;
  display: grid;
  place-items: center;
  height: 188px;
  padding: 22px 20px;
  overflow: hidden;
  border-bottom: 1px solid var(--line);
  background:
    radial-gradient(115% 85% at 50% 4%, color-mix(in srgb, var(--card-accent) 17%, transparent) 0%, transparent 64%),
    linear-gradient(168deg, color-mix(in srgb, var(--card-accent) 5%, var(--paper-deep)) 0%, var(--canvas-deep) 100%);
}

.resume-card__cover::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image: radial-gradient(color-mix(in srgb, var(--card-accent) 32%, transparent) 0.7px, transparent 0.7px);
  background-size: 14px 14px;
  opacity: 0.4;
  pointer-events: none;
}

/* 叠纸舞台：两片后置纸张让封面像一摞简历，而不是单独一张 */
.cover-stage {
  position: relative;
  width: 128px;
  height: 136px;
}

.cover-stage::before,
.cover-stage::after {
  content: "";
  position: absolute;
  inset: 0;
  border: 1px solid rgba(25, 34, 30, 0.05);
  border-radius: 2px;
  background: #fffdf8;
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.cover-stage::before {
  opacity: 0.5;
  transform: rotate(3.4deg) translate(4px, 2px);
}

.cover-stage::after {
  opacity: 0.78;
  transform: rotate(-2.6deg) translate(-3px, 3px);
}

.resume-card:hover .cover-stage::before {
  transform: rotate(5.4deg) translate(7px, 3px);
}

.resume-card:hover .cover-stage::after {
  transform: rotate(-4deg) translate(-6px, 4px);
}

.cover-sheet {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  width: 128px;
  height: 136px;
  padding: 16px 14px;
  border: 1px solid rgba(25, 34, 30, 0.06);
  border-radius: 2px;
  background: #fffefb;
  box-shadow: 0 16px 34px -18px rgba(25, 34, 30, 0.42);
  transform: rotate(-1.4deg);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.3s ease;
}

.resume-card:hover .cover-sheet {
  transform: rotate(0deg) translateY(-4px);
  box-shadow: 0 22px 40px -20px rgba(25, 34, 30, 0.48);
}

.cover-rule {
  width: 22px;
  height: 2px;
  margin-bottom: 9px;
  background: var(--card-accent);
}

.cover-sheet strong {
  overflow: hidden;
  color: var(--ink);
  font-family: var(--serif);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cover-sheet small {
  overflow: hidden;
  margin-top: 3px;
  color: var(--card-accent);
  font-size: 7px;
  letter-spacing: 0.04em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cover-lines {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 14px;
}

.cover-lines i {
  height: 2px;
  border-radius: 2px;
  background: #e8e3d8;
}

.cover-lines i.short {
  width: 62%;
}

.cover-mark {
  position: absolute;
  right: 10px;
  bottom: 9px;
  color: color-mix(in srgb, var(--card-accent) 40%, #fff);
  font-family: var(--mono);
  font-size: 7px;
  letter-spacing: 0.12em;
}

.cover-academic .cover-sheet {
  text-align: center;
}

.cover-academic .cover-rule {
  margin-inline: auto;
}

.cover-sidebar .cover-sheet {
  padding-left: 44px;
}

.cover-sidebar .cover-sheet::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 32px;
  background: color-mix(in srgb, var(--card-accent) 12%, #fff);
}

.cover-band .cover-sheet::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 30px;
  background: var(--card-accent);
}

.cover-band .cover-rule {
  opacity: 0;
}

.resume-card__badges {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  pointer-events: none;
}

.template-badge,
.done-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border: 1px solid rgba(255, 254, 251, 0.7);
  border-radius: 999px;
  background: rgba(255, 254, 251, 0.82);
  color: var(--ink-soft);
  font-size: 9.5px;
  font-weight: 500;
  letter-spacing: 0.02em;
  box-shadow: 0 2px 6px -3px rgba(25, 34, 30, 0.3);
  backdrop-filter: blur(6px);
}

.template-badge i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex: none;
}

.done-badge {
  color: var(--accent);
}

.done-badge .el-icon {
  font-size: 10px;
}

.resume-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 16px 18px 18px;
}

.resume-card__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.resume-card__title {
  min-width: 0;
}

.resume-card__heading h2 {
  overflow: hidden;
  font-family: var(--serif);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-card__heading p {
  overflow: hidden;
  margin-top: 4px;
  color: color-mix(in srgb, var(--card-accent) 72%, var(--ink-soft));
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-card__heading :deep(.el-tooltip__trigger) {
  display: inline-flex;
  flex: none;
}

.more-btn {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--paper-deep);
  color: var(--ink-soft);
  font-size: 15px;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.more-btn:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
  transform: translateY(-1px);
}

.more-btn:focus-visible {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(10, 125, 118, 0.16);
}

.resume-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: var(--faint);
  font-size: 10px;
  letter-spacing: 0.02em;
}

.resume-card__meta span + span::before {
  content: "·";
  margin-right: 8px;
}

.resume-card__progress {
  margin-top: auto;
  padding-top: 16px;
}

.progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 7px;
  color: var(--muted);
  font-size: 10px;
}

.progress-head strong {
  color: var(--ink-soft);
  font-family: var(--mono);
  font-size: 10.5px;
  font-weight: 600;
}

.progress-track {
  height: 4px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--canvas-deep);
}

.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, color-mix(in srgb, var(--card-accent) 62%, #fff), var(--card-accent));
  transition: width 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

.resume-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.resume-score {
  display: flex;
  align-items: center;
  gap: 9px;
  --ring-color: var(--accent);
}

.resume-score.low {
  --ring-color: var(--copper);
}

/* 环形分数：conic-gradient 画进度，内圈用纸色挖空，比裸数字更有分量 */
.score-badge {
  position: relative;
  display: grid;
  place-items: center;
  flex: none;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: conic-gradient(var(--ring-color) calc(var(--score, 0) * 1%), var(--canvas-deep) 0);
}

.score-badge::after {
  content: "";
  position: absolute;
  inset: 3px;
  border-radius: 50%;
  background: var(--paper);
}

.score-badge i {
  position: relative;
  z-index: 1;
  color: var(--ring-color);
  font-family: var(--serif);
  font-size: 14px;
  font-style: normal;
  font-weight: 600;
  line-height: 1;
}

.score-label {
  color: var(--faint);
  font-size: 10px;
  line-height: 1.5;
}

.open-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 13px;
  border: 1px solid color-mix(in srgb, var(--card-accent) 26%, var(--line));
  border-radius: 999px;
  background: color-mix(in srgb, var(--card-accent) 9%, var(--paper));
  color: color-mix(in srgb, var(--card-accent) 82%, var(--ink));
  font-family: var(--sans);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease, gap 0.2s ease;
}

.open-btn .el-icon {
  font-size: 11px;
  transition: transform 0.2s ease;
}

.resume-card:hover .open-btn {
  border-color: var(--card-accent);
  background: var(--card-accent);
  color: #fff;
}

.resume-card:hover .open-btn .el-icon {
  transform: translateX(2px);
}

.resume-card--new {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 370px;
  padding: 30px 26px;
  border-style: dashed;
  border-color: var(--line-strong);
  background: rgba(255, 254, 251, 0.34);
  font-family: inherit;
  text-align: center;
  transition: background 0.24s ease, border-color 0.24s ease, transform 0.24s ease;
}

.resume-card--new:hover {
  border-color: color-mix(in srgb, var(--accent) 40%, var(--line-strong));
  background: var(--paper);
  transform: translateY(-4px);
}

.new-plus {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin-bottom: 15px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--paper);
  color: var(--muted);
  font-size: 17px;
  transition: border-color 0.24s ease, color 0.24s ease, transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.resume-card--new:hover .new-plus {
  border-color: var(--accent);
  color: var(--accent);
  transform: rotate(90deg);
}

.resume-card--new strong {
  font-family: var(--serif);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.resume-card--new p {
  max-width: 200px;
  margin-top: 8px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.75;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 70px 24px;
  border: 1px dashed var(--line-strong);
  border-radius: 14px;
  background: rgba(255, 254, 251, 0.4);
  text-align: center;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 16px;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--muted);
  font-size: 20px;
}

.empty-state h2 {
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 600;
}

.empty-state p {
  max-width: 380px;
  margin: 8px 0 20px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}

.dash-note {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-top: 46px;
  padding: 26px 4px 0;
  border-top: 1px solid var(--line);
}

.dash-note__quote {
  color: var(--gold);
  font-size: 42px;
  line-height: 0.8;
}

.dash-note > div:nth-child(2) {
  flex: 1;
}

.dash-note h3 {
  font-family: var(--serif);
  font-size: 17px;
  font-weight: 600;
}

.dash-note p {
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.8;
}

.create-head .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.create-head h2 {
  margin-top: 6px;
  font-family: var(--serif);
  font-size: 24px;
  font-weight: 600;
}

.create-head p {
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
}

.create-form :deep(.el-form-item__label) {
  color: var(--muted);
  font-size: 12px;
}

.form-block {
  margin-top: 18px;
}

.form-label {
  display: block;
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 12px;
}

/* 新建简历弹窗：左边紧凑列表、右边真实预览。
   60 套版式铺成 4 列小方块会又长又认不出，改成可滚动列表 + 大预览，一屏能比着选。 */
.create-top {
  display: flex;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 16px;
}

.create-top__title {
  flex: 1 1 auto;
  min-width: 0;
}

.create-top .form-block {
  flex: 0 0 auto;
  margin-top: 0;
}

.create-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 268px;
  gap: 18px;
  align-items: start;
}

.create-picker {
  min-width: 0;
}

.picker-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.picker-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-chip {
  padding: 3px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--paper);
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease;
}

.filter-chip:hover {
  border-color: var(--line-strong);
  color: var(--ink-soft);
}

.filter-chip.active {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
}

.picker-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  max-height: 356px;
  padding: 2px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  height: 34px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--paper);
  color: var(--ink);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.picker-item:hover {
  border-color: var(--line-strong);
  background: #fff;
}

.picker-item.active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.picker-dot {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.picker-name {
  overflow: hidden;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.picker-cat {
  flex: 0 0 auto;
  margin-left: auto;
  color: var(--faint);
  font-size: 10px;
}

.create-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.preview-meta strong {
  font-size: 13px;
}

.preview-meta span {
  color: var(--muted);
  font-size: 11px;
}

.form-row {
  display: flex;
  gap: 32px;
}

@media (max-width: 1080px) {
  .resume-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-strip {
    grid-template-columns: repeat(3, 1fr);
  }

  .stat-advice {
    grid-column: 1 / -1;
  }
}

@media (max-width: 800px) {
  .dashboard {
    padding-top: 28px;
  }

  .dashboard-inner {
    width: calc(100% - 40px);
  }

  .dash-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }

  .dash-hero__actions {
    width: 100%;
  }

  .dash-hero__actions :deep(.el-button) {
    flex: 1;
  }

  .stat-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .stat-item {
    padding: 15px;
  }

  .stat-item__value strong {
    font-size: 27px;
  }

  .list-toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .list-search {
    width: 100%;
  }

  .dash-note {
    align-items: flex-start;
  }
}

@media (max-width: 620px) {
  .resume-grid {
    grid-template-columns: 1fr;
  }

  .stat-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .stat-item {
    padding: 14px 12px;
  }

  .stat-item > span {
    font-size: 10px;
    letter-spacing: 0;
  }

  .stat-item__value strong {
    font-size: 24px;
  }

  .stat-item p {
    display: none;
  }

  .stat-advice {
    grid-column: 1 / -1;
    padding: 15px 16px;
  }

  .stat-advice p {
    margin-top: 6px;
    font-size: 10.5px;
  }

  /* 手机上原来靠 flex-wrap 换行，文字块会被压成一条窄列，每行只剩几个字。 */
  .dash-note {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    margin-top: 34px;
    padding-top: 22px;
  }

  .dash-note__quote {
    font-size: 34px;
  }

  .dash-note > div:nth-child(2) {
    flex: none;
    width: 100%;
  }

  .resume-card--new {
    min-height: 210px;
  }

  .template-choices {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .form-row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
