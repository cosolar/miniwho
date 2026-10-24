<script setup>
import { onMounted, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '在这里输入内容。' },
  minHeight: { type: Number, default: 120 }
})

const emit = defineEmits(['update:modelValue'])
const editor = ref(null)
const active = ref({})

function sync(value) {
  if (!editor.value) return
  if (document.activeElement === editor.value) return
  if (editor.value.innerHTML !== value) editor.value.innerHTML = value || ''
}

watch(() => props.modelValue, sync)
// 打开编辑器时把已有内容回显进来；watch 只在后续变更时触发，首次挂载必须手动同步一次。
onMounted(() => sync(props.modelValue))

function emitValue() {
  if (!editor.value) return
  emit('update:modelValue', editor.value.innerHTML)
}

function command(name, value = null) {
  editor.value?.focus()
  document.execCommand(name, false, value)
  emitValue()
  updateActive()
}

function updateActive() {
  active.value = {
    bold: document.queryCommandState('bold'),
    italic: document.queryCommandState('italic'),
    underline: document.queryCommandState('underline'),
    unordered: document.queryCommandState('insertUnorderedList'),
    ordered: document.queryCommandState('insertOrderedList')
  }
}

function handlePaste(event) {
  event.preventDefault()
  const text = (event.clipboardData || window.clipboardData).getData('text/plain')
  document.execCommand('insertText', false, text)
  emitValue()
}

function handleKeydown(event) {
  if (event.key === 'Tab') {
    event.preventDefault()
    document.execCommand('insertText', false, '  ')
  }
}
</script>

<template>
  <div class="rich-editor" :style="{ '--editor-min-height': `${minHeight}px` }">
    <div class="rich-toolbar" role="toolbar" aria-label="文本格式">
      <button type="button" title="加粗" :class="{ active: active.bold }" @mousedown.prevent @click="command('bold')"><b>B</b></button>
      <button type="button" title="斜体" :class="{ active: active.italic }" @mousedown.prevent @click="command('italic')"><i>I</i></button>
      <button type="button" title="下划线" :class="{ active: active.underline }" @mousedown.prevent @click="command('underline')"><u>U</u></button>
      <span class="toolbar-sep" />
      <button type="button" title="无序列表" :class="{ active: active.unordered }" @mousedown.prevent @click="command('insertUnorderedList')">• 列表</button>
      <button type="button" title="有序列表" :class="{ active: active.ordered }" @mousedown.prevent @click="command('insertOrderedList')">1. 列表</button>
      <span class="toolbar-sep" />
      <button type="button" title="清除格式" @mousedown.prevent @click="command('removeFormat')">清格式</button>
      <span class="toolbar-tip">建议用分点描述，保存后自动过滤不安全标签</span>
    </div>
    <div
      ref="editor"
      class="rich-body"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      :data-placeholder="placeholder"
      @input="emitValue"
      @paste="handlePaste"
      @keydown="handleKeydown"
      @keyup="updateActive"
      @mouseup="updateActive"
      @blur="emitValue"
      @focus="updateActive"
    />
  </div>
</template>

<style scoped>
.rich-editor {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fffefb;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.rich-editor:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(10, 125, 118, 0.08);
}

.rich-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 3px;
  padding: 5px 8px;
  border-bottom: 1px solid var(--line);
  background: var(--paper-deep);
}

.rich-toolbar button {
  height: 26px;
  min-width: 28px;
  padding: 0 7px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--ink-soft);
  font-family: var(--sans);
  font-size: 12px;
  cursor: pointer;
}

.rich-toolbar button:hover {
  border-color: var(--line);
  background: var(--paper);
}

.rich-toolbar button.active {
  border-color: rgba(10, 125, 118, 0.2);
  background: var(--accent-soft);
  color: var(--accent);
}

.toolbar-sep {
  width: 1px;
  height: 14px;
  margin: 0 5px;
  background: var(--line);
}

.toolbar-tip {
  margin-left: auto;
  color: var(--faint);
  font-size: 10px;
}

.rich-body {
  min-height: var(--editor-min-height);
  max-height: 380px;
  overflow-y: auto;
  padding: 10px 12px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.75;
  outline: none;
}

.rich-body:empty::before {
  content: attr(data-placeholder);
  color: var(--faint);
  pointer-events: none;
}

.rich-body :deep(ul),
.rich-body :deep(ol) {
  margin: 3px 0;
  padding-left: 1.4em;
}

.rich-body :deep(li) {
  margin: 1px 0;
}

.rich-body :deep(p) {
  margin: 0 0 4px;
}

@media (max-width: 640px) {
  .toolbar-tip {
    display: none;
  }
}
</style>
