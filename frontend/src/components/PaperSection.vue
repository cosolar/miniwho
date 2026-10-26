<script setup>
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import PaperIcon from './PaperIcon.vue'
import { isCustomType, isItemBlank, periodText, formatMonth, salaryText, metaOf } from '../data/sectionCatalog'
import { itemMediaUrl } from '../api/client'

const props = defineProps({
  section: { type: Object, required: true },
  config: { type: Object, default: () => ({}) },
  compact: { type: Boolean, default: false }
})

const type = computed(() => props.section.type)
const baseType = computed(() => isCustomType(type.value) ? 'custom' : type.value)
const meta = computed(() => metaOf(type.value))
const title = computed(() => props.section.title || meta.value.label)
const items = computed(() => (props.section.items || []).filter((item) => !isItemBlank(item)))
const fields = (item) => item?.fields || {}
// 金石是藏青斜切版式：日期读作「2017-09 至 2019-07」，条目左侧留出校徽 / LOGO 栏，
// 求职意向按标签成模块，其余版式沿用点号日期与破折号。
const isRibbon = computed(() => props.config?.layout === 'ribbon')
const dateGlue = computed(() => (isRibbon.value ? '-' : '.'))
const periodSeparator = computed(() => (isRibbon.value || props.config?.layout === 'timeline' ? '至' : '—'))
const periodOf = (item) => periodText(item?.fields?.period, periodSeparator.value, dateGlue.value)
const courseTag = computed(() => (props.config?.layout === 'timeline' ? '相关课程' : '主修课程'))
const courseSeparator = computed(() => (props.config?.layout === 'timeline' ? ' / ' : ' · '))

// 只有教育经历和公司经历有 logo 字段；整模块一条都没有时连栏位都不留，
// 免得没上传过徽标的简历凭空多出一列空框。
const markType = computed(() => (isRibbon.value && (type.value === 'education' || type.value === 'work_experience')
  ? type.value
  : ''))
const hasMarks = computed(() => Boolean(markType.value) && items.value.some((item) => item.fields?.logo))
const sideColumn = computed(() => Boolean(markType.value))

// 右侧栏：城市在上、日期在下，与左侧两行文字对齐；没有城市的模块只剩日期。
function sideLines(item) {
  const f = item.fields || {}
  return [{ text: f.city, muted: false }, { text: periodOf(item), muted: true }].filter((line) => line.text)
}

const safe = (html) => DOMPurify.sanitize(html || '', {
  ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'ul', 'ol', 'li'],
  ALLOWED_ATTR: []
})

