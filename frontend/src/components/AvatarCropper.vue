<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ZoomIn, ZoomOut } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  source: { type: Object, default: null },
  shape: { type: String, default: 'rect' }
})
const emit = defineEmits(['confirm', 'cancel'])

const OUT_LONG_EDGE = 600
/** 右侧预览框的宽度：按裁切框的宽高比推出高度，所见即所得。 */
const THUMB_WIDTH = 84
const view = ref(null)
const box = reactive({ width: 0, height: 0 })
const transform = reactive({ scale: 1, offsetX: 0, offsetY: 0 })
const zoom = ref(1)
const image = ref(null)
let sourceUrl = ''

const aspect = computed(() => (props.shape === 'circle' ? 1 : 86 / 120))
const viewportHeight = computed(() => Math.round(box.width / aspect.value))
const zoomPercent = computed(() => `${Math.round(zoom.value * 100)}%`)
const thumbStyle = computed(() => {
  if (props.shape === 'circle') return { width: `${THUMB_WIDTH}px`, height: `${THUMB_WIDTH}px`, borderRadius: '50%' }
  return { width: `${THUMB_WIDTH}px`, height: `${Math.round(THUMB_WIDTH / aspect.value)}px`, borderRadius: '8px' }
})

function measure() {
  if (!view.value) return
  box.width = Math.round(view.value.clientWidth)
}

function baseScale() {
  if (!image.value || !box.width) return 1
  const height = viewportHeight.value
  return Math.max(box.width / image.value.width, height / image.value.height)
}

function applyZoom(next) {
  zoom.value = Math.min(3, Math.max(1, next))
  recenter()
}

function recenter() {
  if (!image.value || !box.width) return
  const scale = baseScale() * zoom.value
  const height = viewportHeight.value
  transform.scale = scale
  transform.offsetX = (box.width - image.value.width * scale) / 2
  transform.offsetY = (height - image.value.height * scale) / 2
  clamp()
}

function clamp() {
  if (!image.value) return
  const height = viewportHeight.value
  const scaledWidth = image.value.width * transform.scale
  const scaledHeight = image.value.height * transform.scale
  const minX = box.width - scaledWidth
  const minY = height - scaledHeight
  transform.offsetX = Math.min(0, Math.max(minX, transform.offsetX))
  transform.offsetY = Math.min(0, Math.max(minY, transform.offsetY))
}

let dragStart = null

function onPointerDown(event) {
  event.currentTarget.setPointerCapture?.(event.pointerId)
  dragStart = { x: event.clientX, y: event.clientY, offsetX: transform.offsetX, offsetY: transform.offsetY }
}

function onPointerMove(event) {
  if (!dragStart) return
  transform.offsetX = dragStart.offsetX + (event.clientX - dragStart.x)
  transform.offsetY = dragStart.offsetY + (event.clientY - dragStart.y)
  clamp()
}

function onPointerUp() {
  dragStart = null
}

function onWheel(event) {
  event.preventDefault()
  applyZoom(zoom.value + (event.deltaY > 0 ? -0.1 : 0.1))
}

const previewStyle = computed(() => ({
  width: `${image.value ? image.value.width * transform.scale : 0}px`,
  height: `${image.value ? image.value.height * transform.scale : 0}px`,
  transform: `translate(${transform.offsetX}px, ${transform.offsetY}px)`
}))

/** 预览框里的小图：把主舞台的位移和缩放按预览框宽度等比缩一份。 */
const thumbImageStyle = computed(() => {
  if (!image.value || !box.width) return {}
  const k = THUMB_WIDTH / box.width
  return {
    width: `${image.value.width * transform.scale * k}px`,
    height: `${image.value.height * transform.scale * k}px`,
    transform: `translate(${transform.offsetX * k}px, ${transform.offsetY * k}px)`
  }
})

function loadSource(file) {
  return new Promise((resolve, reject) => {
    if (sourceUrl) URL.revokeObjectURL(sourceUrl)
    const url = URL.createObjectURL(file)
    sourceUrl = url
    const img = new Image()
    img.onload = () => {
      image.value = img
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      sourceUrl = ''
      reject(new Error('图片解码失败'))
    }
    img.src = url
  })
}

function crop() {
  if (!image.value || !box.width) return null
  const outHeight = OUT_LONG_EDGE
  const outWidth = Math.round(outHeight * aspect.value)
  const canvas = document.createElement('canvas')
  canvas.width = outWidth
  canvas.height = outHeight
  const ctx = canvas.getContext('2d')
  ctx.imageSmoothingQuality = 'high'
  const srcX = -transform.offsetX / transform.scale
  const srcY = -transform.offsetY / transform.scale
  const srcW = box.width / transform.scale
  const srcH = viewportHeight.value / transform.scale
  ctx.drawImage(image.value, srcX, srcY, srcW, srcH, 0, 0, outWidth, outHeight)
  return canvas
}

function confirm() {
  const canvas = crop()
  if (!canvas) {
    ElMessage.warning('图片还没加载好')
    return
  }
  canvas.toBlob((blob) => {
    if (!blob) {
      ElMessage.error('裁剪失败，请重试')
      return
    }
    emit('confirm', blob)
  }, 'image/jpeg', 0.92)
}

