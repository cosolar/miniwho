<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  visible: { type: Boolean, default: false },
  source: { type: Object, default: null },
  shape: { type: String, default: 'rect' }
})
const emit = defineEmits(['confirm', 'cancel'])

const OUT_LONG_EDGE = 600
const view = ref(null)
const box = reactive({ width: 0, height: 0 })
const transform = reactive({ scale: 1, offsetX: 0, offsetY: 0 })
const zoom = ref(1)
const image = ref(null)
let sourceUrl = ''

const aspect = computed(() => (props.shape === 'circle' ? 1 : 86 / 120))
const viewportHeight = computed(() => Math.round(box.width / aspect.value))

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
    title="裁剪头像"
    width="420px"
    append-to-body
    @close="emit('cancel')"
  >
    <div class="crop-body">
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
      <div class="crop-tools">
        <span>拖动图片调整位置，滚轮或滑块缩放</span>
        <el-slider v-model="zoom" :min="1" :max="3" :step="0.05" @input="applyZoom" />
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" @click="confirm">使用这张</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.crop-body {
  display: grid;
  gap: 14px;
}

.crop-view {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 10px;
  background:
    linear-gradient(45deg, #f2f4f2 25%, transparent 25%) 0 0 / 16px 16px,
    #fafbf9;
  cursor: grab;
  touch-action: none;
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

.crop-tools {
  display: grid;
  gap: 6px;
  color: var(--muted);
  font-size: 12px;
}
</style>
