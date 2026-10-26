const field = (key, label, type = 'text', extra = {}) => ({ key, label, type, ...extra })

const period = (label = '时间') => field('period', label, 'period', { span: 24 })
const rich = (label = '内容', placeholder = '用简短的分点描述你的工作或项目，建议包含动作、规模与结果。') =>
  field('content', label, 'richtext', { span: 24, placeholder })
const textarea = (key, label, placeholder, span = 24) =>
  field(key, label, 'textarea', { placeholder, span, rows: 3 })
const logo = (label, hint) => field('logo', label, 'image', { span: 24, hint })

export const SECTION_META = {
  basic_info: {
    label: '基本信息',
    icon: 'User',
    hint: '联系方式与个人情况按需填写，留空的字段不会出现在简历上。',
    emptyHint: '填写姓名、手机和邮箱，让 HR 能联系到你。',
    single: true,
    hideable: false,
    fields: [
      field('name', '姓名', 'text', { span: 12, placeholder: '例如：郝简历', maxlength: 20 }),
      field('gender', '性别', 'select', { span: 6, options: ['男', '女', '不公开'] }),
      field('showGender', '展示性别', 'switch', { span: 6 }),
      field('phone', '手机号', 'tel', { span: 12, placeholder: '用于接收面试通知', maxlength: 30 }),
      field('email', '邮箱', 'email', { span: 12, placeholder: 'name@example.com', maxlength: 80 }),
      field('identity', '当前身份', 'select', { span: 8, options: ['在校生', '应届生', '职场人', '自由职业'] }),
      field('city', '所在城市', 'text', { span: 8, placeholder: '例如：上海', maxlength: 30 }),
      field('birthday', '出生年月', 'month', { span: 8 }),
      field('showAge', '展示出生年月', 'switch', { span: 8 }),
      field('website', '个人主页', 'url', { span: 16, placeholder: 'https://（选填）' }),
      field('nativePlace', '籍贯', 'text', { span: 6, placeholder: '例如：浙江杭州', maxlength: 30 }),
      field('ethnicity', '民族', 'text', { span: 6, placeholder: '例如：汉族', maxlength: 20 }),
      field('politics', '政治面貌', 'select', {
        span: 6,
        options: ['中共党员', '中共预备党员', '共青团员', '民主党派', '群众']
      }),
      field('maritalStatus', '婚姻状况', 'select', { span: 6, options: ['未婚', '已婚'] })
    ]
  },
  job_intention: {
    label: '求职意向',
    icon: 'Aim',
    hint: '写清楚目标岗位与城市，帮助 HR 快速判断匹配度。',
    emptyHint: '写出目标岗位，例如「后端开发工程师」。',
    single: true,
    fields: [
      field('jobTitle', '目标岗位', 'text', { span: 12, placeholder: '例如：后端开发工程师', maxlength: 30 }),
      field('city', '期望城市', 'text', { span: 12, placeholder: '例如：上海', maxlength: 30 }),
      field('salaryMin', '期望月薪（下限）', 'text', { span: 8, placeholder: '例如：25000' }),
      field('salaryMax', '期望月薪（上限）', 'text', { span: 8, placeholder: '例如：35000' }),
      field('salaryNegotiable', '薪资面议', 'switch', { span: 8 }),
      field('status', '求职状态', 'select', {
        span: 12,
        options: ['在职，月内到岗', '离职，随时到岗', '在校，考虑实习', '暂不考虑机会']
      })
    ]
  },
  education: {
    label: '教育背景',
    icon: 'School',
    hint: '按时间倒序填写。应届生可以补充主修课程、绩点与排名；院校标签会在部分模板中显示为角标。',
    emptyHint: '添加学校、专业和就读时间。',
    single: false,
    fields: [
      field('school', '学校', 'text', { span: 12, placeholder: '学校名称', maxlength: 40 }),
      field('major', '专业', 'text', { span: 12, placeholder: '专业名称', maxlength: 40 }),
      logo('校徽', '建议方形透明 PNG。本模块只要有一条上传了校徽，其余条目会留出同样的位置并画占位徽记。'),
      field('degree', '学历', 'select', { span: 6, options: ['专科', '本科', '硕士', '博士', '其他'] }),
      field('gpa', '绩点', 'text', { span: 6, placeholder: '例如：3.8 / 4.0' }),
      field('rank', '排名', 'text', { span: 6, placeholder: '例如：前 10%' }),
      field('schoolTag', '院校标签', 'select', {
        span: 6,
        options: ['985', '211', '双一流', '境外高校'],
        placeholder: '选填'
      }),
      period('就读时间'),
      field('courses', '主修课程', 'tags', { span: 24, placeholder: '输入后回车添加，例如：数据结构' }),
      textarea('note', '补充说明', '奖学金、交换经历等（选填）')
    ]
  },
  work_experience: {
    label: '工作经历',
    icon: 'Suitcase',
    hint: '用「做了什么 + 怎么做的 + 结果如何」描述，尽量量化结果。',
    emptyHint: '添加公司、职位和工作内容。',
    single: false,
    fields: [
      field('company', '公司', 'text', { span: 12, placeholder: '公司名称', maxlength: 50 }),
      field('position', '职位', 'text', { span: 12, placeholder: '例如：后端开发工程师', maxlength: 40 }),
      logo('公司 LOGO', '建议方形透明 PNG。本模块只要有一条上传了 LOGO，其余条目会留出同样的位置并画占位徽记。'),
      field('workType', '工作性质', 'select', { span: 8, options: ['全职', '实习', '兼职', '远程'] }),
      field('city', '城市', 'text', { span: 8, placeholder: '例如：上海' }),
      field('department', '部门', 'text', { span: 8, placeholder: '例如：交易平台' }),
      period('在职时间'),
      rich('工作内容', '描述职责与成果。建议使用「主导 / 搭建 / 优化」等动词，并附上前后对比数据。')
    ]
  },
  project_experience: {
    label: '项目经历',
    icon: 'Cpu',
    hint: '写清楚项目背景、你的职责和最终结果。链接可以是仓库或线上地址。',
    emptyHint: '添加一个能体现能力的项目。',
    single: false,
    fields: [
      field('name', '项目名称', 'text', { span: 12, placeholder: '项目名称', maxlength: 50 }),
      field('role', '担任角色', 'text', { span: 12, placeholder: '例如：后端负责人' }),
      period('项目时间'),
      field('link', '项目链接', 'url', { span: 24, placeholder: 'https://（选填）' }),
      rich('项目描述', '说明项目解决了什么问题、你的关键动作和可量化的结果。')
    ]
  },
  campus_experience: {
    label: '在校经历',
    icon: 'Flag',
    hint: '社团、学生组织、志愿服务都可以写，重点是组织与协作能力。',
    emptyHint: '添加学生工作、社团或志愿经历。',
    single: false,
    fields: [
      field('organization', '组织 / 团队', 'text', { span: 12, placeholder: '例如：学生会技术部' }),
      field('role', '担任角色', 'text', { span: 12, placeholder: '例如：部长' }),
      period('参与时间'),
      rich('经历描述', '描述你负责的事情、规模与结果。')
    ]
  },
  skills_summary: {
    label: '个人技能',
    icon: 'Tools',
    hint: '按「熟练使用 / 熟悉 / 了解」分组，写具体技术、工具或方法。',
    emptyHint: '写出与你目标岗位最相关的技能。',
    single: true,
    fields: [rich('技能清单', '例如：熟练使用 Java、Spring Boot、MySQL；熟悉 Redis、消息队列与分布式事务。')]
  },
  skill_proficiency: {
    label: '技能熟练度',
    icon: 'DataAnalysis',
    hint: '用等级呈现核心技能，最多 8 项。',
    emptyHint: '添加核心技能和熟练程度。',
    single: false,
    maxItems: 8,
    fields: [
      field('name', '技能名称', 'text', { span: 14, placeholder: '例如：Java' }),
      field('level', '熟练度', 'select', { span: 10, options: ['了解', '熟悉', '熟练', '精通'], default: '熟悉' })
    ]
  },
  paper_publication: {
    label: '论文发表',
    icon: 'Reading',
    hint: '写明论文题目、期刊或会议、作者顺序与检索情况。',
    emptyHint: '添加论文、专利或研究成果。',
    single: true,
    fields: [rich('内容', '例：第一作者，《论文题目》，《期刊名称》，2024 年，EI 检索。')]
  },
  portfolio: {
    label: '作品集',
    icon: 'Picture',
    hint: '附上在线作品集、GitHub 或设计稿链接，并简述亮点。',
    emptyHint: '添加作品链接与简述。',
    single: false,
    fields: [
      field('title', '作品名称', 'text', { span: 12, placeholder: '作品名称', maxlength: 50 }),
      field('link', '作品链接', 'url', { span: 12, placeholder: 'https://' }),
      rich('作品说明', '用一两句话说明作品解决的问题与你的贡献。')
    ]
  },
  self_evaluation: {
    label: '自我评价',
    icon: 'Star',
    hint: '避免空泛形容词。用 2-3 个与岗位相关的能力点，附上事实支撑。',
    emptyHint: '写 2-3 句话概括你的优势。',
    single: true,
    fields: [rich('自我评价', '用具体事实说明你的优势，避免「认真负责、吃苦耐劳」等空泛表述。')]
  },
  postgraduate_info: {
    label: '考研信息',
    icon: 'Aim',
    hint: '适合考研复试与学术简历。',
    emptyHint: '填写报考院校、专业与初试成绩。',
    single: true,
    fields: [
      field('school', '报考院校', 'text', { span: 12, placeholder: '院校名称' }),
      field('major', '报考专业', 'text', { span: 12, placeholder: '专业名称' }),
      field('year', '考试年份', 'text', { span: 8, placeholder: '例如：2026' }),
      field('initialScore', '初试成绩', 'text', { span: 8, placeholder: '例如：412' }),
      field('finalScore', '复试成绩', 'text', { span: 8, placeholder: '例如：89.5' }),
      textarea('note', '备注', '导师方向、研究兴趣等（选填）')
    ]
  },
  competition: {
    label: '竞赛经历',
    icon: 'Trophy',
    hint: '写清竞赛全称、奖项级别、时间与你的贡献。',
    emptyHint: '添加竞赛、黑客松或案例大赛经历。',
    single: false,
    fields: [
      field('name', '竞赛名称', 'text', { span: 12, placeholder: '竞赛名称' }),
      field('award', '奖项', 'text', { span: 12, placeholder: '例如：全国一等奖' }),
      period('参赛时间'),
      rich('经历说明', '简述赛题、你的角色与成果。')
    ]
  },
  award: {
    label: '获奖情况',
    icon: 'Medal',
    hint: '优先展示与目标岗位相关、级别较高的奖项。',
    emptyHint: '添加奖学金、竞赛或荣誉奖项。',
    single: false,
    fields: [
      field('name', '奖项名称', 'text', { span: 12, placeholder: '奖项名称' }),
      field('level', '级别', 'select', { span: 12, options: ['国家级', '省级', '市级', '校级', '其他'] }),
      period('获奖时间'),
      textarea('description', '补充说明', '颁奖单位、获奖比例等（选填）')
    ]
  },
  certificate: {
    label: '资格证书',
    icon: 'Postcard',
    hint: '只写与岗位相关或含金量较高的证书。',
    emptyHint: '添加语言、职业资格或技能证书。',
    single: false,
    fields: [
      field('name', '证书名称', 'text', { span: 12, placeholder: '证书名称' }),
      field('issuer', '颁发机构', 'text', { span: 12, placeholder: '颁发机构（选填）' }),
      field('date', '获得时间', 'month', { span: 12 }),
      textarea('note', '备注', '成绩、编号等（选填）')
    ]
  },
  journal_paper: {
    label: '论文期刊',
    icon: 'Notebook',
    hint: '学术简历可补充论文标题、期刊、作者顺序与链接。',
    emptyHint: '添加期刊论文或会议论文。',
    single: false,
    fields: [
      field('title', '论文题目', 'text', { span: 24, placeholder: '论文题目' }),
      field('journal', '期刊 / 会议', 'text', { span: 12, placeholder: '期刊或会议名称' }),
      field('period', '发表时间', 'month', { span: 12 }),
      field('authors', '作者顺序', 'text', { span: 24, placeholder: '例如：第一作者 / 共同一作' }),
      field('link', '论文链接', 'url', { span: 24, placeholder: 'https://（选填）' })
    ]
  },
  hobby: {
    label: '兴趣爱好',
    icon: 'Coffee',
    hint: '写能体现坚持、协作或创造力的爱好，一句话即可。',
    emptyHint: '用一句话介绍你的兴趣。',
    single: true,
    fields: [textarea('content', '兴趣爱好', '例如：长跑（连续三年参加半程马拉松）、摄影。', 24)]
  },
  custom: {
    label: '自定义模块',
    icon: 'EditPen',
    hint: '自由组织内容，适合作品、志愿服务、个人项目等。',
    emptyHint: '添加自定义内容。',
    single: false,
    maxItems: 10,
    fields: [
      field('title', '标题', 'text', { span: 12, placeholder: '条目标题' }),
      field('subtitle', '副标题', 'text', { span: 12, placeholder: '机构 / 角色（选填）' }),
      period('时间'),
      rich('内容', '写下你想展示的内容。')
    ]
  }
}