function prettyLink(url) {
  if (!url) return ''
  try {
    const parsed = new URL(url)
    return `${parsed.host}${parsed.pathname === '/' ? '' : parsed.pathname}`.replace(/\/$/, '')
  } catch {
    return url.replace(/^https?:\/\//, '')
  }
}

// 有日期的条目在 academic / center 里占左侧日期栏；无日期条目交给 CSS 收成单栏。
function dateTextOf(item) {
  const f = item?.fields || {}
  if (typeof f.period === 'string') return formatMonth(f.period, dateGlue.value)
  return periodText(f.period, periodSeparator.value, dateGlue.value) || formatMonth(f.date, dateGlue.value)
}

// 网址在斜杠和点后换行，窄侧栏里不会被拦腰截断。
function linkSegments(url) {
  const text = prettyLink(url)
  const parts = []
  let buffer = ''
  for (const ch of text) {
    buffer += ch
    if (ch === '/' || ch === '.' || ch === '@') {
      parts.push(buffer)
      buffer = ''
    }
  }
  if (buffer) parts.push(buffer)
  return parts
}

// 城市已经在头部单独成栏，副行再列一遍就是重复；副行只留薪资与状态。
function intentionParts(f) {
  return [
    salaryText(f),
    f.status
  ].filter(Boolean)
}

// 金石的求职意向按标签摊成三栏，面议要读成「期望薪资：面议」而不是「薪资面议」。
function intentionFacts(f) {
  return [
    { label: '期望城市', value: f.city },
    { label: '期望薪资', value: f.salaryNegotiable ? '面议' : salaryText(f) },
    { label: '求职状态', value: f.status },
    { label: '期望职位', value: f.jobTitle }
  ].filter((part) => part.value)
}

function skillLevel(level) {
  return { 了解: 2, 熟悉: 3, 熟练: 4, 精通: 5 }[level] || 3
}
</script>

<template>
  <section class="paper-section" :class="[`is-${baseType}`, `paper-style-${config.sectionStyle || 'line'}`, { 'is-compact': compact }]">
    <h2 class="paper-section__title" :class="{ 'paper-flag-title': config.sectionStyle === 'flag' }">
      <i class="paper-section__icon"><PaperIcon :type="baseType" /></i>
      <span>{{ title }}</span>
    </h2>

    <div class="paper-section__body">
      <template v-if="type === 'skills_summary' || type === 'self_evaluation' || type === 'paper_publication'">
        <div class="paper-rich rich-content" v-html="safe(fields(items[0]).content)" />
      </template>

      <template v-else-if="type === 'hobby'">
        <p class="paper-plain">{{ fields(items[0]).content }}</p>
      </template>

      <template v-else-if="type === 'job_intention'">
        <template v-if="isRibbon">
          <div class="paper-facts-inline">
            <div v-for="part in intentionFacts(fields(items[0]))" :key="part.label">
              <span>{{ part.label }}：</span><strong>{{ part.value }}</strong>
            </div>
          </div>
        </template>
        <template v-else>
          <div v-for="item in items" :key="item.id" class="paper-item paper-item--intention">
            <div class="paper-item-head">
              <div class="paper-item-title">{{ item.fields.jobTitle || '目标岗位' }}</div>
              <div v-if="item.fields.city" class="paper-item-period">{{ item.fields.city }}</div>
            </div>
            <div v-if="salaryText(item.fields) || item.fields.status" class="paper-item-sub">
              <span v-for="part in intentionParts(item.fields)" :key="part">{{ part }}</span>
            </div>
          </div>
        </template>
      </template>

      <template v-else-if="type === 'skill_proficiency'">
        <div class="paper-skills">
          <div v-for="item in items" :key="item.id" class="paper-skill">
            <span class="paper-skill__name">{{ item.fields.name }}</span>
            <span class="paper-skill__dots" :aria-label="item.fields.level">
              <i v-for="n in 5" :key="n" :class="{ filled: n <= skillLevel(item.fields.level) }" />
            </span>
            <span class="paper-skill__level">{{ item.fields.level }}</span>
          </div>
        </div>
      </template>

      <template v-else-if="type === 'postgraduate_info'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item) }">
          <div class="paper-facts">
            <div v-if="item.fields.school"><span>报考院校</span><strong>{{ item.fields.school }}</strong></div>
            <div v-if="item.fields.major"><span>报考专业</span><strong>{{ item.fields.major }}</strong></div>
            <div v-if="item.fields.year"><span>考试年份</span><strong>{{ item.fields.year }}</strong></div>
            <div v-if="item.fields.initialScore"><span>初试成绩</span><strong>{{ item.fields.initialScore }}</strong></div>
            <div v-if="item.fields.finalScore"><span>复试成绩</span><strong>{{ item.fields.finalScore }}</strong></div>
          </div>
          <div v-if="item.fields.note" class="paper-rich rich-content" v-html="safe(item.fields.note)" />
        </div>
      </template>

      <template v-else-if="type === 'education'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item), 'is-marked': hasMarks }">
          <span v-if="hasMarks" class="paper-item-mark">
            <img v-if="item.fields.logo" :src="itemMediaUrl(item.fields.logo)" alt="" />
            <PaperIcon v-else :type="markType" />
          </span>
          <div v-if="sideColumn" class="paper-item-side">
            <div v-for="(line, index) in sideLines(item)" :key="index" :class="{ 'is-muted': line.muted }">{{ line.text }}</div>
          </div>
          <div class="paper-item-head">
            <div class="paper-item-title">
              {{ item.fields.school }}
              <span v-if="item.fields.schoolTag" class="paper-item-tag">{{ item.fields.schoolTag }}</span>
            </div>
            <div v-if="!sideColumn" class="paper-item-period">{{ periodOf(item) }}</div>
          </div>
          <div class="paper-item-sub">
            <span v-if="item.fields.major">{{ item.fields.major }}</span>
            <span v-if="item.fields.degree">{{ item.fields.degree }}</span>
          </div>
          <div v-if="item.fields.gpa || item.fields.rank" class="paper-meta-line">
            <span v-if="item.fields.gpa">GPA {{ item.fields.gpa }}</span>
            <span v-if="item.fields.rank">{{ item.fields.rank }}</span>
          </div>
          <p v-if="item.fields.courses?.length" class="paper-note">
            <span class="paper-note__tag">{{ courseTag }}</span>{{ item.fields.courses.join(courseSeparator) }}
          </p>
          <div v-if="item.fields.note" class="paper-rich rich-content" v-html="safe(item.fields.note)" />
        </div>
      </template>

      <template v-else-if="type === 'work_experience'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item), 'is-marked': hasMarks }">
          <span v-if="hasMarks" class="paper-item-mark">
            <img v-if="item.fields.logo" :src="itemMediaUrl(item.fields.logo)" alt="" />
            <PaperIcon v-else :type="markType" />
          </span>
          <div v-if="sideColumn && sideLines(item).length" class="paper-item-side">
            <div v-for="(line, index) in sideLines(item)" :key="index" :class="{ 'is-muted': line.muted }">{{ line.text }}</div>
          </div>
          <div class="paper-item-head">
            <div class="paper-item-title">{{ item.fields.company }}</div>
            <div v-if="!sideColumn" class="paper-item-period">{{ periodOf(item) }}</div>
          </div>
          <div class="paper-item-sub">
            <span v-if="item.fields.position">{{ item.fields.position }}</span>
            <span v-if="item.fields.department">{{ item.fields.department }}</span>
            <span v-if="item.fields.workType">{{ item.fields.workType }}</span>
            <span v-if="item.fields.city && !sideColumn">{{ item.fields.city }}</span>
          </div>
          <div class="paper-rich rich-content" v-html="safe(item.fields.content)" />
        </div>
      </template>

      <template v-else-if="type === 'project_experience'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item) }">
          <div class="paper-item-head">
            <div class="paper-item-title">{{ item.fields.name }}</div>
            <div class="paper-item-period">{{ periodOf(item) }}</div>
          </div>
          <div class="paper-item-sub">
            <span v-if="item.fields.role">{{ item.fields.role }}</span>
            <a v-if="item.fields.link" :href="item.fields.link" target="_blank" rel="noopener noreferrer">
              <template v-for="(segment, index) in linkSegments(item.fields.link)" :key="index"><wbr v-if="index" />{{ segment }}</template>
            </a>
          </div>
          <div class="paper-rich rich-content" v-html="safe(item.fields.content)" />
        </div>
      </template>

      <template v-else-if="type === 'campus_experience'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item) }">
          <div class="paper-item-head">
            <div class="paper-item-title">{{ item.fields.organization }}</div>
            <div class="paper-item-period">{{ periodOf(item) }}</div>
          </div>
          <div v-if="item.fields.role" class="paper-item-sub">{{ item.fields.role }}</div>
          <div class="paper-rich rich-content" v-html="safe(item.fields.content)" />
        </div>
      </template>

      <template v-else-if="type === 'portfolio'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item) }">
          <div class="paper-item-head">
            <div class="paper-item-title">{{ item.fields.title }}</div>
          </div>
          <div v-if="item.fields.link" class="paper-item-sub">
            <a :href="item.fields.link" target="_blank" rel="noopener noreferrer"><template v-for="(segment, index) in linkSegments(item.fields.link)" :key="index"><wbr v-if="index" />{{ segment }}</template></a>
          </div>
          <div class="paper-rich rich-content" v-html="safe(item.fields.content)" />
        </div>
      </template>

      <template v-else-if="type === 'competition'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item) }">
          <div class="paper-item-head">
            <div class="paper-item-title">{{ item.fields.name }}</div>
            <div class="paper-item-period">{{ periodOf(item) }}</div>
          </div>
          <div v-if="item.fields.award" class="paper-item-sub">{{ item.fields.award }}</div>
          <div class="paper-rich rich-content" v-html="safe(item.fields.content)" />
        </div>
      </template>

      <template v-else-if="type === 'award'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item) }">
          <div class="paper-item-head">
            <div class="paper-item-title">{{ item.fields.name }}</div>
            <div class="paper-item-period">{{ periodOf(item) }}</div>
          </div>
          <div v-if="item.fields.level" class="paper-item-sub">{{ item.fields.level }}</div>
          <p v-if="item.fields.description" class="paper-note">{{ item.fields.description }}</p>
        </div>
      </template>

      <template v-else-if="type === 'certificate'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item) }">
          <div class="paper-item-head">
            <div class="paper-item-title">{{ item.fields.name }}</div>
            <div class="paper-item-period">{{ formatMonth(item.fields.date) }}</div>
          </div>
          <div v-if="item.fields.issuer" class="paper-item-sub">{{ item.fields.issuer }}</div>
          <p v-if="item.fields.note" class="paper-note">{{ item.fields.note }}</p>
        </div>
      </template>

      <template v-else-if="type === 'journal_paper'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item) }">
          <div class="paper-item-head">
            <div class="paper-item-title">{{ item.fields.title }}</div>
            <div class="paper-item-period">{{ formatMonth(item.fields.period) }}</div>
          </div>
          <div class="paper-item-sub">
            <span v-if="item.fields.journal">{{ item.fields.journal }}</span>
            <span v-if="item.fields.authors">{{ item.fields.authors }}</span>
          </div>
          <div v-if="item.fields.link" class="paper-item-sub">
            <a :href="item.fields.link" target="_blank" rel="noopener noreferrer"><template v-for="(segment, index) in linkSegments(item.fields.link)" :key="index"><wbr v-if="index" />{{ segment }}</template></a>
          </div>
        </div>
      </template>

      <template v-else-if="baseType === 'custom'">
        <div v-for="item in items" :key="item.id" class="paper-item" :class="{ 'paper-item--undated': !dateTextOf(item) }">
          <div class="paper-item-head">
            <div class="paper-item-title">{{ item.fields.title }}</div>
            <div class="paper-item-period">{{ periodOf(item) }}</div>
          </div>
          <div v-if="item.fields.subtitle" class="paper-item-sub">{{ item.fields.subtitle }}</div>
          <div class="paper-rich rich-content" v-html="safe(item.fields.content)" />
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.paper-section {
  margin-bottom: var(--paper-section-gap, 6mm);
}

.paper-section:last-child {
  margin-bottom: 0;
}

.paper-section__title {
  position: relative;
  margin: 0 0 3mm;
  color: var(--paper-accent, #2f6f62);
  font-size: calc(var(--paper-base-size, 10.5pt) * 1.08);
  font-weight: 700;
  letter-spacing: 0.08em;
  line-height: 1.35;
}

.paper-section__body {
  color: #2a332e;
  font-size: var(--paper-base-size, 10.5pt);
  line-height: var(--paper-line-height, 1.65);
}

.paper-item + .paper-item {
  margin-top: 3mm;
}

.paper-item-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.paper-item-title {
  color: #18211d;
  font-weight: 700;
  font-size: calc(var(--paper-base-size, 10.5pt) * 1.02);
  line-height: 1.45;
}

/* 院校标签是中文简历的固定语义，用朱色而非模板主色。 */
.paper-item-tag {
  margin-left: 1.2mm;
  color: #c0392b;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.72);
  font-weight: 600;
  letter-spacing: 0.02em;
  vertical-align: 0.28em;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.paper-item-period {
  flex-shrink: 0;
  color: #68746d;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.88);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.paper-item-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 0 10px;
  margin-top: 0.6mm;
  color: #46534c;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.95);
}

.paper-item-sub span + span::before {
  content: "·";
  margin-right: 10px;
  color: #9aa39d;
}

.paper-item-sub a {
  color: var(--paper-accent, #2f6f62);
  text-decoration: underline;
  text-underline-offset: 2px;
  text-decoration-color: rgba(47, 111, 98, 0.35);
}

.paper-rich {
  margin-top: 1mm;
  color: #36423b;
}

.paper-rich :deep(ul),
.paper-rich :deep(ol) {
  margin: 0.5mm 0 0;
  padding-left: 1.2em;
}

.paper-rich :deep(li) {
  margin: 0.5mm 0;
}

.paper-rich :deep(p) {
  margin: 0 0 0.8mm;
}

.paper-plain {
  margin-top: 1mm;
  color: #36423b;
}

.paper-meta-line {
  display: flex;
  gap: 14px;
  margin-top: 0.5mm;
  color: #5a665f;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.9);
}

.paper-note {
  margin-top: 1mm;
  color: #59655e;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.9);
  white-space: pre-line;
}

/* 默认仍是「主修课程：」的读法，需要标签化的版式会覆盖这段伪元素。 */
.paper-note__tag::after {
  content: "：";
}

.paper-skills {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.4mm 8mm;
}

.paper-skill {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 1mm;
  border-bottom: 1px solid rgba(47, 111, 98, 0.12);
}

.paper-skill__name {
  font-weight: 600;
}

.paper-skill__dots {
  display: inline-flex;
  gap: 3px;
}

.paper-skill__dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(47, 111, 98, 0.18);
}

.paper-skill__dots i.filled {
  background: var(--paper-accent, #2f6f62);
}

.paper-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 2mm 6mm;
  margin-bottom: 1mm;
}

.paper-facts > div {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.paper-facts span {
  color: #7b867f;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.82);
}

.paper-facts strong {
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.98);
  font-weight: 600;
}

/* Section title styles */
.paper-style-line .paper-section__title {
  padding-bottom: 1.5mm;
  border-bottom: 1px solid color-mix(in srgb, var(--paper-accent, #2f6f62) 28%, transparent);
}

.paper-style-line .paper-section__title span::before {
  content: "";
  display: inline-block;
  width: 2.6mm;
  height: 2.6mm;
  margin-right: 2.2mm;
  border: 1.4px solid var(--paper-accent, #2f6f62);
  border-radius: 50%;
  vertical-align: 0.1mm;
}

.paper-style-caps .paper-section__title {
  display: flex;
  align-items: center;
  gap: 3mm;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.paper-style-caps .paper-section__title::after {
  content: "";
  flex: 1;
  height: 1px;
  background: color-mix(in srgb, var(--paper-accent, #2f6f62) 30%, transparent);
}

.paper-style-bar .paper-section__title {
  padding-left: 2.8mm;
  border-left: 2.4px solid var(--paper-accent, #2f6f62);
  letter-spacing: 0.06em;
}

.paper-style-block .paper-section__title {
  padding: 1.3mm 2.8mm;
  background: var(--paper-accent, #2f6f62);
  border-radius: 1px;
  color: #fff;
  letter-spacing: 0.08em;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.paper-style-dot .paper-section__title {
  display: flex;
  align-items: center;
  gap: 2.4mm;
}

.paper-style-dot .paper-section__title::before {
  content: "";
  width: 2mm;
  height: 2mm;
  border-radius: 50%;
  background: var(--paper-accent, #2f6f62);
}

.paper-style-dot .paper-section__title::after {
  content: "";
  flex: 1;
  height: 1px;
  background: color-mix(in srgb, var(--paper-accent, #2f6f62) 22%, transparent);
}

/* 青瓦：黑字标题 + 通栏细线，最传统的中文简历读法。 */
.paper-style-rule .paper-section__title {
  margin-bottom: 3.2mm;
  padding-bottom: 1.4mm;
  border-bottom: 1px solid rgba(31, 40, 48, 0.62);
  color: #1f2830;
  font-size: calc(var(--paper-base-size, 10.5pt) * 1.12);
  letter-spacing: 0.1em;
}

/* 图标默认只在需要的版式里出现，其余版式保持纯文字标题。 */
.paper-section__icon {
  display: none;
}

.paper-skill__level {
  display: none;
}

/* 技术：主色描边圆形图标 + 黑字标题 + 通栏虚线。 */
.paper-style-icon .paper-section__title {
  display: flex;
  align-items: center;
  gap: 2mm;
  color: #1f2824;
  font-size: calc(var(--paper-base-size, 10.5pt) * 1.08);
  letter-spacing: 0.04em;
}

.paper-style-icon .paper-section__icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 5.8mm;
  height: 5.8mm;
  border: 0.45mm solid var(--paper-accent, #2f6f62);
  border-radius: 50%;
  background: #fff;
  color: var(--paper-accent, #2f6f62);
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.paper-style-icon .paper-section__icon :deep(svg) {
  width: 56%;
  height: 56%;
}

.paper-style-icon .paper-section__title::after {
  content: "";
  flex: 1;
  align-self: center;
  border-top: 1px dotted color-mix(in srgb, var(--paper-accent, #2f6f62) 55%, transparent);
}

/* 金石：藏青标牌压在左侧竖线上，斜杠与通栏细线由 base.css 的 .paper-flag-title 画。 */
/* 参考稿的标牌底边到首行正文有 6.4mm 空档（基本信息那块只有 3.8mm），
   所以这段留白只加在模块标题上，不跟着页首标题一起放大。 */
.paper-style-flag .paper-section__title {
  margin-left: -5.9mm;
  margin-bottom: 6.4mm;
}

.paper-style-flag .paper-section__body {
  color: #2b3644;
}

.paper-style-flag .paper-item-title {
  color: #1d2733;
  font-size: calc(var(--paper-base-size, 10.5pt) * 1.2);
  font-weight: 600;
  line-height: 1.45;
}

.paper-style-flag .paper-item-sub {
  gap: 0;
  margin-top: 0.6mm;
  color: #2b3644;
  font-size: var(--paper-base-size, 10.5pt);
}

/* 专业与学历之间用竖线，参考稿不是间隔号。 */
.paper-style-flag .paper-item-sub span + span::before {
  content: "|";
  margin: 0 2.2mm;
  color: #c3cad3;
}

/* 院校标签在参考稿里是青绿斜体，不用其他模板的朱色角标。 */
.paper-style-flag .paper-item-tag {
  margin-left: 2mm;
  color: #16a085;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.98);
  font-style: italic;
  font-weight: 600;
  letter-spacing: 0;
  vertical-align: 0;
}

.paper-style-flag .paper-item-period {
  color: #868d8f;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.95);
}

.paper-style-flag .paper-rich {
  margin-top: 1.4mm;
  color: #2f3a48;
}

/* 徽标栏：本模块一条都没上传时整栏消失；缺图的条目画占位徽记，保持栏位对齐。 */
.paper-style-flag .paper-item.is-marked {
  display: flow-root;
}

.paper-style-flag .paper-item-mark {
  float: left;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 15.1mm;
  height: 15.1mm;
  margin: -1.9mm 4.3mm 0 0;
  border: 0.25mm solid #e3e7ee;
  background: #fff;
  color: #bcc5d0;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.paper-style-flag .paper-item-mark img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.paper-style-flag .paper-item-mark :deep(svg) {
  width: 46%;
  height: 46%;
}

/* 右侧栏与左侧两行逐行对齐：城市跟标题同字号，日期跟副行同字号。 */
.paper-style-flag .paper-item-side {
  float: right;
  margin-left: 3mm;
  color: #1d2733;
  font-size: calc(var(--paper-base-size, 10.5pt) * 1.2);
  line-height: 1.45;
  text-align: right;
}

.paper-style-flag .paper-item-side > * + * {
  margin-top: 0.6mm;
}

.paper-style-flag .paper-item-side .is-muted {
  color: #868d8f;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.95);
  line-height: 1.65;
}

/* 描述块通栏排在徽标下方，而不是缩进进文字栏。 */
.paper-style-flag .is-marked .paper-facts,
.paper-style-flag .is-marked .paper-meta-line,
.paper-style-flag .is-marked .paper-note,
.paper-style-flag .is-marked .paper-rich {
  clear: both;
}

/* 求职意向按标签连成一行流，放不下才换行：参考稿的「期望职位」就是接在最后一栏后面，
   而不是被塞进等宽三栏里挤成两行。 */
.paper-facts-inline {
  display: flex;
  flex-wrap: wrap;
  column-gap: 8mm;
  row-gap: 1.6mm;
  color: #2b3644;
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.98);
}

.paper-facts-inline > div {
  display: flex;
  align-items: baseline;
  min-width: 0;
}

.paper-facts-inline span {
  flex: none;
  margin-right: 1.2mm;
  white-space: nowrap;
}

.paper-facts-inline strong {
  color: #1d2733;
  font-weight: 400;
  overflow-wrap: anywhere;
}

.is-compact .paper-section__title {
  font-size: calc(var(--paper-base-size, 10.5pt) * 0.98);
}

.is-compact .paper-item + .paper-item {
  margin-top: 2.2mm;
}
</style>
