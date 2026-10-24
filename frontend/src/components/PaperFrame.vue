<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ResumePaper from './ResumePaper.vue'

const props = defineProps({
  content: { type: Object, default: () => ({ sections: [] }) },
  template: { type: Object, default: null },
  styleData: { type: Object, default: () => ({}) },
  avatar: { type: Object, default: null },
  maxScale: { type: Number, default: 1 },
  padding: { type: Number, default: 0 },
  paged: { type: Boolean, default: true }
})

const wrap = ref(null)
const paper = ref(null)
const emit = defineEmits(['pages'])
const scale = ref(1)
const offsetX = ref(0)
const baseWidth = ref(794)
const baseHeight = ref(1123)
let resizeObserver
let paperObserver
let raf = 0

function measure() {
  cancelAnimationFrame(raf)
  raf = requestAnimationFrame(() => {
    if (!wrap.value || !paper.value) return
    const el = paper.value.$el || paper.value
    baseWidth.value = el.offsetWidth || 794
    baseHeight.value = el.offsetHeight || 1123
    const available = wrap.value.clientWidth - props.padding
    const next = Math.min(props.maxScale, available / baseWidth.value)
    scale.value = Math.max(0.12, next)
    offsetX.value = Math.max(0, (wrap.value.clientWidth - baseWidth.value * scale.value) / 2)
  })
}

function observePaper() {
  if (paperObserver) paperObserver.disconnect()
  const el = paper.value?.$el || paper.value
  if (!el) return
  paperObserver = new ResizeObserver(measure)
  paperObserver.observe(el)
}

onMounted(async () => {
  await nextTick()
  resizeObserver = new ResizeObserver(measure)
  if (wrap.value) resizeObserver.observe(wrap.value)
  observePaper()
  measure()
})

watch(() => [props.content, props.template, props.styleData, props.avatar], async () => {
  await nextTick()
  observePaper()
  measure()
}, { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  paperObserver?.disconnect()
  cancelAnimationFrame(raf)
})

defineExpose({ measure })
</script>

<template>
  <div ref="wrap" class="paper-scaler">
    <div
      class="paper-scaler__inner"
      :style="{ height: `${baseHeight * scale}px` }"
    >
      <div
        class="paper-scaler__paper"
        :style="{ transform: `scale(${scale})`, left: `${offsetX}px` }"
      >
        <ResumePaper
          ref="paper"
          :content="content"
          :template="template"
          :style-data="styleData"
          :avatar="avatar"
          :paged="paged"
          @pages="(count) => emit('pages', count)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.paper-scaler {
  width: 100%;
  overflow: hidden;
}

.paper-scaler__inner {
  position: relative;
  width: 100%;
  transition: height 0.2s ease;
}

.paper-scaler__paper {
  position: absolute;
  top: 0;
  transform-origin: top left;
  box-shadow: var(--shadow);
}

@media (max-width: 900px) {
  .paper-scaler__paper {
    box-shadow: 0 8px 30px -18px rgba(24, 38, 31, 0.3);
  }
}
</style>
