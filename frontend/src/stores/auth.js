import { defineStore } from 'pinia'
import { authApi } from '../api'
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
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
