<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ResumePaper from '../components/ResumePaper.vue'
import { pdfApi } from '../api'

const route = useRoute()
const token = String(route.params.token || '')

const payload = ref(null)
const failed = ref('')
const ready = ref(false)

const template = computed(() => {
  if (!payload.value) return null
  return {
    code: payload.value.templateCode,
    name: payload.value.templateName || payload.value.templateCode,
    category: '',
    config: payload.value.templateConfig || {}
  }
})

async function load() {
  try {
    const data = await pdfApi.exportPayload(token)
    payload.value = data
    document.title = `${data.title || '简历'} · 极简简历`
    await nextTick()
    // 等图片与字体就绪后再交给无头浏览器打印，避免首屏空白页。
    await waitForImages()
    if (document.fonts?.ready) await document.fonts.ready
    ready.value = true
  } catch (error) {
    failed.value = error?.message || '导出链接无效或已过期'
  }
}

function waitForImages() {
  const images = Array.from(document.querySelectorAll('.resume-paper img'))
  if (!images.length) return Promise.resolve()
  return Promise.all(images.map((img) => (img.complete
    ? Promise.resolve()
    : new Promise((resolve) => {
        img.addEventListener('load', resolve, { once: true })
        img.addEventListener('error', resolve, { once: true })
      }))))
}

onMounted(load)
</script>

<template>
  <div class="export-page">
    <ResumePaper
      v-if="payload && ready"
      :content="payload.content"
      :template="template"
      :style-data="payload.style || {}"
      :avatar="payload.avatar"
    />
    <p v-else-if="failed" class="export-page__error">{{ failed }}</p>
  </div>
</template>

<style scoped>
.export-page {
  width: 100%;
  padding: 0;
  background: #fff;
}

.export-page__error {
  padding: 20mm;
  color: #8a938c;
  font-size: 11pt;
  text-align: center;
}

@media print {
  .export-page {
    width: 210mm;
  }
}
</style>
