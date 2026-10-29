<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Edit, InfoFilled, Search, Upload, UploadFilled, View, Warning } from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
import PaperFrame from '../components/PaperFrame.vue'
import TemplateThumb from '../components/TemplateThumb.vue'
import { configOf } from '../data/templatePresets'
import { useTemplates } from '../composables/useTemplates'
import { useSampleResume } from '../composables/useSampleResume'
import { templateApi } from '../api'
import { notifyError } from '../api/client'
import { saveBlob } from '../utils/format'

const router = useRouter()
const { mine, refresh: loadTemplates } = useTemplates()
const { content: sampleContent, avatar: sampleAvatar } = useSampleResume()

/** 站点是托管给别人用的，他们手上没有仓库，说明统一指向文档中心（MiniDocs 分享页）。 */
const DOCS_BASE = 'https://docs.minims.cn/share/miniwho?path=docs/'
const THEME_GUIDE_URL = `${DOCS_BASE}模板主题开发指南.md`
const PACKAGE_FORMAT_URL = `${DOCS_BASE}模板包格式.md`

const packageInput = ref(null)
const importing = ref(false)
const dragging = ref(false)
const busyCode = ref('')
const packageProblems = ref([])
const packageDialog = ref(false)
const previewTarget = ref(null)
const previewVisible = ref(false)
const editingTpl = ref(null)
const editorVisible = ref(false)
const editorText = ref('')
const editorLoading = ref(false)
const editorSaving = ref(false)
const editorProblems = ref([])

const LAYOUT_LABELS = {
  minimal: '单栏留白',
  academic: '学术细线',
  sidebar: '双栏',
  band: '色带',
  center: '居中页眉',
  timeline: '时间轴',
  profile: '头像单栏',
  ribbon: '色带旗标',
  classic: '经典',
  rail: '通栏照片',
  photoheader: '照片页眉',
  formal: '传统单栏'
}

const sharedCount = computed(() => mine.value.filter((item) => item.visibility === 'public').length)

// 模板包是用户自己导入的，数量有限，搜索与排序全在本地做，不额外请求后端。
const PAGE_SIZE = 9
const keyword = ref('')
const sortBy = ref('recent')
const visibleCount = ref(PAGE_SIZE)

const filteredTemplates = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  const list = query
    ? mine.value.filter((tpl) => [
      tpl.name,
      tpl.code,
      tpl.description,
      tpl.author,
      LAYOUT_LABELS[configOf(tpl).layout],
      ...(tpl.tags || [])
    ].filter(Boolean).some((text) => String(text).toLowerCase().includes(query)))
    : [...mine.value]
  if (sortBy.value === 'usage') {
    list.sort((a, b) => (b.usageCount ?? 0) - (a.usageCount ?? 0))
  } else if (sortBy.value === 'name') {
    list.sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'zh-Hans-CN'))
  }
  return list
})

// 每个卡片都要实时渲染一张简历纸，一次全渲染会很卡，所以按批展开。
const shownTemplates = computed(() => filteredTemplates.value.slice(0, visibleCount.value))
const restCount = computed(() => Math.max(0, filteredTemplates.value.length - shownTemplates.value.length))

watch([keyword, sortBy], () => {
  visibleCount.value = PAGE_SIZE
})

function openPackagePicker() {
  packageInput.value?.click()
}

async function onPackageFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) await importPackageFile(file)
}

// 空状态整块都是投放区，拖进来的文件在松手时走同一条导入链路。
function onDragLeave(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) dragging.value = false
}

async function onDrop(event) {
  dragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file && !importing.value) await importPackageFile(file)
}

async function importPackageFile(file) {
  let payload
  try {
    payload = JSON.parse(await file.text())
  } catch {
    ElMessage.error('这个文件不是合法的 JSON，模板包需要保存为 .json')
    return
  }
  importing.value = true
  try {
    const created = await templateApi.importPackage(payload)
    await loadTemplates()
    ElMessage.success(`已导入「${created.name}」，可以在简历的预览页选用它`)
  } catch (error) {
    const problems = error?.details?.problems
    if (Array.isArray(problems) && problems.length) {
      packageProblems.value = problems
      packageDialog.value = true
    } else {
      notifyError(error, '模板包导入失败')
    }
  } finally {
    importing.value = false
  }
}

