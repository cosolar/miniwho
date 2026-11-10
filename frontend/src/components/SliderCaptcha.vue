<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Refresh, Close, CircleCheckFilled, ArrowRight } from '@element-plus/icons-vue'
import { authApi } from '../api'
import { notifyError } from '../api/client'

/**
 * 滑块验证：底图与拼图都由服务端现画，缺口位置不下发；
 * 拖动时的每一次指针采样都记成轨迹（含时间戳）一并提交，由服务端判断这是人还是脚本。
 * 校验通过后拿到一张一次性票据，登录时带上即可。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const stageRef = ref(null)
const loading = ref(false)
const checking = ref(false)
const passed = ref(false)
/** idle | dragging | failed */
const phase = ref('idle')
const hint = ref('按住滑块，把拼图拖到缺口处')

const challenge = reactive({
  id: '',
  background: '',
  template: '',
  bgWidth: 600,
  bgHeight: 360,
  pieceWidth: 60
})

const stageWidth = ref(0)
const offset = ref(0)

/** 舞台按容器宽度等比缩放，所有提交的坐标都用「渲染后的 CSS 像素」，比值才对得上。 */
const ratio = computed(() => (challenge.bgWidth > 0 ? stageWidth.value / challenge.bgWidth : 1))
const pieceRenderedWidth = computed(() => challenge.pieceWidth * ratio.value)
const maxOffset = computed(() => Math.max(0, stageWidth.value - pieceRenderedWidth.value))
const pieceStyle = computed(() => ({
  width: challenge.pieceWidth ? `${challenge.pieceWidth * ratio.value}px` : 'auto',
  transform: `translate3d(${offset.value}px, 0, 0)`
}))
const trackFillStyle = computed(() => ({
  width: `${Math.min(offset.value + 24, stageWidth.value)}px`
}))

/** 一次拖动的现场：起点、采样点、开始时刻。 */
let drag = null

function measure() {
  const width = stageRef.value?.clientWidth || 0
  if (width > 0) stageWidth.value = width
}

async function load() {
  loading.value = true
  passed.value = false
  phase.value = 'idle'
  offset.value = 0
  hint.value = '按住滑块，把拼图拖到缺口处'
  try {
    const data = await authApi.captcha()
    challenge.id = data.id
    challenge.background = data.backgroundImage
    challenge.template = data.templateImage
    challenge.bgWidth = data.backgroundImageWidth || 600
    challenge.bgHeight = data.backgroundImageHeight || 360
    challenge.pieceWidth = data.templateImageWidth || 60
    await nextTick()
    measure()
  } catch (error) {
    challenge.id = ''
    hint.value = '验证码加载失败，点右上角重试'
    notifyError(error, '验证码加载失败')
  } finally {
    loading.value = false
  }
}

/* ---------------- 拖动 ---------------- */

function onPointerDown(event) {
  if (loading.value || checking.value || passed.value || !challenge.id) return
  if (event.button !== undefined && event.button !== 0) return
  event.preventDefault()
  measure()
  offset.value = 0
  phase.value = 'dragging'
  hint.value = '松手完成验证'
  drag = {
    startX: event.clientX,
    startY: event.clientY,
    startTime: performance.now(),
    // 起点必须是原点：服务端用「最后一点 − 第一点」算位移，也用它判断按下时有没有乱跳。
    points: [{ x: 0, y: 0, t: 0 }],
    lastX: 0
  }
  window.addEventListener('pointermove', onPointerMove, { passive: false })
  window.addEventListener('pointerup', onPointerUp)
  window.addEventListener('pointercancel', onPointerUp)
}

function onPointerMove(event) {
  if (!drag) return
  event.preventDefault()
  const x = clamp(event.clientX - drag.startX, 0, maxOffset.value)
  const y = event.clientY - drag.startY
  const t = performance.now() - drag.startTime
  pushPoint(x, y, t)
  offset.value = x
}

/**
 * 采样点之间跨度太大会被服务端判成「瞬移」，所以一次事件跨了多格就补中间点，
 * 补的点按位移比例分摊时间，曲线形状不变、只是更细。
 */
function pushPoint(x, y, t) {
  const last = drag.points[drag.points.length - 1]
  const gap = x - last.x
  if (gap > 24) {
    const steps = Math.ceil(gap / 24)
    for (let i = 1; i < steps; i++) {
      const p = i / steps
      drag.points.push({
        x: round(last.x + gap * p),
        y: round(last.y + (y - last.y) * p),
        t: round(last.t + (t - last.t) * p)
      })
    }
  }
  drag.points.push({ x: round(x), y: round(y), t: round(t) })
  drag.lastX = x
}

async function onPointerUp() {
  if (!drag) return
  const session = drag
  drag = null
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)

  const stopTime = performance.now() - session.startTime
  // 按下就松手不算一次有效的滑动，直接复位，不用去打扰服务端。
  if (stopTime < 80 || session.points.length < 2) {
    phase.value = 'idle'
    offset.value = 0
    hint.value = '按住滑块，把拼图拖到缺口处'
    return
  }
  session.points.push({ x: round(offset.value), y: session.points[session.points.length - 1].y, t: round(stopTime) })
  await submit(session, stopTime)
}