export const SECTION_ORDER = [
  'basic_info', 'job_intention', 'education', 'work_experience', 'project_experience',
  'campus_experience', 'skills_summary', 'skill_proficiency', 'paper_publication',
  'portfolio', 'self_evaluation'
]

export const OPTIONAL_MODULES = [
  { type: 'postgraduate_info', description: '报考院校、初试与复试成绩' },
  { type: 'competition', description: '竞赛名称、奖项与你的贡献' },
  { type: 'award', description: '奖学金、荣誉与获奖级别' },
  { type: 'certificate', description: '语言、职业资格与技能证书' },
  { type: 'journal_paper', description: '期刊论文、会议论文与作者顺序' },
  { type: 'hobby', description: '体现坚持与创造力的兴趣' },
  { type: 'custom', description: '自由组织你的独特履历' }
]

const CUSTOM_TYPE_RE = /^custom_[1-5]$/

export function isCustomType(type) {
  return type === 'custom' || CUSTOM_TYPE_RE.test(type || '')
}

export function metaOf(type) {
  return SECTION_META[isCustomType(type) ? 'custom' : type] || SECTION_META.custom
}

export function uid(prefix = 'itm') {
  return `${prefix}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`
}

export function defaultFields(type) {
  const meta = metaOf(type)
  const fields = {}
  for (const def of meta.fields) {
    if (def.type === 'switch') fields[def.key] = def.default ?? false
    else if (def.type === 'tags') fields[def.key] = []
    else if (def.type === 'period') fields[def.key] = { start: '', end: '', current: false }
    else fields[def.key] = def.default ?? ''
  }
  if (type === 'basic_info') fields.showGender = true
  return fields
}

