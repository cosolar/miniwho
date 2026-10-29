<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, CircleCheckFilled, DocumentChecked, EditPen, InfoFilled, MagicStick,
  Refresh, WarningFilled
} from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import ScoreRing from '../components/ScoreRing.vue'
import { SECTION_META } from '../data/sectionCatalog'
import { reportApi, resumeApi } from '../api'
import { notifyError } from '../api/client'
import { formatDateTime, timeAgo } from '../utils/format'

const route = useRoute()
const router = useRouter()
const resumeId = Number(route.params.id)

const loading = ref(true)
const generating = ref(false)
const loadError = ref('')
const resume = ref(null)
const report = ref(null)

const DIMENSION_HINTS = {
  completeness: '姓名、联系方式、教育、履历等核心信息是否齐全。',
  job_match: '目标岗位是否明确，履历中是否出现相关关键词。',
  expression: '是否少用「负责 / 参与」等弱表达，段落是否简洁。',
  quantification: '履历中是否用数字说明规模、耗时或结果。',
  format: '邮箱、电话、时间等细节是否规范。'
}

const SEVERITY_META = {
  high: { label: '优先修改', tone: 'high' },
  medium: { label: '建议优化', tone: 'medium' },
  low: { label: '锦上添花', tone: 'low' }
}

const dimensions = computed(() => {
  const list = Array.isArray(report.value?.dimensions) ? report.value.dimensions : []
  return list.map((item) => ({
    ...item,
    percent: item.max ? Math.round((Number(item.score) / Number(item.max)) * 100) : 0,
    hint: DIMENSION_HINTS[item.key] || ''
  }))
})

const issues = computed(() => (Array.isArray(report.value?.issues) ? report.value.issues : []))

const highCount = computed(() => issues.value.filter((item) => item.severity === 'high').length)
const mediumCount = computed(() => issues.value.filter((item) => item.severity === 'medium').length)
const lowCount = computed(() => issues.value.filter((item) => item.severity === 'low').length)

const gradeTone = computed(() => {
  const score = Number(report.value?.totalScore) || 0
  if (score >= 90) return 'excellent'
  if (score >= 75) return 'good'
  if (score >= 60) return 'fair'
  return 'weak'
})

const weakest = computed(() => {
  if (!dimensions.value.length) return null
  return [...dimensions.value].sort((a, b) => a.percent - b.percent)[0]
})

const overallComment = computed(() => {
  if (!report.value) return ''
  const score = Number(report.value.totalScore) || 0
  if (score >= 90) return '这份简历已经相当扎实，重点检查投递时的岗位匹配度即可。'
  if (score >= 75) return '整体结构完整、表达清楚，再补几处量化结果会更稳。'
  if (score >= 60) return '基础信息已经具备，建议优先补齐履历细节与成果数据。'
  return '还处于早期版本，先按下方优先事项把核心信息补全。'
})

const breakdownNote = computed(() => {
  if (!weakest.value) return ''
  return `当前相对薄弱的是「${weakest.value.label}」（${weakest.value.score}/${weakest.value.max}），可以优先处理。`
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [detail, latest] = await Promise.all([
      resumeApi.detail(resumeId),
      reportApi.latest(resumeId).catch(() => null)
    ])
    resume.value = detail
    report.value = latest || null
    document.title = latest ? `${detail.title} · 简历诊断 · 极简简历` : `${detail.title} · 极简简历`
  } catch (error) {
    loadError.value = error?.message || '无法加载这份简历'
    notifyError(error, '加载诊断报告失败')
  } finally {
    loading.value = false
  }
}

async function generate() {
  if (generating.value) return
  generating.value = true
  try {
    report.value = await reportApi.generate(resumeId)
    ElMessage.success('诊断报告已更新')
  } catch (error) {
    notifyError(error, '生成诊断报告失败')
  } finally {
    generating.value = false
  }
}

function severityOf(issue) {
  return SEVERITY_META[issue.severity] || SEVERITY_META.low
}

function sectionLabel(issue) {
  return SECTION_META[issue.sectionType]?.label || '简历内容'
}

