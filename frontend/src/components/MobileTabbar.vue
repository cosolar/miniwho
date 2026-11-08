<script setup>
import { Collection, Document, Grid, User } from '@element-plus/icons-vue'
</script>

<template>
  <nav class="app-tabbar no-print" aria-label="主要导航">
    <router-link to="/dashboard" class="app-tab" active-class="is-active">
      <el-icon><Document /></el-icon>
      <span>简历</span>
    </router-link>
    <router-link to="/templates" class="app-tab" active-class="is-active">
      <el-icon><Grid /></el-icon>
      <span>模板</span>
    </router-link>
    <router-link to="/my-templates" class="app-tab" active-class="is-active">
      <el-icon><Collection /></el-icon>
      <span>我的模板</span>
    </router-link>
    <router-link to="/profile" class="app-tab" active-class="is-active">
      <el-icon><User /></el-icon>
      <span>我的</span>
    </router-link>
  </nav>
</template>

<style scoped>
.app-tabbar {
  display: none;
}

@media (max-width: 700px) {
  .app-tabbar {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 85;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    padding: 5px 6px calc(5px + env(safe-area-inset-bottom, 0px));
    border-top: 1px solid var(--line);
    background: rgba(251, 250, 246, 0.96);
    backdrop-filter: blur(18px) saturate(1.2);
  }

  .app-tab {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    padding: 5px 0 3px;
    color: var(--muted);
    font-size: 10px;
    line-height: 1;
    transition: color 0.2s ease;
  }

  .app-tab .el-icon {
    font-size: 19px;
  }

  .app-tab.is-active {
    color: var(--accent);
  }
}
</style>
