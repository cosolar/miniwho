<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Refresh, View, Warning } from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import PaperFrame from '../components/PaperFrame.vue'
import { adminApi } from '../api'
import { templateByCode } from '../data/templatePresets'
import { useTemplates } from '../composables/useTemplates'
import { formatDateTime } from '../utils/format'

const route = useRoute()
const resumeId = Number(route.params.id)
const loading = ref(true)
const loadError = ref('')
const resume = ref(null)
const pageCount = ref(1)
const { all: templates, refresh, ensure } = useTemplates()

const template = computed(() => templateByCode(resume.value?.templateCode, templates.value))
const templateMissing = computed(() => Boolean(resume.value?.templateCode)
  && !templates.value.some((tpl) => tpl.code === resume.value.templateCode))
const styleData = computed(() => {
  const style = resume.value?.style || {}
  return {
    filter: style.filter || 'standard',
    fontSizeStep: Number(style.fontSizeStep || 0),
    spacingStep: Number(style.spacingStep || 0),
    showBrandFooter: style.showBrandFooter !== false,
    pageMargin: {
      custom: !!style.pageMargin?.custom,
      top: Number(style.pageMargin?.top ?? 15),
      right: Number(style.pageMargin?.right ?? 15),
      bottom: Number(style.pageMargin?.bottom ?? 12),
      left: Number(style.pageMargin?.left ?? 15)
    }
  }
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const detail = await adminApi.userResume(resumeId)
    resume.value = detail
    await refresh()
    // 别人的私有导入包不在目录里，管理员按编码单独取一次配置。
    await ensure(detail.templateCode)
  } catch (error) {
    loadError.value = error?.message || '加载简历失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="admin-preview">
      <header class="admin-preview__bar">
        <router-link class="admin-preview__back" to="/admin">
          <el-icon><ArrowLeft /></el-icon>
          返回管理后台
        </router-link>
        <div class="admin-preview__title">
          <span class="mono">ADMIN PREVIEW</span>
          <h1>{{ resume?.title || '简历预览' }}</h1>
          <p v-if="resume">
            模板 {{ resume.templateName || resume.templateCode }}
            <template v-if="route.query.user"> · 所属账号 {{ route.query.user }}</template>
            <template v-if="resume.updatedAt"> · 更新于 {{ formatDateTime(resume.updatedAt) }}</template>
          </p>
        </div>
        <span class="admin-preview__badge">
          <el-icon><View /></el-icon>
          只读预览，不会改动这份简历
        </span>
      </header>

      <div v-if="loading" v-loading="true" class="admin-preview__state"></div>

      <div v-else-if="loadError" class="admin-preview__state admin-preview__state--error">
        <p>{{ loadError }}</p>
        <el-button plain @click="load">
          <el-icon><Refresh /></el-icon>
          重新加载
        </el-button>
      </div>

      <div v-else class="admin-preview__stage-area">
        <p v-if="templateMissing" class="admin-preview__notice">
          <el-icon><Warning /></el-icon>
          <span>这份简历记录的模板「{{ resume.templateCode }}」已下架或不存在，纸面暂按默认版式渲染。</span>
        </p>
        <div class="stage-meta">
          <span><i></i>A4 实际尺寸预览</span>
          <span>共 {{ pageCount }} 页 · 虚线为实际分页位置</span>
        </div>
        <div class="preview-stage">
          <div class="preview-stage__inner">
            <PaperFrame
              :content="resume.content"
              :template="template"
              :style-data="styleData"
              :avatar="resume.avatar"
              :padding="0"
              @pages="(count) => (pageCount = count)"
            />
          </div>
        </div>
        <div class="stage-foot">
          <span>210 × 297 mm · {{ template.name }}模板</span>
          <span>内容只读，需要修改请让用户本人操作</span>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.admin-preview {
  padding-bottom: 70px;
}

.admin-preview__bar {
  position: sticky;
  top: 62px;
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 12px max(24px, calc((100% - var(--content-width) - 80px) / 2));
  border-bottom: 1px solid var(--line);
  background: rgba(244, 241, 233, 0.9);
  backdrop-filter: blur(14px);
}

.admin-preview__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  color: var(--muted);
  font-size: 11px;
}

.admin-preview__back:hover {
  color: var(--accent);
}

.admin-preview__title {
  flex: 1;
  min-width: 0;
  padding-left: 18px;
  border-left: 1px solid var(--line-strong);
}

.admin-preview__title .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.admin-preview__title h1 {
  margin: 1px 0 2px;
  overflow: hidden;
  color: var(--ink);
  font-size: 17px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-preview__title p {
  color: var(--faint);
  font-size: 10.5px;
}

.admin-preview__badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  padding: 5px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  color: var(--muted);
  font-size: 10.5px;
}

.admin-preview__state {
  display: grid;
  place-items: center;
  gap: 12px;
  min-height: 320px;
  color: var(--muted);
  font-size: 12px;
}

.admin-preview__state--error {
  color: var(--danger);
}

.admin-preview__stage-area {
  width: min(calc(var(--content-width) + 80px), calc(100% - 48px));
  margin: 22px auto 0;
}

.admin-preview__notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding: 9px 12px;
  border: 1px solid rgba(181, 72, 63, 0.2);
  border-radius: 10px;
  background: #fbeeec;
  color: var(--danger);
  font-size: 11px;
  line-height: 1.6;
}

.stage-meta,
.stage-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 4px 8px;
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.04em;
}

.stage-meta span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
}

.stage-meta i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--success);
}

.stage-foot {
  padding: 8px 4px 0;
}

.preview-stage {
  padding: 30px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background:
    radial-gradient(600px 420px at 50% -10%, rgba(10, 125, 118, 0.07), transparent 70%),
    linear-gradient(180deg, var(--canvas-deep), #e7e1d4);
}

.preview-stage__inner {
  width: min(794px, 100%);
  margin: 0 auto;
  filter: drop-shadow(0 24px 48px rgba(25, 34, 30, 0.16));
}

.preview-stage__inner :deep(.paper-scaler__paper) {
  box-shadow: none;
}

@media (max-width: 700px) {
  .admin-preview__bar {
    flex-wrap: wrap;
    gap: 8px 12px;
    padding: 10px 16px;
  }

  .admin-preview__title {
    order: 3;
    width: 100%;
    padding-left: 0;
    border-left: 0;
  }

  .admin-preview__badge {
    margin-left: auto;
  }

  .admin-preview__stage-area {
    width: calc(100% - 24px);
    margin-top: 14px;
  }

  .preview-stage {
    padding: 12px;
  }

  .stage-foot span:last-child {
    display: none;
  }
}
</style>
