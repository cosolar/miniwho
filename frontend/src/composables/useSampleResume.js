import { computed, ref } from 'vue'
import { sampleApi, templateApi } from '../api'
import { sampleContent as bundledSample } from '../data/sampleResume'
import { TEMPLATE_FALLBACKS } from '../data/templatePresets'

const content = ref(bundledSample)
const templateCode = ref(TEMPLATE_FALLBACKS[0].code)
/** 示例用了导入模板时的真实配置（含净化 CSS + scopeClass），取不到就回退官方预设。 */
const customTemplate = ref(null)
let inflight

/** 示例照片不占后端头像通道（那要签名链接，十几张缩略图会反复过期），按性别挑打包的静态照。 */
const SAMPLE_PHOTOS = { 男: '/demo/avatar-male.jpg', 女: '/demo/avatar-female.jpg' }

/**
 * 全站唯一的示例简历：管理员在工作台把任意一份简历设为示例后，首页与模板中心的预览都会跟着变。
 * 初值取打包的默认示例，接口成功后覆盖——后端没起、或这套部署没设示例时，首页那张纸不能是空的。
 * 模块级单例，一次页面加载只发一个请求，十几张缩略图共用同一份内容。
 */
export function useSampleResume() {
  if (!inflight) {
    inflight = sampleApi.resume()
      .then((data) => {
        if (!Array.isArray(data?.content?.sections)) return
        content.value = data.content
        if (!data.templateCode) return
        templateCode.value = data.templateCode
        // 打包预设只含官方 12 套；示例换成导入模板时补拉一次详情，
        // ResumePaper 会用 config 里的 scopeClass + styleCss 注入净化 CSS。
        // 模板私有或已下架时详情接口 404，保持官方回退样式（作者本人可见）。
        if (TEMPLATE_FALLBACKS.some((tpl) => tpl.code === data.templateCode)) return
        templateApi.detail(data.templateCode)
          .then((detail) => { if (detail?.code === data.templateCode) customTemplate.value = detail })
          .catch(() => {})
      })
      .catch(() => {})
      .finally(() => { inflight = null })
  }

  const template = computed(() =>
    TEMPLATE_FALLBACKS.find((tpl) => tpl.code === templateCode.value)
    || customTemplate.value
    || TEMPLATE_FALLBACKS[0]
  )
  const basicFields = computed(() => {
    const basic = content.value.sections?.find((section) => section.type === 'basic_info')
    return basic?.items?.[0]?.fields || {}
  })
  const displayName = computed(() => basicFields.value.name || '')
  const avatar = computed(() => {
    const url = SAMPLE_PHOTOS[basicFields.value.gender]
    return url ? { present: true, shape: 'rect', url } : null
  })

  return { content, template, displayName, avatar }
}