async function exportPackage(tpl) {
  busyCode.value = tpl.code
  try {
    const blob = await templateApi.exportPackage(tpl.code)
    await saveBlob(blob, `${tpl.code}-${tpl.version || '1.0.0'}.json`)
    ElMessage.success('模板包已下载，发给别人就能导入')
  } catch (error) {
    notifyError(error, '导出模板包失败')
  } finally {
    busyCode.value = ''
  }
}

/**
 * 分享与导出权限是一次 PUT 一起提交的：关闭分享时保留作者的导出权限设置，
 * 免得再次分享后原本的选择被悄悄改掉。
 */
async function updateShare(tpl, shared, exportable) {
  busyCode.value = tpl.code
  try {
    await templateApi.share(tpl.code, { shared, exportable })
    await loadTemplates()
    if (!shared) ElMessage.success(`「${tpl.name}」已从模板中心下架，别人不能再选用`)
    else if (!exportable) ElMessage.success(`已分享到模板中心：别人只能选用，不能导出模板包`)
    else ElMessage.success('已分享到模板中心，本站用户都能选用并导出')
  } catch (error) {
    notifyError(error, '分享设置保存失败')
  } finally {
    busyCode.value = ''
  }
}

async function removeTemplate(tpl) {
  try {
    await ElMessageBox.confirm(
      `删除后「${tpl.name}」会从我的模板里移除，本地保存的模板包文件不受影响，随时可以再导入。`
      + (tpl.visibility === 'public' ? '它当前已分享，删除后别人也无法再选用。' : ''),
      '删除导入的模板',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    busyCode.value = tpl.code
    await templateApi.remove(tpl.code)
    await loadTemplates()
    ElMessage.success('模板已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(error, '删除模板失败')
  } finally {
    busyCode.value = ''
  }
}

function useInResume(tpl) {
  router.push({ path: '/dashboard', query: { template: tpl.code } })
}

function openPreview(tpl) {
  previewTarget.value = tpl
  previewVisible.value = true
}

/** 在线编辑：把作者自己的模板包原文取回来，改完直接重新导入同名编码即可原地更新。 */
async function openJsonEditor(tpl) {
  editingTpl.value = tpl
  editorText.value = ''
  editorProblems.value = []
  editorLoading.value = true
  editorVisible.value = true
  try {
    const blob = await templateApi.exportPackage(tpl.code)
    editorText.value = await blob.text()
  } catch (error) {
    editorVisible.value = false
    notifyError(error, '读取模板包失败')
  } finally {
    editorLoading.value = false
  }
}

function formatEditor() {
  try {
    editorText.value = JSON.stringify(JSON.parse(editorText.value), null, 2)
  } catch (error) {
    ElMessage.error(`JSON 语法错误：${error.message}`)
  }
}

async function saveEditor() {
  let payload
  try {
    payload = JSON.parse(editorText.value)
  } catch (error) {
    ElMessage.error(`JSON 语法错误：${error.message}`)
    return
  }
  const composed = `${payload?.id?.namespace ?? ''}--${payload?.id?.code ?? ''}`
  if (composed !== editingTpl.value?.code) {
    ElMessage.error('不能修改 id.namespace 与 id.code，否则会变成另一个模板')
    return
  }
  editorProblems.value = []
  editorSaving.value = true
  try {
    const saved = await templateApi.importPackage(payload)
    await loadTemplates()
    editorVisible.value = false
    ElMessage.success(`「${saved.name}」已更新，新版式立即生效`)
  } catch (error) {
    const problems = error?.details?.problems
    if (Array.isArray(problems) && problems.length) {
      editorProblems.value = problems
      ElMessage.error('模板包未通过校验，按下面提示改完再保存')
    } else {
      notifyError(error, '模板包保存失败')
    }
  } finally {
    editorSaving.value = false
  }
}

onMounted(loadTemplates)
</script>

<template>
  <AppLayout>
    <div class="mine-page">
      <header class="mine-hero">
        <div>
          <div class="card-kicker">MY TEMPLATES</div>
          <h1 class="display">我的模板</h1>
          <p>
            这里管理你自己导入的模板包：{{ mine.length }} 套，其中 {{ sharedCount }} 套已分享到模板中心。
            导入的包只包含版式令牌与净化过的 CSS，服务端会逐条规则重新生成后才注入页面，不含任何脚本。
          </p>
        </div>
        <div class="mine-hero__actions">
          <el-button type="primary" :loading="importing" @click="openPackagePicker">
            <el-icon><Upload /></el-icon>
            <span>{{ importing ? '正在校验…' : '导入模板包' }}</span>
          </el-button>
          <input
            ref="packageInput"
            class="mine-file"
            type="file"
            accept="application/json,.json"
            @change="onPackageFile"
          />
          <el-button @click="router.push('/templates')">去模板中心</el-button>
        </div>
      </header>

      <div
        v-if="!mine.length"
        class="surface mine-empty"
        :class="{ 'is-dragging': dragging }"
        @dragenter.prevent="dragging = true"
        @dragover.prevent
        @dragleave.prevent="onDragLeave"
        @drop.prevent="onDrop"
      >
        <span class="mine-empty__badge"><el-icon><InfoFilled /></el-icon></span>
        <h2>还没有导入过模板包</h2>
        <p>
          模板包就是一个 <code>.json</code> 文件：别人分享给你的，或者你从模板中心导出后改过配色、间距的，装进来就能用。
          想自己调样式，先看这份
          <a :href="THEME_GUIDE_URL" target="_blank" rel="noopener">模板包怎么写</a>。
        </p>
        <button
          type="button"
          class="package-drop"
          :class="{ 'is-busy': importing }"
          :disabled="importing"
          @click="openPackagePicker"
        >
          <span class="package-drop__icon"><el-icon><UploadFilled /></el-icon></span>
          <span class="package-drop__title">
            {{ importing ? '正在校验模板包…' : (dragging ? '松开鼠标即可导入' : '选择 .json 模板包') }}
          </span>
          <span class="package-drop__hint">
            <template v-if="dragging">导入后服务端会逐条重写规则，不会执行多余脚本</template>
            <template v-else>或直接把文件拖到这里 · 只接受 .json</template>
          </span>
        </button>
      </div>

      <template v-else>
        <div class="mine-toolbar">
          <div class="mine-toolbar__search">
            <el-input v-model="keyword" placeholder="搜索名称、编码、作者或描述" clearable>
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>
          <div class="mine-toolbar__side">
            <span class="mine-toolbar__count">{{ keyword.trim() ? '找到' : '共' }} <b>{{ filteredTemplates.length }}</b> 套</span>
            <el-select v-model="sortBy" size="small" class="mine-toolbar__sort" placeholder="排序">
              <el-option label="最近导入" value="recent" />
              <el-option label="使用最多" value="usage" />
              <el-option label="按名称" value="name" />
            </el-select>
          </div>
        </div>

        <div v-if="!shownTemplates.length" class="surface mine-empty">
          <span class="mine-empty__badge"><el-icon><Search /></el-icon></span>
          <h2>没有找到匹配的模板</h2>
          <p>试试名称、编码、作者或版式里的关键词，比如「qingling」「青领」「头像单栏」。</p>
          <el-button @click="keyword = ''">清除搜索</el-button>
        </div>

        <div v-else class="mine-grid">
          <article
            v-for="tpl in shownTemplates"
            :key="tpl.code"
            class="mine-card"
            :style="{ '--tpl-accent': configOf(tpl).accent || '#0a7d76' }"
          >
            <button type="button" class="mine-card__preview" :aria-label="`预览「${tpl.name}」完整效果`" @click="openPreview(tpl)">
              <span class="mine-card__label" :class="tpl.visibility === 'public' ? 'is-on' : 'is-off'">
                <i></i>{{ tpl.visibility === 'public' ? '已分享到模板中心' : '仅自己可见' }}
              </span>
              <div class="mine-card__thumb">
                <TemplateThumb :content="sampleContent" :template="tpl" :avatar="sampleAvatar" />
              </div>
              <span class="mine-card__view">
                <el-icon><View /></el-icon> 预览完整版式
              </span>
            </button>

            <div class="mine-card__body">
              <header class="mine-card__head">
                <h2>{{ tpl.name }}</h2>
                <span class="mine-card__layout">
                  <i :style="{ background: configOf(tpl).accent || 'var(--accent)' }"></i>
                  {{ LAYOUT_LABELS[configOf(tpl).layout] || '单栏' }}
                </span>
              </header>

              <ul class="mine-card__facts">
                <li class="mono-code">{{ tpl.code }}</li>
                <li>v{{ tpl.version || '1.0.0' }}</li>
                <li>{{ tpl.author || '未署名' }}</li>
                <li><b>{{ tpl.usageCount ?? 0 }}</b> 份简历在用</li>
              </ul>

              <p class="mine-card__desc">{{ tpl.description || '未填写模板描述' }}</p>

              <div class="mine-card__switches">
                <label class="switch-row">
                  <span class="switch-copy">
                    <strong>分享到模板中心</strong>
                    <small>本站其他用户可在模板中心看到「{{ tpl.author || '未署名' }} 分享」并选用</small>
                  </span>
                  <el-switch
                    :model-value="tpl.visibility === 'public'"
                    :loading="busyCode === tpl.code"
                    @update:model-value="(value) => updateShare(tpl, Boolean(value), tpl.exportable !== false)"
                  />
                </label>
                <label class="switch-row" :class="{ 'is-muted': tpl.visibility !== 'public' }">
                  <span class="switch-copy">
                    <strong>允许他人导出模板包</strong>
                    <small>{{ tpl.visibility === 'public'
                      ? (tpl.exportable !== false ? '关闭后别人只能选用，下载按钮对他们隐藏' : '别人只能选用，不能把包下载走')
                      : '分享后才能设置' }}</small>
                  </span>
                  <el-switch
                    :model-value="tpl.exportable !== false"
                    :disabled="tpl.visibility !== 'public'"
                    :loading="busyCode === tpl.code"
                    @update:model-value="(value) => updateShare(tpl, true, Boolean(value))"
                  />
                </label>
              </div>

              <footer class="mine-card__actions">
                <button type="button" class="use-template" @click="useInResume(tpl)">
                  用它新建简历<i>→</i>
                </button>
                <div class="mine-card__ghosts">
                  <el-button size="small" link @click="openJsonEditor(tpl)">
                    <el-icon><Edit /></el-icon> 编辑
                  </el-button>
                  <span class="ghost-sep" aria-hidden="true"></span>
                  <el-button size="small" link :loading="busyCode === tpl.code" @click="exportPackage(tpl)">
                    <el-icon><Download /></el-icon> 导出
                  </el-button>
                  <span class="ghost-sep" aria-hidden="true"></span>
                  <el-button size="small" link type="danger" :loading="busyCode === tpl.code" @click="removeTemplate(tpl)">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </div>
              </footer>
            </div>
          </article>
        </div>

        <div v-if="restCount" class="mine-more">
          <el-button plain @click="visibleCount += PAGE_SIZE">
            显示更多（还有 {{ restCount }} 套）
          </el-button>
        </div>
      </template>

      <p class="mine-note">
        <el-icon><Warning /></el-icon>
        <span>删除不会动已经选用它的简历：还有简历在用时服务端会拒绝删除，请先给那些简历换模板。取消分享后，别人已用的简历仍按该版式渲染，只是不再能被新简历选中。</span>
      </p>
    </div>

    <el-dialog
      v-model="previewVisible"
      class="preview-dialog"
      width="980px"
      align-center
      destroy-on-close
    >
      <template #header>
        <div v-if="previewTarget" class="preview-dialog__head">
          <div>
            <span class="mono">TEMPLATE / {{ previewTarget.code.toUpperCase() }}</span>
            <h2>{{ previewTarget.name }}</h2>
          </div>
          <span class="tag tag--accent">
            {{ previewTarget.visibility === 'public' ? '已分享到模板中心' : '仅自己可见' }}
          </span>
        </div>
      </template>

      <div v-if="previewTarget" class="preview-dialog__layout">
        <div class="preview-dialog__paper">
          <PaperFrame
            :content="sampleContent"
            :template="previewTarget"
            :avatar="sampleAvatar"
            :style-data="{ showBrandFooter: true }"
          />
        </div>
        <aside class="preview-dialog__side">
          <p class="preview-dialog__desc">{{ previewTarget.description || '未填写模板描述' }}</p>
          <ul class="preview-dialog__facts">
            <li>版式 {{ LAYOUT_LABELS[configOf(previewTarget).layout] || '单栏' }}</li>
            <li>作者 {{ previewTarget.author || '未署名' }}</li>
            <li>v{{ previewTarget.version || '1.0.0' }}</li>
            <li>{{ previewTarget.usageCount ?? 0 }} 份简历在用</li>
          </ul>
          <div class="preview-dialog__actions">
            <el-button type="primary" @click="useInResume(previewTarget)">用它新建简历</el-button>
            <el-button :loading="busyCode === previewTarget.code" @click="exportPackage(previewTarget)">
              <el-icon><Download /></el-icon> 导出模板包
            </el-button>
          </div>
          <p class="preview-dialog__hint">预览用的是站内示例简历。分享与导出权限在卡片上的开关里调整。</p>
        </aside>
      </div>
    </el-dialog>

    <el-dialog
      v-model="editorVisible"
      class="json-dialog"
      width="880px"
      align-center
      destroy-on-close
    >
      <template #header>
        <div class="json-dialog__head">
          <div>
            <span class="mono">EDIT PACKAGE / {{ editingTpl?.code }}</span>
            <h2>{{ editingTpl?.name || '编辑模板包' }}</h2>
          </div>
          <span class="tag">保存后立即生效</span>
        </div>
      </template>

      <p class="json-dialog__hint">
        直接改模板包 JSON，字段与校验规则见
        <a :href="PACKAGE_FORMAT_URL" target="_blank" rel="noopener">模板包格式</a>。
        不能修改 <code>id.namespace</code> / <code>id.code</code>，否则会变成另一个模板；CSS 仍会由服务端逐条重新生成。
      </p>

      <el-input
        v-model="editorText"
        type="textarea"
        :rows="22"
        :disabled="editorLoading"
        :placeholder="editorLoading ? '正在读取模板包…' : ''"
        class="json-editor"
        spellcheck="false"
      />

      <div v-if="editorProblems.length" class="json-problems">
        <p>未通过校验（{{ editorProblems.length }} 处）：</p>
        <ol>
          <li v-for="(problem, index) in editorProblems" :key="index">{{ problem }}</li>
        </ol>
      </div>

      <template #footer>
        <div class="json-dialog__foot">
          <el-button link :disabled="editorLoading" @click="formatEditor">格式化</el-button>
          <span class="json-dialog__spacer" />
          <el-button @click="editorVisible = false">取消</el-button>
          <el-button type="primary" :loading="editorSaving" :disabled="editorLoading" @click="saveEditor">
            保存并生效
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="packageDialog" width="520px" align-center class="package-dialog">
      <template #header>
        <div class="package-dialog__head">
          <span class="package-dialog__icon"><el-icon><Warning /></el-icon></span>
          <div>
            <h2>模板包未通过校验</h2>
            <p>共 {{ packageProblems.length }} 处问题，一次改完再导入。</p>
          </div>
        </div>
      </template>
      <ol class="package-problems">
        <li v-for="(problem, index) in packageProblems" :key="index">{{ problem }}</li>
      </ol>
      <template #footer>
        <div class="package-dialog__foot">
          <span class="mono">字段含义见 <a :href="PACKAGE_FORMAT_URL" target="_blank" rel="noopener">模板包格式</a></span>
          <el-button type="primary" @click="packageDialog = false">知道了</el-button>
        </div>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<style scoped>
.mine-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
  width: min(var(--content-width), calc(100% - 64px));
  margin: 0 auto;
}

.mine-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.mine-hero h1 {
  margin: 6px 0 10px;
  font-size: 30px;
}

.mine-hero p {
  max-width: 640px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.mine-hero__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mine-file {
  display: none;
}

.mine-empty {
  padding: 46px 28px 42px;
  text-align: center;
  transition: border-color 0.22s ease, background 0.22s ease;
}

/* 拖文件进来时整块空状态都可投放，边框与底色亮一下作为反馈。 */
.mine-empty.is-dragging {
  border-color: color-mix(in srgb, var(--accent) 42%, var(--line));
  background: color-mix(in srgb, var(--accent) 4%, var(--paper));
}

.mine-empty__badge {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin: 0 auto;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 22px;
}

.mine-empty h2 {
  margin: 14px 0 8px;
  font-size: 18px;
}

.mine-empty p {
  max-width: 540px;
  margin: 0 auto 22px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.9;
}

/* 说明里的文档链接跟着主色走，别露出浏览器默认的蓝下划线。 */
.mine-empty a,
.json-dialog__hint a,
.package-dialog__foot a {
  color: var(--accent);
  text-decoration: none;
  border-bottom: 1px solid color-mix(in srgb, var(--accent) 34%, transparent);
}

.mine-empty a:hover,
.json-dialog__hint a:hover,
.package-dialog__foot a:hover {
  border-bottom-color: var(--accent);
}

.mine-empty code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--mono);
  font-size: 11px;
}