export function createItem(type) {
  return { id: uid(), fields: defaultFields(type) }
}

export function createBlankSection(type, options = {}) {
  const meta = metaOf(type)
  const custom = isCustomType(type)
  const section = {
    id: uid('sec'),
    type,
    title: custom ? (options.title || '自定义模块') : meta.label,
    visible: true,
    order: 0,
    meta: {},
    items: []
  }
  if (type === 'job_intention') section.meta.displayMode = 'title_only'
  if (custom) section.meta.customTitle = options.title || '自定义模块'
  if (meta.single) section.items.push(createItem(type))
  return section
}

export function plainText(html) {
  if (!html) return ''
  if (typeof document === 'undefined') return String(html).replace(/<[^>]+>/g, ' ')
  const div = document.createElement('div')
  div.innerHTML = html
  return (div.textContent || '').replace(/\s+/g, ' ').trim()
}

export function formatMonth(value) {
  if (!value) return ''
  const [year, month] = value.split('-')
  return month ? `${year}.${month}` : value
}

export function periodText(periodValue, separator = '—') {
  if (!periodValue) return ''
  const start = formatMonth(periodValue.start)
  const end = formatMonth(periodValue.end)
  if (periodValue.current) return start ? `${start} ${separator === '至' ? '' : separator + ' '}至今` : '至今'
  if (start && end) return `${start} ${separator} ${end}`
  return start || end
}

