<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowUp, ArrowDown, CopyDocument, Delete } from '@element-plus/icons-vue'
import FieldControl from './FieldControl.vue'
import { metaOf, createItem } from '../data/sectionCatalog'

const props = defineProps({
  draft: { type: Object, required: true },
  saving: { type: Boolean, default: false },
  embedded: { type: Boolean, default: false },
  resumeId: { type: [Number, String], default: null }
})

const emit = defineEmits(['save', 'cancel'])
const meta = computed(() => metaOf(props.draft.type))
const isSingle = computed(() => meta.value.single)
const maxItems = computed(() => meta.value.maxItems || 20)

function addItem() {
  if (!props.draft.items) props.draft.items = []
  if (props.draft.items.length >= maxItems.value) {
    ElMessage.warning(`最多添加 ${maxItems.value} 条内容`)
    return
  }
  props.draft.items.push(createItem(props.draft.type))
}

function copyItem(index) {
  if (props.draft.items.length >= maxItems.value) {
    ElMessage.warning(`最多添加 ${maxItems.value} 条内容`)
    return
  }
  const clone = JSON.parse(JSON.stringify(props.draft.items[index]))
  clone.id = createItem(props.draft.type).id
  props.draft.items.splice(index + 1, 0, clone)
}

function removeItem(index) {
  props.draft.items.splice(index, 1)
}

function move(index, delta) {
  const next = index + delta
  if (next < 0 || next >= props.draft.items.length) return
  const [item] = props.draft.items.splice(index, 1)
  props.draft.items.splice(next, 0, item)
}

function labelFor(index) {
  if (isSingle.value) return ''
  return `第 ${index + 1} 段`
}
</script>

<template>
  <div class="section-editor">
    <div v-if="!embedded" class="editor-head">
      <div>
        <div class="editor-title-row">
          <h3>{{ draft.title || meta.label }}</h3>
          <span class="tag">{{ isSingle ? '单条目' : '可添加多段' }}</span>
        </div>
        <p>{{ meta.hint }}</p>
      </div>
      <el-select
        v-if="draft.type === 'job_intention'"
        v-model="draft.meta.displayMode"
        size="small"
        class="display-mode"
        placeholder="展示方式"
      >
        <el-option label="显示在姓名下方" value="title_only" />
        <el-option label="作为独立模块展示" value="full" />
      </el-select>
    </div>
    <div v-else-if="draft.type === 'job_intention'" class="embedded-mode">
      <span>求职意向展示方式</span>
      <el-select v-model="draft.meta.displayMode" size="small" class="display-mode">
        <el-option label="显示在姓名下方" value="title_only" />
        <el-option label="作为独立模块展示" value="full" />
      </el-select>
    </div>

    <div class="editor-items">
      <article
        v-for="(item, index) in draft.items"
        :key="item.id"
        class="editor-item"
        :class="{ 'editor-item--single': isSingle }"
      >
        <div v-if="!isSingle" class="editor-item-head">
          <span class="item-index">{{ labelFor(index) }}</span>
          <div class="item-actions">
            <button type="button" title="上移" :disabled="index === 0" @click="move(index, -1)">
              <el-icon><ArrowUp /></el-icon>
            </button>
            <button type="button" title="下移" :disabled="index === draft.items.length - 1" @click="move(index, 1)">
              <el-icon><ArrowDown /></el-icon>
            </button>
            <button type="button" title="复制" @click="copyItem(index)">
              <el-icon><CopyDocument /></el-icon>
            </button>
            <button type="button" title="删除" class="danger" @click="removeItem(index)">
              <el-icon><Delete /></el-icon>
            </button>
          </div>
        </div>

        <div class="editor-field-grid">
          <FieldControl
            v-for="def in meta.fields"
            :key="def.key"
            v-model="item.fields"
            :field="def"
            :resume-id="resumeId"
            :style="{ gridColumn: `span ${def.span || 12}` }"
          />
        </div>
      </article>
    </div>

    <div v-if="!isSingle" class="editor-add">
      <el-button plain :disabled="draft.items.length >= maxItems" @click="addItem">
        + {{ draft.items.length ? '再添加一段' : '添加第一段内容' }}
      </el-button>
      <span v-if="draft.type === 'skill_proficiency'" class="add-tip">最多 {{ maxItems }} 项</span>
    </div>

    <div class="editor-actions">
      <span class="save-hint">点击保存后才会写入云端，取消不会保留本次修改。</span>
      <div class="action-buttons">
        <el-button :disabled="saving" @click="emit('cancel')">取消</el-button>
        <el-button type="primary" :loading="saving" @click="emit('save')">保存模块</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.section-editor {
  margin-top: 14px;
  padding-top: 16px;
  border-top: 1px dashed var(--line-strong);
}

.editor-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.editor-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.editor-title-row h3 {
  font-family: var(--serif);
  font-size: 16px;
  font-weight: 600;
}

.editor-head p {
  max-width: 540px;
  margin-top: 5px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.display-mode {
  width: 144px;
  flex-shrink: 0;
}

.embedded-mode {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--muted);
  font-size: 11px;
}

.editor-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.editor-item {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper-deep);
}

.editor-item--single {
  padding: 14px;
  background: var(--paper);
}

.editor-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.item-index {
  color: var(--muted);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.item-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.item-actions button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.item-actions button:hover:not(:disabled) {
  border-color: var(--line);
  background: var(--paper);
  color: var(--ink);
}

.item-actions button.danger:hover:not(:disabled) {
  border-color: rgba(181, 72, 63, 0.25);
  background: #fbeeec;
  color: var(--danger);
}

.item-actions button:disabled {
  opacity: 0.28;
  cursor: default;
}

.editor-field-grid {
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  gap: 12px 14px;
}

.editor-add {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.editor-add :deep(.el-button) {
  border-style: dashed;
  color: var(--muted);
}

.add-tip {
  color: var(--faint);
  font-size: 11px;
}

.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.save-hint {
  color: var(--faint);
  font-size: 11px;
  line-height: 1.6;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 760px) {
  .editor-head {
    flex-direction: column;
  }

  .display-mode {
    width: 100%;
  }

  .editor-field-grid {
    gap: 10px;
  }

  .editor-field-grid > * {
    grid-column: span 24 !important;
  }

  .editor-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .action-buttons {
    justify-content: flex-end;
  }
}
</style>
