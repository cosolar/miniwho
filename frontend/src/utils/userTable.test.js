import { describe, expect, it } from 'vitest'
import { columnWidth, compareUsers, queryUsers, textWidth, userColumnWidths } from './userTable'

const users = [
  { id: 1, username: 'alice', nickname: '张三', email: 'a@example.com', role: 'user', createdAt: 100, resumeCount: 2, storageBytes: 100, online: false, status: 1 },
  { id: 2, username: 'bob', nickname: '李四', email: '', role: 'admin', createdAt: 300, resumeCount: 1, storageBytes: 900, online: true, status: 2 },
  { id: 3, username: 'carol', nickname: '', email: 'c@example.com', role: 'user', createdAt: 200, resumeCount: 5, storageBytes: 0, online: false, status: 1 }
]

describe('文本与列宽估算', () => {
  it('中文按全宽、英文数字按半宽计算', () => {
    expect(textWidth('在线')).toBe(26)
    expect(textWidth('ab')).toBe(14)
    expect(textWidth(null)).toBe(0)
  })

  it('取最宽一行并夹在上下限之间', () => {
    expect(columnWidth(['a', 'abc'], 100, 200, 20)).toBe(100)
    expect(columnWidth(['abcdefghij'], 50, 200, 20)).toBe(90)
    expect(columnWidth(['x'.repeat(100)], 50, 120, 20)).toBe(120)
  })

  it('列宽随内容变宽但不突破上限', () => {
    const narrow = userColumnWidths([{ username: 'a', nickname: 'a', email: 'a@b.co', storageBytes: 0, online: true, status: 1 }])
    const wide = userColumnWidths([{
      username: 'a-very-long-username', nickname: '一个很长的昵称',
      email: 'long.address@example.com', storageBytes: 1024 * 1024, online: false, status: 2
    }])
    expect(wide.user).toBeGreaterThan(narrow.user)
    expect(wide.email).toBeGreaterThan(narrow.email)
    expect(wide.user).toBeLessThanOrEqual(220)
    expect(narrow.actions).toBe(150)
  })
})

describe('搜索与排序', () => {
  it('默认按注册时间倒序', () => {
    expect(queryUsers(users, '', { prop: 'createdAt', order: 'descending' }).map((item) => item.id))
      .toEqual([2, 3, 1])
  })

  it('数值、在线状态与文本列都可排序', () => {
    expect(queryUsers(users, '', { prop: 'resumeCount', order: 'ascending' }).map((item) => item.id))
      .toEqual([2, 1, 3])
    expect(queryUsers(users, '', { prop: 'online', order: 'descending' }).map((item) => item.id))
      .toEqual([2, 3, 1])
    expect(queryUsers(users, '', { prop: 'username', order: 'ascending' }).map((item) => item.id))
      .toEqual([1, 2, 3])
  })

  it('同值时按 id 稳定排序，且不改动原数组', () => {
    expect(compareUsers(users[0], users[2], 'online')).toBe(0)
    expect(queryUsers(users, 'example', { prop: 'online', order: 'ascending' }).map((item) => item.id))
      .toEqual([1, 3])
    expect(queryUsers(users, 'bob', null).map((item) => item.id)).toEqual([2])
    expect(users.map((item) => item.id)).toEqual([1, 2, 3])
  })
})
