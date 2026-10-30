import { describe, expect, it } from 'vitest'
import {
  createBlankSection,
  formatMonth,
  isCustomType,
  isItemBlank,
  metaOf,
  periodText,
  plainText,
  pruneSection,
  salaryText,
  sectionCountText,
  sectionHasContent,
  sectionLines,
  validateSection,
  visibleSections
} from './sectionCatalog'

const item = (fields) => ({ id: `itm_${Math.random()}`, fields })
const section = (type, items, extra = {}) => ({ type, items, visible: true, order: 0, ...extra })

describe('isCustomType / metaOf', () => {
  it('识别 custom 与 custom_1..5', () => {
    expect(isCustomType('custom')).toBe(true)
    expect(isCustomType('custom_3')).toBe(true)
    expect(isCustomType('custom_6')).toBe(false)
    expect(isCustomType('education')).toBe(false)
    expect(isCustomType(undefined)).toBe(false)
  })

  it('自定义子类型复用自定义模块的元数据，未知类型兜底', () => {
    expect(metaOf('custom_2').label).toBe('自定义模块')
    expect(metaOf('unknown_type').label).toBe('自定义模块')
    expect(metaOf('education').label).toBe('教育背景')
  })
})

describe('formatMonth / periodText', () => {
  it('月份格式化成点分隔', () => {
    expect(formatMonth('2026-09')).toBe('2026.09')
    expect(formatMonth('2026')).toBe('2026')
    expect(formatMonth('')).toBe('')
  })

  it('区间文本覆盖起止、至今与单边', () => {
    expect(periodText({ start: '2025-03', end: '2026-01' })).toBe('2025.03 — 2026.01')
    expect(periodText({ start: '2025-03', current: true })).toBe('2025.03 — 至今')
    expect(periodText({ current: true })).toBe('至今')
    expect(periodText({ start: '2025-03' })).toBe('2025.03')
    expect(periodText({ start: '2025-03', current: true }, '至')).toBe('2025.03 至今')
    expect(periodText(null)).toBe('')
  })
})

describe('salaryText', () => {
  it('面议优先', () => {
    expect(salaryText({ salaryNegotiable: true })).toBe('薪资面议')
  })

  it('上下限按千元缩写', () => {
    expect(salaryText({ salaryMin: '25000', salaryMax: '35000' })).toBe('25K–35K')
    expect(salaryText({ salaryMin: '25000' })).toBe('25K 起')
    expect(salaryText({ salaryMax: '35000' })).toBe('最高 35K')
    expect(salaryText({ salaryMin: '22500', salaryMax: '30000' })).toBe('22.5K–30K')
  })

  it('带单位或分隔符的输入只保留数字', () => {
    expect(salaryText({ salaryMin: '25,000 元' })).toBe('25K 起')
  })

  it('全部留空时返回空串', () => {
    expect(salaryText({})).toBe('')
    expect(salaryText(null)).toBe('')
  })
})

describe('isItemBlank', () => {
  it('只勾了展示开关或空时间轴算空白', () => {
    expect(isItemBlank(item({ showGender: true, showAge: false, salaryNegotiable: false }))).toBe(true)
    expect(isItemBlank(item({ period: { start: '', end: '', current: false } }))).toBe(true)
    expect(isItemBlank(item({ name: '   ' }))).toBe(true)
  })

  it('任一字段有内容即非空', () => {
    expect(isItemBlank(item({ name: '郝简历' }))).toBe(false)
    expect(isItemBlank(item({ period: { start: '2025-03' } }))).toBe(false)
    expect(isItemBlank(item({ courses: ['数据结构'] }))).toBe(false)
  })
})

describe('sectionHasContent / pruneSection', () => {
  it('基本信息必须有姓名，其余模块看条目', () => {
    expect(sectionHasContent(section('basic_info', [item({ name: '郝简历' })]))).toBe(true)
    expect(sectionHasContent(section('basic_info', [item({ phone: '13800000000' })]))).toBe(false)
    expect(sectionHasContent(section('education', [item({ school: '浙江大学' })]))).toBe(true)
    expect(sectionHasContent(section('education', [item({})]))).toBe(false)
    expect(sectionHasContent(null)).toBe(false)
  })

  it('保存前剔除空白条目，单条模块保留占位', () => {
    const education = section('education', [item({ school: 'A' }), item({}), item({ school: 'B' })])
    expect(pruneSection(education).items).toHaveLength(2)

    const basic = section('basic_info', [item({})])
    expect(pruneSection(basic).items).toHaveLength(1)
  })
})

