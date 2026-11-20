<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { LIST_COLOR_PRESETS, listColorToken } from '../data/listColors'

/**
 * 无序 / 有序的标记样式。value 写进 data-list（含默认的 disc / decimal），
 * 这样纸面能严格按用户选择渲染；lines 是下拉卡片里三行示例，让用户点之前就能看出序号长什么样。
 */
const UL_STYLES = [
  { value: 'none', label: '无', none: true },
  { value: 'disc', label: '实心圆', lines: ['●', '●', '●'] },
  { value: 'circle', label: '空心圆', lines: ['○', '○', '○'] },
  { value: 'square', label: '实心方块', lines: ['■', '■', '■'] },
  { value: 'square-open', label: '空心方块', lines: ['▫', '▫', '▫'] },
  { value: 'diamond', label: '实心菱形', lines: ['◆', '◆', '◆'], small: true },
  { value: 'diamond-open', label: '空心菱形', lines: ['◇', '◇', '◇'], small: true },
  { value: 'triangle', label: '实心三角', lines: ['▸', '▸', '▸'] },
  { value: 'check', label: '对勾', lines: ['✓', '✓', '✓'] }
]
const OL_STYLES = [
  { value: 'none', label: '无', none: true },
  { value: 'decimal', label: '数字序号', lines: ['1.', '2.', '3.'] },
  { value: 'paren', label: '括号序号', lines: ['（1）', '（2）', '（3）'] },
  { value: 'paren-right', label: '右括号序号', lines: ['1)', '2)', '3)'] },
  { value: 'cjk', label: '中文数字', lines: ['一、', '二、', '三、'] },
  { value: 'roman-upper', label: '大写罗马', lines: ['I.', 'II.', 'III.'] },
  { value: 'roman-lower', label: '小写罗马', lines: ['i.', 'ii.', 'iii.'] },
  { value: 'alpha-upper', label: '大写字母', lines: ['A.', 'B.', 'C.'] },
  { value: 'alpha-lower', label: '小写字母', lines: ['a.', 'b.', 'c.'] }
]

/**
 * 列表标记取色：工具栏上的「1.」按钮弹出小面板，点色块即选色（另含自定义取色）。
 * 选中的 #rrggbb 写进 data-list-color；选「跟随模板主题色」删掉该属性，标记回到模板主题色。
 * 真实着色由渲染层把该值设成列表的内联 --list-color，var(--list-color, 正文色) 接管。
 */

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '在这里输入内容。' },
  minHeight: { type: Number, default: 120 }
})

const emit = defineEmits(['update:modelValue'])
const editor = ref(null)
const active = ref({})
const menu = ref('')
/** 编辑器内最后一次有效选区：取色面板点色块时编辑器已经失焦，靠它把光标放回去。 */
const savedRange = ref(null)

/**
 * 打开已有内容时把 data-list-color 回填成内联 --list-color。
 * 存档里只有 data-*（内联 style 会被后端清洗掉），不回填的话重新打开编辑器
 * 标记就退回正文色，用户会以为「颜色没保存住」。
 */
function hydrateListColors() {
  if (!editor.value) return
  for (const list of editor.value.querySelectorAll('ul[data-list-color], ol[data-list-color]')) {
    const token = listColorToken(list.dataset.listColor)
    if (token) list.style.setProperty('--list-color', token)
    else list.removeAttribute('data-list-color')
  }
}

function sync(value) {
  if (!editor.value) return
  if (document.activeElement === editor.value) return
  if (editor.value.innerHTML !== value) {
    editor.value.innerHTML = value || ''
    hydrateListColors()
  }
}

watch(() => props.modelValue, sync)
// 打开编辑器时把已有内容回显进来；watch 只在后续变更时触发，首次挂载必须手动同步一次。
onMounted(() => sync(props.modelValue))

function emitValue() {
  if (!editor.value) return
  emit('update:modelValue', editor.value.innerHTML)
}

/**
 * 执行浏览器原生命令。撤销 / 恢复也走这里（undo / redo）：contenteditable 自带历史栈，
 * 打字、加粗、删改都记在里面，不需要自己维护一份。代价是我们用 DOM 直接改的
 * 列表样式 / 标记颜色不进这个栈，撤销不会回退它们——所以每次改完都要 emitValue()，
 * 让父组件拿到当前 HTML，避免和 history 里的快照对不上。
 */
function command(name, value = null) {
  editor.value?.focus()
  document.execCommand(name, false, value)
  emitValue()
  updateActive()
}

