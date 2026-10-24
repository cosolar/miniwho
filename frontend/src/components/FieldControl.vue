<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Picture, Upload } from '@element-plus/icons-vue'
import RichTextEditor from './RichTextEditor.vue'
import { itemMediaApi } from '../api'
import { itemMediaUrl, notifyError } from '../api/client'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: Object, default: () => ({}) },
  resumeId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue'])
const type = computed(() => props.field.type || 'text')
const value = computed(() => props.modelValue?.[props.field.key])
const periodValue = computed(() => props.modelValue?.period || { start: '', end: '', current: false })

const fileInput = ref(null)
const uploading = ref(false)
const brokenKey = ref('')
const imageSrc = computed(() => itemMediaUrl(value.value))
// key 还在但文件不在了（换过部署、或早期上传没保存被回收）也要能看出来，别留个破图。
const imageReady = computed(() => Boolean(imageSrc.value) && brokenKey.value !== String(value.value || ''))
const staleImage = computed(() => Boolean(value.value) && !imageReady.value)

function onImageError() {
  brokenKey.value = String(value.value || '')
}

function setValue(next) {
  emit('update:modelValue', { ...props.modelValue, [props.field.key]: next })
}

function setPeriod(patch) {
  setValue({ start: '', end: '', current: false, ...periodValue.value, ...patch })
}

function toggleCurrent(current) {
  setPeriod(current ? { current: true, end: '' } : { current: false })
}

function pickImage() {
  if (uploading.value) return
  if (!props.resumeId) {
    ElMessage.warning('请先保存简历再上传图片')
    return
  }
  fileInput.value?.click()
}

async function onImageFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || uploading.value) return
  if (!/image\/(jpeg|png)/.test(file.type)) {
    ElMessage.warning('仅支持 JPG 或 PNG 图片')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 2MB')
    return
  }
  uploading.value = true
  try {
    const data = await itemMediaApi.upload(props.resumeId, file)
    setValue(data.key)
    ElMessage.success('图片已上传，保存模块后生效')
  } catch (error) {
    notifyError(error, '图片上传失败')
  } finally {
    uploading.value = false
  }
}

// 只清掉字段，文件留给后端在内容落库后回收：换成空值后不保存就走掉的话，旧的图还得能用。
function clearImage() {
  setValue('')
}
</script>

