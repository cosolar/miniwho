<script setup>
import SiteHeader from '../components/SiteHeader.vue'
import SiteFooter from '../components/SiteFooter.vue'
</script>

<template>
  <div class="public-layout">
    <SiteHeader />
    <main>
      <router-view />
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.public-layout {
  min-height: 100vh;
  background:
    radial-gradient(1200px 600px at 85% -10%, rgba(194, 160, 100, 0.12), transparent 60%),
    radial-gradient(900px 500px at -10% 22%, rgba(10, 125, 118, 0.08), transparent 60%),
    var(--canvas);
}
</style>
