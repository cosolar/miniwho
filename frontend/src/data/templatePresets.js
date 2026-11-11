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
  },
  {
    code: 'yunjian', name: '云笺', category: '创意',
    description: '米色页眉带压住带框证件照，姓名居中带英文小标，右上角虚线名片，正文分区带编号与英文注释，传统中文简历的读法。',
    tags: ['照片', '编号', '单栏', '校招'],
    config: { layout: 'framed', accent: '#2f5c8f', font: 'sans', sectionStyle: 'number', density: 'standard', free: true, photo: true }
  },
  {
    code: 'qieying', name: '切影', category: '创意',
    description: '整条深墨绿页眉压住全幅，姓名反白、照片从页眉下沿切出来；正文每个分区自己分两列，标题右对齐压一条竖线。',
    tags: ['暗色页眉', '照片', '两列分区', '社招'],
    config: { layout: 'qieying', accent: '#1f4a42', font: 'sans', sectionStyle: 'side', density: 'standard', free: true, photo: true }
  },
  {
    code: 'biaoge', name: '表册', category: '通用',
    description: '账簿式的黑白排版：页眉用粗顶线压住，联系方式右对齐成一列；每个分区是一条双线表头，左中文右英文。',
    tags: ['黑白', '表格', '双线', '国企'],
    config: { layout: 'biaoge', accent: '#14161a', font: 'sans', sectionStyle: 'thead', density: 'standard', free: true, photo: false }
  },
  {
    code: 'kapi', name: '卡片', category: '互联网',
    description: '经历与自评各成一张浅灰卡片，标题做成实心胶囊；页眉是圆形头像配姓名，块与块之间靠留白分开。',
    tags: ['卡片', '圆头像', '胶囊标题', '产品'],
    config: { layout: 'kapi', accent: '#3b5a75', font: 'sans', sectionStyle: 'chip', density: 'standard', free: true, photo: true }
  },
  {
    code: 'zazhi', name: '杂志', category: '创意',
    description: '姓名放大到标题级，下面压一条粗红线；分区标题带两位序号并拉一条细线到页边，留白大、字号对比强。',
    tags: ['大标题', '衬线', '序号', '设计'],
    config: { layout: 'editorial', accent: '#8a2a2f', font: 'serif', sectionStyle: 'folio', density: 'standard', free: true, photo: true }
  },
  {
    code: 'lantu', name: '蓝图', category: '技术',
    description: '整页铺 5 毫米淡蓝网格，页眉上下各一道蓝线；分区标题以短线起头、虚线收尾，像一张工程图纸。',
    tags: ['网格', '工程感', '蓝色', '技术'],
    config: { layout: 'blueprint', accent: '#1f4e79', font: 'sans', sectionStyle: 'tick', density: 'standard', free: true, photo: false }
  },
  {
    code: 'zheye', name: '折页', category: '简约',
    description: '单栏正文配三列页眉，分区只留一条细下划线，紧凑密度把行距与模块间距收进一档；灰黑色调、不带照片，适合经历多、想压到一页的简历。',
    tags: ['单栏', '紧凑', '多内容', '社招'],
    config: { layout: 'fold', accent: '#4c4a45', font: 'sans', sectionStyle: 'plain', density: 'compact', free: true, photo: false }
  },
  {
    code: 'qingai', name: '青霭', category: '通用',
    description: '灰绿通栏页眉配深绿方头标题与圆点条目，姓名下压一条短绿线，日期靠右。适合行政、人事、教育与公共服务方向的中文单页简历。',
    tags: ['单栏', '灰绿', '页眉色带', '圆点条目', '通用'],
    config: { layout: 'profile', accent: '#4d7a5f', font: 'sans', sectionStyle: 'line', density: 'standard', free: true, photo: true }
  },
  {
    code: 'lanzhe', name: '蓝辙', category: '学术',
    description: '蓝色时间轴版式：日期独立左栏，每块内容挂一条虚线导轨与同心圆节点，技能排成分段进度条。适合技术、运营与项目制岗位。',
    tags: ['时间轴', '蓝色', '日期栏', '进度条', '技术岗'],
    config: { layout: 'academic', accent: '#2f7fa8', font: 'sans', sectionStyle: 'caps', density: 'standard', free: true, photo: true }
  },
  {
    code: 'subai', name: '素白', category: '学术',
    description: '右上角英文标头，模块标题中英双行，日期独立左栏、专业贴右。适合校招、实习与学术方向的简历。',
    tags: ['中英标题', '日期栏', '素色', '校招', '学术'],
    config: { layout: 'academic', accent: '#33719e', font: 'sans', sectionStyle: 'caps', density: 'standard', free: true, photo: true }
  },
  {
    code: 'yanlu', name: '研路', category: '学术',
    description: '藏青页眉配矩形标牌标题，报考信息排成表格，正文适合长段落陈述。适合考研、调剂与复试材料。',
    tags: ['考研', '调剂', '藏青', '标牌标题', '表格'],
    config: { layout: 'band', accent: '#223a5e', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'jianqing', name: '简青', category: '技术',
    description: '线条图标配蓝色标题，日期、单位、专业压成一行，正文用细圆点分条。适合技术、质检与制造类岗位。',
    tags: ['线条图标', '蓝色', '单行条目', '技术岗', '质检'],
    config: { layout: 'profile', accent: '#2b6f9e', font: 'sans', sectionStyle: 'line', density: 'standard', free: true, photo: true }
  },
  {
    code: 'yadai', name: '雅黛', category: '商务',
    description: '灰蓝通栏标题带，姓名与求职意向同行，信息两列夹在上下细线之间，日期左栏成列。适合设计、服装与艺术类岗位。',
    tags: ['灰蓝', '通栏标题带', '日期栏', '设计岗', '商务'],
    config: { layout: 'formal', accent: '#566f8b', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'suding', name: '素钉', category: '通用',
    description: '「个人简历」大字标头配浅灰图钉标题带，页眉收成一条浅灰信息带。适合通讯、运维与工程类岗位。',
    tags: ['图钉', '浅灰', '双行标头', '工程岗', '通用'],
    config: { layout: 'band', accent: '#45596b', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'shuangying', name: '双影', category: '双栏',
    description: '白底通栏页眉，左侧窄栏收拢意向与技能，右侧主栏专注履历，标题压一道粗线。适合通用、管理与运营岗位。',
    tags: ['双栏', '白底', '窄栏', '管理岗', '高密度'],
    config: { layout: 'sidebar', accent: '#2b3b4e', font: 'sans', sectionStyle: 'bar', density: 'standard', free: true, photo: true }
  },
  {
    code: 'qimo', name: '旗墨', category: '国企',
    description: '顶部 RESUME 旗标开场，藏青标牌标题拖一条细线，联系方式两列对齐。适合国企、行政与传统行业投递。',
    tags: ['旗标', '藏青', 'RESUME', '国企', '传统'],
    config: { layout: 'band', accent: '#1d3557', font: 'sans', sectionStyle: 'flag', density: 'standard', free: true, photo: true }
  },
  {
    code: 'ziyuan', name: '紫垣', category: '双栏',
    description: '淡紫通栏页眉配圆形照片，左侧淡紫窄栏排技能条，主栏挂虚线导轨与圆点标题。适合物流、供应链与运营岗位。',
    tags: ['紫色', '圆形照片', '侧栏', '导轨圆点', '物流'],
    config: { layout: 'sidebar', accent: '#6a5aa8', font: 'sans', sectionStyle: 'bar', density: 'standard', free: true, photo: true }
  },
  {
    code: 'zhuling', name: '朱绫', category: '商务',
    description: '绯红页眉配锯齿下沿，朱红短线压在标题下，日期与单位同一行展开。适合互联网、计算机与研发岗位。',
    tags: ['绯红', '锯齿', '日期左栏', '互联网', '研发'],
    config: { layout: 'band', accent: '#b5312a', font: 'sans', sectionStyle: 'line', density: 'standard', free: true, photo: true }
  },
  {
    code: 'xian', name: '汐岸', category: '互联网',
    description: '浅蓝通栏页眉，左侧蓝色窄栏排成白卡，主栏点线导轨配右侧小图标。适合供应链、外贸与项目协调岗位。',
    tags: ['浅蓝', '侧栏白卡', '点线导轨', '供应链', '互联网'],
    config: { layout: 'sidebar', accent: '#3a6b8a', font: 'sans', sectionStyle: 'bar', density: 'standard', free: true, photo: true }
  },
  {
    code: 'bibo', name: '碧波', category: '通用',
    description: '青碧波浪页眉压着居中的圆形证件照，模块标题是实心青圆配同色标题，技能排成三列分段进度条。适合通用、行政与服务岗的单页简历。',
    tags: ['居中', '青碧', '波浪页眉', '圆头像', '通用'],
    config: { layout: 'center', accent: '#3fb3a3', font: 'sans', sectionStyle: 'line', density: 'standard', free: true, photo: true }
  },
  {
    code: 'chengyi', name: '橙意', category: '销售',
    description: '左侧圆形头像配橙色竖条标题，正文收进浅灰细框，技能条分三列排开。适合销售、市场与门店服务岗。',
    tags: ['橙色', '圆头像', '浅框', '销售', '进度条'],
    config: { layout: 'profile', accent: '#ef8a3d', font: 'sans', sectionStyle: 'line', density: 'standard', free: true, photo: true }
  },
  {
    code: 'tianqing', name: '天青', category: '国企',
    description: '「个人简历」大字压天青粗线，模块标题是天青实底白字横条，联系方式用竖线分隔。适合国企、行政与应届岗位。',
    tags: ['天青', '个人简历', '实底标题', '国企', '通用'],
    config: { layout: 'band', accent: '#3aa7d4', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'mobai', name: '墨白', category: '商务',
    description: '墨黑竖块压标题、通栏细线分区，日期独立左栏与单位岗位同一行，技能按名称熟练度两列铺开。适合商务、法务与工程岗。',
    tags: ['墨黑', '竖块标题', '细线', '商务', '稳重'],
    config: { layout: 'formal', accent: '#23282d', font: 'sans', sectionStyle: 'rule', density: 'standard', free: true, photo: true }
  },
  {
    code: 'liujin', name: '鎏金', category: '国企',
    description: '金色粗线压「个人简历」标头，模块标题是金色实底白字横条，证件照带金色细框。适合国企、事业单位与传统行业。',
    tags: ['金色', '个人简历', '实底标题', '国企', '证件照'],
    config: { layout: 'band', accent: '#b39152', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'sulan', name: '素栏', category: '双栏',
    description: '左侧窄栏收拢求职意向与技能条，右侧主栏专注教育与履历，两栏之间用一条细线分开。适合理工、运营与项目岗。',
    tags: ['双栏', '灰蓝', '窄栏', '技能条', '通用'],
    config: { layout: 'sidebar', accent: '#7d8d99', font: 'sans', sectionStyle: 'bar', density: 'standard', free: true, photo: true }
  },
  {
    code: 'qinghan', name: '青函', category: '医疗',
    description: '圆形证件照配蓝色姓名，模块标题是蓝色实心圆徽配同色标题，技能按名称熟练度两列排开。适合护理、医疗与康复岗位。',
    tags: ['蓝色', '圆头像', '圆徽标题', '医疗', '护理'],
    config: { layout: 'profile', accent: '#2f8fc4', font: 'sans', sectionStyle: 'line', density: 'standard', free: true, photo: true }
  },
  {
    code: 'huijie', name: '灰阶', category: '技术',
    description: '左栏放基本信息与技能，右栏每块履历挂一条竖线节点，日期与单位压成一行。适合技术、产品与工程岗。',
    tags: ['灰阶', '圆头像', '导轨节点', '技术', '双栏'],
    config: { layout: 'sidebar', accent: '#33393d', font: 'sans', sectionStyle: 'bar', density: 'standard', free: true, photo: true }
  },
  {
    code: 'feiying', name: '绯樱', category: '医疗',
    description: '顶部绯色 RESUME 旗标开场，模块标题是绯色实底白字横条，证件照带同色细框。适合护理、医美与服务行业。',
    tags: ['绯色', 'RESUME', '实底标题', '医疗', '护理'],
    config: { layout: 'band', accent: '#f0837f', font: 'sans', sectionStyle: 'block', density: 'standard', free: true, photo: true }
  },
  {
    code: 'xuanmo', name: '玄墨', category: '管理',
    description: '深墨侧栏从页顶一直通到底，白字放基本信息、证书与自我评价；主栏标题是墨色圆角标牌。适合管理、供应链与运营岗。',
    tags: ['深色侧栏', '墨色标牌', '管理', '双栏', '沉稳'],
    config: { layout: 'sidebar', accent: '#1f262b', font: 'sans', sectionStyle: 'bar', density: 'standard', free: true, photo: true }
  },
  {
    code: 'yehang', name: '夜航', category: '互联网',
    description: '藏青侧栏通到页顶，右上角压一枚淡淡的 RESUME 水印，主栏标题用藏青字配同色细线。适合物流、供应链与互联网运营岗。',
    tags: ['藏青侧栏', 'RESUME水印', '互联网', '物流', '双栏'],
    config: { layout: 'sidebar', accent: '#16324f', font: 'sans', sectionStyle: 'bar', density: 'standard', free: true, photo: true }
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
