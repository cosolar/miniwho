import { defineStore } from 'pinia'
import { accountApi, authApi } from '../api'
import { TOKEN_KEY, USER_KEY } from '../api/client'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser(),
    /**
     * 账号头像不进 localStorage：url 是六小时的签名链接，缓存下来下次打开只会是一张碎图。
     * loaded 用来避免每次路由切换都重复拉一次。
     */
    avatar: null,
    avatarLoaded: false,
    loading: false
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.role === 'admin'
  },
  actions: {
    setSession(data) {
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    async login(payload) {
      const data = await authApi.login(payload)
      return this.setSession(data)
    },
    async register(payload) {
      const data = await authApi.register(payload)
      return this.setSession(data)
    },
    async registerWithEmail(payload) {
      const data = await authApi.registerEmail(payload)
      return this.setSession(data)
    },
    /** OAuth 回调把令牌放在 URL 片段里送回来；收下后立刻抹掉地址栏，再拉一次用户信息。 */
    async adoptToken(token) {
      this.token = token
      localStorage.setItem(TOKEN_KEY, token)
      await this.fetchMe()
      return token
    },
    async fetchMe() {
      if (!this.token) return null
      try {
        const user = await authApi.me()
        this.user = user
        localStorage.setItem(USER_KEY, JSON.stringify(user))
        return user
      } catch (error) {
        if (error.status === 401) this.clear()
        throw error
      }
    },
    /**
     * 拉账号头像。已拉过就跳过，除非链接过期（图片加载失败）需要 force 一次。
     * 失败不弹提示：头像位有首字母兜底，不该为一个装饰件打断主流程。
     */
    async loadAvatar(force = false) {
      if (!this.token || (this.avatarLoaded && !force)) return this.avatar
      try {
        this.avatar = await accountApi.avatar()
        this.avatarLoaded = true
      } catch {
        this.avatar = null
      }
      return this.avatar
    },
    setAvatar(data) {
      this.avatar = data
      this.avatarLoaded = true
    },
    async updateProfile(payload) {
      const user = await authApi.updateProfile(payload)
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      return user
    },
    async changePassword(payload) {
      await authApi.changePassword(payload)
    },
    async logout() {
      try {
        if (this.token) await authApi.logout()
      } catch {
        // Exit locally even if the token has already expired.
      }
      this.clear()
    },
    clear() {
      this.token = ''
      this.user = null
      this.avatar = null
      this.avatarLoaded = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