describe('validateSection', () => {
  it('基本信息校验姓名、邮箱与手机号', () => {
    expect(validateSection(section('basic_info', [item({})]))).toBe('请填写姓名')
    expect(validateSection(section('basic_info', [item({ name: '郝简历', email: 'a@b' })]))).toBe('邮箱格式不正确')
    expect(validateSection(section('basic_info', [item({ name: '郝简历', phone: 'abc' })]))).toBe('手机号格式不正确')
    expect(validateSection(section('basic_info', [item({ name: '郝简历', email: 'a@b.com', phone: '138-0000-0000' })]))).toBe('')
  })

  it('求职意向允许整体留空，写了内容才要求岗位', () => {
    expect(validateSection(section('job_intention', [item({})]))).toBe('')
    expect(validateSection(section('job_intention', [item({ city: '上海' })]))).toBe('请填写目标岗位')
    expect(validateSection(section('job_intention', [item({ jobTitle: '后端工程师' })]))).toBe('')
  })

  it('多条目模块校验必填字段', () => {
    expect(validateSection(section('education', [item({ school: '浙江大学' })]))).toBe('')
    expect(validateSection(section('education', [item({ major: '计算机' })]))).toBe('请填写学校')
    expect(validateSection(section('work_experience', [item({ company: '某公司' })]))).toBe('请填写职位')
    expect(validateSection(section('work_experience', [item({ company: '某公司', position: '后端' })]))).toBe('')
  })
})

describe('sectionLines / sectionCountText', () => {
  it('基本信息拼成身份行与联系方式行', () => {
    const lines = sectionLines(section('basic_info', [item({
      name: '郝简历',
      identity: '应届生',
      city: '上海',
      phone: '13800000000',
      email: 'hao@example.com'
    })]))
    expect(lines).toEqual(['郝简历 · 应届生 · 上海', '13800000000 · hao@example.com'])
  })

  it('教育背景最多列两条并带时间段', () => {
    const lines = sectionLines(section('education', [
      item({ school: '浙江大学', major: '计算机', degree: '硕士', period: { start: '2023-09', end: '2026-06' } }),
      item({ school: '浙江大学', degree: '本科' }),
      item({ school: '第三所' })
    ]))
    expect(lines).toHaveLength(2)
    expect(lines[0]).toBe('浙江大学 · 计算机 · 硕士　2023.09 — 2026.06')
  })

  it('富文本摘要去掉标签', () => {
    expect(plainText('<p>做过 <b>三件事</b></p>')).toBe('做过 三件事')
    expect(sectionLines(section('self_evaluation', [item({ content: '<p>认真</p>' })]))).toEqual(['认真'])
  })

  it('条目计数区分单条模块与列表模块', () => {
    expect(sectionCountText(section('basic_info', [item({ name: '郝简历' })]))).toBe('已填写')
    expect(sectionCountText(section('basic_info', [item({})]))).toBe('待填写')
    expect(sectionCountText(section('education', [item({ school: 'A' }), item({}), item({ school: 'B' })]))).toBe('2 条')
    expect(sectionCountText(section('education', [item({})]))).toBe('待填写')
  })
})

describe('visibleSections / createBlankSection', () => {
  it('过滤隐藏模块并按 order 排序，不改原数组', () => {
    const content = {
      sections: [
        section('education', [], { order: 2 }),
        section('award', [], { order: 1 }),
        section('hobby', [], { order: 0, visible: false })
      ]
    }
    expect(visibleSections(content).map((s) => s.type)).toEqual(['award', 'education'])
    expect(content.sections).toHaveLength(3)
    expect(visibleSections(null)).toEqual([])
  })

  it('单条模块自带一条空白条目，求职意向默认只显示标题', () => {
    const intention = createBlankSection('job_intention')
    expect(intention.items).toHaveLength(1)
    expect(intention.meta.displayMode).toBe('title_only')

    const custom = createBlankSection('custom_2', { title: '开源贡献' })
    expect(custom.title).toBe('开源贡献')
    expect(custom.meta.customTitle).toBe('开源贡献')
    expect(custom.items).toHaveLength(0)
  })
})