export function salaryText(fields) {
  if (!fields) return ''
  if (fields.salaryNegotiable) return '薪资面议'
  const tidy = (value) => {
    if (!value) return ''
    const raw = String(value).replace(/[^\d.]/g, '')
    const num = Number(raw)
    if (!raw || Number.isNaN(num)) return String(value)
    return num >= 1000 ? `${Math.round(num / 100) / 10}K` : `${num}K`
  }
  const min = tidy(fields.salaryMin)
  const max = tidy(fields.salaryMax)
  if (min && max) return `${min}–${max}`
  if (min) return `${min} 起`
  if (max) return `最高 ${max}`
  return ''
}

export function isItemBlank(item) {
  const fields = item?.fields || {}
  for (const [key, value] of Object.entries(fields)) {
    if (key === 'showGender' || key === 'showAge' || key === 'salaryNegotiable') continue
    if (key === 'period') {
      if (value?.start || value?.end || value?.current) return false
      continue
    }
    if (Array.isArray(value) && value.length) return false
    if (typeof value === 'string' && value.trim()) return false
    if (typeof value === 'number' && value) return false
  }
  return true
}

export function sectionHasContent(section) {
  if (!section) return false
  if (section.type === 'basic_info') return Boolean(section.items?.[0]?.fields?.name?.trim())
  return (section.items || []).some((item) => !isItemBlank(item))
}

