import client from './client'

export const authApi = {
  login: (payload) => client.post('/auth/login', payload),
  register: (payload) => client.post('/auth/register', payload),
  logout: () => client.post('/auth/logout'),
  me: () => client.get('/auth/me'),
  updateProfile: (payload) => client.put('/auth/profile', payload),
  changePassword: (payload) => client.put('/auth/password', payload)
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

export const adminApi = {
  stats: () => client.get('/admin/stats'),
  users: () => client.get('/admin/users'),
  updateUserStatus: (id, status) => client.put(`/admin/users/${id}/status`, { status }),
  templates: () => client.get('/admin/templates'),
  updateTemplateStatus: (code, status) => client.put(`/admin/templates/${code}/status`, { status }),
  exportTemplates: () => client.get('/admin/templates/export', { responseType: 'blob' }),
  feedback: () => client.get('/admin/feedback'),
  updateFeedback: (id, payload) => client.put(`/admin/feedback/${id}`, payload),
  sample: () => client.get('/admin/sample'),
  setSample: (id, sample) => client.put(`/admin/resumes/${id}/sample`, { sample })
}
