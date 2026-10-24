import { computed, ref } from 'vue'
import { sampleApi } from '../api'
import { sampleContent as bundledSample } from '../data/sampleResume'
import { TEMPLATE_FALLBACKS } from '../data/templatePresets'

const content = ref(bundledSample)
const templateCode = ref(TEMPLATE_FALLBACKS[0].code)
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
        if (data.templateCode) templateCode.value = data.templateCode
      })
      .catch(() => {})
      .finally(() => { inflight = null })
  }

  const template = computed(
    () => TEMPLATE_FALLBACKS.find((tpl) => tpl.code === templateCode.value) || TEMPLATE_FALLBACKS[0]
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
