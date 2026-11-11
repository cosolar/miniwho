import client from './client'

export const authApi = {
  /** 登录页据此决定渲染哪些注册 / 登录入口，无需鉴权。 */
  config: () => client.get('/public/auth-config'),
  /** 滑块验证码出题：只回图片与尺寸，缺口位置不下发。 */
  captcha: () => client.get('/auth/captcha'),
  /** 提交一次滑动的完整轨迹，通过后换一张一次性票据（登录时带上）。 */
  captchaCheck: (payload) => client.post('/auth/captcha/check', payload),
  login: (payload) => client.post('/auth/login', payload),
  /** 连错 3 次后切换到验证码登录：先按账号发码，再只凭验证码进入。 */
  loginCode: (username) => client.post('/auth/login-code', { username }),
  loginByCode: (payload) => client.post('/auth/login-by-code', payload),
  /** 验证码登录后的免原密码改密，票据由服务端记在本次登录的令牌上。 */
  passwordAfterCode: (newPassword) => client.put('/auth/password-after-code', { newPassword }),
  register: (payload) => client.post('/auth/register', payload),
  emailCode: (email) => client.post('/auth/email-code', { email }),
  registerEmail: (payload) => client.post('/auth/register-email', payload),
  resetCode: (email) => client.post('/auth/password-code', { email }),
  resetPassword: (payload) => client.post('/auth/password-reset', payload),
  logout: () => client.post('/auth/logout'),
  me: () => client.get('/auth/me'),
  updateProfile: (payload) => client.put('/auth/profile', payload),
  changePassword: (payload) => client.put('/auth/password', payload)
}

export const accountApi = {
  /**
   * 账号全量数据导出（zip）。走 blob：后端直接流式吐压缩包，不是 ApiResponse。
   * 单独放大超时：数据多的账号打包要几十秒，用默认的 20 秒会掐断一个其实成功的下载。
   */
  exportData: () => client.get('/account/export', { responseType: 'blob', timeout: 600000 }),
  /** 申请注销：设过密码的账号交密码，第三方登录的账号交邮箱验证码。 */
  requestDeletion: (payload) => client.post('/account/deletion', payload),
  cancelDeletion: () => client.delete('/account/deletion'),
  /** 注销验证码只服务没有密码的第三方账号，收件地址由后端取当前账号绑定的邮箱。 */
  deletionCode: () => client.post('/account/deletion/code'),
  /** 换绑邮箱的验证码发给「新」地址，随保存资料接口一起提交。 */
  changeEmailCode: (payload) => client.post('/account/email-code', payload),
  /** 磁盘用量与配额：要扫目录，所以不进 /auth/me，个人中心按需拉一次。 */
  storage: () => client.get('/account/storage'),
  /**
   * 账号头像的状态与签名链接。链接六小时过期，所以每次进页面现取，
   * 不进 /auth/me：那个接口的返回值会被缓存进 localStorage。
   */
  avatar: () => client.get('/account/avatar'),
  uploadAvatar: (file) => {
    const form = new FormData()
    form.append('file', file)
    return client.post('/account/avatar', form, { timeout: 60000 })
  },
  removeAvatar: () => client.delete('/account/avatar')
}

export const resumeApi = {
  list: () => client.get('/resumes'),
  create: (payload) => client.post('/resumes', payload),
  detail: (id) => client.get(`/resumes/${id}`),
  workbench: (id) => client.get(`/resumes/${id}/workbench`),
  rename: (id, title) => client.put(`/resumes/${id}`, { title }),
  duplicate: (id) => client.post(`/resumes/${id}/duplicate`),
  remove: (id) => client.delete(`/resumes/${id}`),
  saveSection: (id, type, section, contentVersion) =>
    client.put(`/resumes/${id}/sections/${type}`, { section, contentVersion }),
  savePrefs: (id, payload) => client.patch(`/resumes/${id}/module-prefs`, payload),
  addModule: (id, section, contentVersion) =>
    client.post(`/resumes/${id}/modules`, { section, contentVersion }),
  removeModule: (id, type, version) =>
    client.delete(`/resumes/${id}/modules/${type}`, { params: { version } }),
  saveStyle: (id, payload) => client.put(`/resumes/${id}/style`, payload)
}

export const templateApi = {
  list: () => client.get('/templates'),
  page: (params) => client.get('/templates/page', { params }),
  mine: () => client.get('/templates/mine'),
  detail: (code) => client.get(`/templates/${code}`),
  importPackage: (payload) => client.post('/templates/import', payload),
  share: (code, payload) => client.put(`/templates/${code}/share`, payload),
  remove: (code) => client.delete(`/templates/${code}`),
  exportPackage: (code) => client.get(`/templates/${code}/export`, { responseType: 'blob' })
}

