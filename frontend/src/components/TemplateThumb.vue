<script setup>
import PaperFrame from './PaperFrame.vue'

defineProps({
  content: { type: Object, required: true },
  template: { type: Object, required: true },
  avatar: { type: Object, default: null },
  selected: { type: Boolean, default: false }
})
</script>

<template>
  <div class="template-thumb" :class="{ 'is-selected': selected }">
    <div class="template-thumb__frame">
      <PaperFrame
        :content="content"
        :template="template"
        :avatar="avatar"
        :paged="false"
        :style-data="{ showBrandFooter: false, fontSizeStep: -1 }"
      />
    </div>
    <div class="template-thumb__shade">
      <span class="template-thumb__badge">预览版式</span>
    </div>
  </div>
</template>

<style scoped>
.template-thumb {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #f1ede3;
  /* 卡片缩略图常放在 <button> 里，按钮的 text-align: center 会一路继承进纸面，把正文居中排歪。 */
  text-align: left;
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.35s ease, border-color 0.25s ease;
}

.template-thumb__frame {
  pointer-events: none;
  padding: 12px 12px 0;
  max-height: 380px;
  overflow: hidden;
}

.template-thumb__shade {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 16px;
  background: linear-gradient(to top, rgba(25, 34, 30, 0.28), transparent 32%);
  opacity: 0;
  transition: opacity 0.25s ease;
}

.template-thumb__badge {
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 254, 251, 0.94);
  color: var(--ink);
  font-size: 11px;
  letter-spacing: 0.04em;
  box-shadow: var(--shadow-sm);
}

.template-thumb.is-selected {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent);
}

.template-thumb:hover {
  transform: translateY(-5px);
  border-color: var(--line-strong);
  box-shadow: var(--shadow);
}

.template-thumb:hover .template-thumb__shade,
.template-thumb.is-selected .template-thumb__shade {
  opacity: 1;
}

@media (max-width: 760px) {
  .template-thumb__frame {
    max-height: 340px;
  }
}
</style>