function goFix(issue) {
  router.push({ path: `/resumes/${resumeId}`, query: { section: issue.sectionType } })
}

function goBack() {
  router.push(`/resumes/${resumeId}`)
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="report-page">
      <div class="report-shell">
        <div class="report-top no-print">
          <button class="back-link" type="button" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回编辑
          </button>
          <div v-if="resume" class="report-top__actions">
            <span class="report-top__title">{{ resume.title }}</span>
            <el-button :loading="generating" @click="generate">
              <el-icon v-if="!generating"><Refresh /></el-icon>
              {{ report ? '重新诊断' : '开始诊断' }}
            </el-button>
          </div>
        </div>

        <el-skeleton v-if="loading" :rows="8" animated class="report-skeleton" />

        <div v-else-if="loadError" class="report-error surface">
          <el-icon><WarningFilled /></el-icon>
          <h2>报告加载失败</h2>
          <p>{{ loadError }}</p>
          <el-button type="primary" @click="load">重新加载</el-button>
        </div>

        <template v-else>
          <header class="report-hero">
            <div class="report-hero__copy">
              <span class="eyebrow">Resume review</span>
              <h1 class="display">
                简历诊断<span v-if="report" class="grade-pill" :class="`is-${gradeTone}`">{{ report.grade }}</span>
              </h1>
              <p class="report-hero__lead">
                {{ report ? overallComment : '用五项维度检查这份简历的完整度、匹配度与表达质量，所有分析都在本地服务中完成。' }}
              </p>
              <div v-if="report" class="report-meta">
                <span>内容版本 v{{ report.contentVersion }}</span>
                <span class="dot"></span>
                <span>{{ formatDateTime(report.createdAt) }}</span>
                <span class="dot"></span>
                <span>{{ timeAgo(report.createdAt) }}</span>
              </div>
            </div>
            <div class="report-hero__score">
              <ScoreRing
                :score="report?.totalScore || 0"
                :grade="report?.grade || '未诊断'"
                :size="144"
                :label="report ? '综合得分' : '等待诊断'"
              />
              <p v-if="report">{{ breakdownNote }}</p>
              <p v-else>大约需要 1 秒</p>
            </div>
          </header>

          <div v-if="!report" class="report-empty surface">
            <div class="report-empty__icon"><el-icon><DocumentChecked /></el-icon></div>
            <h2>还没有诊断记录</h2>
            <p>系统会从完整度、岗位匹配、表达质量、成果量化、格式规范五个维度打分，并给出可执行的修改建议。</p>
            <el-button type="primary" size="large" :loading="generating" @click="generate">
              <el-icon v-if="!generating"><MagicStick /></el-icon>
              生成诊断报告
            </el-button>
            <small>诊断只读取当前简历内容，不会上传到任何第三方服务。</small>
          </div>

          <template v-else>
            <div v-if="!report.current" class="report-stale no-print">
              <el-icon><InfoFilled /></el-icon>
              <div>
                <strong>简历内容已经更新</strong>
                <p>这份报告生成于 v{{ report.contentVersion }}，当前内容已更新。重新诊断可以获得最新评分。</p>
              </div>
              <el-button size="small" :loading="generating" @click="generate">立即更新</el-button>
            </div>

            <div class="report-grid">
              <section class="report-card surface">
                <header class="card-head">
                  <div>
                    <span class="card-kicker">五维评分</span>
                    <h2>得分构成</h2>
                  </div>
                  <span class="card-total">{{ report.totalScore }}<small>/ 100</small></span>
                </header>
                <ul class="dimension-list">
                  <li v-for="item in dimensions" :key="item.key" class="dimension-item">
                    <div class="dimension-head">
                      <div>
                        <strong>{{ item.label }}</strong>
                        <span class="dimension-summary">{{ item.summary }}</span>
                      </div>
                      <span class="dimension-score">{{ item.score }}<small>/{{ item.max }}</small></span>
                    </div>
                    <div class="dimension-track">
                      <span class="dimension-fill" :style="{ width: `${item.percent}%` }"></span>
                    </div>
                    <p class="dimension-hint">{{ item.hint }}</p>
                  </li>
                </ul>
              </section>

              <aside class="report-aside">
                <section class="summary-card surface">
                  <span class="card-kicker">修改顺序</span>
                  <h2>{{ issues.length ? '优先处理这些' : '没有发现明显问题' }}</h2>
                  <p v-if="issues.length">按影响程度排序，先处理高优先级问题，再润色细节。</p>
                  <p v-else>这份内容在评分规则下表现不错，投递前再通读一遍即可。</p>
                  <div class="severity-row">
                    <div class="severity-chip is-high">
                      <strong>{{ highCount }}</strong>
                      <span>优先修改</span>
                    </div>
                    <div class="severity-chip is-medium">
                      <strong>{{ mediumCount }}</strong>
                      <span>建议优化</span>
                    </div>
                    <div class="severity-chip is-low">
                      <strong>{{ lowCount }}</strong>
                      <span>锦上添花</span>
                    </div>
                  </div>
                  <div class="summary-divider"></div>
                  <p class="summary-note">
                    <el-icon><InfoFilled /></el-icon>
                    诊断采用固定规则，结果可以复现；它不能替代真实 HR 的判断。
                  </p>
                </section>

                <section class="quick-card surface no-print">
                  <span class="card-kicker">接下来</span>
                  <h2>把建议变成内容</h2>
                  <ul>
                    <li><span>01</span> 按优先级填写真实履历与数据。</li>
                    <li><span>02</span> 在预览页切换模板，确认 A4 排版。</li>
                    <li><span>03</span> 生成分享链接，投递前自测打开效果。</li>
                  </ul>
                  <el-button class="quick-btn" @click="router.push(`/resumes/${resumeId}/preview`)">
                    去预览与分享
                  </el-button>
                </section>
              </aside>
            </div>

            <section class="issues-card surface">
              <header class="card-head">
                <div>
                  <span class="card-kicker">修改清单</span>
                  <h2>具体建议</h2>
                </div>
                <span class="issues-count">{{ issues.length }} 条</span>
              </header>

              <div v-if="!issues.length" class="issues-empty">
                <el-icon><CircleCheckFilled /></el-icon>
                <strong>没有需要优先处理的问题</strong>
                <p>继续补充与目标岗位相关的项目成果，会让简历更有说服力。</p>
              </div>

              <ul v-else class="issue-list">
                <li v-for="issue in issues" :key="issue.id" class="issue-item" :class="`is-${severityOf(issue).tone}`">
                  <div class="issue-mark">
                    <el-icon><WarningFilled /></el-icon>
                  </div>
                  <div class="issue-body">
                    <div class="issue-head">
                      <strong>{{ issue.title }}</strong>
                      <span class="issue-tag">{{ severityOf(issue).label }}</span>
                      <span class="issue-section">{{ sectionLabel(issue) }}</span>
                    </div>
                    <p>{{ issue.detail }}</p>
                    <div class="issue-suggestion">
                      <span>建议</span>
                      <p>{{ issue.suggestion }}</p>
                    </div>
                  </div>
                  <button class="issue-fix no-print" type="button" @click="goFix(issue)">
                    <el-icon><EditPen /></el-icon>
                    去修改
                  </button>
                </li>
              </ul>
            </section>

            <footer class="report-footer no-print">
              <p>本次诊断基于 {{ resume?.title }} 的当前内容，v{{ report.contentVersion }}。</p>
              <div class="report-footer__actions">
                <el-button @click="router.push(`/resumes/${resumeId}/preview`)">预览简历</el-button>
                <el-button type="primary" :loading="generating" @click="generate">
                  <el-icon v-if="!generating"><Refresh /></el-icon>
                  重新诊断
                </el-button>
              </div>
            </footer>
          </template>
        </template>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.report-page {
  padding: 28px 0 72px;
  background:
    radial-gradient(900px 420px at 12% -10%, rgba(176, 95, 60, 0.06), transparent 60%),
    transparent;
}

.report-shell {
  width: min(var(--content-width), calc(100% - 56px));
  margin: 0 auto;
}

.report-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 30px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 0;
  border: none;
  background: none;
  color: var(--muted);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.back-link:hover {
  color: var(--accent);
}

.report-top__actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.report-top__title {
  max-width: 320px;
  overflow: hidden;
  color: var(--muted);
  font-family: var(--serif);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-skeleton {
  padding: 40px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--paper);
}

.report-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 24px;
  text-align: center;
}