/** 光标所在的有序 / 无序列表，取最近的 li 的父节点；不在列表里返回 null。 */
function activeList() {
  const selection = window.getSelection()
  if (!selection || !selection.rangeCount) return null
  let node = selection.anchorNode
  if (!node) return null
  if (node.nodeType !== Node.ELEMENT_NODE) node = node.parentElement
  const list = node?.closest?.('li')?.parentElement
  return list && (list.tagName === 'OL' || list.tagName === 'UL') ? list : null
}

/**
 * 记住编辑器内的选区。点工具栏上的按钮会把焦点移出 contenteditable，
 * 浏览器随即清掉选区，activeList() 就找不到列表了——取色面板点下去「没反应」正是这个原因。
 * 用 selectionchange 持续记录，比在按钮上逐个 @mousedown.prevent 更稳（原生取色输入框没法 prevent）。
 */
function rememberSelection() {
  const selection = window.getSelection()
  if (!selection || !selection.rangeCount || !editor.value) return
  const range = selection.getRangeAt(0)
  if (!editor.value.contains(range.commonAncestorContainer)) return
  savedRange.value = range.cloneRange()
}

/** 把光标放回上次记录的选区；记录点已被重排掉时静默跳过。 */
function restoreSelection() {
  const selection = window.getSelection()
  const range = savedRange.value
  if (!selection || !range || !editor.value?.contains(range.startContainer)) return
  selection.removeAllRanges()
  selection.addRange(range)
}

/**
 * 切换列表标记：沿用浏览器生成 / 已有的 <ul>、<ol>，只改 data-list，
 * 所以换样式不会重排列表项、不丢文字；每一种样式（含默认）都写入标记。
 */
function applyList(kind, style) {
  editor.value?.focus()
  const tag = kind === 'ul' ? 'UL' : 'OL'
  const current = activeList()

  // 「无」：光标本来就在同类型列表里时，再执行一次切换命令即退回普通段落。
  if (style === 'none') {
    if (current && current.tagName === tag) {
      command(kind === 'ul' ? 'insertUnorderedList' : 'insertOrderedList')
    }
    menu.value = ''
    return
  }

  let list = current
  if (!list || list.tagName !== tag) {
    command(kind === 'ul' ? 'insertUnorderedList' : 'insertOrderedList')
    list = activeList()
  }
  if (list && list.tagName === tag) {
    // 默认样式（disc / decimal）也必须写入：ls 靠 data-list 判定用户选择，
    // 删掉属性会让列表退化成「裸列表」，模板自带的 li::before 装饰就会接管，
    // 例如选了数字序号却显示成圆点。历史无属性的裸列表仍由纸面兜底渲染。
    list.dataset.list = style
    normalizeEditorLists()
  }
  emitValue()
  updateActive()
  menu.value = ''
}

/**
 * 给光标所在的列表设置标记颜色：把 #rrggbb 写进 data-list-color。
 * 「主题色」（value 为 null / 空）删掉属性，标记回到模板主题色。
 * 编辑器里同时设一条内联 --list-color，标记立刻所见即所得；
 * 存档值只认 data-list-color，渲染层再据此设内联样式。
 */
function applyListColor(value) {
  if (!editor.value) return
  // 取色面板里的色块 / 原生取色框都会让编辑器失焦，先把光标放回列表再取列表。
  editor.value.focus()
  restoreSelection()
  const list = activeList()
  if (!list) return
  const token = listColorToken(value)
  if (token) {
    list.dataset.listColor = token
    list.style.setProperty('--list-color', token)
  } else {
    delete list.dataset.listColor
    list.style.removeProperty('--list-color')
  }
  emitValue()
  updateActive()
}

/**
 * 相邻的同款列表合成一个：用户逐段应用列表时，浏览器会为每段各建一个 <ol>，
 * 每个都从 1 开始。这里把前 / 后紧邻的同 tag、同 data-list 列表并进来，序号才能接着排。
 */function mergeAdjacentLists(list) {
  const selection = window.getSelection()
  const anchorNode = selection?.anchorNode || null
  const anchorOffset = selection?.anchorOffset || 0

  const sameKind = (other) => other
    && other.tagName === list.tagName
    && (other.dataset.list || '') === (list.dataset.list || '')

  let current = list
  let prev = current.previousElementSibling
  while (sameKind(prev)) {
    while (current.firstChild) prev.appendChild(current.firstChild)
    current.remove()
    current = prev
    prev = current.previousElementSibling
  }

  let next = current.nextElementSibling
  while (sameKind(next)) {
    while (next.firstChild) current.appendChild(next.firstChild)
    next.remove()
    next = current.nextElementSibling
  }

  // 节点只是换了父级，原来的文本节点还在，把光标按原偏移放回去。
  if (anchorNode && document.contains(anchorNode)) {
    const range = document.createRange()
    const max = anchorNode.nodeType === Node.TEXT_NODE
      ? anchorNode.length
      : anchorNode.childNodes.length
    range.setStart(anchorNode, Math.min(anchorOffset, max))
    range.collapse(true)
    selection.removeAllRanges()
    selection.addRange(range)
  }
  return current
}

