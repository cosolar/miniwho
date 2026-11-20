import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { LIST_COLOR_PRESETS, listColorToken } from './listColors'

const here = dirname(fileURLToPath(import.meta.url))
const baseCss = readFileSync(resolve(here, '../styles/base.css'), 'utf-8')

describe('listColorToken', () => {
  it('主题色 / 空 / 未定义一律当作没设置，标记回到模板主题色', () => {
    expect(listColorToken('theme')).toBeNull()
    expect(listColorToken('')).toBeNull()
    expect(listColorToken(null)).toBeNull()
    expect(listColorToken(undefined)).toBeNull()
  })

  it('合法 #rrggbb 原样返回，大写规整成小写', () => {
    expect(listColorToken('#a33a2f')).toBe('#a33a2f')
    expect(listColorToken('#A33A2F')).toBe('#a33a2f')
    expect(listColorToken('#2E6FD1')).toBe('#2e6fd1')
  })

  it('非法值一律当作没设置，避免正文混入任意字符串', () => {
    expect(listColorToken('brick')).toBeNull()
    expect(listColorToken('red')).toBeNull()
    expect(listColorToken('#a33a2')).toBeNull()
    expect(listColorToken('#a33a2ff')).toBeNull()
    expect(listColorToken('a33a2f')).toBeNull()
    expect(listColorToken('red; background:url(x)')).toBeNull()
  })
})

describe('LIST_COLOR_PRESETS', () => {
  it('预设色都是合法的六位十六进制', () => {
    expect(LIST_COLOR_PRESETS.length).toBeGreaterThan(0)
    for (const hex of LIST_COLOR_PRESETS) {
      expect(hex, `${hex} 应为 #rrggbb`).toMatch(/^#[0-9a-f]{6}$/)
    }
  })

  it('预设色无重复', () => {
    expect(new Set(LIST_COLOR_PRESETS).size).toBe(LIST_COLOR_PRESETS.length)
  })
})

describe('样式表着色规则', () => {
  it('base.css 用 var(--list-color, 主题色) 给 marker 与 li::before 着色', () => {
    expect(baseCss).toMatch(/li::marker[^}]*var\(--list-color, var\(--paper-accent\)\)/s)
    expect(baseCss).toMatch(/li::before[^}]*var\(--list-color, var\(--paper-accent\)\)/s)
  })

  it('base.css 不再保留旧枚举映射（颜色改由渲染层设内联 --list-color）', () => {
    expect(baseCss).not.toMatch(/\[data-list-color='[a-z]+'\]/)
  })
})