/* 导入入口做成投放区：虚线框 + 圆形图标，鼠标悬停时抬起来，看起来就是「往这里放」。 */
.package-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  width: min(440px, 100%);
  margin: 0 auto;
  padding: 24px 24px 20px;
  border: 1.5px dashed color-mix(in srgb, var(--accent) 32%, var(--line-strong));
  border-radius: 16px;
  background:
    radial-gradient(130% 120% at 50% 0%, color-mix(in srgb, var(--accent) 8%, transparent), transparent 72%),
    var(--paper-deep);
  color: var(--ink-soft);
  font: inherit;
  text-align: center;
  cursor: pointer;
  transition:
    transform 0.26s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.22s ease,
    background 0.22s ease,
    box-shadow 0.22s ease;
}

.package-drop:hover {
  border-color: color-mix(in srgb, var(--accent) 70%, var(--line-strong));
  box-shadow: 0 18px 34px -26px rgba(10, 125, 118, 0.55);
  transform: translateY(-2px);
}

.package-drop:focus-visible {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 22%, transparent);
}

.mine-empty.is-dragging .package-drop {
  border-style: solid;
  border-color: var(--accent);
  background:
    radial-gradient(130% 120% at 50% 0%, color-mix(in srgb, var(--accent) 16%, transparent), transparent 72%),
    var(--accent-soft);
  box-shadow: 0 20px 40px -26px rgba(10, 125, 118, 0.6);
  transform: translateY(-2px) scale(1.01);
}

