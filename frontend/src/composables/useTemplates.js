import { computed, ref } from 'vue'
import { templateApi } from '../api'
import { TOKEN_KEY } from '../api/client'
import { TEMPLATE_FALLBACKS } from '../data/templatePresets'

/**
 * 模板目录：官方 + 站内已分享的 + 我自己导入的，工作台、预览页、看板与导出 PDF 共用同一份。
 * 三处必须解析到同一个配置，否则导入的模板会悄悄退回内置版式。
 */
export function useTemplates() {
  const catalog = ref(TEMPLATE_FALLBACKS)
  const mine = ref([])
  const detached = ref([])

  const mineCodes = computed(() => new Set(mine.value.map((item) => item.code)))
  const official = computed(() => catalog.value.filter((item) => item.source !== 'imported'))
  /** 模板中心的数据源：官方 + 站内已分享的，自己未分享的包不在这里。 */
  const library = computed(() => catalog.value.map((item) => ({ ...item, isMine: mineCodes.value.has(item.code) })))
  const community = computed(() => {
    const rows = []
    const seen = new Set()
    catalog.value
      .filter((item) => item.source === 'imported')
      .forEach((item) => {
        seen.add(item.code)
        rows.push({ ...item, isMine: mineCodes.value.has(item.code) })
      })
    mine.value.forEach((item) => {
      if (seen.has(item.code)) return
      seen.add(item.code)
      rows.push({ ...item, isMine: true })
    })
    return rows
  })
  const all = computed(() => [...official.value, ...community.value, ...detached.value])

  async function refresh() {
    try {
      const list = await templateApi.list()
      if (Array.isArray(list) && list.length) catalog.value = list
    } catch {
      catalog.value = TEMPLATE_FALLBACKS
    }
    // /api/templates/** 是公开路径，/mine 要登录；未登录时不发请求，免得触发全局 401 跳转。
    if (!localStorage.getItem(TOKEN_KEY)) {
      mine.value = []
      return all.value
    }
    try {
      const list = await templateApi.mine()
      mine.value = Array.isArray(list) ? list : []
    } catch {
      mine.value = []
    }
    return all.value
  }

  /**
   * 作者取消分享后，别人已经在用的简历仍要按原样渲染：目录里查不到时按编码单独取一次配置。
   * 取不到就交给页面的「模板不在你的列表里」提示处理。
   */
  async function ensure(code) {
    if (!code || all.value.some((item) => item.code === code)) return
    try {
      const detail = await templateApi.detail(code)
      if (detail && detail.code) detached.value = [...detached.value, detail]
    } catch {
      // 已下架或不存在：保持缺失状态，纸面按默认版式渲染。
    }
  }

  return { official, community, library, mine, all, refresh, ensure }
}