.report-error .el-icon {
  color: var(--copper);
  font-size: 36px;
}

.report-error h2 {
  font-family: var(--serif);
  font-size: 22px;
}

.report-error p {
  color: var(--muted);
  font-size: 13px;
}

.report-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 48px;
  padding: 6px 4px 36px;
}

.report-hero__copy h1 {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 14px;
  font-size: clamp(36px, 4.2vw, 52px);
}

.grade-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 999px;
  font-family: var(--sans);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

.grade-pill.is-excellent {
  color: var(--accent-dark);
  background: var(--accent-soft);
}

.grade-pill.is-good {
  color: #55714f;
  background: #eff3e9;
}

.grade-pill.is-fair {
  color: #8d6b32;
  background: #f7efdf;
}

.grade-pill.is-weak {
  color: #93412b;
  background: var(--copper-soft);
}

.report-hero__lead {
  max-width: 560px;
  margin-top: 16px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.9;
}

.report-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 20px;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.02em;
}

.report-meta .dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--line-strong);
}

.report-hero__score {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.report-hero__score p {
  max-width: 190px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
  text-align: center;
}

.report-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 64px 32px 56px;
  text-align: center;
}

.report-empty__icon {
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  margin-bottom: 4px;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: var(--paper-deep);
  color: var(--accent);
  font-size: 26px;
}

