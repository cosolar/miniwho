<script setup>
import MobileTabbar from '../components/MobileTabbar.vue'
import SiteHeader from '../components/SiteHeader.vue'
import SiteFooter from '../components/SiteFooter.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
</script>

<template>
  <div class="public-layout" :class="{ 'has-tabbar': auth.isLoggedIn }">
    <SiteHeader />
    <main>
      <router-view />
    </main>
    <SiteFooter />
    <MobileTabbar v-if="auth.isLoggedIn" />
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

@media (max-width: 700px) {
  .public-layout.has-tabbar {
    padding-bottom: calc(62px + env(safe-area-inset-bottom, 0px));
  }
}
</style>
