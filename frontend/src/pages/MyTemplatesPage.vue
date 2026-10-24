<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, InfoFilled, Plus, Upload, Warning } from '@element-plus/icons-vue'
import AppLayout from '../layouts/AppLayout.vue'
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

const packageInput = ref(null)
const importing = ref(false)
const busyCode = ref('')
const packageProblems = ref([])
const packageDialog = ref(false)

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

function openPackagePicker() {
  packageInput.value?.click()
}

async function onPackageFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
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

      <div v-if="!mine.length" class="surface mine-empty">
        <el-icon><InfoFilled /></el-icon>
        <h2>还没有导入过模板包</h2>
        <p>
          收到别人的 <code>.json</code> 模板包，点上面的「导入模板包」即可；
          也可以从模板中心把喜欢的官方版式导出成包，改改配色与间距再导回来。
          字段含义与可写的 CSS 见项目里的 <code>docs/模板包格式.md</code>。
        </p>
        <el-button @click="openPackagePicker">
          <el-icon><Plus /></el-icon> 导入模板包
        </el-button>
      </div>

      <div v-else class="mine-grid">
        <article v-for="tpl in mine" :key="tpl.code" class="surface mine-card">
          <div class="mine-card__thumb">
            <TemplateThumb :content="sampleContent" :template="tpl" :avatar="sampleAvatar" />
          </div>
          <div class="mine-card__body">
            <header>
              <h2>{{ tpl.name }}</h2>
              <span class="status-tag" :class="tpl.visibility === 'public' ? 'is-on' : 'is-off'">
                <i></i>{{ tpl.visibility === 'public' ? '已分享到模板中心' : '仅自己可见' }}
              </span>
            </header>
            <p class="mine-card__meta mono-code">{{ tpl.code }}</p>
            <p class="mine-card__desc">{{ tpl.description || '未填写模板描述' }}</p>
            <ul class="mine-card__facts">
              <li>作者 {{ tpl.author || '未署名' }}</li>
              <li>v{{ tpl.version || '1.0.0' }}</li>
              <li>{{ LAYOUT_LABELS[configOf(tpl).layout] || '单栏' }}</li>
              <li>被 {{ tpl.usageCount ?? 0 }} 份简历使用</li>
            </ul>

            <div class="mine-card__switches">
              <label class="switch-row">
                <el-switch
                  :model-value="tpl.visibility === 'public'"
                  :loading="busyCode === tpl.code"
                  @update:model-value="(value) => updateShare(tpl, Boolean(value), tpl.exportable !== false)"
                />
                <span>
                  <strong>分享到模板中心</strong>
                  <small>本站其他用户可在模板中心看到「{{ tpl.author || '未署名' }} 分享」并选用</small>
                </span>
              </label>
              <label class="switch-row" :class="{ 'is-muted': tpl.visibility !== 'public' }">
                <el-switch
                  :model-value="tpl.exportable !== false"
                  :disabled="tpl.visibility !== 'public'"
                  :loading="busyCode === tpl.code"
                  @update:model-value="(value) => updateShare(tpl, true, Boolean(value))"
                />
                <span>
                  <strong>允许他人导出模板包</strong>
                  <small>{{ tpl.visibility === 'public'
                    ? (tpl.exportable !== false ? '关闭后别人只能选用，下载按钮对他们隐藏' : '别人只能选用，不能把包下载走')
                    : '分享后才能设置' }}</small>
                </span>
              </label>
            </div>

            <footer class="mine-card__actions">
              <el-button size="small" type="primary" link @click="useInResume(tpl)">用它新建简历</el-button>
              <el-button size="small" link :loading="busyCode === tpl.code" @click="exportPackage(tpl)">
                <el-icon><Download /></el-icon> 导出模板包
              </el-button>
              <el-button size="small" link type="danger" :loading="busyCode === tpl.code" @click="removeTemplate(tpl)">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </footer>
          </div>
        </article>
      </div>

      <p class="mine-note">
        <el-icon><Warning /></el-icon>
        <span>删除不会动已经选用它的简历：还有简历在用时服务端会拒绝删除，请先给那些简历换模板。取消分享后，别人已用的简历仍按该版式渲染，只是不再能被新简历选中。</span>
      </p>
    </div>

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
          <span class="mono">字段含义见 docs/模板包格式.md</span>
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
  max-width: 1080px;
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
  padding: 44px 28px;
  text-align: center;
}

.mine-empty .el-icon {
  color: var(--accent);
  font-size: 30px;
}

.mine-empty h2 {
  margin: 12px 0 8px;
  font-size: 18px;
}

.mine-empty p {
  max-width: 520px;
  margin: 0 auto 18px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.9;
}

.mine-empty code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--accent-soft);
  color: var(--accent);
  font-family: var(--mono);
  font-size: 11px;
}

.mine-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.mine-card {
  display: flex;
  gap: 14px;
  padding: 14px;
}

.mine-card__thumb {
  flex-shrink: 0;
  width: 108px;
}

.mine-card__body {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.mine-card__body header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mine-card__body h2 {
  font-size: 16px;
}

.mine-card__meta {
  color: var(--faint);
  font-size: 10.5px;
  letter-spacing: 0.03em;
}

.mine-card__desc {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.mine-card__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--faint);
  font-size: 11px;
}

.mine-card__switches {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 2px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.switch-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  cursor: pointer;
}

.switch-row span {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.switch-row strong {
  font-size: 12px;
  font-weight: 600;
}

.switch-row small {
  color: var(--faint);
  font-size: 10.5px;
  line-height: 1.6;
}

.switch-row.is-muted {
  opacity: 0.55;
  cursor: not-allowed;
}

.mine-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
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

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 600;
}

.status-tag i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.status-tag.is-on {
  background: var(--accent-soft);
  color: var(--accent);
}

.status-tag.is-off {
  background: rgba(120, 120, 120, 0.12);
  color: var(--muted);
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
</style>
