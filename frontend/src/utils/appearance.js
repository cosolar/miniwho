/**
 * 站点内容宽度：服务端只存一个整数，前端换成 CSS 变量下发给布局，
 * 所有页面外壳都写 min(var(--content-width)…)，因此换值不需要重新构建。
 */
const DEFAULT_CONTENT_WIDTH = 1200
const STORAGE_KEY = 'miniwho.contentWidth'

/** 启动时先用上次记住的值写进变量，避免默认宽度闪一下。 */
export function cachedContentWidth() {
  return Number(localStorage.getItem(STORAGE_KEY)) || DEFAULT_CONTENT_WIDTH
}

export function applyContentWidth(width) {
  const px = Number(width)
  const safe = Number.isFinite(px) && px > 0 ? px : DEFAULT_CONTENT_WIDTH
  const next = `${Math.round(safe)}px`
  const previous = document.documentElement.style.getPropertyValue('--content-width')
  document.documentElement.style.setProperty('--content-width', next)
  localStorage.setItem(STORAGE_KEY, String(Math.round(safe)))
  // 按容器宽度测量的组件（如模板中心的分类胶囊）只监听窗口 resize，宽度变了补发一次。
  if (previous && previous !== next) window.dispatchEvent(new Event('resize'))
}
