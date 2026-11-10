import { formatFileSize } from './format'

/** 每批追加的行数与渲染上限：DOM 数量可控，用户再多也不会把后台拖垮。 */
export const USER_PAGE_SIZE = 40
export const USER_MAX_RENDERED = 300

const CJK = /[\u2e80-\u9fff\uf900-\ufaff]/
const TEXT_FIELDS = new Set(['username', 'nickname', 'email', 'role'])
const collator = new Intl.Collator('zh-Hans-CN', { numeric: true, sensitivity: 'base' })

/** 按字符估算文本像素宽度：中文按一个字宽、英文数字按半个字宽，够用来定列宽。 */
export function textWidth(value) {
  let width = 0
  for (const ch of String(value ?? '')) width += CJK.test(ch) ? 13 : 7
  return width
}

/** 列宽取样本里最宽的一行，再夹在上下限之间，避免个别超长内容把表格撑爆。 */
export function columnWidth(values, min, max, padding = 28) {
  const widest = values.reduce((acc, value) => Math.max(acc, textWidth(value)), 0)
  return Math.round(Math.min(max, Math.max(min, widest + padding)))
}

export function compareUsers(a, b, prop) {
  if (prop === 'online') return Number(Boolean(a.online)) - Number(Boolean(b.online))
  if (TEXT_FIELDS.has(prop)) return collator.compare(String(a[prop] ?? ''), String(b[prop] ?? ''))
  return (Number(a[prop]) || 0) - (Number(b[prop]) || 0)
}

/** 搜索与排序都基于全量数据，滚动只控制渲染多少行，结果才不会因为分批而失真。 */
export function queryUsers(users, keyword, sortState) {
  const query = String(keyword ?? '').trim().toLowerCase()
  const list = query
    ? users.filter((item) =>
        [item.username, item.nickname, item.email].filter(Boolean).some((value) =>
          String(value).toLowerCase().includes(query)))
    : users.slice()
  const { prop, order } = sortState || {}
  if (!prop) return list
  const factor = order === 'ascending' ? 1 : -1
  return list.sort((a, b) => (compareUsers(a, b, prop) || (a.id - b.id)) * factor)
}

/** 列宽按前 200 条样本估算：不随滚动加载变化，表格不会边滚边抖。 */
export function userColumnWidths(users) {
  const sample = users.slice(0, 200)
  return {
    user: columnWidth(sample.flatMap((item) => [item.nickname || item.username, `@${item.username || ''}`]), 150, 220, 46),
    email: columnWidth(sample.map((item) => item.email), 170, 260, 88),
    role: 88,
    resumeCount: 66,
    storage: columnWidth(sample.map((item) => formatFileSize(item.storageBytes || 0)), 84, 120, 24),
    createdAt: 104,
    status: columnWidth(sample.map((item) => `${item.online ? '在线' : '离线'}${item.status !== 1 ? ' 已冻结' : ''}`), 100, 140, 24),
    actions: 150
  }
}
