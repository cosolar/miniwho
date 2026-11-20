<script setup>
defineProps({
  light: { type: Boolean, default: false },
  small: { type: Boolean, default: false }
})
</script>

<template>
  <router-link to="/" class="brand" :class="{ 'brand--light': light, 'brand--small': small }" aria-label="极简简历首页">
    <span class="brand__mark">
      <img src="/logo-mark.png" alt="" width="160" height="162" />
    </span>
    <span class="brand__text">
      <strong>极简简历</strong>
      <small><span v-for="ch in 'MINIWHO'" :key="ch">{{ ch }}</span></small>
    </span>
  </router-link>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ink);
  flex-shrink: 0;
}

.brand__mark {
  width: 36px;
  height: 36px;
  overflow: hidden;
  border-radius: 50%;
  transition: transform 0.25s ease;
}

.brand:hover .brand__mark {
  transform: rotate(-3deg) translateY(-1px);
}

.brand__mark img {
  display: block;
  width: 100%;
  height: 100%;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1;
}

.brand__text strong {
  font-family: var(--serif);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.04em;
  /* 抵消末字符后面的字距，让盒子宽度等于「历」的可见右边缘 */
  margin-right: -0.04em;
}

/* 每个字母一个 span，两端对齐撑满标题宽度，跟随标题字号自适应。 */
.brand__text small {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  /* 两端各内缩一点，比标题可见宽度略窄 */
  padding: 0 2px;
  color: var(--muted);
  font-family: var(--mono);
  font-size: 8px;
}

.brand--light {
  color: #f6f3ea;
}

/* 深色底上青绿徽标对比不足，提亮一档，白色字形不受影响。 */
.brand--light .brand__mark {
  filter: brightness(1.3);
}

.brand--light .brand__text small {
  color: rgba(246, 243, 234, 0.6);
}

.brand--small .brand__mark {
  width: 30px;
  height: 30px;
}

.brand--small .brand__text strong {
  font-size: 16px;
}
</style>
