export const TEMPLATE_FALLBACKS = [
  {
    code: 'qinghe', name: '青禾', category: '简约',
    description: '极简排字版式，只用字号、留白与发丝线分层，日期靠右。',
    tags: ['单栏', '极简', '社招', '通用'],
    config: { layout: 'minimal', accent: '#2b3137', font: 'sans', sectionStyle: 'line', density: 'standard', free: true, photo: true }
  },
  {
    code: 'moxian', name: '墨线', category: '学术',
    description: '日期独立左栏，朱红强调单位与项目，细线分区，适合科研与技术岗位。',
    tags: ['学术', '考研', '日期栏', '单栏'],
    config: { layout: 'academic', accent: '#b4232a', font: 'sans', sectionStyle: 'caps', density: 'standard', free: true, photo: true }
  },
  {
    code: 'ceying', name: '侧影', category: '双栏',
    description: '藏青通栏页眉，右侧浅灰信息栏收拢意向与技能，正文专注履历。',
    tags: ['双栏', '藏青', '社招', '高密度'],
    config: { layout: 'sidebar', accent: '#1f2f44', font: 'sans', sectionStyle: 'bar', density: 'standard', free: true, photo: true }
  },
  {
    code: 'chengshi', name: '城市', category: '商务',
    description: '白底页眉配深靛蓝模块色带，联系方式分栏对齐，稳健的商务风格。',
    tags: ['商务', '金融', '社招', '单栏'],
    config: { layout: 'band', accent: '#1f3864', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'jianya', name: '简雅', category: '简约',
    description: '低多边形淡青横幅与居中页眉，模块图标配细线，日期独立左栏。',
    tags: ['居中', '通用', '横幅', '单栏'],
    config: { layout: 'center', accent: '#1f9e8f', font: 'sans', sectionStyle: 'line', density: 'standard', free: true, photo: true }
  },
  {
    code: 'qiuye', name: '秋叶', category: '创意',
    description: '证件照居左，青绿方块标题配右对齐日期，条目紧凑、层次清爽，适合技术、产品与运营岗位。',
    tags: ['创意', '青绿', '校招', '技术'],
    config: { layout: 'timeline', accent: '#2f6f62', font: 'sans', sectionStyle: 'dot', density: 'standard', free: true, photo: true }
  },
  {
    code: 'jishu', name: '技术', category: '商务',
    description: '证件照居左，描边圆形图标配虚线分割，联系信息按标签两列排布。',
    tags: ['照片', '单栏', '社招', '技术'],
    config: { layout: 'profile', accent: '#2f6f62', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: true }
  },
  {
    code: 'xueshu', name: '学术', category: '学术',
    description: '角标照片框与制图线装饰，青色实底标签，论文、竞赛与荣誉分区清晰。',
    tags: ['学术', '考研', '论文', '经典'],
    config: { layout: 'classic', accent: '#35a9bb', font: 'sans', sectionStyle: 'caps', density: 'standard', free: true, photo: true }
  },
  {
    code: 'chenxing', name: '晨星', category: '双栏',
    description: '白色侧栏配深红圆弧装饰，技能点线对齐，右侧专注履历与成果。',
    tags: ['照片', '双栏', '校招', '通用'],
    config: { layout: 'rail', accent: '#8c1f28', font: 'sans', sectionStyle: 'bar', density: 'standard', free: true, photo: true }
  },
  {
    code: 'qihang', name: '启航', category: '商务',
    description: '淡蓝模块底色带配右侧证件照，信息按标签对齐，稳重体面。',
    tags: ['照片', '商务', '社招', '单栏'],
    config: { layout: 'photoheader', accent: '#2b7cb8', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'qingwa', name: '青瓦', category: '简约',
    description: '紧凑页眉与青绿标题短线，通栏细线分区，最传统的中文简历读法。',
    tags: ['照片', '单栏', '校招', '社招'],
    config: { layout: 'formal', accent: '#1f9e8f', font: 'sans', sectionStyle: 'rule', density: 'standard', free: true, photo: true }
  },
  {
    code: 'jinshi', name: '金石', category: '商务',
    description: '藏青斜切色带与哑金圆徽，模块标牌压在通栏竖线上，教育与工作条目可放校徽 / 公司 LOGO，稳重的工程风格。',
    tags: ['照片', '商务', '工程', '徽标'],
    config: { layout: 'ribbon', accent: '#3e5984', font: 'sans', sectionStyle: 'flag', density: 'standard', free: true, photo: true }
  }
]

export const TEMPLATE_CATEGORIES = ['全部', '简约', '双栏', '商务', '学术', '创意']

export function templateByCode(code, templates = []) {
  return templates.find((item) => item.code === code)
    || TEMPLATE_FALLBACKS.find((item) => item.code === code)
    || TEMPLATE_FALLBACKS[0]
}

export function configOf(template) {
  return { ...TEMPLATE_FALLBACKS[0].config, ...(template?.config || {}) }
}
