import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import {
  daysLeft,
  formatDate,
  formatDateTime,
  formatFileSize,
  greeting,
  timeAgo
} from './format'

const at = (y, m, d, h = 0, min = 0) => new Date(y, m - 1, d, h, min).getTime()

describe('formatDate / formatDateTime', () => {
  it('把毫秒时间戳格式化成点分隔的日期', () => {
    expect(formatDate(at(2026, 10, 30))).toBe('2026.10.30')
    expect(formatDateTime(at(2026, 10, 30, 14, 5))).toBe('2026.10.30 14:05')
  })

  it('空值与非法值统一显示为破折号', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate(undefined)).toBe('—')
    expect(formatDate('not-a-number')).toBe('—')
    expect(formatDateTime(0)).toBe('—')
  })
})

describe('timeAgo', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(at(2026, 10, 30, 12, 0))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('按时间跨度切换单位', () => {
    const now = Date.now()
    expect(timeAgo(now - 30_000)).toBe('刚刚')
    expect(timeAgo(now - 5 * 60_000)).toBe('5 分钟前')
    expect(timeAgo(now - 3 * 3600_000)).toBe('3 小时前')
    expect(timeAgo(now - 2 * 86400_000)).toBe('2 天前')
  })

  it('超过一周退回具体日期', () => {
    expect(timeAgo(Date.now() - 30 * 86400_000)).toBe('2026.09.30')
  })

  it('空值显示破折号', () => {
    expect(timeAgo('')).toBe('—')
  })
})

describe('daysLeft', () => {
  it('没有有效期时显示永久有效', () => {
    expect(daysLeft(null)).toBe('永久有效')
    expect(daysLeft(0)).toBe('永久有效')
  })

  it('向上取整到天，过期显示已过期', () => {
    const now = Date.now()
    expect(daysLeft(now + 2 * 86400_000)).toBe('2 天后过期')
    expect(daysLeft(now + 2 * 86400_000 - 1000)).toBe('2 天后过期')
    expect(daysLeft(now - 1000)).toBe('已过期')
  })
})

describe('formatFileSize', () => {
  it('按 B / KB / MB 分档', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2 KB')
    expect(formatFileSize(1024 * 1024 * 1.5)).toBe('1.5 MB')
    expect(formatFileSize('abc')).toBe('0 B')
  })
})

describe('greeting', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it.each([
    [3, '夜深了'],
    [9, '早上好'],
    [12, '中午好'],
    [15, '下午好'],
    [21, '晚上好']
  ])('%i 点返回 %s', (hour, expected) => {
    vi.useFakeTimers()
    vi.setSystemTime(at(2026, 10, 30, hour, 0))
    expect(greeting()).toBe(expected)
  })
})