.report-empty h2 {
  font-family: var(--serif);
  font-size: 24px;
}

.report-empty > p {
  max-width: 500px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.report-empty small {
  color: var(--faint);
  font-size: 11px;
}

.report-stale {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
  padding: 14px 18px;
  border: 1px solid #e8d9c4;
  border-radius: var(--radius);
  background: #fbf6ea;
  color: #7d6435;
}

.report-stale > .el-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.report-stale div {
  flex: 1;
}

.report-stale strong {
  font-size: 13px;
}

.report-stale p {
  margin-top: 3px;
  font-size: 12px;
  opacity: 0.82;
}

.report-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 316px;
  gap: 22px;
  align-items: start;
}

.report-card,
.issues-card {
  padding: 28px 30px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
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

.card-head h2,
.summary-card h2,
.quick-card h2 {
  margin-top: 6px;
  font-family: var(--serif);
  font-size: 21px;
  font-weight: 600;
}

.card-total {
  font-family: var(--serif);
  font-size: 34px;
  font-weight: 600;
  line-height: 1;
}

.card-total small {
  color: var(--faint);
  font-family: var(--sans);
  font-size: 12px;
  font-weight: 400;
}

.dimension-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.dimension-item {
  padding: 16px 0;
  border-top: 1px solid var(--line);
}

.dimension-item:first-child {
  border-top: none;
  padding-top: 4px;
}

.dimension-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.dimension-head strong {
  font-size: 14px;
  font-weight: 600;
}

.dimension-summary {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.dimension-score {
  flex-shrink: 0;
  font-family: var(--mono);
  font-size: 15px;
  font-weight: 600;
}

.dimension-score small {
  color: var(--faint);
  font-size: 10px;
  font-weight: 400;
}

.dimension-track {
  height: 5px;
  margin-top: 10px;
  border-radius: 999px;
  background: var(--canvas-deep);
  overflow: hidden;
}

.dimension-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent), #6b9487);
  transition: width 0.7s cubic-bezier(0.22, 1, 0.36, 1);
}

.dimension-hint {
  margin-top: 7px;
  color: var(--faint);
  font-size: 11px;
}

.report-aside {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.summary-card,
.quick-card {
  padding: 24px;
}

.summary-card > p,
.quick-card > p {
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}

.severity-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 18px;
}

.severity-chip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 8px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  text-align: center;
}

.severity-chip strong {
  font-family: var(--serif);
  font-size: 20px;
  line-height: 1.2;
}