export function pruneSection(section) {
  const meta = metaOf(section.type)
  const items = (section.items || []).filter((item) => meta.single || !isItemBlank(item))
  return { ...section, items }
}

export function validateSection(section) {
  const meta = metaOf(section.type)
  const items = (section.items || []).filter((item) => !isItemBlank(item))
  if (section.type === 'basic_info') {
    const fields = section.items?.[0]?.fields || {}
    if (!fields.name?.trim()) return '请填写姓名'
    if (fields.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email)) return '邮箱格式不正确'
    if (fields.phone && !/^[\d+\-\s()]{6,30}$/.test(fields.phone)) return '手机号格式不正确'
    return ''
  }
  if (section.type === 'job_intention') {
    const fields = section.items?.[0]?.fields || {}
    if (!fields.jobTitle?.trim() && !isItemBlank(section.items?.[0])) return '请填写目标岗位'
    return ''
  }
  if (!meta.single && items.length === 0 && (section.items || []).some((item) => !isItemBlank(item))) {
    return '请补全条目内容'
  }
  const requiredMap = {
    education: ['school', 'school'],
    work_experience: ['company', 'position'],
    project_experience: ['name'],
    campus_experience: ['organization'],
    portfolio: ['title'],
    competition: ['name'],
    award: ['name'],
    certificate: ['name'],
    journal_paper: ['title'],
    skill_proficiency: ['name'],
    custom: ['title'],
    postgraduate_info: ['school']
  }
  const required = requiredMap[isCustomType(section.type) ? 'custom' : section.type]
  if (required) {
    for (const item of items) {
      for (const key of required) {
        if (!item.fields?.[key]?.trim?.()) {
          const label = meta.fields.find((def) => def.key === key)?.label || '必填项'
          return `请填写${label}`
        }
      }
    }
  }
  return ''
}

