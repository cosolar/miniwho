import { beforeEach, describe, expect, it, vi } from 'vitest'
import { applyContentWidth, cachedContentWidth } from './appearance'

const CSS_VAR = '--content-width'
const STORAGE_KEY = 'miniwho.contentWidth'

describe('cachedContentWidth', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.style.removeProperty(CSS_VAR)
  })

  it('没有缓存时回落到 1200', () => {
    expect(cachedContentWidth()).toBe(1200)
  })

  it('读上次写入的值，坏值回落到默认', () => {
    localStorage.setItem(STORAGE_KEY, '1440')
    expect(cachedContentWidth()).toBe(1440)

    localStorage.setItem(STORAGE_KEY, 'wide')
    expect(cachedContentWidth()).toBe(1200)
  })
})

describe('applyContentWidth', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.style.removeProperty(CSS_VAR)
  })

  it('写入 CSS 变量并记住数值', () => {
    applyContentWidth(1360)
    expect(document.documentElement.style.getPropertyValue(CSS_VAR)).toBe('1360px')
    expect(localStorage.getItem(STORAGE_KEY)).toBe('1360')
  })

  it('四舍五入到整数像素', () => {
    applyContentWidth(1320.6)
    expect(document.documentElement.style.getPropertyValue(CSS_VAR)).toBe('1321px')
  })

  it('非法输入回落到默认宽度', () => {
    applyContentWidth('')
    expect(document.documentElement.style.getPropertyValue(CSS_VAR)).toBe('1200px')

    applyContentWidth(-10)
    expect(document.documentElement.style.getPropertyValue(CSS_VAR)).toBe('1200px')

    applyContentWidth('abc')
    expect(document.documentElement.style.getPropertyValue(CSS_VAR)).toBe('1200px')
  })

  it('宽度真的变化时补发一次 resize，同值不重复触发', () => {
    const onResize = vi.fn()
    window.addEventListener('resize', onResize)

    // 首次写入没有旧值可比，按测量组件的要求不触发
    applyContentWidth(1360)
    expect(onResize).not.toHaveBeenCalled()

    applyContentWidth(1400)
    expect(onResize).toHaveBeenCalledTimes(1)

    applyContentWidth(1400)
    expect(onResize).toHaveBeenCalledTimes(1)

    window.removeEventListener('resize', onResize)
  })
})
