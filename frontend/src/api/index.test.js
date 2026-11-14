import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 回归：验证码登录后设置新密码，请求体的形状必须与接口 DTO 对得上。
 *
 * <p>之前 API 层写成 `passwordAfterCode: (newPassword) => put(url, { newPassword })`，
 * 而调用处传的是 `{ newPassword: form.newPassword }`，于是实际发出去的是
 * `{"newPassword":{"newPassword":"..."}}`——字符串的位置上放了个对象，
 * 服务端反序列化失败，回 400「请求体格式不正确」，用户点「保存新密码」永远失败。
 */
const put = vi.fn().mockResolvedValue({ data: {} })
const post = vi.fn().mockResolvedValue({ data: {} })
const get = vi.fn().mockResolvedValue({ data: {} })

vi.mock('../api/client', () => ({
  default: { get, post, put },
  TOKEN_KEY: 'miniwho.token',
  USER_KEY: 'miniwho.user',
  API_BASE: '/api',
  API_ORIGIN: '',
  apiUrl: (p) => `/api${p}`,
  mediaUrl: (p) => p,
  itemMediaUrl: (p) => p,
  notifyError: () => {},
  ElMessage: { error: () => {} }
}))

const { authApi } = await import('./index')

describe('authApi.passwordAfterCode', () => {
  beforeEach(() => {
    put.mockClear()
  })

  it('按调用处给的形状原样发出，不额外包一层', () => {
    authApi.passwordAfterCode({ newPassword: 'miniwhoCosolar' })

    expect(put).toHaveBeenCalledTimes(1)
    const [url, body] = put.mock.calls[0]
    expect(url).toBe('/auth/password-after-code')
    // newPassword 的位置上必须是字符串本身，不能再套一层对象。
    expect(body).toEqual({ newPassword: 'miniwhoCosolar' })
    expect(typeof body.newPassword).toBe('string')
  })
})
