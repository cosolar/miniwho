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
  paged: { type: Boolean, default: true },
  /**
   * width：只按容器宽度缩放，纸面铺满可用宽度（长简历向下滚）。
   * page：再压一道「一页 A4 的高度」，让整页同时进画面，适合在窄面板里看版式。
   */
  fitMode: { type: String, default: 'width' },
  /** fitMode = page 时容器可用于放一页的高度（px，已扣掉内边距）。 */
  fitHeight: { type: Number, default: 0 }
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

/** A4 高宽比：一页的高度折算成「纸面宽度 × 该比例」，多页简历也只按一页算，剩下的靠滚动。 */
const PAGE_RATIO = 297 / 210

function measure() {
  cancelAnimationFrame(raf)
  raf = requestAnimationFrame(() => {
    if (!wrap.value || !paper.value) return
    const el = paper.value.$el || paper.value
    baseWidth.value = el.offsetWidth || 794
    baseHeight.value = el.offsetHeight || 1123
    const available = wrap.value.clientWidth - props.padding
    let next = available / baseWidth.value
    if (props.fitMode === 'page' && props.fitHeight > 0) {
      next = Math.min(next, props.fitHeight / (baseWidth.value * PAGE_RATIO))
    }
    scale.value = Math.max(0.12, Math.min(props.maxScale, next))
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

// 切换适宽 / 整页、或面板被放大缩小时，缩放比例要跟着重算。
watch(() => [props.fitMode, props.fitHeight], () => measure())

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
