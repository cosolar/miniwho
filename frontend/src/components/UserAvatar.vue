<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { mediaUrl } from '../api/client'

/**
 * 账号头像位：有图放图，没图退回昵称首字母。
 *
 * <p>尺寸、圆角、底色都交给调用方的类名（顶栏 30px、个人中心 74px），这里只管图片本身；
 * 三个使用点各写一套 img 样式反而容易长歪。
 */
const props = defineProps({
  text: { type: String, default: '我' }
})

const auth = useAuthStore()
const broken = ref(false)
let retried = false

const src = computed(() => (auth.avatar?.present ? mediaUrl(auth.avatar.url) : ''))

function onError() {
  // 签名链接有效六小时，挂着页面超时后第一次加载失败先重取一次，再失败才认栽。
  if (retried) {
    broken.value = true
    return
  }
  retried = true
  auth.loadAvatar(true)
}

watch(() => auth.avatar?.url, () => {
  broken.value = false
  retried = false
})

onMounted(() => auth.loadAvatar())
</script>

<template>
  <span class="user-avatar">
    <img v-if="src && !broken" :src="src" alt="账号头像" @error="onError" />
    <template v-else>{{ props.text }}</template>
  </span>
</template>

<style scoped>
.user-avatar img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}
</style>
