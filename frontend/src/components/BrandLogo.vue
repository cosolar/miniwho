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
      <small>MINIWHO</small>
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
}

.brand__text small {
  margin-top: 4px;
  color: var(--muted);
  font-family: var(--mono);
  font-size: 8px;
  letter-spacing: 0.3em;
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