export function sectionLines(section) {
  if (!section) return []
  const fields = section.items?.[0]?.fields || {}
  const items = section.items || []
  const sectionType = isCustomType(section.type) ? 'custom' : section.type
  switch (sectionType) {
    case 'basic_info': {
      const parts = [fields.name, fields.identity, fields.city].filter(Boolean)
      const contacts = [fields.phone, fields.email].filter(Boolean).join(' · ')
      return [parts.join(' · '), contacts].filter(Boolean)
    }
    case 'job_intention': {
      const parts = [fields.jobTitle, fields.city, salaryText(fields)].filter(Boolean)
      return parts.length ? [parts.join(' · ')] : []
    }
    case 'education':
      return items.filter((item) => !isItemBlank(item)).slice(0, 2).map((item) => {
        const f = item.fields
        const main = [f.school, f.major, f.degree].filter(Boolean).join(' · ')
        const time = periodText(f.period)
        return time ? `${main}　${time}` : main
      })
    case 'work_experience':
      return items.filter((item) => !isItemBlank(item)).slice(0, 2).map((item) => {
        const f = item.fields
        const main = [f.company, f.position].filter(Boolean).join(' · ')
        return periodText(f.period) ? `${main}　${periodText(f.period)}` : main
      })
    case 'project_experience':
      return items.filter((item) => !isItemBlank(item)).slice(0, 2).map((item) => {
        const f = item.fields
        return [f.name, f.role].filter(Boolean).join(' · ')
      })
    case 'campus_experience':
      return items.filter((item) => !isItemBlank(item)).slice(0, 2).map((item) => {
        const f = item.fields
        return [f.organization, f.role].filter(Boolean).join(' · ')
      })
    case 'skills_summary':
    case 'self_evaluation':
    case 'paper_publication':
      return [plainText(fields.content)].filter(Boolean)
    case 'skill_proficiency':
      return [items.filter((item) => item.fields?.name).map((item) => item.fields.name).join(' · ')].filter(Boolean)
    case 'portfolio':
      return [items.filter((item) => item.fields?.title).map((item) => item.fields.title).join(' · ')].filter(Boolean)
    case 'hobby':
      return [plainText(fields.content)].filter(Boolean)
    case 'postgraduate_info':
      return [[fields.school, fields.major, fields.initialScore && `初试 ${fields.initialScore}`].filter(Boolean).join(' · ')].filter(Boolean)
    case 'competition':
      return items.filter((item) => !isItemBlank(item)).slice(0, 2).map((item) => [item.fields.name, item.fields.award].filter(Boolean).join(' · '))
    case 'award':
      return items.filter((item) => !isItemBlank(item)).slice(0, 2).map((item) => [item.fields.name, item.fields.level].filter(Boolean).join(' · '))
    case 'certificate':
      return items.filter((item) => !isItemBlank(item)).slice(0, 2).map((item) => [item.fields.name, item.fields.issuer].filter(Boolean).join(' · '))
    case 'journal_paper':
      return items.filter((item) => !isItemBlank(item)).slice(0, 2).map((item) => [item.fields.title, item.fields.journal].filter(Boolean).join(' · '))
    case 'custom':
      return items.filter((item) => !isItemBlank(item)).slice(0, 2).map((item) => [item.fields.title, item.fields.subtitle].filter(Boolean).join(' · '))
    default:
      return []
  }
}

export function sectionCountText(section) {
  if (!section) return ''
  const meta = metaOf(section.type)
  const count = (section.items || []).filter((item) => !isItemBlank(item)).length
  if (meta.single) return sectionHasContent(section) ? '已填写' : '待填写'
  return count ? `${count} 条` : '待填写'
}

export function visibleSections(content) {
  if (!content?.sections) return []
  return [...content.sections]
    .filter((section) => section.visible !== false)
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
}
