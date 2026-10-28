const PAGE_MM = { width: 210, height: 297 }
const BREAK_CLASS = 'paper-page-break'
const TIGHT_CLASS = 'paper-footer-tight'
const FRAG_CLASS = 'paper-item--frag'
const FOOTER_MIN_GAP = 8
/** 断点与块起点的高度比对容差（未缩放 px）。 */
const EPS = 0.6

function num(value) {
  const parsed = Number.parseFloat(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function visibleChildren(el) {
  return el ? Array.from(el.children).filter((child) => child.offsetHeight > 0) : []
}

/** 富文本块的 p 与各 li 各自成块：长条目能在 bullet 之间落刀，bullet 本身不被拦腰截断。 */
function flowBlocks(el) {
  const out = []
  if (!el) return out
  Array.from(el.children).forEach((child) => {
    if (child.offsetHeight <= 0) return
    if (child.tagName === 'UL' || child.tagName === 'OL') {
      Array.from(child.children).forEach((li) => {
        if (li.offsetHeight > 0) out.push(li)
      })
    } else {
      out.push(child)
    }
  })
  return out
}

function columnUnits(elements, geo) {
  const { box, scale } = geo
  const measured = elements.map((el) => {
    const rect = el.getBoundingClientRect()
    const style = getComputedStyle(el)
    return {
      el,
      top: (rect.top - box.top) / scale,
      height: rect.height / scale,
      marginTop: num(style.marginTop) / scale,
      marginBottom: num(style.marginBottom) / scale,
      container: el.classList.contains('paper-section'),
    }
  }).filter((unit) => unit.height > 0).sort((a, b) => a.top - b.top)

  measured.forEach((unit, index) => {
    const next = measured[index + 1]
    // 推进量取真实布局间距，模块间距与条目间距都不会漏算。
    unit.advance = next ? next.top - unit.top : unit.height + unit.marginBottom
  })
  return measured
}

function range(el, geo, edge) {
  const rect = el.getBoundingClientRect()
  const value = edge === 'bottom' ? rect.bottom : rect.top
  return (value - geo.box.top) / geo.scale
}

function chain(el, root, prop) {
  let total = 0
  let node = el
  while (node && node !== root) {
    total += num(getComputedStyle(node)[prop])
    node = node.parentElement
  }
  return total + (node ? num(getComputedStyle(node)[prop]) : 0)
}

/** 纸面的上页边（毫米）：用户设过页边距时读他设的值，否则是通用版式的 15mm。 */
function marginTopMm(root) {
  const mm = Number.parseFloat(getComputedStyle(root).getPropertyValue('--paper-mt'))
  return Number.isFinite(mm) ? mm : 15
}

/**
 * 落刀点必须是某一列里某个块的真起点，同时是每一列的空隙：
 * 两列同属一个 flex 行，只要另一块的强制分页落在别的高度上，浏览器就会再挤出一页。
 */
function cutAt(candidates, blocks, floor, ceiling) {
  for (let i = candidates.length - 1; i >= 0; i -= 1) {
    const y = candidates[i]
    if (y <= floor || y > ceiling) continue
    if (blocks.some(([start, end]) => y > start && y < end)) continue
    return y
  }
  return null
}

/**
 * 各列各自记账，但共用同一批页边界：每次只在整个 flex 行都能落刀的位置断页。
 * 返回断点列表（未缩放 px），页数即断点数 + 1。
 */
function packColumns(columns, page, root, forceAsideBreak = false, splittables = []) {
  // 通栏版式把页眉铺到纸边，根节点没有上内边距；续页仍要留出与首页相同的上页边。
  // 用户改过页边距时以他设的值为准，否则退回通用版式的 15mm。
  const minPadTop = (root.offsetWidth / PAGE_MM.width) * marginTopMm(root)
  const cols = columns.filter((column) => column.units.length).map(({ units, spans }) => ({
    units,
    next: 0,
    pageStart: 0,
    last: null,
    cursor: units[0].top,
    limits: units.map((unit) => page - chain(unit.el, root, 'paddingBottom')),
    padTops: units.map((unit) => Math.max(chain(unit.el, root, 'paddingTop'), minPadTop)),
    extraSpans: spans || [],
  }))
  if (!cols.length) return []

  const tops = []
  const blocks = []
  cols.forEach((col) => {
    col.units.forEach((unit) => {
      tops.push(unit.top)
      // 模块盒子本身就是条目容器，只有不可再分的块才挡住落刀点。
      if (!unit.container) blocks.push([unit.top, unit.top + unit.height])
    })
    col.extraSpans.forEach((span) => blocks.push(span))
  })
  const candidates = [...new Set(tops)].sort((a, b) => a - b)

  const breaks = []
  let guard = 0
  while (guard++ < 64) {
    const overflows = []
    cols.forEach((col) => {
      while (col.next < col.units.length) {
        const unit = col.units[col.next]
        const limit = col.limits[col.next]
        if (col.cursor + unit.advance <= limit) {
          col.cursor += unit.advance
          col.last = unit
          col.next += 1
          continue
        }
        if (unit.isFooter) {
          // 页脚先压缩与上一模块的间距留在本页，实在放不下才连带上一模块一起挪走。
          const tight = col.cursor - unit.marginTop + FOOTER_MIN_GAP + unit.height + unit.marginBottom
          if (tight <= limit) {
            unit.el.classList.add(TIGHT_CLASS)
            col.cursor = tight
            col.last = unit
            col.next += 1
            continue
          }
          const drag = col.next > col.pageStart + 1 && col.last ? col.last.top : unit.top
          overflows.push(Math.min(drag, unit.top))
        } else {
          overflows.push(unit.top)
        }
        return
      }
    })
    if (!overflows.length) break

    const ceiling = Math.min(...overflows)
    const floor = breaks.length ? breaks[breaks.length - 1] : cols[0].units[0].top
    const cut = cutAt(candidates, blocks, floor, ceiling)
    // 单块内容高过整页时无处落刀，交给浏览器自然断页。
    if (cut == null) break
    breaks.push(cut)
    // 断点落在长条目内部时，打印里要允许该条目分片；
    // 否则整条 break-inside: avoid 会被挪到下页，留出一大段空白。
    splittables.forEach(({ el, start, end }) => {
      if (start + EPS < cut && cut + EPS < end) el.classList.add(FRAG_CLASS)
    })

    cols.forEach((col, colIndex) => {
      const index = col.units.findIndex((unit) => unit.top >= cut - EPS)
      // 页首元素本身不能断页，否则会在 PDF 里留下空白页。
      if (index <= col.pageStart || index === -1) return
      const target = col.units[index]
      const padTop = col.padTops[index]
      // 只有正好落在断点上的块才强制分页，另一列的剩余内容随整行自然续排；
      // 两列各自打标记就会各断各的高度，PDF 比预览多出一页。
      if (forceAsideBreak && colIndex > 0) {
        target.el.classList.add(BREAK_CLASS)
        target.el.style.setProperty('--paper-page-pad-top', `${padTop}px`)
      } else if (Math.abs(target.top - cut) < EPS) {
        target.el.classList.add(BREAK_CLASS)
        target.el.style.setProperty('--paper-page-pad-top', `${padTop}px`)
      }
      col.cursor = padTop + (target.top - cut) + target.advance
      col.pageStart = index
      col.next = index + 1
      col.last = target
    })
  }

  return breaks
}

/**
 * 模块与模块内条目都作为可落点：条目从第二条起独立成块，
 * 标题与首条绑成一个不可拆的块，只有第二条起的条目能单独落到下一页。
 * 长条目的正文再按 p / li 拆成可落点（首条内容仍与标题行绑死），
 * 整条挪不动时就在 bullet 之间落刀，页面填到装得下的最后一条为止。
 */
function mainElements(root, geo) {
  const elements = []
  const blocks = []
  const splittables = []
  const header = root.querySelector('.paper-header')
  if (header) elements.push(header)
  const scope = root.querySelector('.sidebar-main') || root.querySelector('.paper-flow') || root
  scope.querySelectorAll('.paper-section').forEach((section) => {
    elements.push(section)
    const body = section.querySelector('.paper-section__body')
    const items = visibleChildren(body)
    blocks.push([range(section, geo, 'top'), items.length ? range(items[0], geo, 'top') : range(section, geo, 'bottom')])
    items.slice(1).forEach((item) => elements.push(item))
    items.forEach((item) => {
      // 自我评价 / 个人技能等模块的正文是一整段富文本，段落与 bullet 同样可拆。
      const box = item.classList.contains('paper-rich') || item.classList.contains('paper-plain')
        ? item
        : item.querySelector('.paper-rich')
      const children = flowBlocks(box)
      if (children.length <= 1) return
      elements.push(...children.slice(1))
      blocks.push([range(item, geo, 'top'), range(children[1], geo, 'top')])
      if (box !== item) splittables.push({ el: item, start: range(item, geo, 'top'), end: range(item, geo, 'bottom') })
    })
  })
  const footer = root.querySelector('.paper-footer')
  return { elements, blocks, footer, splittables }
}

function asideOf(root) {
  return root.querySelector('.paper-rail') || root.querySelector('.sidebar-aside')
}

/**
 * 按 A4 高度计算断点并打上强制分页标记。
 * 正文列与侧栏列共用同一批页边界，两列都不会在页边界被拦腰截断；
 * 屏幕上的分隔线与打印 / 服务端 PDF 用的是同一个断点，预览分页即导出分页。
 */
export function paginatePaper(root) {
  if (!(root instanceof HTMLElement) || !root.offsetWidth) return { pages: 1, marks: [] }
  const pxPerMm = root.offsetWidth / PAGE_MM.width
  const pageHeight = PAGE_MM.height * pxPerMm
  const box = root.getBoundingClientRect()
  const scale = box.width / (root.offsetWidth || box.width) || 1
  const geo = { box, scale }

  root.querySelectorAll(`.${BREAK_CLASS}`).forEach((el) => {
    el.classList.remove(BREAK_CLASS)
    el.style.removeProperty('--paper-page-pad-top')
  })
  root.querySelectorAll(`.${TIGHT_CLASS}`).forEach((el) => el.classList.remove(TIGHT_CLASS))
  root.querySelectorAll(`.${FRAG_CLASS}`).forEach((el) => el.classList.remove(FRAG_CLASS))

  const { elements, blocks, footer, splittables } = mainElements(root, geo)
  if (footer) elements.push(footer)
  const splitEls = new Set(splittables.map((item) => item.el))
  const mainUnits = columnUnits(elements, geo)
  mainUnits.forEach((unit) => {
    // 可拆的长条目不再是整块原子：落刀点由它内部的 p / li 提供。
    if (splitEls.has(unit.el)) unit.container = true
    unit.isFooter = unit.el === footer
  })

  const asideUnits = columnUnits(visibleChildren(asideOf(root)), geo)

  const breaks = packColumns(
    [{ units: mainUnits, spans: blocks }, { units: asideUnits }],
    pageHeight,
    root,
    Boolean(root.querySelector('.paper-rail')),
    splittables,
  )

  return { pages: breaks.length + 1, marks: breaks }
}