watch(() => [props.visible, props.source], async ([next]) => {
  if (!next || !props.source) return
  zoom.value = 1
  try {
    await loadSource(props.source)
  } catch (error) {
    image.value = null
    ElMessage.error(error?.message || '图片读取失败')
    emit('cancel')
    return
  }
  await nextTick()
  measure()
  recenter()
})

watch(() => props.shape, () => {
  if (props.visible) recenter()
})

onBeforeUnmount(() => {
  if (sourceUrl) URL.revokeObjectURL(sourceUrl)
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    class="crop-dialog"
    width="460px"
    align-center
    append-to-body
    @close="emit('cancel')"
  >
    <template #header>
      <div class="crop-head">
        <p class="crop-eyebrow">简历照片</p>
        <h3 class="crop-title">裁剪头像</h3>
      </div>
    </template>

    <div class="crop-layout">
      <div
        ref="view"
        class="crop-view"
        :class="{ 'crop-view--circle': shape === 'circle' }"
        :style="{ height: `${viewportHeight}px` }"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @wheel="onWheel"
      >
        <img
          v-if="image"
          class="crop-image"
          :src="image.src"
          :style="previewStyle"
          alt="待裁剪头像"
          draggable="false"
        />
      </div>

      <aside class="crop-rail">
        <p class="crop-rail__label">预览</p>
        <div class="crop-thumb" :style="thumbStyle">
          <img v-if="image" :src="image.src" :style="thumbImageStyle" alt="" draggable="false" />
        </div>
        <p class="crop-rail__hint">拖动调整位置<br />滚轮或滑块缩放</p>
      </aside>
    </div>

    <div class="crop-zoom">
      <el-icon class="crop-zoom__icon"><ZoomOut /></el-icon>
      <el-slider
        v-model="zoom"
        class="crop-zoom__slider"
        :min="1"
        :max="3"
        :step="0.05"
        :show-tooltip="false"
        @input="applyZoom"
      />
      <el-icon class="crop-zoom__icon"><ZoomIn /></el-icon>
      <span class="crop-zoom__value">{{ zoomPercent }}</span>
    </div>

    <template #footer>
      <div class="crop-foot">
        <el-button text @click="emit('cancel')">取消</el-button>
        <el-button type="primary" @click="confirm">使用这张</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.crop-dialog :deep(.el-dialog__header) {
  padding: 22px 26px 0;
}

.crop-dialog :deep(.el-dialog__body) {
  padding: 16px 26px 0;
}

.crop-dialog :deep(.el-dialog__footer) {
  padding: 0;
}

.crop-head {
  display: grid;
  gap: 4px;
}

.crop-eyebrow {
  margin: 0;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.crop-title {
  margin: 0;
  color: var(--ink);
  font-family: var(--serif);
  font-size: 21px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.crop-layout {
  display: grid;
  grid-template-columns: minmax(0, 300px) minmax(84px, 1fr);
  gap: 18px;
  align-items: start;
}

.crop-view {
  position: relative;
  width: 100%;
  max-width: 300px;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  background:
    linear-gradient(45deg, #f2f4f2 25%, transparent 25%) 0 0 / 16px 16px,
    linear-gradient(-45deg, #f2f4f2 25%, transparent 25%) 0 8px / 16px 16px,
    #fafbf9;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.65),
    0 1px 2px rgba(31, 38, 32, 0.05),
    0 14px 34px -26px rgba(31, 38, 32, 0.4);
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.crop-view:active {
  cursor: grabbing;
}

.crop-view--circle {
  border-radius: 50%;
}

.crop-image {
  position: absolute;
  top: 0;
  left: 0;
  max-width: none;
  user-select: none;
  pointer-events: none;
}

.crop-rail {
  display: grid;
  justify-items: start;
  gap: 8px;
  padding-top: 2px;
}

.crop-rail__label {
  margin: 0;
  color: var(--faint);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.crop-thumb {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--line);
  background: var(--paper-deep);
  box-shadow: 0 1px 2px rgba(31, 38, 32, 0.06);
}

.crop-thumb img {
  position: absolute;
  top: 0;
  left: 0;
  max-width: none;
  user-select: none;
  pointer-events: none;
}

.crop-rail__hint {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.7;
}

.crop-zoom {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--paper-deep);
}

.crop-zoom__icon {
  color: var(--muted);
  font-size: 16px;
}

.crop-zoom :deep(.el-slider__runway) {
  background: var(--line);
}

.crop-zoom :deep(.el-slider__bar) {
  background: var(--accent);
}

.crop-zoom__slider {
  flex: 1 1 auto;
  min-width: 0;
}

.crop-zoom__value {
  width: 40px;
  color: var(--muted);
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.crop-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 26px 20px;
  border-top: 1px solid var(--line);
}

.crop-foot :deep(.el-button) {
  min-width: 88px;
}

@media (max-width: 560px) {
  .crop-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .crop-rail {
    grid-auto-flow: column;
    align-items: center;
    justify-content: start;
    gap: 12px;
  }

  .crop-rail__hint {
    margin: 0;
  }
}
</style>
