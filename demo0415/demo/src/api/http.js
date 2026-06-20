import axios from 'axios'
import { useAuthStore } from '../stores/auth'

export const http = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 60000,
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    const data = resp.data
    // 后端统一结构 {success,message,data}
    if (data && data.success === false) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return resp
  },
  (err) => Promise.reject(err),
)