<template>
  <div class="form-field" :class="{ 'form-field--switch': type === 'switch' }">
    <template v-if="type === 'switch'">
      <span class="field-label">{{ field.label }}</span>
      <el-switch :model-value="Boolean(value)" size="small" @update:model-value="setValue" />
    </template>

    <template v-else>
      <label class="field-label">
        {{ field.label }}
        <span v-if="field.required" class="required">*</span>
      </label>

      <el-input
        v-if="type === 'text' || type === 'email' || type === 'tel' || type === 'url'"
        :model-value="value"
        :type="type === 'email' ? 'email' : type === 'tel' ? 'tel' : 'text'"
        :placeholder="field.placeholder"
        :maxlength="field.maxlength"
        @update:model-value="setValue"
      />

      <el-input
        v-else-if="type === 'textarea'"
        :model-value="value"
        type="textarea"
        :rows="field.rows || 3"
        :placeholder="field.placeholder"
        resize="vertical"
        @update:model-value="setValue"
      />

      <el-select
        v-else-if="type === 'select'"
        :model-value="value"
        :placeholder="field.placeholder || '请选择'"
        clearable
        style="width: 100%"
        @update:model-value="setValue"
      >
        <el-option v-for="option in field.options || []" :key="option" :label="option" :value="option" />
      </el-select>

      <el-date-picker
        v-else-if="type === 'month'"
        :model-value="value"
        type="month"
        value-format="YYYY-MM"
        format="YYYY-MM"
        :placeholder="field.placeholder || '选择月份'"
        style="width: 100%"
        @update:model-value="setValue"
      />

      <el-select
        v-else-if="type === 'tags'"
        :model-value="value || []"
        multiple
        filterable
        allow-create
        default-first-option
        :reserve-keyword="false"
        :placeholder="field.placeholder || '输入后回车添加'"
        style="width: 100%"
        @update:model-value="setValue"
      />

      <div v-else-if="type === 'period'" class="period-row">
        <el-date-picker
          :model-value="periodValue.start"
          type="month"
          value-format="YYYY-MM"
          format="YYYY-MM"
          placeholder="开始月份"
          style="flex: 1; min-width: 110px"
          @update:model-value="(val) => setPeriod({ start: val || '' })"
        />
        <span class="period-sep">—</span>
        <el-date-picker
          v-if="!periodValue.current"
          :model-value="periodValue.end"
          type="month"
          value-format="YYYY-MM"
          format="YYYY-MM"
          placeholder="结束月份"
          style="flex: 1; min-width: 110px"
          @update:model-value="(val) => setPeriod({ end: val || '' })"
        />
        <span v-else class="current-mark">至今</span>
        <el-checkbox
          :model-value="Boolean(periodValue.current)"
          size="small"
          @update:model-value="toggleCurrent"
        >
          至今
        </el-checkbox>
      </div>

      <RichTextEditor
        v-else-if="type === 'richtext'"
        :model-value="value"
        :placeholder="field.placeholder"
        @update:model-value="setValue"
      />

      <div v-else-if="type === 'image'" class="image-field">
        <div class="image-field__box" :class="{ 'image-field__box--empty': !imageReady }">
          <img v-if="imageReady" :src="imageSrc" :alt="field.label" @error="onImageError" />
          <el-icon v-else><Picture /></el-icon>
        </div>
        <div class="image-field__side">
          <div class="image-field__actions">
            <button type="button" class="image-field__btn" :disabled="uploading" @click="pickImage">
              <el-icon><Upload /></el-icon>
              {{ uploading ? '上传中…' : imageReady ? '更换图片' : '上传图片' }}
            </button>
            <button
              v-if="value"
              type="button"
              class="image-field__btn image-field__btn--ghost"
              :disabled="uploading"
              @click="clearImage"
            >
              <el-icon><Delete /></el-icon>
              移除
            </button>
          </div>
          <p class="image-field__hint">
            {{ staleImage ? '这张图在当前服务器上已经不存在，重新上传即可恢复。' : field.hint }}
          </p>
        </div>
        <input
          ref="fileInput"
          type="file"
          class="image-field__input"
          accept="image/png,image/jpeg"
          @change="onImageFile"
        />
      </div>

      <el-input v-else :model-value="value" :placeholder="field.placeholder" @update:model-value="setValue" />
    </template>
  </div>
</template>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.form-field--switch {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 32px;
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--paper-deep);
}

.field-label {
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.4;
}

.form-field--switch .field-label {
  font-size: 12px;
}

.required {
  color: var(--danger);
  margin-left: 2px;
}

.period-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.period-sep {
  color: var(--faint);
}

.current-mark {
  flex: 1;
  min-width: 110px;
  color: var(--muted);
  font-size: 12px;
}

.period-row :deep(.el-checkbox) {
  margin-right: 0;
}

.period-row :deep(.el-checkbox__label) {
  font-size: 12px;
  color: var(--muted);
}

.image-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.image-field__box {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 46px;
  height: 46px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
}

.image-field__box img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.image-field__box--empty {
  border-style: dashed;
  color: var(--faint);
}

.image-field__side {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.image-field__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.image-field__btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 11px;
  border: 1px solid var(--accent);
  border-radius: 7px;
  background: var(--accent);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.image-field__btn:hover:not(:disabled) {
  border-color: var(--accent-dark);
  background: var(--accent-dark);
}

.image-field__btn--ghost {
  border-style: dashed;
  border-color: var(--line-strong);
  background: transparent;
  color: var(--muted);
}

.image-field__btn--ghost:hover:not(:disabled) {
  border-color: var(--danger);
  background: transparent;
  color: var(--danger);
}

.image-field__btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.image-field__hint {
  margin: 0;
  color: var(--faint);
  font-size: 11.5px;
  line-height: 1.5;
}

.image-field__input {
  display: none;
}

@media (max-width: 560px) {
  .period-row {
    gap: 6px;
  }
}
</style>