.severity-chip span {
  color: var(--muted);
  font-size: 10px;
}

.severity-chip.is-high strong {
  color: var(--copper);
}

.severity-chip.is-medium strong {
  color: var(--gold);
}

.severity-chip.is-low strong {
  color: var(--accent);
}

.summary-divider {
  height: 1px;
  margin: 20px 0 14px;
  background: var(--line);
}

.summary-note {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0 !important;
  color: var(--faint) !important;
  font-size: 11px !important;
}

.summary-note .el-icon {
  margin-top: 3px;
  flex-shrink: 0;
}

.quick-card ul {
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.quick-card li {
  display: flex;
  gap: 10px;
  padding: 7px 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.quick-card li span {
  flex-shrink: 0;
  padding-top: 1px;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 10px;
}

.quick-btn {
  width: 100%;
  margin-top: 14px;
}

.issues-card {
  margin-top: 22px;
}

.issues-count {
  padding: 3px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  font-size: 11px;
}

.issue-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.issue-item {
  display: flex;
  gap: 14px;
  padding: 20px 0;
  border-top: 1px solid var(--line);
}

.issue-item:first-child {
  border-top: none;
  padding-top: 4px;
}

.issue-mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--canvas-deep);
  color: var(--faint);
  font-size: 15px;
}

.issue-item.is-high .issue-mark {
  background: var(--copper-soft);
  color: var(--copper);
}

.issue-item.is-medium .issue-mark {
  background: #f7efdf;
  color: #a17c3d;
}

.issue-body {
  flex: 1;
  min-width: 0;
}

.issue-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.issue-head strong {
  font-size: 14px;
  font-weight: 600;
}

.issue-tag {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--canvas-deep);
  color: var(--muted);
  font-size: 10px;
}

.issue-item.is-high .issue-tag {
  background: var(--copper-soft);
  color: var(--copper);
}

.issue-item.is-medium .issue-tag {
  background: #f7efdf;
  color: #997231;
}

.issue-section {
  color: var(--faint);
  font-size: 11px;
}

.issue-body > p {
  margin-top: 6px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}

.issue-suggestion {
  display: flex;
  gap: 9px;
  margin-top: 10px;
  padding: 10px 12px;
  border-left: 2px solid var(--line-strong);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--paper-deep);
}

.issue-suggestion > span {
  flex-shrink: 0;
  color: var(--accent);
  font-size: 10px;
  letter-spacing: 0.08em;
  line-height: 1.9;
}

.issue-suggestion p {
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.8;
}

.issue-fix {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  flex-shrink: 0;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.issue-fix:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.issues-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 30px 0 18px;
  text-align: center;
}

.issues-empty .el-icon {
  color: var(--accent);
  font-size: 30px;
}

.issues-empty strong {
  font-size: 14px;
}

.issues-empty p {
  color: var(--muted);
  font-size: 12px;
}

.report-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 26px;
  padding: 0 4px;
}

.report-footer p {
  color: var(--faint);
  font-size: 11px;
}

.report-footer__actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 900px) {
  .report-grid {
    grid-template-columns: 1fr;
  }

  .report-aside {
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: stretch;
  }
}

@media (max-width: 700px) {
  .report-page {
    padding: 18px 0 48px;
  }

  .report-shell {
    width: calc(100% - 28px);
  }

  .report-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 24px;
    padding-bottom: 26px;
  }

  .report-hero__score {
    flex-direction: row;
    align-items: center;
    gap: 18px;
  }

  .report-hero__score p {
    text-align: left;
  }

  .report-aside {
    grid-template-columns: 1fr;
  }

  .report-card,
  .issues-card {
    padding: 22px 18px;
  }

  .report-top {
    flex-wrap: wrap;
    gap: 10px;
    margin-bottom: 22px;
  }

  .report-top__title {
    display: none;
  }

  .issue-item {
    flex-wrap: wrap;
  }

  .issue-fix {
    margin-left: 46px;
  }

  .report-footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .report-footer__actions {
    width: 100%;
  }

  .report-footer__actions .el-button {
    flex: 1;
  }
}
</style>