async function submit(session, stopTime) {
  checking.value = true
  hint.value = '正在校验轨迹…'
  try {
    const data = await authApi.captchaCheck({
      id: challenge.id,
      data: {
        bgImageWidth: Math.round(stageWidth.value),
        bgImageHeight: Math.round(stageWidth.value * (challenge.bgHeight / challenge.bgWidth)),
        templateImageWidth: Math.round(pieceRenderedWidth.value),
        templateImageHeight: Math.round(stageWidth.value * (challenge.bgHeight / challenge.bgWidth)),
        startTime: 0,
        stopTime: Math.round(stopTime),
        trackList: session.points.map((point) => ({
          x: point.x,
          y: point.y,
          t: point.t,
          type: 'MOVE'
        }))
      }
    })
    passed.value = true
    phase.value = 'idle'
    hint.value = '验证通过'
    window.setTimeout(() => {
      emit('success', data.ticket)
      visible.value = false
    }, 520)
  } catch (error) {
    // 这张题在服务端已经核销掉了，无论怎么失败都得换一张。
    phase.value = 'failed'
    hint.value = error?.message || '验证未通过，请重新拖动滑块'
    offset.value = 0
    window.setTimeout(() => {
      if (visible.value) load()
    }, 1600)
  } finally {
    checking.value = false
  }
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function round(value) {
  return Math.round(value * 100) / 100
}

function close() {
  visible.value = false
}

watch(visible, (open) => {
  if (open) {
    nextTick(() => {
      measure()
      load()
    })
  } else {
    drag = null
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('pointerup', onPointerUp)
    window.removeEventListener('pointercancel', onPointerUp)
  }
}, { immediate: true })

watch(stageWidth, () => measure())

window.addEventListener('resize', measure)

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)
  window.removeEventListener('resize', measure)
})
</script>

<template>
  <el-dialog
    v-model="visible"
    width="404px"
    align-center
    :show-close="false"
    :close-on-click-modal="false"
    class="slider-captcha"
    append-to-body
  >
    <template #header>
      <div class="slider-captcha__head">
        <div>
          <p class="slider-captcha__title">安全验证</p>
          <p class="slider-captcha__sub">拖动滑块完成拼图</p>
        </div>
        <div class="slider-captcha__actions">
          <button type="button" class="icon-btn" title="换一张" :disabled="loading || checking" @click="load">
            <el-icon><Refresh /></el-icon>
          </button>
          <button type="button" class="icon-btn" title="关闭" @click="close">
            <el-icon><Close /></el-icon>
          </button>
        </div>
      </div>
    </template>

    <div class="slider-captcha__body">
      <div
        ref="stageRef"
        class="stage"
        :class="{ 'is-dragging': phase === 'dragging', 'is-passed': passed, 'is-failed': phase === 'failed' }"
        :style="{ aspectRatio: `${challenge.bgWidth} / ${challenge.bgHeight}` }"
      >
        <img v-if="challenge.background" class="stage__bg" :src="challenge.background" alt="" draggable="false" />
        <img
          v-if="challenge.template"
          class="stage__piece"
          :src="challenge.template"
          :style="pieceStyle"
          alt=""
          draggable="false"
        />
        <div v-if="loading" class="stage__veil">加载中</div>
        <transition name="fade">
          <div v-if="passed" class="stage__passed">
            <el-icon><CircleCheckFilled /></el-icon>
            <span>验证通过</span>
          </div>
        </transition>
      </div>

      <div class="track" :class="{ 'is-dragging': phase === 'dragging', 'is-passed': passed }">
        <div class="track__fill" :style="trackFillStyle" />
        <div
          class="track__handle"
          :style="{ transform: `translate3d(${offset}px, 0, 0)` }"
          @pointerdown="onPointerDown"
        >
          <el-icon><ArrowRight /></el-icon>
        </div>
        <span class="track__hint">{{ loading ? '正在生成题目…' : hint }}</span>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.slider-captcha__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.slider-captcha__title {
  margin: 0;
  color: var(--ink);
  font-family: var(--serif);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.slider-captcha__sub {
  margin: 4px 0 0;
  color: var(--faint);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.slider-captcha__actions {
  display: flex;
  gap: 4px;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.icon-btn:hover:not(:disabled) {
  background: var(--paper-deep);
  color: var(--ink);
}

.icon-btn:disabled {
  color: var(--faint);
  cursor: not-allowed;
}

.slider-captcha__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.stage {
  position: relative;
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  background: var(--paper-deep);
  user-select: none;
  touch-action: none;
}

.stage.is-failed {
  animation: shake 0.32s ease;
}

.stage__bg {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  pointer-events: none;
}

.stage__piece {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  pointer-events: none;
  filter: drop-shadow(0 2px 6px rgba(25, 34, 30, 0.35));
  will-change: transform;
}

.stage__veil {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(244, 241, 233, 0.82);
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.stage__passed {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(10, 125, 118, 0.86);
  color: #fff;
  font-size: 14px;
  letter-spacing: 0.1em;
}

.track {
  position: relative;
  height: 44px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper-deep);
  overflow: hidden;
}

.track__fill {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  background: var(--accent-soft);
  transition: width 0.04s linear;
}

.track.is-passed .track__fill {
  background: #d8ebe4;
}

.track__handle {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 7px;
  background: var(--paper);
  border: 1px solid var(--line-strong);
  color: var(--accent);
  cursor: grab;
  touch-action: none;
  box-shadow: var(--shadow-sm);
}

.track.is-dragging .track__handle {
  cursor: grabbing;
}

.track.is-passed .track__handle {
  color: var(--success);
}

.track__hint {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 0 52px;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.04em;
  pointer-events: none;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  75% { transform: translateX(4px); }
}
</style>
