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
  },
  {
    code: 'qingling', name: '青领', category: '销售',
    description: '青蓝通栏色带开场，圆徽模块标题配英文副名，姓名与求职意向同行、联系方式三列铺开。适合销售、市场、运营等偏业务的中文单页简历。',
    tags: ['单栏','青蓝','色带','圆徽','业务岗'],
    config: { layout: 'profile', accent: '#4fb2c1', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: true }
  },
  {
    code: 'ziteng', name: '紫藤', category: '通用',
    description: '浅紫通栏页眉与左侧淡紫侧栏，圆形证件照、圆徽模块标题，技能与证书收进窄栏。适合教育、行政、人事等通用岗位的单页简历。',
    tags: ['侧栏','紫色','圆形头像','通用'],
    config: { layout: 'sidebar', accent: '#7a5cb8', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: true }
  },
  {
    code: 'lanzhou', name: '蓝舟', category: '国企',
    description: '深蓝 RESUME 竖标、居中姓名与通栏蓝带标题，联系方式压在蓝色横条上，日期居左。适合国企、行政、教育与金融方向的传统单页简历。',
    tags: ['竖标','深蓝','通栏标题','传统'],
    config: { layout: 'band', accent: '#1f4e8c', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'cangyan', name: '苍岩', category: '管理',
    description: '藏青几何页眉与六边形模块徽章，日期居左、职位居右，英文副名收在标题后。适合管理、金融、咨询与工程方向的稳重单页简历。',
    tags: ['几何','藏青','六边形','稳重'],
    config: { layout: 'profile', accent: '#1d3557', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: true }
  },
  {
    code: 'mochuan', name: '墨川', category: '财务',
    description: '深青通栏页眉与同色左侧栏，证书、技能、爱好收进暗色窄栏，正文保持白底。适合财务、会计、审计等需要沉稳质感的岗位。',
    tags: ['侧栏','深青','会计','沉稳'],
    config: { layout: 'sidebar', accent: '#226d75', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: true }
  },
  {
    code: 'bijian', name: '碧笺', category: '通用',
    description: '「个人简历」大标题配青蓝细线，照片贴右上角，模块标题为青蓝实底横条，技能用分段进度条。适合通用、行政、教育与应届岗位。',
    tags: ['浅色','青蓝','进度条','应届'],
    config: { layout: 'profile', accent: '#2f7f8f', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'songshi', name: '松石', category: '技术',
    description: '墨绿压顶细线与左置照片，模块标题是浅灰绿条，正文收进浅灰卡片。适合技术、产品、运营等偏理性的岗位。',
    tags: ['卡片','墨绿','照片居左','技术'],
    config: { layout: 'photoheader', accent: '#2f6d5f', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'zitang', name: '紫棠', category: '通用',
    description: '紫色渐变细线压顶，照片居右，模块标题铺淡紫整条并配紫色圆徽。适合设计、教育、市场等需要柔和个人风格的单页简历。',
    tags: ['紫色','淡色块','照片居右','柔和'],
    config: { layout: 'profile', accent: '#6d4fa1', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'qingci', name: '青瓷', category: '通用',
    description: '青瓷色压顶细线、淡青标题条与水色内容卡，照片居右，整体留白舒展。适合教育、文职、医疗与公共服务方向。',
    tags: ['青瓷','浅色','淡雅','卡片'],
    config: { layout: 'profile', accent: '#2b7f83', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'zhusha', name: '朱砂', category: '国企',
    description: '白底黑字配朱红压顶线与点线标题，照片带角标边框，工作项目正文自动编号。适合国企、事业单位、法务与工程方向。',
    tags: ['红色','经典','编号列表','角标照片'],
    config: { layout: 'classic', accent: '#c0262d', font: 'sans', sectionStyle: 'caps', density: 'standard', free: true, photo: true }
  },
  {
    code: 'zhuyin', name: '竹隐', category: '技术',
    description: '通栏墨绿页眉配圆形证件照与白色图标联系方式，模块标题用实心圆徽，条目压成单位—专业—日期三栏。密度接近招聘平台模板，适合技术、产品与应届生投递。',
    tags: ['绿色','通栏页眉','圆形照片','三栏条目'],
    config: { layout: 'photoheader', accent: '#2f7d4f', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: true }
  },
  {
    code: 'lanyu', name: '蓝屿', category: '技术',
    description: '白底大姓名、钢蓝图标联系方式与右置证件照，模块标题沿左侧竖线展开，条目带节点圆点。适合技术、运营与项目制岗位。',
    tags: ['钢蓝','时间轴','左侧竖线','图标联系'],
    config: { layout: 'classic', accent: '#3f6e8c', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: true }
  },
  {
    code: 'haichuan', name: '海川', category: '商务',
    description: '证件照居左，姓名与应聘岗位压在通到纸边的蓝色横幅上，联系方式排成 2×2 图标格；模块标题居中，两侧细线压短粗蓝条。适合校招与社招的通用投递。',
    tags: ['蓝色','横幅页眉','居中标题','证件照居左'],
    config: { layout: 'band', accent: '#1f5fa8', font: 'sans', sectionStyle: 'caps', density: 'standard', free: true, photo: true }
  },
  {
    code: 'jiandu', name: '简牍', category: '通用',
    description: '暖白纸面配棕色双线与发丝线分隔，衬线姓名与左栏模块标题，正文按「标签在左、内容在右」的两栏排布。适合学术、研究、教育与国企方向的传统单页简历。',
    tags: ['单栏','棕色','衬线','左栏标题','传统'],
    config: { layout: 'minimal', accent: '#8f6a45', font: 'serif', sectionStyle: 'line', density: 'standard', free: true, photo: false }
  },
  {
    code: 'sujian', name: '素笺', category: '通用',
    description: '米白纸面配四角柔色块，右侧圆角证件照，姓名与求职意向同行、基本信息两列铺开，绿色圆徽模块标题加深色正文。适合产品、运营、设计等通用岗位的单页简历。',
    tags: ['单栏','米白','绿色','信息网格','通用'],
    config: { layout: 'profile', accent: '#6cab8f', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: true }
  },
  {
    code: 'liucai', name: '流彩', category: '互联网',
    description: '蓝紫渐变通栏页眉开场，姓名与联系方式反白压在色带上，正文白色纸面、蓝色模块标题、条目日期靠右。适合技术、设计与新产品岗位的现代单页简历。',
    tags: ['单栏','渐变','蓝色','简洁','技术岗'],
    config: { layout: 'minimal', accent: '#3a6fd8', font: 'sans', sectionStyle: 'line', density: 'standard', free: true, photo: false }
  },
  {
    code: 'zifeng', name: '紫锋', category: '互联网',
    description: '紫色斜切顶带开场，双箭头模块标题配点线分隔，姓名前带角标、求职意向做成实心徽章，日期靠右。适合互联网、前端与产品岗位的技术型单页简历。',
    tags: ['单栏','紫色','斜切','双箭头','技术岗'],
    config: { layout: 'profile', accent: '#5b3fd6', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: false }
  },
  {
    code: 'qinglan', name: '青岚', category: '互联网',
    description: '灰绿卡片式分栏：页眉一整块浅灰信息卡，证件照居左，姓名上方压一行 PERSONAL RESUME；每个模块是一张浅色圆角卡片，左侧圆徽配中文标题与英文副名。适合技术、产品与设计岗位的现代单页简历。',
    tags: ['卡片','浅绿','圆徽','英文副名','现代'],
    config: { layout: 'profile', accent: '#4f7f68', font: 'sans', sectionStyle: 'icon', density: 'standard', free: true, photo: true }
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
