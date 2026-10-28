<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import PaperSection from './PaperSection.vue'
import PaperIcon from './PaperIcon.vue'
import { TEMPLATE_FALLBACKS, configOf } from '../data/templatePresets'
import { sectionHasContent, visibleSections } from '../data/sectionCatalog'
import { mediaUrl } from '../api/client'
import { paginatePaper } from '../utils/paperPagination'

const props = defineProps({
  content: { type: Object, default: () => ({ sections: [] }) },
  template: { type: Object, default: null },
  styleData: { type: Object, default: () => ({}) },
  avatar: { type: Object, default: null },
  paged: { type: Boolean, default: true }
})

const emit = defineEmits(['pages'])

const root = ref(null)
const pageMarks = ref([])
const pageCount = ref(1)
let running = false
let again = false
let observer

// 分页不依赖绘制帧：无头浏览器打印与后台标签页都可能拿不到 requestAnimationFrame。
function paginate() {
  if (!props.paged) {
    pageMarks.value = []
    pageCount.value = 1
    emit('pages', 1)
    return
  }
  if (running) {
    again = true
    return
  }
  running = true
  nextTick().then(() => {
    do {
      again = false
      if (!root.value) break
      const result = paginatePaper(root.value)
      pageMarks.value = result.marks
      pageCount.value = result.pages
      emit('pages', result.pages)
    } while (again)
    running = false
  })
}

onMounted(() => {
  paginate()
  if (document.fonts?.ready) document.fonts.ready.then(paginate)
  observer = new ResizeObserver(paginate)
  if (root.value) observer.observe(root.value)
  window.addEventListener('resize', paginate)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('resize', paginate)
  scopedStyle?.remove()
  scopedStyle = null
})

watch(() => [props.content, props.template, props.styleData, props.avatar, props.paged], paginate, { deep: true })

const photo = computed(() => mediaUrl(props.avatar?.url || ''))
const photoShape = computed(() => (props.avatar?.shape === 'circle' ? 'circle' : 'rect'))

const config = computed(() => configOf(props.template))
const sections = computed(() => visibleSections(props.content))
const basicSection = computed(() => sections.value.find((section) => section.type === 'basic_info'))
const basic = computed(() => basicSection.value?.items?.[0]?.fields || {})
const intentionSection = computed(() => sections.value.find((section) => section.type === 'job_intention'))
const intention = computed(() => intentionSection.value?.items?.[0]?.fields || {})
const titleOnly = computed(() => (intentionSection.value?.meta?.displayMode || 'title_only') === 'title_only')

const name = computed(() => basic.value.name?.trim() || '你的姓名')
const isRibbon = computed(() => config.value.layout === 'ribbon')
// 岗位只在「显示在姓名下方」这一档进页眉；独立模块模式由正文自己带岗位，重复一次反而像两份。
const jobTitle = computed(() => (
  titleOnly.value && !isRibbon.value ? intention.value.jobTitle?.trim() || '' : ''
))
// 金石把性别与身份读在姓名后面，联系方式那一行留给手机、邮箱与生日。
const identity = computed(() => {
  const parts = []
  if (isRibbon.value && basic.value.showGender && basic.value.gender) parts.push(basic.value.gender)
  if (basic.value.identity?.trim()) parts.push(basic.value.identity.trim())
  return parts.join(' · ')
})
const monogram = computed(() => name.value.slice(0, 1))

// 籍贯、民族、政治面貌按需填写，写进页眉的同一行；窄栏版式另起一个「基本信息」块。
const basicFacts = computed(() => {
  const list = []
  const add = (type, value) => {
    if (value?.trim()) list.push({ type, value: value.trim(), plainLabel: true })
  }
  add('nativePlace', basic.value.nativePlace)
  add('ethnicity', basic.value.ethnicity)
  add('politics', basic.value.politics)
  add('maritalStatus', basic.value.maritalStatus)
  return list
})

const contacts = computed(() => {
  const list = []
  if (basic.value.phone) list.push({ type: 'phone', value: basic.value.phone })
  if (basic.value.email) list.push({ type: 'email', value: basic.value.email })
  if (basic.value.city) list.push({ type: 'city', value: basic.value.city })
  list.push(...basicFacts.value)
  if (basic.value.website) list.push({ type: 'website', value: basic.value.website })
  if (basic.value.showGender && basic.value.gender && !isRibbon.value) list.push({ type: 'gender', value: basic.value.gender })
  if (basic.value.showAge && basic.value.birthday) {
    list.push({ type: 'birthday', value: isRibbon.value ? basic.value.birthday : basic.value.birthday.replace('-', '.') })
  }
  return list
})
// 侧栏已经把联系方式单独成块，籍贯等不该混在「联系方式」标题下。
const railContacts = computed(() => contacts.value.filter((item) => !item.plainLabel))

const contactLabels = {
  phone: '手机',
  email: '邮箱',
  city: '居住地',
  nativePlace: '籍贯',
  ethnicity: '民族',
  politics: '政治面貌',
  maritalStatus: '婚姻状况',
  website: '主页',
  gender: '性别',
  birthday: '生日'
}

const paperSections = computed(() => sections.value.filter((section) => {
  if (section.type === 'basic_info') return false
  // 金石没有「只把期望职位放在页眉」这一档，求职意向始终按标签摊成独立模块。
  if (section.type === 'job_intention' && titleOnly.value && !isRibbon.value) return false
  return sectionHasContent(section)
}))

const isSidebar = computed(() => config.value.layout === 'sidebar')
const isRail = computed(() => config.value.layout === 'rail')
// 侧栏与通栏版式把求职意向收进窄栏，正文才能专注经历。
const asideTypes = computed(() => {
  const base = ['skill_proficiency', 'certificate', 'hobby', 'portfolio']
  return isSidebar.value || isRail.value ? [...base, 'job_intention'] : base
})
const asideSections = computed(() => paperSections.value.filter((section) => asideTypes.value.includes(section.type)))
const mainSections = computed(() => paperSections.value.filter((section) => !asideTypes.value.includes(section.type)))
// 秋叶把自我评价收进页眉当简介，参考稿里姓名下方就是这段无标题文字。
const headerSummarySection = computed(() => (
  config.value.layout === 'timeline'
    ? paperSections.value.find((section) => section.type === 'self_evaluation' && section.items?.some((item) => item.fields?.content))
    : null
))
const headerSummaryHtml = computed(() => DOMPurify.sanitize(
  headerSummarySection.value?.items?.[0]?.fields?.content || '',
  { ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'ul', 'ol', 'li'], ALLOWED_ATTR: [] }
))
const labeledHeader = computed(() => ['formal', 'timeline'].includes(config.value.layout))
// 「手机 138…」比裸号码更好扫读，所有官方版式统一带标签。
const labeledContacts = computed(() => true)
// 秋叶用图标代替文字标签，页眉更接近参考稿的一行联系方式。
const contactIcons = computed(() => config.value.layout === 'timeline')
// 通栏照片侧栏与居中横幅各自有照片位，页眉再放一次会重复。
const headerPhoto = computed(() =>
  Boolean(photo.value) && !isRail.value && config.value.layout !== 'center')
// 支持照片的版式即使没上传头像也占住照片位，避免版式在两种状态间跳动。
const photoSlot = computed(() =>
  headerPhoto.value || (config.value.photo && !isRail.value && config.value.layout !== 'center'))
const visibleMain = computed(() => {
  const list = (isSidebar.value || isRail.value) ? mainSections.value : paperSections.value
  const summary = headerSummarySection.value
  return summary ? list.filter((section) => section.id !== summary.id) : list
})

const filter = computed(() => props.styleData?.filter || 'standard')
const footerEnabled = computed(() => props.styleData?.showBrandFooter !== false)

const paperStyle = computed(() => {
  const fontStep = Number(props.styleData?.fontSizeStep || 0)
  const spacingStep = Number(props.styleData?.spacingStep || 0)
  const vars = {
    '--paper-accent': config.value.accent || '#2f6f62',
    '--paper-font-family': config.value.font === 'serif' ? 'var(--serif)' : 'var(--sans)',
    '--paper-base-size': `${(10.5 + fontStep * 0.5).toFixed(2)}pt`,
    '--paper-line-height': (1.65 + spacingStep * 0.08).toFixed(2),
    '--paper-section-gap': `${(5.6 + spacingStep * 0.9).toFixed(2)}mm`
  }
  // 只有用户自己调过才写这四个变量：各版式的默认留白并不相同（侧影通栏页眉靠 15mm 反白、
  // 竖排用 11/14mm），缺省时让它们各自的取值生效，改这一处不会把 12 套模板的观感全推平。
  const margin = props.styleData?.pageMargin
  if (margin?.custom) {
    vars['--paper-mt'] = `${Number(margin.top ?? 15)}mm`
    vars['--paper-mr'] = `${Number(margin.right ?? 15)}mm`
    vars['--paper-mb'] = `${Number(margin.bottom ?? 12)}mm`
    vars['--paper-ml'] = `${Number(margin.left ?? 15)}mm`
  }
  return vars
})