/**
 * Chrome 对单个段落执行 insertOrderedList 时会生成 <p><ol>…</ol></p>。
 * 这样每一行都是被 <p> 包住的独立 <ol>，彼此不是兄弟节点，合并逻辑就失效了。
 * 把只包着一个列表的无意义 P / DIV 拆掉，让列表回到编辑区顶层。
 */
function unwrapListWrapper(list) {
  let parent = list.parentElement
  while (parent && parent !== editor.value && (parent.tagName === 'P' || parent.tagName === 'DIV')) {
    const onlyList = [...parent.childNodes].every((node) =>
      node === list || (node.nodeType === Node.TEXT_NODE && !node.textContent.trim())
    )
    if (!onlyList) break
    parent.replaceWith(list)
    parent = list.parentElement
  }
}

/**
 * 编辑过程中持续修正列表：浏览器在带 data-list 的列表里按回车时，常把新的一行
 * 拆成紧邻但没带样式标记的新列表，于是序号重新从 1 开始。这里给相邻同类型列表
 * 继承样式并合并，让用户边输边看到连续序号，而不是等下一次点按钮才修。
 */
function normalizeEditorLists() {
  if (!editor.value) return false
  let changed = false

  for (const list of editor.value.querySelectorAll('ol, ul')) {
    const parent = list.parentElement
    unwrapListWrapper(list)
    if (parent !== list.parentElement) changed = true
  }

  let node = editor.value.firstElementChild

  while (node) {
    if (node.tagName === 'OL' || node.tagName === 'UL') {
      const next = node.nextElementSibling
      if (next && next.tagName === node.tagName) {
        const nodeStyle = node.dataset.list || ''
        const nextStyle = next.dataset.list || ''
        const style = nodeStyle || nextStyle
        if (style && nodeStyle !== nextStyle) {
          if (!nodeStyle) {
            node.dataset.list = style
            changed = true
          }
          if (!nextStyle) {
            next.dataset.list = style
            changed = true
          }
        }
      }
      const merged = mergeAdjacentLists(node)
      if (merged !== node) changed = true
      node = merged.nextElementSibling
    } else {
      node = node.nextElementSibling
    }
  }
  return changed
}

function handleInput() {
  normalizeEditorLists()
  emitValue()
  updateActive()
}

function toggleMenu(kind) {
  editor.value?.focus()
  // 打开面板前刷新一次，当前列表样式才能在卡片上高亮。
  updateActive()
  menu.value = menu.value === kind ? '' : kind
}

function updateActive() {
  const list = activeList()
  const isUl = list?.tagName === 'UL'
  const isOl = list?.tagName === 'OL'
  active.value = {
    bold: document.queryCommandState('bold'),
    italic: document.queryCommandState('italic'),
    underline: document.queryCommandState('underline'),
    strike: document.queryCommandState('strikeThrough'),
    unordered: isUl,
    ordered: isOl,
    listStyle: list ? list.dataset.list || (isUl ? 'disc' : 'decimal') : '',
    listColor: list ? (listColorToken(list.dataset.listColor) || '') : ''
  }
}

function onDocumentPointerDown(event) {
  if (!menu.value) return
  if (event.target instanceof Element && event.target.closest('.list-control, .color-control')) return
  menu.value = ''
}

