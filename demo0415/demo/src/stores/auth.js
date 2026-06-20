import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
  }),
  getters: {
    isLoggedIn: (s) => Boolean(s.token),
    role: (s) => (s.user?.role || 'user'),
  },
  actions: {
    setAuth(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('token', token || '')
      localStorage.setItem('user', JSON.stringify(user || null))
    },
    logout() {
      this.setAuth('', null)
    },
  },
})

