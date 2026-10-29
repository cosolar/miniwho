<script setup>
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { siteApi } from './api'
import { applyContentWidth, cachedContentWidth } from './utils/appearance'

// 先用上次记住的宽度，避免默认值闪一下；再向服务端校正。
// 内容宽度影响按容器测量的组件，所以必须在页面挂载前就写进 CSS 变量。
applyContentWidth(cachedContentWidth())

// 取不到就沿用缓存值或默认值，不为它阻塞首屏。
onMounted(async () => {
  try {
    const config = await siteApi.config()
    applyContentWidth(config?.contentWidth)
  } catch {
    // 离线或接口异常时保持当前宽度
  }
})
</script>

<template>
  <RouterView v-slot="{ Component }">
    <transition name="page" mode="out-in">
      <component :is="Component" />
    </transition>
  </RouterView>
</template>