function onDocumentKeydown(event) {
  if (event.key === 'Escape') menu.value = ''
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentPointerDown)
  document.addEventListener('keydown', onDocumentKeydown)
  document.addEventListener('selectionchange', rememberSelection)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentPointerDown)
  document.removeEventListener('keydown', onDocumentKeydown)
  document.removeEventListener('selectionchange', rememberSelection)
})

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
      <button type="button" title="加粗" :class="{ active: active.bold }" @mousedown.prevent @click="command('bold')"><span class="tool-letter tool-letter--bold">B</span></button>
      <button type="button" title="斜体" :class="{ active: active.italic }" @mousedown.prevent @click="command('italic')"><span class="tool-letter tool-letter--italic">I</span></button>
      <button type="button" title="下划线" :class="{ active: active.underline }" @mousedown.prevent @click="command('underline')"><span class="tool-letter tool-letter--underline">U</span></button>
      <button type="button" title="删除线" :class="{ active: active.strike }" @mousedown.prevent @click="command('strikeThrough')"><span class="tool-letter tool-letter--strike">S</span></button>
      <span class="toolbar-sep" />
      <div class="list-control">
        <button type="button" class="list-btn" title="选择无序列表样式" :class="{ active: active.unordered, open: menu === 'ul' }" @mousedown.prevent @click="toggleMenu('ul')">
          <svg class="tool-icon tool-icon--solid" viewBox="0 0 1024 1024" aria-hidden="true">
            <path d="M338.432 76.8h604.5184a56.832 56.832 0 0 1 0 113.664H338.432a56.832 56.832 0 0 1 0-113.664z m0 384h604.5184a56.832 56.832 0 0 1 0 113.664H338.432a56.832 56.832 0 1 1 0-113.664z m0 384h604.5184a56.832 56.832 0 0 1 0 113.664H338.432a56.832 56.832 0 1 1 0-113.664z m-223.232-614.4a89.6 89.6 0 1 1 0-179.2 89.6 89.6 0 0 1 0 179.2z m0 384a89.6 89.6 0 1 1 0-179.2 89.6 89.6 0 0 1 0 179.2z m0 384a89.6 89.6 0 1 1 0-179.2 89.6 89.6 0 0 1 0 179.2z" />
          </svg>
          <svg class="caret-icon" viewBox="0 0 8 8" aria-hidden="true"><path d="M1.6 3l2.4 2.4L6.4 3" /></svg>
        </button>
        <div v-if="menu === 'ul'" class="list-panel">
          <p class="list-panel__title">无序列表</p>
          <div class="list-grid">
            <button
              v-for="opt in UL_STYLES"
              :key="opt.value"
              type="button"
              class="style-card"
              :class="{ selected: active.unordered && active.listStyle === opt.value }"
              :title="opt.label"
              @mousedown.prevent
              @click="applyList('ul', opt.value)"
            >
              <span v-if="opt.none" class="style-none">
                <svg viewBox="0 0 18 18" aria-hidden="true">
                  <circle cx="9" cy="9" r="6.2" />
                  <path d="M4.6 13.4L13.4 4.6" />
                </svg>
              </span>
              <span v-else class="style-lines">
                <span v-for="(line, index) in opt.lines" :key="index" class="style-line">
                  <span class="style-mark" :class="{ 'style-mark--small': opt.small }">{{ line }}</span>
                  <span class="style-bar" />
                </span>
              </span>
            </button>
          </div>
        </div>
      </div>
      <div class="list-control">
        <button type="button" class="list-btn" title="选择有序列表样式" :class="{ active: active.ordered, open: menu === 'ol' }" @mousedown.prevent @click="toggleMenu('ol')">
          <svg class="tool-icon tool-icon--solid" viewBox="0 0 1102 1024" aria-hidden="true">
            <path d="M352.055 65.105h646.09c35.74 0 64.632 29.522 64.632 65.813a65.184 65.184 0 0 1-64.554 65.814H351.976a65.184 65.184 0 0 1-64.554-65.814c0-36.291 28.892-65.813 64.554-65.813z m0 394.88h646.09c35.74 0 64.632 29.444 64.632 65.735a65.184 65.184 0 0 1-64.554 65.814H351.976a65.184 65.184 0 0 1-64.554-65.814c0-36.291 28.892-65.734 64.554-65.734z m0 394.803h646.09c35.74 0 64.632 29.443 64.632 65.813a65.184 65.184 0 0 1-64.554 65.735H351.976a65.184 65.184 0 0 1-64.554-65.735c0-36.37 28.892-65.813 64.554-65.813zM51.643 69.12V38.18c9.92 0.787 19.839 0.787 29.68 0a40.307 40.307 0 0 0 31.016-28.97 38.89 38.89 0 0 0 0-9.211h41.41v229.639h-48.495V69.04h-53.61z m0 509.975a130.682 130.682 0 0 1 45.188-49.36c14.17-9.683 27.553-20.468 40.15-32.277a48.494 48.494 0 0 0 14.17-33.536 37.315 37.315 0 0 0-7.794-24.405 28.183 28.183 0 0 0-23.224-9.84 27.632 27.632 0 0 0-31.017 15.115 78.252 78.252 0 0 0-5.196 28.34H41.96c-0.079-16.61 3.464-33.064 10.313-48.021a64.318 64.318 0 0 1 64.554-34.875c21.098-1.102 41.724 6.22 57.547 20.39 14.328 14.013 22.122 33.694 21.334 53.926a74.63 74.63 0 0 1-14.878 46.053c-9.447 11.494-20.547 21.413-32.907 29.6l-18.107 13.226-23.223 17.713A51.8 51.8 0 0 0 96.2 583.661h99.507v40.15H39.362c0.236-15.745 3.7-31.175 10.313-45.424l1.968 0.708zM83.92 948.233c-0.157 7.873 1.338 15.745 4.566 22.988 5.51 11.415 17.32 18.106 29.68 17.162a32.513 32.513 0 0 0 20.704-6.613 34.639 34.639 0 0 0 9.683-26.294 30.309 30.309 0 0 0-18.107-30.939 91.714 91.714 0 0 0-32.277-8.58V883.05c10.234 0.393 20.626-1.181 30.388-4.566a27.081 27.081 0 0 0 14.8-28.341 30.624 30.624 0 0 0-7.715-21.728 27.553 27.553 0 0 0-21.334-8.502 26.924 26.924 0 0 0-23.224 10.47 44.086 44.086 0 0 0-7.085 27.711H41.88c0.315-11.257 2.362-22.358 5.826-32.907a74.316 74.316 0 0 1 17.398-25.034 60.539 60.539 0 0 1 20.704-12.517 90.133 90.133 0 0 1 29.68-3.306 78.488 78.488 0 0 1 52.98 17.161c13.384 11.337 20.705 28.341 20.076 46.054a51.958 51.958 0 0 1-12.281 34.875 40.78 40.78 0 0 1-15.51 11.809c6.771 1.574 12.912 5.195 17.478 10.549 11.808 11.415 18.106 27.553 17.398 44.085a75.103 75.103 0 0 1-19.996 51.958c-15.745 15.824-37.394 24.011-59.437 22.437a71.954 71.954 0 0 1-64.633-32.277 97.46 97.46 0 0 1-11.021-44.716l43.298 1.968z" />
          </svg>
          <svg class="caret-icon" viewBox="0 0 8 8" aria-hidden="true"><path d="M1.6 3l2.4 2.4L6.4 3" /></svg>
        </button>
        <div v-if="menu === 'ol'" class="list-panel">
          <p class="list-panel__title">有序列表</p>
          <div class="list-grid">
            <button
              v-for="opt in OL_STYLES"
              :key="opt.value"
              type="button"
              class="style-card"
              :class="{ selected: active.ordered && active.listStyle === opt.value }"
              :title="opt.label"
              @mousedown.prevent
              @click="applyList('ol', opt.value)"
            >
              <span v-if="opt.none" class="style-none">
                <svg viewBox="0 0 18 18" aria-hidden="true">
                  <circle cx="9" cy="9" r="6.2" />
                  <path d="M4.6 13.4L13.4 4.6" />
                </svg>
              </span>
              <span v-else class="style-lines">
                <span v-for="(line, index) in opt.lines" :key="index" class="style-line">
                  <span class="style-mark style-mark--wide" :class="{ 'style-mark--small': opt.small }">{{ line }}</span>
                  <span class="style-bar" />
                </span>
              </span>
            </button>
          </div>
        </div>
      </div>
      <div class="color-control">
        <button
          type="button"
          class="color-btn"
          title="设置序号颜色"
          :class="{ active: active.unordered || active.ordered, open: menu === 'color' }"
          :disabled="!active.unordered && !active.ordered"
          @mousedown.prevent
          @click="toggleMenu('color')"
        >
          <svg class="color-mark" viewBox="0 0 14 14" aria-hidden="true" :style="{ '--mark-color': active.listColor || 'var(--accent)' }">
            <text class="color-mark__digit" x="1.7" y="9.2">1.</text>
            <rect class="color-mark__bar" x="1.4" y="10.9" width="11.2" height="2" rx="1" />
          </svg>
          <svg class="caret-icon" viewBox="0 0 8 8" aria-hidden="true"><path d="M1.6 3l2.4 2.4L6.4 3" /></svg>
        </button>
        <div v-if="menu === 'color'" class="list-panel color-panel">
          <p class="list-panel__title">序号颜色</p>
          <button
            type="button"
            class="color-auto"
            :class="{ selected: !active.listColor }"
            @mousedown.prevent
            @click="applyListColor(null)"
          >
            <span class="color-auto__dot" aria-hidden="true" />
            跟随模板主题色
          </button>
          <div class="color-grid">
            <button
              v-for="color in LIST_COLOR_PRESETS"
              :key="color"
              type="button"
              class="color-dot"
              :class="{ selected: active.listColor === color }"
              :style="{ '--dot': color }"
              :title="color"
              @mousedown.prevent
              @click="applyListColor(color)"
            />
            <label class="color-dot color-dot--custom" title="自定义颜色">
              <input type="color" :value="active.listColor || '#33403a'" @change="applyListColor($event.target.value)" />
              <svg viewBox="0 0 12 12" aria-hidden="true"><path d="M6 2.5v7M2.5 6h7" /></svg>
            </label>
          </div>
        </div>
      </div>
      <span class="toolbar-sep" />
      <button type="button" title="清除格式" @mousedown.prevent @click="command('removeFormat')">
        <svg class="tool-icon" viewBox="0 0 14 14" aria-hidden="true">
          <rect x="3.2" y="6.1" width="8.4" height="4.4" rx="1.2" transform="rotate(-45 7.4 8.3)" />
          <path d="M4.4 8.2l3.1 3.1" />
        </svg>
      </button>
      <span class="toolbar-sep" />
      <button type="button" title="撤销" @mousedown.prevent @click="command('undo')">
        <svg class="tool-icon" viewBox="0 0 14 14" aria-hidden="true">
          <path d="M4.6 3.2L2.2 5.6l2.4 2.4" />
          <path d="M2.2 5.6h6.4a3.4 3.4 0 0 1 0 6.8H5.2" />
        </svg>
      </button>
      <button type="button" title="恢复" @mousedown.prevent @click="command('redo')">
        <svg class="tool-icon" viewBox="0 0 14 14" aria-hidden="true">
          <path d="M9.4 3.2l2.4 2.4-2.4 2.4" />
          <path d="M11.8 5.6H5.4a3.4 3.4 0 0 0 0 6.8H8.8" />
        </svg>
      </button>
      <span class="toolbar-tip">保存后自动过滤不安全标签</span>
    </div>
    <div
      ref="editor"
      class="rich-body"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      :data-placeholder="placeholder"
      @input="handleInput"
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
  /* 列表样式菜单要溢出到编辑区上方，这里不能再裁切，圆角交给工具栏自己收。 */
  overflow: visible;
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
  gap: 2px;
  padding: 4px 8px;
  border-bottom: 1px solid var(--line);
  border-radius: 8px 8px 0 0;
  background: var(--paper-deep);
}