// 导入包带来的样式已经由服务端净化过，这里只用 textContent 写进一个独立的 <style>，
// 不给模板作者任何 HTML 注入面。它插在后加载位置，因此与内置 scoped 样式打平时由作者胜出，
// 不需要（净化器也不允许）!important。
let scopedStyle = null

function applyScopedStyle() {
  const css = config.value.styleCss
  if (!css || !config.value.scopeClass) {
    scopedStyle?.remove()
    scopedStyle = null
    return
  }
  if (!scopedStyle) {
    scopedStyle = document.createElement('style')
    scopedStyle.setAttribute('data-template-scope', config.value.scopeClass)
    document.head.appendChild(scopedStyle)
  }
  scopedStyle.textContent = css
}

// 换模板时先注入再测量：内置样式与导入包的高度不同，断页位置也随之变化。
watch(() => [config.value.scopeClass, config.value.styleCss], () => {
  applyScopedStyle()
  paginate()
}, { immediate: true })

function displayUrl(url) {
  return url.replace(/^https?:\/\//, '').replace(/\/$/, '')
}

function contactHref(item) {
  if (item.type === 'phone') return `tel:${item.value}`
  if (item.type === 'email') return `mailto:${item.value}`
  if (item.type === 'website') return item.value
  return null
}

// 在 @ . / 之后给出换行点，窄栏里邮箱和网址不会从单词中间断开。
function breakable(value) {
  const text = String(value ?? '')
  const parts = []
  let buffer = ''
  for (const ch of text) {
    buffer += ch
    if (ch === '@' || ch === '.' || ch === '/' || ch === '·') {
      parts.push(buffer)
      buffer = ''
    }
  }
  if (buffer) parts.push(buffer)
  return parts
}
</script>

<template>
  <article
    ref="root"
    class="resume-paper"
    :class="[`layout-${config.layout || 'minimal'}`, `filter-${filter}`, config.scopeClass]"
    :style="paperStyle"
  >
    <div v-if="pageMarks.length" class="paper-pages no-print" aria-hidden="true">
      <div
        v-for="(offset, index) in pageMarks"
        :key="index"
        class="paper-page-sep"
        :style="{ top: `${offset}px` }"
      >
        <span class="paper-page-sep__label">第 {{ index + 2 }} 页 · 共 {{ pageMarks.length + 1 }} 页</span>
      </div>
    </div>
    <aside v-if="isRail" class="paper-rail">
      <img
        v-if="photo"
        class="paper-photo paper-photo--rail"
        :class="`paper-photo--${photoShape}`"
        :src="photo"
        alt=""
      />
      <div v-else class="rail-monogram">{{ monogram }}</div>
      <div class="rail-block">
        <h3 class="rail-title">联系方式</h3>
        <ul class="rail-contacts">
          <li v-for="item in railContacts" :key="item.type">
            <span class="rail-label">{{ contactLabels[item.type] }}</span>
            <a v-if="contactHref(item)" :href="contactHref(item)">
              <template v-for="(segment, index) in breakable(item.type === 'website' ? displayUrl(item.value) : item.value)" :key="index"><wbr v-if="index" />{{ segment }}</template>
            </a>
            <span v-else>
              <template v-for="(segment, index) in breakable(item.value)" :key="index"><wbr v-if="index" />{{ segment }}</template>
            </span>
          </li>
        </ul>
      </div>
      <div v-if="basicFacts.length" class="rail-block">
        <h3 class="rail-title">基本信息</h3>
        <ul class="rail-contacts">
          <li v-for="item in basicFacts" :key="item.type">
            <span class="rail-label">{{ contactLabels[item.type] }}</span>
            <span>{{ item.value }}</span>
          </li>
        </ul>
      </div>
      <PaperSection
        v-for="section in asideSections"
        :key="section.id"
        :section="section"
        :config="config"
        compact
      />
    </aside>

    <div class="paper-flow">
      <div v-if="config.layout === 'ribbon'" class="paper-banner" aria-hidden="true">
        <b class="paper-banner__band paper-banner__band--navy" />
        <b class="paper-banner__band paper-banner__band--gold" />
        <span class="paper-banner__marks">
          <i><PaperIcon type="education" /></i>
          <i><PaperIcon type="work_experience" /></i>
          <i><PaperIcon type="skills_summary" /></i>
        </span>
      </div>
      <h2 v-if="config.layout === 'ribbon'" class="paper-banner__title paper-flag-title">
        <span>基本信息</span>
      </h2>
      <header class="paper-header" :class="{ 'paper-header--photo': photoSlot }">
        <div v-if="config.layout === 'center'" class="paper-header__crest">
          <img v-if="photo" class="paper-photo paper-photo--crest" :class="`paper-photo--${photoShape}`" :src="photo" alt="" />
          <div v-else class="paper-monogram">{{ monogram }}</div>
        </div>
        <div class="paper-header__main">
          <div class="paper-name-row">
            <h1 class="paper-name">{{ name }}</h1>
            <span v-if="identity" class="paper-identity">{{ identity }}</span>
          </div>
          <p v-if="jobTitle" class="paper-role"><span v-if="labeledHeader" class="paper-role__label">求职意向：</span>{{ jobTitle }}</p>
          <div v-if="headerSummaryHtml" class="paper-header__summary rich-content" v-html="headerSummaryHtml" />
          <div v-if="!isRail" class="paper-contacts" :class="{ 'paper-contacts--labeled': labeledContacts }">
            <template v-for="item in contacts" :key="item.type">
              <a v-if="contactHref(item)" :href="contactHref(item)" class="paper-contact">
                <span v-if="labeledContacts" class="paper-contact__label" :class="{ 'paper-contact__label--text': item.plainLabel }">
                  <PaperIcon v-if="contactIcons && !item.plainLabel" :type="item.type" />
                  <template v-else>{{ contactLabels[item.type] }}</template>
                </span>
                <template v-for="(segment, index) in breakable(item.type === 'website' ? displayUrl(item.value) : item.value)" :key="index"><wbr v-if="index" />{{ segment }}</template>
              </a>
              <span v-else class="paper-contact">
                <span v-if="labeledContacts" class="paper-contact__label" :class="{ 'paper-contact__label--text': item.plainLabel }">
                  <PaperIcon v-if="contactIcons && !item.plainLabel" :type="item.type" />
                  <template v-else>{{ contactLabels[item.type] }}</template>
                </span>
                <template v-for="(segment, index) in breakable(item.value)" :key="index"><wbr v-if="index" />{{ segment }}</template>
              </span>
            </template>
          </div>
        </div>
        <img
          v-if="headerPhoto"
          class="paper-photo"
          :class="`paper-photo--${photoShape}`"
          :src="photo"
          alt=""
        />
        <div v-else-if="config.photo && !isRail && config.layout !== 'center'" class="paper-photo paper-photo--monogram">{{ monogram }}</div>
      </header>

      <div class="paper-content">
        <div v-if="isSidebar" class="sidebar-layout">
          <aside class="sidebar-aside">
            <img
              v-if="photo && !isSidebar"
              class="paper-photo paper-photo--aside"
              :class="`paper-photo--${photoShape}`"
              :src="photo"
              alt=""
            />
            <PaperSection
              v-for="section in asideSections"
              :key="section.id"
              :section="section"
              :config="config"
              compact
            />
          </aside>
          <div class="sidebar-main">
            <PaperSection
              v-for="section in mainSections"
              :key="section.id"
              :section="section"
              :config="config"
            />
          </div>
        </div>
        <template v-else>
          <PaperSection
            v-for="section in visibleMain"
            :key="section.id"
            :section="section"
            :config="config"
          />
        </template>
        <div v-if="!visibleMain.length" class="paper-empty">
          <p>从「基本信息」开始，慢慢把履历写进来。</p>
        </div>
      </div>

      <footer v-if="footerEnabled" class="paper-footer">
        <span>极简简历 · MINIWHO</span>
        <span>{{ name }} · Resume</span>
      </footer>
    </div>
  </article>
</template>

<style scoped>
.resume-paper {
  color: #232c27;
  background: #fff;
  font-family: var(--paper-font-family);
  font-size: var(--paper-base-size);
  line-height: var(--paper-line-height);
  --paper-surface: #fff;
}

.resume-paper.filter-soft {
  background: #fdfaf2;
}

.resume-paper.filter-mono {
  filter: grayscale(1);
}

/* 分页提示线：位置与打印分页点完全一致。 */
.paper-pages {
  position: absolute;
  inset: 0;
  z-index: 5;
  pointer-events: none;
}

.paper-page-sep {
  position: absolute;
  right: 0;
  left: 0;
  height: 0;
  border-top: 1px dashed color-mix(in srgb, var(--paper-accent) 55%, transparent);
}

.paper-page-sep__label {
  position: absolute;
  top: -9px;
  right: 0;
  padding: 2px 10px;
  border: 1px solid color-mix(in srgb, var(--paper-accent) 28%, transparent);
  border-radius: 999px;
  background: #fff;
  color: var(--paper-accent);
  font-family: var(--sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.paper-header {
  margin-bottom: 7mm;
  padding-bottom: 4mm;
  border-bottom: 1px solid color-mix(in srgb, var(--paper-accent) 30%, transparent);
}

.paper-name-row {
  display: flex;
  align-items: center;
  gap: 3mm;
  flex-wrap: wrap;
}

.paper-name {
  margin: 0;
  font-family: var(--serif);
  font-size: calc(var(--paper-base-size) * 2.35);
  font-weight: 700;
  letter-spacing: 0.08em;
  line-height: 1.2;
}

.paper-identity {
  padding: 0.4mm 2.2mm;
  border: 1px solid color-mix(in srgb, var(--paper-accent) 30%, transparent);
  border-radius: 999px;
  color: var(--paper-accent);
  font-size: calc(var(--paper-base-size) * 0.72);
  letter-spacing: 0.06em;
}

.paper-role {
  margin-top: 1.6mm;
  color: var(--paper-accent);
  font-size: calc(var(--paper-base-size) * 1.02);
  font-weight: 600;
  letter-spacing: 0.06em;
}

.paper-contacts {
  display: flex;
  flex-wrap: wrap;
  gap: 1mm 5mm;
  margin-top: 2.6mm;
  color: #4e5b54;
  font-size: calc(var(--paper-base-size) * 0.87);
}

.paper-contact {
  position: relative;
  color: inherit;
  overflow-wrap: anywhere;
}

.paper-contact + .paper-contact::before {
  content: "/";
  position: absolute;
  left: -2.9mm;
  color: #aab2ad;
}

.paper-contact__label {
  flex: none;
  margin-right: 1.8mm;
  color: #8a949b;
  white-space: nowrap;
}

.paper-monogram {
  display: grid;
  place-items: center;
  width: 18mm;
  height: 18mm;
  margin: 0 auto 4mm;
  border-radius: 50%;
  background: color-mix(in srgb, var(--paper-accent) 12%, #fff);
  color: var(--paper-accent);
  font-family: var(--serif);
  font-size: calc(var(--paper-base-size) * 2.1);
  font-weight: 700;
}

.paper-content {
  min-height: 180mm;
}

/* 头像：一寸照按 25mm×35mm，圆形头像按 26mm 直径，均靠页眉右侧对齐。 */
.paper-header--photo {
  display: flex;
  align-items: flex-start;
  gap: 6mm;
}

.paper-header--photo .paper-header__main {
  flex: 1 1 auto;
  min-width: 0;
}

.layout-academic .paper-header--photo {
  display: block;
}

.paper-photo {
  flex: 0 0 auto;
  width: 25mm;
  height: 35mm;
  border-radius: 1mm;
  object-fit: cover;
  background: #eef1ef;
  box-shadow: 0 1mm 3mm -1.5mm rgba(24, 38, 31, 0.4);
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.paper-photo--circle {
  width: 26mm;
  height: 26mm;
  border-radius: 50%;
}

.paper-header__crest {
  display: flex;
  justify-content: center;
}

.paper-photo--crest {
  box-shadow: none;
  border: 0.6mm solid color-mix(in srgb, var(--paper-accent) 22%, #fff);
}

.paper-photo--aside {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 5 / 7;
  margin: 0 0 4mm;
  border-radius: 1mm;
}

.paper-photo--aside.paper-photo--circle {
  width: 32mm;
  aspect-ratio: 1 / 1;
  margin-inline: auto;
}

.layout-band .paper-photo {
  border: 0.8mm solid #fff;
  box-shadow: none;
}

.layout-band .paper-photo--circle {
  border-radius: 50%;
}

.paper-empty {
  display: grid;
  place-items: center;
  min-height: 80mm;
  border: 1px dashed color-mix(in srgb, var(--paper-accent) 22%, transparent);
  color: #9aa39d;
  font-size: 9pt;
}

.paper-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 9mm;
  padding-top: 2.5mm;
  border-top: 1px solid rgba(35, 44, 39, 0.12);
  color: #9aa39d;
  font-size: calc(var(--paper-base-size) * 0.68);
  letter-spacing: 0.08em;
}

/* 与 paperPagination.js 的 FOOTER_MIN_GAP 保持一致：页脚收不进本页时压缩这段间距。 */
.paper-footer.paper-footer-tight {
  margin-top: 8px;
}

/* Academic: 墨线 —— 左侧小照片，日期独立成栏，朱红强调 */
.layout-academic .paper-header {
  margin-bottom: 6mm;
  padding-bottom: 3.4mm;
  text-align: left;
  border-bottom: 1px solid #d8d8d8;
}

.layout-academic .paper-header--photo {
  display: flex;
  align-items: flex-start;
  gap: 6mm;
}

.layout-academic .paper-photo {
  order: -1;
  width: 23mm;
  height: 31mm;
  border: 0.4mm solid #dcdcdc;
  box-shadow: none;
}

.layout-academic .paper-photo--monogram {
  display: grid;
  place-items: center;
  background: #f2f4f3;
  color: #8b9590;
  font-family: var(--serif);
  font-size: calc(var(--paper-base-size) * 2);
  font-weight: 700;
}

.layout-academic .paper-name-row,
.layout-academic .paper-contacts {
  justify-content: flex-start;
}

.layout-academic .paper-name {
  color: #1a1a1a;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2.05);
  letter-spacing: 0.08em;
}

.layout-academic .paper-identity {
  padding: 0;
  border: none;
  color: #5d5d5d;
  font-size: calc(var(--paper-base-size) * 0.88);
}

.layout-academic .paper-role {
  margin-top: 1.4mm;
  color: #333;
  font-weight: 400;
  letter-spacing: 0.04em;
}

.layout-academic .paper-contacts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1mm 8mm;
  margin-top: 2.4mm;
  color: #454545;
  font-size: calc(var(--paper-base-size) * 0.88);
}

.layout-academic .paper-contact + .paper-contact::before {
  display: none;
}

.layout-academic .paper-contact__label {
  margin-right: 1.8mm;
  color: #8a8a8a;
}

.layout-academic .paper-contact {
  margin-right: 0;
}

/* 日期独占左栏，单位与岗位排在右侧，和参考稿的阅读顺序一致。 */
.layout-academic :deep(.paper-item) {
  display: grid;
  grid-template-columns: 20mm minmax(0, 1fr);
  column-gap: 3.5mm;
  align-items: start;
}

.layout-academic :deep(.paper-item-head) {
  display: contents;
}

.layout-academic :deep(.paper-item-period) {
  grid-column: 1;
  grid-row: 1 / span 6;
  padding-top: 0.5mm;
  color: #8a8a8a;
  font-size: calc(var(--paper-base-size) * 0.78);
  line-height: 1.4;
  text-align: left;
  white-space: normal;
}

.layout-academic :deep(.paper-item-title),
.layout-academic :deep(.paper-item-sub),
.layout-academic :deep(.paper-facts),
.layout-academic :deep(.paper-meta-line),
.layout-academic :deep(.paper-note),
.layout-academic :deep(.paper-rich) {
  grid-column: 2;
}

/* 校徽 / 公司 LOGO 与日期共用左栏：徽标在上、日期在下。 */
.layout-academic :deep(.paper-item.is-marked) {
  display: grid !important;
}

.layout-academic :deep(.paper-item.is-marked .paper-item-mark) {
  float: none;
  grid-column: 1;
  grid-row: 1 / span 2;
  margin: 0 0 1.6mm;
}

.layout-academic :deep(.paper-item.is-marked .paper-item-period) {
  grid-row: 3 / span 4;
}

.layout-academic :deep(.is-work_experience .paper-item-title),
.layout-academic :deep(.is-project_experience .paper-item-title) {
  color: var(--paper-accent);
}

.layout-academic :deep(.paper-item-tag) {
  color: var(--paper-accent);
}

.layout-academic :deep(.paper-style-caps .paper-section__title) {
  display: block;
  margin-bottom: 2.8mm;
  padding-bottom: 1.2mm;
  border-bottom: 1px solid #333;
  color: #1a1a1a;
  font-size: calc(var(--paper-base-size) * 1.08);
  letter-spacing: 0.04em;
  text-transform: none;
}

.layout-academic :deep(.paper-style-caps .paper-section__title)::after {
  display: none;
}

/* Minimal: 青禾 —— 只用字号、留白与发丝线分层的排字版式 */
.layout-minimal .paper-header {
  margin-bottom: 7mm;
  padding-bottom: 0;
  border-bottom: none;
}

.layout-minimal .paper-header--photo {
  align-items: flex-start;
  gap: 8mm;
}

.layout-minimal .paper-header__main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 5mm;
  row-gap: 0.8mm;
}

.layout-minimal .paper-name {
  color: #1b2126;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 1.95);
  letter-spacing: 0.04em;
}

.layout-minimal .paper-identity {
  padding: 0;
  border: none;
  color: #6f777d;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-minimal .paper-role {
  margin-top: 0;
  color: #3a4147;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.layout-minimal .paper-contacts {
  display: grid;
  flex: 1 0 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1mm 10mm;
  margin-top: 2.6mm;
  color: #39424a;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-minimal .paper-contact + .paper-contact::before {
  display: none;
}

.layout-minimal .paper-contact__label {
  margin-right: 2mm;
  color: #9aa1a7;
}

.layout-minimal .paper-photo {
  width: 25mm;
  height: 35mm;
  border-radius: 0;
  box-shadow: none;
}

.layout-minimal .paper-photo--monogram {
  display: grid;
  place-items: center;
  background: #f3f4f5;
  color: #8d959b;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2.2);
  font-weight: 700;
}

.layout-minimal :deep(.paper-style-line .paper-section__title) {
  margin-bottom: 2.6mm;
  padding-bottom: 1.3mm;
  border-bottom: 1px solid #d7dade;
  color: #1b2126;
  font-size: calc(var(--paper-base-size) * 1.1);
  letter-spacing: 0.06em;
}

.layout-minimal :deep(.paper-style-line .paper-section__title span::before) {
  display: none;
}

.layout-minimal :deep(.paper-item-title) {
  color: #1b2126;
}

.layout-minimal :deep(.paper-item-period) {
  color: #9aa1a7;
}

.layout-minimal :deep(.paper-item-tag) {
  margin-left: 1.6mm;
  padding: 0.2mm 1.6mm;
  border-radius: 0.6mm;
  background: #eef0f2;
  color: #6b737a;
  font-size: calc(var(--paper-base-size) * 0.72);
  font-weight: 500;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-minimal :deep(.paper-note__tag) {
  display: inline-block;
  margin-right: 1.8mm;
  padding: 0.2mm 1.6mm;
  border-radius: 0.6mm;
  background: #eef0f2;
  color: #6b737a;
  font-size: calc(var(--paper-base-size) * 0.78);
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-minimal :deep(.paper-note__tag)::after {
  content: "";
}

/* Sidebar: 侧影 —— 藏青通栏页眉，右侧浅灰信息栏 */
.layout-sidebar {
  padding-top: 0;
}

.layout-sidebar .paper-header {
  margin: 0 calc(var(--paper-mr, 15mm) * -1) 7mm calc(var(--paper-ml, 15mm) * -1);
  padding: 11mm var(--paper-mr, 15mm) 8mm var(--paper-ml, 15mm);
  border: none;
  background: var(--paper-accent);
  color: #fff;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-sidebar .paper-header--photo {
  align-items: center;
  gap: 7mm;
}

.layout-sidebar .paper-photo {
  order: -1;
  width: 24mm;
  height: 33mm;
  border: 0.5mm solid rgba(255, 255, 255, 0.32);
  border-radius: 0.8mm;
  box-shadow: none;
}

.layout-sidebar .paper-photo--monogram {
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-family: var(--serif);
  font-size: calc(var(--paper-base-size) * 2.2);
  font-weight: 700;
}

.layout-sidebar .paper-name {
  color: #fff;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 1.95);
  letter-spacing: 0.05em;
}

.layout-sidebar .paper-identity {
  padding: 0;
  border: none;
  color: rgba(255, 255, 255, 0.72);
  font-size: calc(var(--paper-base-size) * 0.86);
}

.layout-sidebar .paper-role {
  margin-top: 1mm;
  color: rgba(255, 255, 255, 0.92);
  font-weight: 600;
}

.layout-sidebar .paper-contacts {
  display: flex;
  flex-wrap: wrap;
  gap: 1.2mm 8mm;
  margin-top: 2.6mm;
  color: rgba(255, 255, 255, 0.9);
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-sidebar .paper-contact + .paper-contact::before {
  display: none;
}

.layout-sidebar .paper-contact__label {
  margin-right: 1.8mm;
  color: rgba(255, 255, 255, 0.6);
}

.layout-sidebar .paper-content {
  min-height: 190mm;
}

.layout-sidebar .sidebar-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 54mm;
  gap: 7mm;
  align-items: start;
}

/* 跨页时底色跟着正文列铺满每一页。 */
.layout-sidebar .sidebar-aside {
  grid-column: 2;
  grid-row: 1;
  align-self: stretch;
  padding: 5mm 4.5mm;
  background: #f4f5f7;
  border-top: 2px solid var(--paper-accent);
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-sidebar .sidebar-main {
  grid-column: 1;
  grid-row: 1;
  min-width: 0;
}

.layout-sidebar .sidebar-aside :deep(.paper-section) {
  margin-top: 6mm;
}

.layout-sidebar :deep(.paper-style-bar .paper-section__title) {
  display: flex;
  align-items: center;
  gap: 2mm;
  padding: 0;
  border-left: none;
  color: #1d2836;
  font-size: calc(var(--paper-base-size) * 1.02);
  letter-spacing: 0.04em;
}

.layout-sidebar :deep(.paper-style-bar .paper-section__icon) {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 5.6mm;
  height: 5.6mm;
  border-radius: 1.4mm;
  background: var(--paper-accent);
  color: #fff;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-sidebar :deep(.paper-style-bar .paper-section__icon svg) {
  width: 62%;
  height: 62%;
}

.layout-sidebar :deep(.paper-skills) {
  grid-template-columns: minmax(0, 1fr);
  gap: 1.6mm;
}

.layout-sidebar :deep(.paper-skill) {
  padding-bottom: 0;
  border-bottom: none;
}

.layout-sidebar :deep(.paper-skill__name) {
  display: flex;
  flex: 1;
  align-items: baseline;
  gap: 2mm;
}

.layout-sidebar :deep(.paper-skill__name)::after {
  content: "";
  flex: 1;
  border-bottom: 1px dotted #c3c9d0;
}

.layout-sidebar :deep(.paper-skill__dots) {
  display: none;
}

.layout-sidebar :deep(.paper-skill__level) {
  display: inline;
  color: #1d2836;
  font-size: calc(var(--paper-base-size) * 0.86);
}

/* Band: 城市 —— 白底页眉配深靛蓝模块色带 */
.layout-band .paper-header {
  margin-bottom: 6mm;
  padding-bottom: 0;
  border-bottom: none;
}

.layout-band .paper-header--photo {
  align-items: flex-start;
  gap: 7mm;
}

.layout-band .paper-photo {
  order: -1;
  width: 25mm;
  height: 35mm;
  border: 0.4mm solid #e1e5eb;
  box-shadow: none;
}

.layout-band .paper-photo--monogram {
  display: grid;
  place-items: center;
  background: #eef1f5;
  color: #8b939e;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2.2);
  font-weight: 700;
}

.layout-band .paper-header__main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 5mm;
  row-gap: 0.6mm;
}

.layout-band .paper-name {
  color: #1c2430;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2);
  letter-spacing: 0.04em;
}

.layout-band .paper-identity {
  padding: 0;
  border: none;
  color: #6c7683;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-band .paper-role {
  margin-top: 0;
  color: #333d4a;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.layout-band .paper-contacts {
  display: grid;
  flex: 1 0 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1mm 10mm;
  margin-top: 3.4mm;
  padding-top: 2.6mm;
  border-top: 1px solid #d9dfe8;
  color: #2f3946;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-band .paper-contact + .paper-contact::before {
  display: none;
}

.layout-band .paper-contact__label {
  margin-right: 2mm;
  color: #7d8794;
}

.layout-band :deep(.paper-style-block .paper-section__title) {
  padding: 1.3mm 3mm;
  background: var(--paper-accent);
  border-radius: 0;
  color: #fff;
  font-size: calc(var(--paper-base-size) * 1.03);
  letter-spacing: 0.08em;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-band :deep(.paper-item-title) {
  color: #1c2430;
}

.layout-band :deep(.paper-item-period) {
  color: #8a93a0;
}

.layout-band :deep(.paper-item-sub) {
  color: #5a6471;
}

/* Center: 简雅 —— 低多边形淡青横幅与居中页眉 */
.layout-center {
  padding-top: 0;
}

.layout-center .paper-header {
  margin: 0 calc(var(--paper-mr, 15mm) * -1) 6mm calc(var(--paper-ml, 15mm) * -1);
  padding: 2mm var(--paper-mr, 15mm) 0 var(--paper-ml, 15mm);
  border: none;
  background-color: #d9edf0;
  background-image:
    linear-gradient(118deg, rgba(255, 255, 255, 0.6) 0 17%, transparent 17% 45%, rgba(255, 255, 255, 0.34) 45% 61%, transparent 61%),
    linear-gradient(196deg, rgba(31, 158, 143, 0.2) 0 25%, transparent 25% 57%, rgba(31, 158, 143, 0.13) 57% 100%);
  text-align: center;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-center .paper-header__crest {
  margin-bottom: 2mm;
}

.layout-center .paper-photo--crest {
  width: 25mm;
  height: 34mm;
  border: 1mm solid #fff;
  border-radius: 0;
  box-shadow: 0 1mm 3mm -1.2mm rgba(26, 62, 66, 0.35);
}

.layout-center .paper-monogram {
  width: 20mm;
  height: 20mm;
  margin: 0 auto 2mm;
  border: 1mm solid #fff;
  background: rgba(255, 255, 255, 0.5);
  color: var(--paper-accent);
}

.layout-center .paper-name-row {
  justify-content: center;
}

.layout-center .paper-name {
  color: #1f2a2e;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 1.9);
  letter-spacing: 0.1em;
}

.layout-center .paper-identity {
  padding: 0;
  border: none;
  color: #4f6267;
  font-size: calc(var(--paper-base-size) * 0.88);
}

.layout-center .paper-role {
  margin-top: 1mm;
  color: #26454a;
  font-weight: 400;
  letter-spacing: 0.04em;
}

.layout-center .paper-contacts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1mm 8mm;
  margin: 4mm calc(var(--paper-mr, 15mm) * -1) 0 calc(var(--paper-ml, 15mm) * -1);
  padding: 3.4mm var(--paper-mr, 15mm) 3.4mm var(--paper-ml, 15mm);
  background: #fff;
  color: #2f3a3d;
  font-size: calc(var(--paper-base-size) * 0.9);
  text-align: left;
}

.layout-center .paper-contact + .paper-contact::before {
  display: none;
}

.layout-center .paper-contact__label {
  margin-right: 1.8mm;
  color: #7b878b;
}

.layout-center :deep(.paper-style-line .paper-section__title) {
  display: flex;
  align-items: center;
  gap: 2mm;
  padding-bottom: 1.4mm;
  border-bottom: 1px solid color-mix(in srgb, var(--paper-accent) 42%, #fff);
  color: #24858a;
  font-size: calc(var(--paper-base-size) * 1.04);
  letter-spacing: 0.06em;
}

.layout-center :deep(.paper-style-line .paper-section__title span::before) {
  display: none;
}

.layout-center :deep(.paper-style-line .paper-section__icon) {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 4.6mm;
  height: 4.6mm;
  color: var(--paper-accent);
}

.layout-center :deep(.paper-style-line .paper-section__icon svg) {
  width: 100%;
  height: 100%;
}

/* 日期独立左栏，两行显示也不挤压正文。 */
.layout-center :deep(.paper-item) {
  display: grid;
  grid-template-columns: 17mm minmax(0, 1fr);
  column-gap: 3.5mm;
  align-items: start;
}

.layout-center :deep(.paper-item-head) {
  display: contents;
}

.layout-center :deep(.paper-item-period) {
  grid-column: 1;
  grid-row: 1 / span 6;
  padding-top: 0.5mm;
  color: #7c898d;
  font-size: calc(var(--paper-base-size) * 0.76);
  line-height: 1.4;
  text-align: left;
  white-space: normal;
}

.layout-center :deep(.paper-item-title),
.layout-center :deep(.paper-item-sub),
.layout-center :deep(.paper-facts),
.layout-center :deep(.paper-meta-line),
.layout-center :deep(.paper-note),
.layout-center :deep(.paper-rich) {
  grid-column: 2;
}

/* 校徽 / 公司 LOGO 与日期共用左栏：徽标在上、日期在下。 */
.layout-center :deep(.paper-item.is-marked) {
  display: grid !important;
}

.layout-center :deep(.paper-item.is-marked .paper-item-mark) {
  float: none;
  grid-column: 1;
  grid-row: 1 / span 2;
  margin: 0 0 1.6mm;
}

.layout-center :deep(.paper-item.is-marked .paper-item-period) {
  grid-row: 3 / span 4;
}

/* 作品集、论文、技能等没有日期的条目不应保留空的日期栏。 */
.layout-academic :deep(.paper-item--undated),
.layout-center :deep(.paper-item--undated) {
  display: block;
}

/* Timeline: 秋叶 —— 青绿方块标题配右对齐日期，条目紧凑 */
.layout-timeline .paper-header {
  margin-bottom: 6mm;
  padding: 0 0 3.4mm;
  border-bottom: 1px solid color-mix(in srgb, var(--paper-accent) 18%, #fff);
}

/* 姓名、性别和求职意向收在一行，页眉不再占三行。 */
.layout-timeline .paper-header__main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 5mm;
  row-gap: 1mm;
}

.layout-timeline .paper-name-row {
  gap: 2.2mm;
}

.layout-timeline .paper-name {
  color: #18211d;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 1.85);
  letter-spacing: 0.02em;
}

.layout-timeline .paper-identity {
  padding: 0;
  border: none;
  border-radius: 0;
  color: #6f7a73;
  font-size: calc(var(--paper-base-size) * 0.86);
  letter-spacing: 0.02em;
}

.layout-timeline .paper-role {
  margin-top: 0;
  color: var(--paper-accent);
  font-size: calc(var(--paper-base-size) * 0.95);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.layout-timeline .paper-role__label {
  color: inherit;
}

.layout-timeline .paper-contacts {
  flex: 0 0 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 1.6mm 11mm;
  margin-top: 3mm;
  color: #3f4c45;
  font-size: calc(var(--paper-base-size) * 0.88);
}

.layout-timeline .paper-contact {
  display: inline-flex;
  align-items: center;
  gap: 1.6mm;
}

.layout-timeline .paper-contact + .paper-contact::before {
  display: none;
}

.layout-timeline .paper-contact__label {
  display: inline-flex;
  flex: 0 0 auto;
  width: 3.4mm;
  height: 3.4mm;
  margin-right: 0;
  color: var(--paper-accent);
}

.layout-timeline .paper-contact__label .paper-icon {
  width: 100%;
  height: 100%;
}

/* 籍贯等文字标签在图标位里只有 3.4mm 宽，会压到数值上，这里放开成正常文字。 */
.layout-timeline .paper-contact__label--text {
  width: auto;
  height: auto;
  color: #8a949b;
}

.layout-timeline .paper-header__summary {
  flex: 0 0 100%;
  margin-top: 2.8mm;
  color: #3a463f;
  font-size: calc(var(--paper-base-size) * 0.92);
  line-height: 1.62;
}

.layout-timeline .paper-header__summary p {
  margin: 0 0 0.8mm;
}

.layout-timeline .paper-header__summary p:last-child {
  margin-bottom: 0;
}

.layout-timeline .paper-header__summary ul,
.layout-timeline .paper-header__summary ol {
  margin: 0;
  padding-left: 4.2mm;
}

/* 参考稿的证件照在页眉左侧，姓名与简介整体右移。 */
.layout-timeline .paper-photo,
.layout-timeline .paper-photo--monogram {
  order: -1;
}

.layout-timeline .paper-photo {
  border: 0.4mm solid #dfe4e1;
  box-shadow: none;
}

.layout-timeline .paper-photo--monogram {
  display: grid;
  place-items: center;
  background: #f1f4f2;
  color: #8b968f;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2.2);
  font-weight: 700;
}

/* 方块标题 + 通栏细线，取代原来的竖向时间轴。 */
.layout-timeline :deep(.paper-section) {
  margin-bottom: 5mm;
}

.layout-timeline :deep(.paper-style-dot .paper-section__title) {
  display: flex;
  align-items: center;
  gap: 2.6mm;
  margin-bottom: 2.6mm;
  padding-bottom: 1.1mm;
  border-bottom: 1px solid color-mix(in srgb, var(--paper-accent) 20%, #fff);
  color: color-mix(in srgb, var(--paper-accent) 88%, #10231b);
  font-size: calc(var(--paper-base-size) * 1.06);
  letter-spacing: 0.06em;
}

.layout-timeline :deep(.paper-style-dot .paper-section__title::before) {
  width: 2.3mm;
  height: 2.3mm;
  border-radius: 0;
  background: color-mix(in srgb, var(--paper-accent) 84%, #fff);
}

.layout-timeline :deep(.paper-style-dot .paper-section__title::after) {
  display: none;
}

.layout-timeline :deep(.paper-section__body) {
  margin-left: 0;
  padding-left: 0;
  border-left: none;
}

.layout-timeline :deep(.paper-item) {
  position: relative;
}

.layout-timeline :deep(.paper-item)::before {
  display: none;
}

.layout-timeline :deep(.paper-item + .paper-item) {
  margin-top: 2.4mm;
}

/* 教育经历把专业与学位并到校名同一行，日期仍然靠右。 */
.layout-timeline :deep(.is-education .paper-item) {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 3mm;
}

.layout-timeline :deep(.is-education .paper-item-head) {
  display: contents;
}

.layout-timeline :deep(.is-education .paper-item-title) {
  order: 1;
}

.layout-timeline :deep(.is-education .paper-item-sub) {
  order: 2;
  margin-top: 0;
}

.layout-timeline :deep(.is-education .paper-item-period) {
  order: 3;
  margin-left: auto;
}

/* 教育条目是 flex 行：徽标排在最前，与校名同一基线。 */
.layout-timeline :deep(.is-education .paper-item.is-marked) {
  display: flex !important;
}

.layout-timeline :deep(.is-education .paper-item-mark) {
  float: none;
  flex: none;
  align-self: flex-start;
  order: 0;
  margin: 0 1.6mm 0 0;
}

.layout-timeline :deep(.is-education .paper-meta-line),
.layout-timeline :deep(.is-education .paper-note),
.layout-timeline :deep(.is-education .paper-rich) {
  flex: 0 0 100%;
  order: 4;
}

.layout-timeline :deep(.paper-item-title) {
  color: #16211b;
  font-size: calc(var(--paper-base-size) * 1.04);
}

.layout-timeline :deep(.paper-item-period) {
  color: #6c7770;
  font-size: calc(var(--paper-base-size) * 0.84);
  letter-spacing: 0;
}

.layout-timeline :deep(.paper-item-sub) {
  margin-top: 0.4mm;
  gap: 0 6px;
  color: #3c4a42;
  font-size: calc(var(--paper-base-size) * 0.95);
}

.layout-timeline :deep(.paper-item-sub span + span::before) {
  content: "|";
  margin-right: 6px;
  color: #a9b4ad;
}

.layout-timeline :deep(.is-work_experience .paper-item-sub) {
  color: #2a372f;
  font-weight: 600;
}

.layout-timeline :deep(.paper-item-tag) {
  display: inline-block;
  margin-left: 2mm;
  padding: 0 1.5mm;
  border: 1px solid color-mix(in srgb, var(--paper-accent) 42%, #fff);
  border-radius: 999px;
  color: color-mix(in srgb, var(--paper-accent) 86%, #163d30);
  font-size: calc(var(--paper-base-size) * 0.66);
  font-weight: 500;
  line-height: 1.4;
  letter-spacing: 0;
  vertical-align: 0.12em;
}

.layout-timeline :deep(.paper-meta-line) {
  gap: 10px;
  color: #5c675f;
  font-size: calc(var(--paper-base-size) * 0.87);
}

.layout-timeline :deep(.paper-note) {
  margin-top: 0.8mm;
  color: #5c675f;
  font-size: calc(var(--paper-base-size) * 0.87);
}

.layout-timeline :deep(.paper-note__tag) {
  color: #46534b;
  font-weight: 600;
}

.layout-timeline :deep(.paper-rich) {
  margin-top: 0.6mm;
}

.layout-timeline :deep(.paper-rich li) {
  margin: 0.3mm 0;
}

.layout-timeline :deep(.paper-rich p) {
  margin-bottom: 0.6mm;
}

/* Profile: 技术 —— 证件照居左，圆形描边图标配虚线分割 */
.layout-profile .paper-header {
  margin-bottom: 6.5mm;
  padding-bottom: 0;
  border-bottom: none;
}

.layout-profile .paper-header--photo {
  align-items: flex-start;
  gap: 6.5mm;
}

.layout-profile .paper-name {
  color: #1f2824;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2.05);
  letter-spacing: 0.02em;
}

.layout-profile .paper-identity {
  padding: 0;
  border: none;
  color: #6c776f;
  font-size: calc(var(--paper-base-size) * 0.88);
}

.layout-profile .paper-role {
  margin-top: 1.2mm;
  color: #34423a;
  font-size: var(--paper-base-size);
  font-weight: 400;
}

.layout-profile .paper-contacts--labeled {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.2mm 8mm;
  margin-top: 2.6mm;
  color: #33403a;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-profile .paper-contact {
  display: flex;
  gap: 1.6mm;
}

.layout-profile .paper-contact + .paper-contact::before {
  display: none;
}

.layout-profile .paper-contact__label {
  flex: 0 0 auto;
  color: #8a948e;
  font-weight: 400;
}

.layout-profile .paper-photo {
  order: -1;
  width: 24mm;
  height: 33mm;
  border: 0.4mm solid #dde3e0;
  border-radius: 0.8mm;
  box-shadow: none;
}

.layout-profile .paper-photo--monogram {
  display: grid;
  place-items: center;
  background: #f1f4f2;
  color: #8b958f;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2.2);
  font-weight: 700;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

/* 职业技能用「名称 + 熟练度」文字对，比圆点更接近传统中文简历的读法。 */
.layout-profile :deep(.paper-skill__dots) {
  display: none;
}

.layout-profile :deep(.paper-skill__level) {
  display: inline;
  color: var(--paper-accent);
  font-size: calc(var(--paper-base-size) * 0.85);
}

/* 细边框标签呼应标题的描边圆形图标。 */
.layout-profile :deep(.paper-note__tag) {
  display: inline-block;
  margin-right: 2mm;
  padding: 0.2mm 2mm;
  border: 1px solid color-mix(in srgb, var(--paper-accent) 40%, #fff);
  border-radius: 999px;
  color: var(--paper-accent);
  font-size: calc(var(--paper-base-size) * 0.76);
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-profile :deep(.paper-note__tag)::after {
  content: "";
}

.layout-profile :deep(.paper-item-tag) {
  color: var(--paper-accent);
  font-weight: 500;
}

/* Ribbon: 金石 —— 藏青斜切色带与哑金圆徽，左侧一条通栏竖线，模块标题是压线的标牌 */
.resume-paper.layout-ribbon {
  --paper-gold: #dec697;
  /* 参考稿的正文右界只留 12.6mm 页边，比通用版式宽 2.4mm；左页边保持不变，
     竖线正好落在左页边线上，正文再往里缩 4.2mm。 */
  padding-right: calc(var(--paper-mr, 15mm) - 2.4mm);
}

/* 竖线用 border 而不是背景：跨页时背景会被裁掉，border 在每一页的碎片上都会重画。 */
.layout-ribbon .paper-flow {
  margin-left: -2.4mm;
  padding-left: 3.9mm;
  border-left: 0.3mm solid var(--paper-accent);
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-ribbon .paper-banner {
  position: relative;
  height: 12.4mm;
  margin-top: -4.5mm;
}

/* 两条斜切色带按参考稿铺进页边，45° 平行四边形，一藏青一哑金。 */
.layout-ribbon .paper-banner__band {
  position: absolute;
  display: block;
  width: 105.8mm;
  height: 2.7mm;
  transform: skewX(45deg);
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-ribbon .paper-banner__band--navy {
  top: 0;
  left: -9.1mm;
  background: var(--paper-accent);
}

.layout-ribbon .paper-banner__band--gold {
  top: 4mm;
  left: 79.7mm;
  background: var(--paper-gold);
}

/* 三枚圆徽压在右上角，最右一枚出正文栏是参考稿的排法。 */
.layout-ribbon .paper-banner__marks {
  position: absolute;
  top: -4.9mm;
  right: -6mm;
  display: flex;
  gap: 5.85mm;
}

.layout-ribbon .paper-banner__marks i {
  display: grid;
  place-items: center;
  width: 6.75mm;
  height: 6.75mm;
  border-radius: 50%;
  background: var(--paper-gold);
  color: #fff;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-ribbon .paper-banner__marks :deep(svg) {
  width: 56%;
  height: 56%;
}

.layout-ribbon .paper-banner__title {
  margin-left: -5.9mm;
}

.layout-ribbon .paper-header {
  margin-bottom: 4mm;
  padding-bottom: 0;
  border-bottom: none;
}

.layout-ribbon .paper-header--photo {
  align-items: flex-start;
  gap: 3.7mm;
}

.layout-ribbon .paper-name-row {
  gap: 3.4mm;
}

.layout-ribbon .paper-name {
  color: #1d2733;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 1.4);
  letter-spacing: 0.02em;
}

.layout-ribbon .paper-identity {
  padding: 0;
  border: none;
  color: #1d2733;
  font-size: var(--paper-base-size);
}

/* 联系方式一行三栏，标签与值同色，读作「手机：138…」。 */
.layout-ribbon .paper-contacts--labeled {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1.8mm 3mm;
  margin-top: 3.4mm;
  color: #2b3644;
  font-size: calc(var(--paper-base-size) * 0.95);
}

.layout-ribbon .paper-contact {
  display: flex;
  gap: 0;
  min-width: 0;
}

.layout-ribbon .paper-contact + .paper-contact::before {
  display: none;
}

.layout-ribbon .paper-contact__label {
  flex: none;
  margin-right: 1.2mm;
  color: #2b3644;
  white-space: nowrap;
}

.layout-ribbon .paper-contact__label::after {
  content: "：";
}

.layout-ribbon .paper-photo {
  width: 24mm;
  height: 33.6mm;
  margin: 0;
  border: none;
  border-radius: 0;
  box-shadow: none;
}

.layout-ribbon .paper-photo--monogram {
  display: grid;
  place-items: center;
  background: #f2f5f9;
  color: #98a2ae;
  font-family: var(--serif);
  font-size: calc(var(--paper-base-size) * 2.2);
  font-weight: 700;
}

/* Classic: 学术 —— 角标照片框与制图线装饰，青色实底标签 */
.layout-classic .paper-header {
  margin-bottom: 6mm;
  padding: 0 0 4mm;
  text-align: left;
  border-top: none;
  border-bottom: 1px solid #dce6ea;
}

.layout-classic .paper-header--photo {
  align-items: flex-start;
  gap: 8mm;
}

.layout-classic .paper-name-row,
.layout-classic .paper-contacts {
  justify-content: flex-start;
}

.layout-classic .paper-header__main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 5mm;
  row-gap: 0.6mm;
}

.layout-classic .paper-name {
  color: #1d2a30;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 1.95);
  letter-spacing: 0.04em;
}

.layout-classic .paper-identity {
  padding: 0;
  border: none;
  color: #6b7a80;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-classic .paper-role {
  margin-top: 0;
  color: #7b8a90;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.layout-classic .paper-contacts {
  display: grid;
  flex: 1 0 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1mm 10mm;
  margin-top: 2.4mm;
  color: #33424a;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-classic .paper-contact + .paper-contact::before {
  display: none;
}

.layout-classic .paper-contact__label {
  margin-right: 1.8mm;
  color: #87969c;
}

/* 四角细线框：8 段背景各画一条边，padding 让线框与照片留出呼吸。 */
.layout-classic .paper-photo {
  width: 25mm;
  height: 34mm;
  padding: 2mm;
  border: none;
  border-radius: 0;
  background-color: #fff;
  background-image:
    linear-gradient(var(--paper-accent), var(--paper-accent)),
    linear-gradient(var(--paper-accent), var(--paper-accent)),
    linear-gradient(var(--paper-accent), var(--paper-accent)),
    linear-gradient(var(--paper-accent), var(--paper-accent)),
    linear-gradient(var(--paper-accent), var(--paper-accent)),
    linear-gradient(var(--paper-accent), var(--paper-accent)),
    linear-gradient(var(--paper-accent), var(--paper-accent)),
    linear-gradient(var(--paper-accent), var(--paper-accent));
  background-repeat: no-repeat;
  background-size: 3.4mm 0.5mm, 0.5mm 3.4mm, 3.4mm 0.5mm, 0.5mm 3.4mm,
    3.4mm 0.5mm, 0.5mm 3.4mm, 3.4mm 0.5mm, 0.5mm 3.4mm;
  background-position: left top, left top, right top, right top,
    left bottom, left bottom, right bottom, right bottom;
  box-shadow: none;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-classic .paper-photo--monogram {
  display: grid;
  place-items: center;
  color: var(--paper-accent);
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2.2);
  font-weight: 700;
  background-clip: content-box;
  background-color: #f2f6f7;
}

.layout-classic :deep(.paper-style-caps .paper-section__title) {
  display: flex;
  align-items: center;
  gap: 2.2mm;
  margin-bottom: 2.8mm;
  padding-bottom: 1mm;
  border-bottom: none;
  color: #1d2a30;
  font-size: calc(var(--paper-base-size) * 1.1);
  letter-spacing: 0.04em;
  text-transform: none;
}

.layout-classic :deep(.paper-style-caps .paper-section__title)::before {
  content: "";
  flex: 0 0 auto;
  width: 8mm;
  height: 2.2mm;
  background:
    linear-gradient(var(--paper-accent), var(--paper-accent)) left center / 0.5mm 2.2mm no-repeat,
    linear-gradient(var(--paper-accent), var(--paper-accent)) 1.3mm center / 0.5mm 2.2mm no-repeat,
    linear-gradient(var(--paper-accent), var(--paper-accent)) 2.6mm center / 0.5mm 2.2mm no-repeat,
    linear-gradient(var(--paper-accent), var(--paper-accent)) right center / 4mm 0.5mm no-repeat;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-classic :deep(.paper-style-caps .paper-section__title)::after {
  content: "";
  flex: 1;
  height: 1px;
  background: color-mix(in srgb, var(--paper-accent) 32%, #fff);
}

.layout-classic :deep(.paper-item-tag) {
  display: inline-block;
  margin-left: 1.4mm;
  padding: 0.2mm 1.8mm;
  border-radius: 0.4mm;
  background: var(--paper-accent);
  color: #fff;
  font-size: calc(var(--paper-base-size) * 0.72);
  font-weight: 500;
  vertical-align: 0.24em;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-classic :deep(.paper-note__tag) {
  color: var(--paper-accent);
  font-weight: 600;
}

/* Formal: 青瓦 —— 紧凑页眉，青绿标题短线配通栏细线 */
.layout-formal .paper-header {
  margin-bottom: 6.5mm;
  padding-bottom: 0;
  border-bottom: none;
}

.layout-formal .paper-header--photo {
  align-items: flex-start;
  gap: 8mm;
}

.layout-formal .paper-name {
  color: #1f2830;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 1.9);
  letter-spacing: 0.08em;
}

.layout-formal .paper-identity {
  padding: 0;
  border: none;
  color: #5b666e;
  font-size: calc(var(--paper-base-size) * 0.88);
}

.layout-formal .paper-role {
  margin-top: 1.2mm;
  color: #2f3a42;
  font-size: calc(var(--paper-base-size) * 0.96);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.layout-formal .paper-role__label {
  color: #7b858c;
  font-weight: 400;
}

.layout-formal .paper-contacts--labeled {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.4mm 8mm;
  margin-top: 2.6mm;
  color: #33404a;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-formal .paper-contact {
  display: flex;
  gap: 2.2mm;
}

.layout-formal .paper-contact + .paper-contact::before {
  display: none;
}

.layout-formal .paper-contact__label {
  flex: 0 0 auto;
  color: #8a949b;
  letter-spacing: 0.02em;
}

.layout-formal .paper-photo {
  order: -1;
  width: 25mm;
  height: 34mm;
  margin-top: 0;
  border: none;
  border-radius: 0;
  box-shadow: none;
}

.layout-formal .paper-photo--circle {
  width: 28mm;
  height: 28mm;
}

/* 这套版式围绕证件照设计，没有照片时用首字占住左上角，不留白。 */
.layout-formal .paper-photo--monogram {
  display: grid;
  place-items: center;
  background: #f2f4f6;
  color: #8d979e;
  font-family: var(--serif);
  font-size: calc(var(--paper-base-size) * 2.4);
  font-weight: 700;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

/* 专业 / 学历与绩点排在同一行，读起来更接近传统中文简历的条目。 */
.layout-formal :deep(.paper-item-sub),
.layout-formal :deep(.paper-meta-line) {
  display: inline-flex;
}

.layout-formal :deep(.paper-meta-line) {
  margin-left: 4mm;
}

.layout-formal :deep(.paper-item-sub span + span)::before {
  content: "|";
  margin: 0 2.6mm;
  color: #c2c9ce;
}

/* 标题文字下压一条 0.8mm 主色短线，再以通栏发丝线收尾。 */
.layout-formal :deep(.paper-style-rule .paper-section__title) {
  margin-bottom: 3mm;
  padding-bottom: 1.2mm;
  border-bottom: 1px solid color-mix(in srgb, var(--paper-accent) 34%, #fff);
  color: color-mix(in srgb, var(--paper-accent) 78%, #12231f);
  font-size: calc(var(--paper-base-size) * 1.1);
  letter-spacing: 0.06em;
}

.layout-formal :deep(.paper-style-rule .paper-section__title span) {
  background: linear-gradient(var(--paper-accent), var(--paper-accent)) left bottom / 100% 0.7mm no-repeat;
  padding-bottom: 0.8mm;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-formal :deep(.paper-item-tag) {
  color: var(--paper-accent);
  font-weight: 500;
}

.layout-formal :deep(.paper-note__tag) {
  display: inline-block;
  margin-right: 2.2mm;
  padding: 0.3mm 1.8mm;
  border-radius: 0.6mm;
  background: #eef2f1;
  color: #5b6a66;
  font-size: calc(var(--paper-base-size) * 0.78);
  letter-spacing: 0.04em;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-formal :deep(.paper-note__tag)::after {
  content: "";
}

/* Rail: 晨星 —— 白色侧栏 + 深红圆弧装饰，技能点线对齐 */
.layout-rail {
  display: grid;
  grid-template-columns: 64mm minmax(0, 1fr);
  padding: 0;
}

.paper-rail {
  position: relative;
  padding: 20mm 7mm 12mm;
  background: #fff;
  color: #262f35;
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
}

/* 左上角深红圆弧与三颗白点，是这套版式唯一的装饰。 */
.paper-rail::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 42mm;
  height: 33mm;
  background: var(--paper-accent);
  border-bottom-right-radius: 100% 88%;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.paper-rail::after {
  content: "";
  position: absolute;
  top: 12mm;
  left: 15mm;
  width: 1.5mm;
  height: 1.5mm;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 4mm 0 0 #fff, 0 8mm 0 0 #fff;
}

.paper-rail .paper-section {
  margin-top: 7mm;
}

.paper-rail :deep(.paper-section__title) {
  display: block;
  margin-bottom: 2.4mm;
  padding: 0;
  border: none;
  color: #5c6872;
  font-size: calc(var(--paper-base-size) * 0.98);
  letter-spacing: 0.06em;
}

.paper-rail :deep(.paper-section__title)::after {
  content: "";
  display: block;
  width: 5mm;
  height: 1.4mm;
  margin-top: 1.2mm;
  background: #d9dde1;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.paper-rail :deep(.paper-skill) {
  padding: 0.9mm 0;
  border-bottom: none;
}

.paper-rail :deep(.paper-skill__name) {
  display: flex;
  flex: 1;
  align-items: baseline;
  gap: 2mm;
  color: #2b353b;
}

.paper-rail :deep(.paper-skill__name)::after {
  content: "";
  flex: 1;
  border-bottom: 1px dotted #bcc2c8;
}

.paper-rail :deep(.paper-skill__dots) {
  display: none;
}

.paper-rail :deep(.paper-skill__level) {
  display: inline;
  color: #66717a;
  font-size: calc(var(--paper-base-size) * 0.84);
}

.paper-rail :deep(.paper-skills) {
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
}

.paper-rail :deep(.paper-item-sub),
.paper-rail :deep(.paper-item-period),
.paper-rail :deep(.paper-note) {
  color: #6b747b;
}

.paper-photo--rail {
  position: relative;
  z-index: 1;
  display: block;
  width: 44mm;
  height: auto;
  aspect-ratio: 5 / 7;
  margin: 2mm 0 7mm;
  border: 0.6mm solid #fff;
  border-radius: 1.2mm;
  box-shadow: 0 1mm 4mm -1.4mm rgba(30, 40, 46, 0.35);
}

.paper-photo--rail.paper-photo--circle {
  width: 40mm;
  aspect-ratio: 1 / 1;
  margin-inline: auto 0;
  margin-bottom: 7mm;
  border-radius: 50%;
}

.rail-monogram {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 44mm;
  aspect-ratio: 5 / 7;
  margin: 2mm 0 7mm;
  border: 0.6mm solid #e2e5e8;
  border-radius: 1.2mm;
  background: #f4f5f6;
  color: #7a848b;
  font-family: var(--serif);
  font-size: calc(var(--paper-base-size) * 3.2);
  font-weight: 700;
}

.rail-block {
  margin-bottom: 7mm;
}

.rail-title {
  margin: 0 0 2.4mm;
  color: #5c6872;
  font-size: calc(var(--paper-base-size) * 0.98);
  font-weight: 700;
  letter-spacing: 0.06em;
}

.rail-contacts {
  margin: 0;
  padding: 0;
  list-style: none;
}

.rail-contacts li {
  display: flex;
  align-items: baseline;
  gap: 1.5mm;
  margin-bottom: 1.8mm;
  color: #2b353b;
  font-size: calc(var(--paper-base-size) * 0.82);
  line-height: 1.5;
  word-break: break-word;
}

.rail-contacts a {
  color: inherit;
}

.rail-label {
  flex: 0 0 auto;
  min-width: 9mm;
  color: #8b9399;
  font-size: calc(var(--paper-base-size) * 0.78);
}

.layout-rail .paper-flow {
  min-width: 0;
  padding: var(--paper-mt, 16mm) var(--paper-mr, 14mm) var(--paper-mb, 12mm) var(--paper-ml, 11mm);
}

.layout-rail .paper-header {
  margin-bottom: 6mm;
  padding-bottom: 0;
  border-bottom: none;
}

.layout-rail .paper-name {
  color: #1f262b;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2.4);
  letter-spacing: 0.04em;
}

.layout-rail .paper-identity {
  padding: 0;
  border: none;
  color: #6b747a;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-rail .paper-role {
  margin-top: 1.4mm;
  color: #3d464c;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.layout-rail :deep(.paper-style-bar .paper-section__title) {
  display: block;
  padding: 0;
  border-left: none;
  color: #262f35;
  font-size: calc(var(--paper-base-size) * 1.04);
  letter-spacing: 0.05em;
}

.layout-rail :deep(.paper-style-bar .paper-section__title)::after {
  content: "";
  display: block;
  width: 5mm;
  height: 1.4mm;
  margin-top: 1.2mm;
  background: #d9dde1;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-rail .paper-content {
  min-height: 190mm;
}

/* Photo header: 启航 —— 白底页眉 + 淡蓝模块色带 */
.layout-photoheader .paper-header {
  align-items: flex-start;
  gap: 7mm;
  margin-bottom: 7mm;
  padding-bottom: 0;
  border-bottom: none;
}

.layout-photoheader .paper-photo {
  width: 26mm;
  height: 36mm;
  margin: 0;
  border: 0.4mm solid #dbe3ec;
  border-radius: 0.6mm;
  box-shadow: none;
}

.layout-photoheader .paper-photo--circle {
  width: 30mm;
  height: 30mm;
  border-radius: 50%;
}

.layout-photoheader .paper-photo--monogram {
  display: grid;
  place-items: center;
  background: #eef2f7;
  color: #8b96a3;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2.2);
  font-weight: 700;
}

.layout-photoheader .paper-name {
  color: #1c2731;
  font-family: var(--paper-font-family);
  font-size: calc(var(--paper-base-size) * 2);
  letter-spacing: 0.04em;
}

.layout-photoheader .paper-identity {
  padding: 0;
  border: none;
  color: #6c7885;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-photoheader .paper-role {
  margin-top: 1.2mm;
  color: #33404d;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.layout-photoheader .paper-contacts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.2mm 10mm;
  margin: 2.8mm 0 0;
  padding: 0;
  background: none;
  color: #33404d;
  font-size: calc(var(--paper-base-size) * 0.9);
}

.layout-photoheader .paper-contact + .paper-contact::before {
  display: none;
}

.layout-photoheader .paper-contact__label {
  margin-right: 2mm;
  color: #8793a0;
}

.layout-photoheader :deep(.paper-style-block .paper-section__title) {
  padding: 1.4mm 2.8mm;
  background: color-mix(in srgb, var(--paper-accent) 10%, #fff);
  border-radius: 0;
  color: color-mix(in srgb, var(--paper-accent) 88%, #0f2532);
  font-size: calc(var(--paper-base-size) * 1.02);
  letter-spacing: 0.08em;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}

.layout-photoheader :deep(.paper-item-period) {
  color: #8d99a5;
}

@media print {
  .resume-paper {
    background: #fff !important;
  }

  .layout-band .paper-header {
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .sidebar-aside {
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
}
</style>