.package-drop:disabled {
  cursor: progress;
  opacity: 0.8;
}

.package-drop__icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin-bottom: 8px;
  border-radius: 50%;
  background: var(--paper);
  color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--line), 0 12px 22px -16px rgba(10, 125, 118, 0.7);
  font-size: 21px;
  transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1), background 0.22s ease, color 0.22s ease;
}

.package-drop:hover .package-drop__icon,
.mine-empty.is-dragging .package-drop__icon {
  background: var(--accent);
  color: #fff;
  transform: translateY(-3px);
}

.package-drop.is-busy .package-drop__icon {
  animation: package-pulse 1.2s ease-in-out infinite;
}

.package-drop__title {
  color: var(--ink);
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.6;
}

.package-drop__hint {
  color: var(--faint);
  font-size: 11px;
  line-height: 1.7;
}

.mine-empty.is-dragging .package-drop__hint {
  color: var(--accent-dark);
}

@keyframes package-pulse {
  50% {
    opacity: 0.6;
    transform: scale(0.94);
  }
}

@media (prefers-reduced-motion: reduce) {
  .package-drop,
  .package-drop__icon {
    transition: none;
  }

  .package-drop.is-busy .package-drop__icon {
    animation: none;
  }
}

.mine-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.mine-toolbar__search {
  width: 300px;
  max-width: 100%;
}