.rich-toolbar button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  min-width: 26px;
  padding: 0 5px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--ink-soft);
  font-family: var(--sans);
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  flex-shrink: 0;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease;
}

.rich-toolbar button:hover {
  border-color: var(--line);
  background: var(--paper);
}

/* 四个字母用同一个 16px 盒子居中，只换字重 / 斜体 / 装饰线，
   这样 B、I、U、S 不会因为字体度量不同而各偏一边。 */
.tool-letter {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 16px;
  font-size: 12px;
  line-height: 1;
}

/* 用描边加重而不是换粗体字重：粗体字体的行度量不同，会把整个字形顶高 1px。 */
.tool-letter--bold {
  -webkit-text-stroke: 0.4px currentColor;
}

.tool-letter--italic {
  font-style: italic;
}

.tool-letter--underline {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.tool-letter--strike {
  text-decoration: line-through;
}

/* 列表主按钮只留图标，右侧小箭头展开图形化样式面板。 */
.list-control {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.list-control + .list-control {
  margin-left: 2px;
}

.rich-toolbar button.list-btn {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  min-width: 30px;
  padding: 0 3px;
}

/* 箭头并进主按钮：整块都能点开样式面板，不再单独放一个难点的窄箭头。 */
.rich-toolbar button.list-btn .caret-icon {
  width: 7px;
  height: 7px;
  margin-left: 1px;
  color: var(--muted);
}

.rich-toolbar button.list-btn:hover .caret-icon,
.rich-toolbar button.list-btn.open .caret-icon {
  color: var(--accent);
}

.caret-icon {
  width: 8px;
  height: 8px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* 取色器：和列表按钮同构（图标 + 小箭头），图标是「1.」——直接说明它管的是序号颜色，
   选中颜色后这个「1.」就按选中的颜色显示。 */
.color-control {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.rich-toolbar button.color-btn {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  min-width: 30px;
  padding: 0 3px;
}

/* 「1.」说明作用对象是序号，下面的色条沿用「字体颜色」的成熟约定，并且永远是当前
   生效的颜色（未选色时是主题色），一眼就能看出这是取色而不是又一个列表样式按钮。 */
.color-mark {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.color-mark__digit {
  fill: currentColor;
  font-family: var(--mono);
  font-size: 9.2px;
  font-weight: 500;
}

.color-mark__bar {
  fill: var(--mark-color, var(--accent));
}

.rich-toolbar button.color-btn:hover .caret-icon,
.rich-toolbar button.color-btn.open .caret-icon {
  color: var(--accent);
}

.rich-toolbar button.color-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.rich-toolbar button.color-btn:disabled:hover {
  border-color: transparent;
  background: transparent;
}

/* 色板面板：比列表样式面板窄一些，六个一行刚好放下 30 个预设色。 */
.list-panel.color-panel {
  width: 214px;
}

.rich-toolbar .color-auto {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  height: 26px;
  margin: 0 0 8px;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fffefb;
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1;
  cursor: pointer;
}

.rich-toolbar .color-auto:hover {
  border-color: rgba(10, 125, 118, 0.4);
  background: #fff;
}

.rich-toolbar .color-auto.selected {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
  box-shadow: 0 0 0 1px rgba(10, 125, 118, 0.3) inset;
}

/* 主题色是模板给的，编辑器里不知道具体色值，用一小段渐变示意「跟着模板走」。 */
.color-auto__dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: conic-gradient(from 200deg, var(--accent), var(--copper), var(--gold), var(--accent));
  box-shadow: 0 0 0 1px rgba(25, 34, 30, 0.1) inset;
}

.color-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 6px;
}

.rich-toolbar .color-dot {
  position: relative;
  width: 100%;
  min-width: 0;
  height: auto;
  aspect-ratio: 1 / 1;
  padding: 0;
  border: 1px solid rgba(25, 34, 30, 0.12);
  border-radius: 50%;
  background: var(--dot, transparent);
  cursor: pointer;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
}

.rich-toolbar .color-dot:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(25, 34, 30, 0.18);
}

/* 选中态用「白色内圈 + 主题色外环」的环状描边，叠在任何底色上都能看清。 */
.rich-toolbar .color-dot.selected {
  box-shadow: 0 0 0 2px #fffefb, 0 0 0 3.5px var(--accent);
}

.rich-toolbar .color-dot--custom {
  display: grid;
  place-items: center;
  border: 1px dashed var(--line-strong);
  background: #fffefb;
  color: var(--muted);
}

.rich-toolbar .color-dot--custom:hover {
  border-color: var(--accent);
  color: var(--accent);
  box-shadow: none;
}

.color-dot--custom svg {
  width: 11px;
  height: 11px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.4;
  stroke-linecap: round;
  pointer-events: none;
}

/* 原生取色框铺满整个圆形按钮但完全透明，点哪儿都能唤起系统取色面板。 */
.color-dot--custom input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  opacity: 0;
  cursor: pointer;
}


.list-panel {
  position: absolute;
  top: calc(100% + 7px);
  left: 0;
  z-index: 30;
  width: 330px;
  max-width: calc(100vw - 28px);
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--paper);
  box-shadow: 0 18px 40px -22px rgba(24, 38, 31, 0.35), 0 4px 14px rgba(24, 38, 31, 0.06);
}

