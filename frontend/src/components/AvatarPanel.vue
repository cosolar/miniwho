<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Picture, Upload } from '@element-plus/icons-vue'
import AvatarCropper from './AvatarCropper.vue'
import { avatarApi } from '../api'
import { mediaUrl, notifyError } from '../api/client'

const props = defineProps({
  resumeId: { type: Number, required: true },
  avatar: { type: Object, default: null }
})
const emit = defineEmits(['update'])

const input = ref(null)
const busy = ref(false)
const source = ref(null)
const cropper = ref(false)

const shape = computed(() => props.avatar?.shape || 'rect')
const thumb = computed(() => mediaUrl(props.avatar?.url || ''))

function pick() {
  input.value?.click()
}

async function onFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!/image\/(jpeg|png)/.test(file.type)) {
    ElMessage.warning('头像仅支持 JPG 或 PNG 图片')
    return
  }
  if (file.size > 3 * 1024 * 1024) {
    ElMessage.warning('头像图片不能超过 3MB')
    return
  }
  source.value = file
  cropper.value = true
}

async function onCrop(blob) {
  cropper.value = false
  source.value = null
  busy.value = true
  try {
    const file = new File([blob], 'avatar.jpg', { type: 'image/jpeg' })
    const data = await avatarApi.upload(props.resumeId, file)
    emit('update', data)
    ElMessage.success('头像已更新')
  } catch (error) {
    notifyError(error, '头像上传失败')
  } finally {
    busy.value = false
  }
}

async function toggleShape(next) {
  if (next === shape.value || busy.value) return
  busy.value = true
  try {
    emit('update', await avatarApi.setShape(props.resumeId, next))
  } catch (error) {
    notifyError(error, '头像形状保存失败')
  } finally {
    busy.value = false
  }
}

async function remove() {
  try {
    await ElMessageBox.confirm('移除后当前简历不再展示照片，可以随时重新上传。', '移除头像', {
      confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  busy.value = true
  try {
    emit('update', await avatarApi.remove(props.resumeId))
    ElMessage.success('头像已移除')
  } catch (error) {
    notifyError(error, '移除头像失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="avatar-panel">
    <div class="avatar-panel__head">
      <strong>简历照片</strong>
      <span>建议 400×500 以上的正面照，JPG / PNG，不超过 3MB。</span>
    </div>

    <div class="avatar-panel__body">
      <div class="avatar-preview" :class="`avatar-preview--${shape}`">
        <img v-if="avatar?.present && thumb" :src="thumb" alt="简历头像" />
        <el-icon v-else class="avatar-preview__empty"><Picture /></el-icon>
      </div>

      <div class="avatar-panel__actions">
        <button class="avatar-btn avatar-btn--primary" type="button" :disabled="busy" @click="pick">
          <el-icon><Upload /></el-icon>
          {{ avatar?.present && thumb ? '更换照片' : '上传照片' }}
        </button>
        <div class="avatar-shape">
          <button
            type="button"
            :class="{ active: shape === 'rect' }"
            :disabled="busy || !avatar?.present"
            @click="toggleShape('rect')"
          >证件照</button>
          <button
            type="button"
            :class="{ active: shape === 'circle' }"
            :disabled="busy || !avatar?.present"
            @click="toggleShape('circle')"
          >圆形</button>
        </div>
        <button
          v-if="avatar?.present"
          class="avatar-btn avatar-btn--ghost"
          type="button"
          :disabled="busy"
          @click="remove"
        >
          <el-icon><Delete /></el-icon>
          移除
        </button>
      </div>
    </div>

    <input
      ref="input"
      type="file"
      class="avatar-panel__input"
      accept="image/jpeg,image/png"
      @change="onFile"
    />

    <AvatarCropper
      :visible="cropper"
      :source="source"
      :shape="shape"
      @confirm="onCrop"
      @cancel="cropper = false; source = null"
    />
  </div>
</template>

<style scoped>
.avatar-panel {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--paper-deep);
}

.avatar-panel__head {
  display: grid;
  gap: 3px;
}

.avatar-panel__head strong {
  font-size: 13px;
}

.avatar-panel__head span {
  color: var(--muted);
  font-size: 12px;
}

.avatar-panel__body {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.avatar-preview {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 62px;
  height: 87px;
  overflow: hidden;
  border: 1px dashed var(--line-strong);
  border-radius: 6px;
  background: #fff;
}

.avatar-preview--circle {
  width: 76px;
  height: 76px;
  border-radius: 50%;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-preview__empty {
  color: var(--faint);
  font-size: 22px;
}

.avatar-panel__actions {
  display: grid;
  gap: 8px;
  justify-items: start;
}

.avatar-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink);
  font-size: 12.5px;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease;
}

.avatar-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
}

.avatar-btn--primary {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.avatar-btn--primary:hover:not(:disabled) {
  color: #fff;
  border-color: var(--accent-dark);
}

.avatar-btn--ghost {
  border-style: dashed;
  color: var(--muted);
}

.avatar-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.avatar-shape {
  display: inline-flex;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.avatar-shape button {
  padding: 5px 12px;
  border: none;
  background: #fff;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.avatar-shape button + button {
  border-left: 1px solid var(--line);
}

.avatar-shape button.active {
  background: var(--accent-soft);
  color: var(--accent);
}

.avatar-shape button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.avatar-panel__input {
  display: none;
}

@media (max-width: 520px) {
  .avatar-panel__actions {
    flex: 1;
    min-width: 0;
    justify-items: stretch;
  }

  .avatar-btn {
    justify-content: center;
  }

  .avatar-shape {
    display: flex;
  }

  .avatar-shape button {
    flex: 1;
  }
}
</style>