.mine-toolbar__side {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mine-toolbar__count {
  color: var(--faint);
  font-size: 11.5px;
}

.mine-toolbar__count b {
  color: var(--ink-soft);
  font-weight: 600;
}

.mine-toolbar__sort {
  width: 118px;
}

.mine-more {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

.mine-more :deep(.el-button) {
  border-style: dashed;
  color: var(--muted);
}

.mine-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
}

/* 卡片改成纵向：预览区在上、信息区在下，正文不再被缩略图挤成窄条。 */
.mine-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--paper);
  box-shadow: var(--shadow-sm);
  transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.28s ease, border-color 0.28s ease;
}

.mine-card:hover {
  transform: translateY(-3px);
  border-color: var(--line-strong);
  box-shadow: var(--shadow);
}

/* 预览台：用模板自己的主色做一层很淡的底，卡片之间靠颜色区分，而不是靠边框。 */
.mine-card__preview {
  position: relative;
  display: block;
  width: 100%;
  height: 200px;
  padding: 14px 16px 0;
  overflow: hidden;
  border: 0;
  border-bottom: 1px solid var(--line);
  background:
    radial-gradient(120% 100% at 50% 0%, color-mix(in srgb, var(--tpl-accent) 18%, #fff) 0%, var(--paper-deep) 68%);
  font: inherit;
  text-align: left;
  cursor: zoom-in;
}

.mine-card__preview:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--accent);
}