.list-panel__title {
  margin: 0 0 8px 2px;
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.14em;
}

.list-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
}

.rich-toolbar .style-card {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-width: 0;
  height: 58px;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fffefb;
}

.rich-toolbar .style-card:hover {
  border-color: rgba(10, 125, 118, 0.4);
  background: #fff;
}

.rich-toolbar .style-card.selected {
  border-color: var(--accent);
  background: var(--accent-soft);
  box-shadow: 0 0 0 1px rgba(10, 125, 118, 0.35) inset;
}

.style-lines {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.style-line {
  display: flex;
  align-items: center;
  gap: 5px;
}

.style-mark {
  width: 20px;
  flex-shrink: 0;
  color: #42504a;
  font-size: 9px;
  font-style: normal;
  line-height: 1;
  text-align: right;
  white-space: nowrap;
}

.style-mark--wide {
  width: 28px;
}

.style-mark--small {
  font-size: 7px;
}

.style-bar {
  flex: 1;
  height: 1px;
  background: rgba(25, 34, 30, 0.28);
}

.style-none {
  display: grid;
  place-items: center;
  color: var(--muted);
}

.style-none svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.1;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* 工具栏按钮统一 26px 方形，图标居中；撤销 / 恢复与清除格式都是纯图标，靠 title 说明。 */
.tool-icon {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* 有序 / 无序列表图标是实心路径，改用 currentColor 填充以跟随按钮状态色。 */
.tool-icon--solid {
  fill: currentColor;
  stroke: none;
}

.rich-toolbar button.active {
  border-color: rgba(10, 125, 118, 0.2);
  background: var(--accent-soft);
  color: var(--accent);
}

.toolbar-sep {
  width: 1px;
  height: 14px;
  margin: 0 3px;
  background: var(--line);
}

.toolbar-tip {
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
  overflow: hidden;
  color: var(--faint);
  font-size: 10px;
  white-space: nowrap;
  text-overflow: ellipsis;
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

/* 用户给列表选过颜色时，编辑器里也按这个颜色显示标记，所见即所得。
   颜色以内联 --list-color 设在该列表上（见 applyListColor），
   ::marker / ::before 都用 var(--list-color, 正文色) 着色，编辑器与纸面同一套逻辑；
   未选色时 --list-color 未定义，回落到当前正文颜色。 */

.rich-body :deep(li)::marker {
  color: var(--list-color, currentColor);
}

.rich-body :deep(ol[data-list="paren"] > li::before),
.rich-body :deep(ol[data-list="paren-right"] > li::before) {
  color: var(--list-color, currentColor);
}

.rich-body :deep(ul[data-list="disc"]) {
  list-style-type: disc;
}

.rich-body :deep(ul[data-list="circle"]) {
  list-style-type: circle;
}

.rich-body :deep(ul[data-list="square"]) {
  list-style-type: square;
}

.rich-body :deep(ul[data-list="square-open"]) {
  list-style-type: "□   ";
}

.rich-body :deep(ul[data-list="square-open"] > li::marker) {
  font-size: 0.62em;
}

.rich-body :deep(ul[data-list="diamond"]) {
  list-style-type: "◆   ";
}

.rich-body :deep(ul[data-list="diamond-open"]) {
  list-style-type: "◇   ";
}

.rich-body :deep(ul[data-list="triangle"]) {
  list-style-type: "▸   ";
}

/* 菱形字形天生比圆点大一圈，单独压小，和其余标记视觉等宽。 */
.rich-body :deep(ul[data-list="diamond"] > li::marker),
.rich-body :deep(ul[data-list="diamond-open"] > li::marker) {
  font-size: 0.62em;
}

.rich-body :deep(ul[data-list="check"] > li::marker) {
  font-size: 0.75em;
}

.rich-body :deep(ul[data-list="check"]) {
  list-style-type: "✓   ";
}

.rich-body :deep(ol[data-list="decimal"]) {
  list-style-type: decimal;
}

.rich-body :deep(ol[data-list="cjk"]) {
  list-style-type: miniwho-cjk;
}

.rich-body :deep(ol[data-list="roman-upper"]) {
  list-style-type: upper-roman;
}

.rich-body :deep(ol[data-list="roman-lower"]) {
  list-style-type: lower-roman;
}

.rich-body :deep(ol[data-list="alpha-upper"]) {
  list-style-type: upper-alpha;
}

.rich-body :deep(ol[data-list="alpha-lower"]) {
  list-style-type: lower-alpha;
}

/* 带括号的序号：沿用 <ol> 的层级，只把标记换成（1）（2）。
   伪元素右对齐挂在行首左侧、宽度自适应且不换行，两位数序号也不会挤到正文上。 */
.rich-body :deep(ol[data-list="paren"]) {
  padding-left: 1.2em;
  list-style: none;
  counter-reset: paren-item;
}

.rich-body :deep(ol[data-list="paren"] > li) {
  position: relative;
  counter-increment: paren-item;
}

/* 等宽字体只作用于序号标记，正文保持模板原字体；缩小字号让宽括号完整挂在左侧。 */
.rich-body :deep(ol[data-list="paren"] > li::before) {
  content: "（" counter(paren-item) "）";
  position: absolute;
  right: calc(100% + 0.08em);
  top: 0;
  font-family: var(--mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.62em;
  line-height: 1.7;
  white-space: nowrap;
}

.rich-body :deep(ol[data-list="paren-right"]) {
  padding-left: 1.2em;
  list-style: none;
  counter-reset: paren-right-item;
}

.rich-body :deep(ol[data-list="paren-right"] > li) {
  position: relative;
  counter-increment: paren-right-item;
}

.rich-body :deep(ol[data-list="paren-right"] > li::before) {
  content: counter(paren-right-item) ")";
  position: absolute;
  right: calc(100% + 0.12em);
  top: 0;
  font-family: var(--mono);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
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