export const sampleApi = {
  /** 无鉴权的公开示例简历，管理员未设置时返回 null。 */
  resume: () => client.get('/public/sample-resume')
}

export const attachmentApi = {  list: (resumeId) => client.get(`/resumes/${resumeId}/attachments`),
  upload: (resumeId, file, onProgress) => {
    const form = new FormData()
    form.append('file', file)
    return client.post(`/resumes/${resumeId}/attachments`, form, {
      timeout: 60000,
      onUploadProgress: (event) => {
        if (event.total && onProgress) onProgress(Math.round((event.loaded / event.total) * 100))
      }
    })
  },
  remove: (attachmentId) => client.delete(`/attachments/${attachmentId}`),
  fetchBlob: (attachmentId) => client.get(`/attachments/${attachmentId}/file`, { responseType: 'blob' })
}

export const itemMediaApi = {
  upload: (resumeId, file) => {
    const form = new FormData()
    form.append('file', file)
    return client.post(`/resumes/${resumeId}/media`, form, { timeout: 60000 })
  }
}

export const avatarApi = {
  info: (resumeId) => client.get(`/resumes/${resumeId}/avatar/info`),
  upload: (resumeId, file) => {
    const form = new FormData()
    form.append('file', file)
    return client.post(`/resumes/${resumeId}/avatar`, form, { timeout: 60000 })
  },
  setShape: (resumeId, shape) => client.put(`/resumes/${resumeId}/avatar-shape`, { shape }),
  remove: (resumeId) => client.delete(`/resumes/${resumeId}/avatar`)
}

export const pdfApi = {
  status: () => client.get('/pdf/status'),
  download: (resumeId, params) => client.get(`/resumes/${resumeId}/pdf`, {
    params, responseType: 'blob', timeout: 90000
  }),
  exportPayload: (token) => client.get(`/public/exports/${token}`)
}

export const reportApi = {
  generate: (resumeId) => client.post(`/resumes/${resumeId}/report`),
  latest: (resumeId) => client.get(`/resumes/${resumeId}/report/latest`)
}

export const shareApi = {
  create: (resumeId, payload) => client.post(`/resumes/${resumeId}/shares`, payload),
  list: (resumeId) => client.get(`/resumes/${resumeId}/shares`),
  revoke: (shareId) => client.delete(`/shares/${shareId}`),
  publicGet: (token) => client.get(`/public/shares/${token}`),
  unlock: (token, password) => client.post(`/public/shares/${token}/unlock`, { password })
}

export const feedbackApi = {
  submit: (payload) => client.post('/feedback', payload)
}

export const statsApi = {
  /** 公开统计：累计访问次数 + 最近 5 分钟在线人数。 */
  get: () => client.get('/public/stats'),
  visit: (visitorId) => client.post('/public/stats/visit', { visitorId }),
  ping: (visitorId) => client.post('/public/stats/ping', { visitorId })
}

export const siteApi = {
  /** 前台布局要用的站点配置（目前只有内容宽度），无需鉴权。 */
  config: () => client.get('/public/site-config')
}

export const adminApi = {
  stats: () => client.get('/admin/stats'),
  system: () => client.get('/admin/system'),
  users: () => client.get('/admin/users'),
  userResumes: (id) => client.get(`/admin/users/${id}/resumes`),
  userResume: (id) => client.get(`/admin/resumes/${id}`),
  onlineUsers: () => client.get('/admin/users/online'),
  updateUserStatus: (id, status) => client.put(`/admin/users/${id}/status`, { status }),
  resetUserPassword: (id) => client.put(`/admin/users/${id}/password`),
  templates: () => client.get('/admin/templates'),
  updateTemplateStatus: (code, status) => client.put(`/admin/templates/${code}/status`, { status }),
  updateTemplateMeta: (code, payload) => client.put(`/admin/templates/${code}/meta`, payload),
  exportTemplates: () => client.get('/admin/templates/export', { responseType: 'blob' }),
  feedback: () => client.get('/admin/feedback'),
  updateFeedback: (id, payload) => client.put(`/admin/feedback/${id}`, payload),
  sample: () => client.get('/admin/sample'),
  setSample: (id, sample) => client.put(`/admin/resumes/${id}/sample`, { sample }),
  authSettings: () => client.get('/admin/settings'),
  updateAuthSettings: (payload) => client.put('/admin/settings', payload),
  siteConfig: () => client.get('/admin/site-config'),
  updateSiteConfig: (payload) => client.put('/admin/site-config', payload)
}