/* 悬浮提示：这里是可点的入口，点击后弹窗看完整版式。 */
.mine-card__view {
  position: absolute;
  bottom: 14px;
  left: 50%;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 13px;
  border-radius: 999px;
  background: rgba(25, 34, 30, 0.82);
  color: #f6f3ea;
  font-size: 10.5px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transform: translate(-50%, 6px);
  transition: opacity 0.25s ease, transform 0.25s ease;
  backdrop-filter: blur(4px);
}

.mine-card__preview:hover .mine-card__view,
.mine-card__preview:focus-visible .mine-card__view {
  opacity: 1;
  transform: translate(-50%, 0);
}

.mine-card__thumb {
  height: 100%;
  overflow: hidden;
  border-radius: 8px 8px 0 0;
}

/* 缩略图在原位只当封面用：去掉它自带的悬浮跳动与「预览版式」蒙层。 */
.mine-card__thumb :deep(.template-thumb) {
  border-radius: 8px 8px 0 0;
  box-shadow: 0 14px 28px -18px rgba(25, 34, 30, 0.45);
}

.mine-card__thumb :deep(.template-thumb__frame) {
  padding: 10px 10px 0;
  max-height: none;
}

.mine-card__thumb :deep(.template-thumb:hover) {
  transform: none;
  border-color: var(--line);
  box-shadow: 0 14px 28px -18px rgba(25, 34, 30, 0.45);
}

