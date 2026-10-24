<script setup>
import { computed } from 'vue'

const props = defineProps({
  score: { type: Number, default: 0 },
  size: { type: Number, default: 96 },
  grade: { type: String, default: '' },
  label: { type: String, default: '简历得分' }
})

const safeScore = computed(() => Math.max(0, Math.min(100, Number(props.score) || 0)))
const color = computed(() => {
  if (safeScore.value >= 90) return '#0a7d76'
  if (safeScore.value >= 75) return '#5c7f5e'
  if (safeScore.value >= 60) return '#b08542'
  return '#b05f3c'
})
const ringStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: `conic-gradient(${color.value} ${safeScore.value * 3.6}deg, #e8e3d8 0deg)`
}))
</script>

<template>
  <div class="score-ring" :style="ringStyle" :aria-label="`${label} ${safeScore} 分`">
    <div class="score-ring__inner">
      <strong :style="{ color }">{{ safeScore }}</strong>
      <span>{{ grade || label }}</span>
    </div>
  </div>
</template>

<style scoped>
.score-ring {
  position: relative;
  display: grid;
  place-items: center;
  border-radius: 50%;
  flex-shrink: 0;
}

.score-ring__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: calc(100% - 10px);
  height: calc(100% - 10px);
  border-radius: 50%;
  background: var(--paper);
}

.score-ring strong {
  font-family: var(--serif);
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
}

.score-ring span {
  margin-top: 5px;
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.08em;
}
</style>
