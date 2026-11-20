/**
 * 列表标记颜色的预设色板。
 *
 * 用户可以自由选任意色值（el-color-picker 的取色面板），这里这一组只是
 * el-color-picker「预设颜色」快捷区里顺手能点到的常用色，省得每次都调色盘。
 *
 * 存储：选中的真实 hex 值写进富文本的 data-list-color（如 data-list-color="#a33a2f"），
 * 后端只放行 #rrggbb 格式。渲染时由渲染层把该值设成列表的内联 --list-color，
 * base.css 里 li::marker / li::before 的 var(--list-color, 主题色) 就会用它着色。
 * 不走 CSS attr()：attr() 在自定义属性里返回字符串 token，当颜色用跨浏览器不可靠，
 * 所以渲染层用 JS 设内联样式，既稳又不必放开 style 属性。
 *
 * theme（null）表示「跟随模板主题色」——不写 data-list-color，标记回到模板主题色。
 * listColorToken 只认 #rrggbb，把值规整成小写；theme / 空 / 非法值一律当作没设置。
 */
export const LIST_COLOR_PRESETS = [
  '#2b3236', // 墨黑
  '#4a4f52', // 炭灰
  '#6b7280', // 中灰
  '#9aa3ab', // 浅灰
  '#475569', // 石板
  '#c62828', // 正红
  '#a33a2f', // 砖红
  '#b03a5b', // 玫红
  '#7d2f43', // 酒红
  '#c2621a', // 橙
  '#b8860b', // 琥珀
  '#8a6033', // 赭石
  '#7d7350', // 土黄
  '#2f5d3a', // 墨绿
  '#5c6a3f', // 橄榄
  '#1f7a4d', // 翠绿
  '#1f9e8f', // 青绿
  '#6b8f7d', // 浅绿
  '#1f3a5f', // 藏青
  '#2e6fd1', // 正蓝
  '#1f6f9e', // 湖蓝
  '#3f8fb5', // 天蓝
  '#4a6b7c', // 钢蓝
  '#6a4a6e', // 紫棠
  '#5b3f8c', // 深紫
  '#8b6fa8', // 藕荷
  '#3b4c8c', // 靛青
  '#5c4033', // 咖啡
  '#7b5245', // 可可
  '#a3855f' // 浅棕
]

/** 合法的标记色值：只接受 #rrggbb 小写 hex，其余（含非法输入）一律当作没设置。 */
const HEX_COLOR = /^#[0-9a-f]{6}$/

/**
 * 把存储值规整成可用的 hex；theme / 空 / 非法值返回 null（= 跟随主题色）。
 * 传入大写会转小写，容错历史数据。
 */
export function listColorToken(value) {
  if (!value || value === 'theme') return null
  const normalized = String(value).toLowerCase()
  return HEX_COLOR.test(normalized) ? normalized : null
}

/**
 * DOMPurify 钩子：正文清洗后，把列表上合法的 data-list-color 设成内联 --list-color，
 * li::marker / li::before 的 var(--list-color, 主题色) 才会着色。只认 #rrggbb，
 * 非法值一律忽略，等价于跟随主题色。DOMPurify 默认剥掉 style，所以这里显式回填，
 * 既不必在 ALLOWED_ATTR 里放开 style，也不会把任意 CSS 带进正文。
 * 在每个渲染组件模块顶层 registerDompurifyListColor() 一次即可（钩子是全局的，重复注册会累积）。
 */
export function registerDompurifyListColor(purify) {
  purify.addHook('afterSanitizeAttributes', (node) => {
    const color = listColorToken(node.getAttribute?.('data-list-color'))
    if (color) node.style.setProperty('--list-color', color)
  })
}