.mine-card__thumb :deep(.template-thumb__shade) {
  display: none;
}

.mine-card__label {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(255, 254, 251, 0.86);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(6px);
  font-size: 10.5px;
  font-weight: 600;
  line-height: 1.6;
}

.mine-card__label i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.mine-card__label.is-on {
  color: var(--accent);
}

.mine-card__label.is-off {
  color: var(--muted);
}

.mine-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 16px;
}

.mine-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.mine-card__head h2 {
  font-family: var(--serif);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

/* 版式标签带一个模板主色圆点，和上面的预览底色呼应。 */
.mine-card__layout {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 2px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  font-size: 10.5px;
  white-space: nowrap;
}

.mine-card__layout i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.mine-card__facts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 8px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--faint);
  font-size: 10.5px;
}

.mine-card__facts li + li::before {
  content: "·";
  margin-right: 8px;
  color: var(--line-strong);
}

.mine-card__facts b {
  color: var(--ink-soft);
  font-weight: 600;
}

.mine-card__desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.75;
}

.mine-card__switches {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 2px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--paper-deep);
}

.switch-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
}

.switch-row + .switch-row {
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.switch-copy {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.switch-copy strong {
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 600;
}

.switch-copy small {
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.6;
}

.switch-row :deep(.el-switch) {
  flex-shrink: 0;
  margin-top: 2px;
}

.switch-row.is-muted {
  opacity: 0.55;
  cursor: not-allowed;
}

.mine-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.use-template {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 13px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--sans);
  font-size: 11.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.use-template i {
  font-style: normal;
  transition: transform 0.2s ease;
}

.use-template:hover {
  background: var(--accent);
  color: #fff;
}

.use-template:hover i {
  transform: translateX(2px);
}

.mine-card__ghosts {
  display: flex;
  align-items: center;
  gap: 2px;
}

.mine-card__ghosts :deep(.el-button) {
  margin-left: 0;
  padding: 0 4px;
}

.ghost-sep {
  width: 1px;
  height: 10px;
  background: var(--line);
}

.mine-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--paper);
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.75;
}

.mine-note .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--accent);
}

.package-dialog__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.package-dialog__icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
}

.package-dialog__head h2 {
  font-size: 16px;
}

.package-dialog__head p {
  color: var(--muted);
  font-size: 11.5px;
}

.package-problems {
  margin: 0;
  padding-left: 20px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.75;
}

.package-problems li::marker {
  color: var(--accent);
  font-family: var(--mono);
  font-size: 10px;
}

.package-dialog__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.package-dialog__foot .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.04em;
}

:global(.json-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: 16px;
}

.json-dialog__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.json-dialog__head .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.json-dialog__head h2 {
  margin-top: 6px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 600;
}

.json-dialog__hint {
  margin-bottom: 10px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.8;
}

.json-dialog__hint code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--mono);
  font-size: 10.5px;
}

.json-editor :deep(.el-textarea__inner) {
  max-height: 54vh;
  padding: 12px 14px;
  background: var(--paper-deep);
  color: var(--ink-soft);
  font-family: var(--mono);
  font-size: 11.5px;
  line-height: 1.65;
  tab-size: 2;
}

.json-problems {
  max-height: 140px;
  margin-top: 10px;
  padding: 10px 14px;
  overflow-y: auto;
  border: 1px solid rgba(181, 72, 63, 0.25);
  border-radius: 10px;
  background: #fbeeec;
  color: var(--danger);
  font-size: 11.5px;
  line-height: 1.7;
}

.json-problems ol {
  margin: 4px 0 0;
  padding-left: 18px;
}

.json-dialog__foot {
  display: flex;
  align-items: center;
  gap: 8px;
}

.json-dialog__spacer {
  flex: 1;
}

:global(.preview-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: 16px;
}

:global(.preview-dialog .el-dialog__body) {
  padding-top: 4px;
}

.preview-dialog__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.preview-dialog__head .mono {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.preview-dialog__head h2 {
  margin-top: 6px;
  font-family: var(--serif);
  font-size: 24px;
  font-weight: 600;
}

.preview-dialog__layout {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(260px, 0.65fr);
  gap: 28px;
}

.preview-dialog__paper {
  max-height: 70vh;
  overflow-y: auto;
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--canvas-deep);
}

.preview-dialog__side {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding-top: 2px;
}

.preview-dialog__desc {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.85;
}

.preview-dialog__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin: 16px 0 0;
  padding: 14px 0 0;
  border-top: 1px solid var(--line);
  list-style: none;
  color: var(--faint);
  font-size: 11px;
}

.preview-dialog__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.preview-dialog__hint {
  margin-top: auto;
  padding-top: 18px;
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.7;
}

@media (max-width: 760px) {
  .preview-dialog__layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 18px;
  }

  .preview-dialog__paper {
    max-height: 52vh;
    padding: 12px;
  }
}

@media (max-width: 560px) {
  .mine-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .mine-toolbar__search {
    width: 100%;
  }

  .mine-toolbar__side {
    width: 100%;
    justify-content: space-between;
  }

  .mine-card__preview {
    height: 168px;
  }

  .mine-card__actions {
    flex-wrap: wrap;
    row-gap: 8px;
  }
}
</style>
