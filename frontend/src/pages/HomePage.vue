<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Check, Key, Lock, View, Files, MagicStick } from '@element-plus/icons-vue'
import PaperFrame from '../components/PaperFrame.vue'
import TemplateThumb from '../components/TemplateThumb.vue'
import { TEMPLATE_FALLBACKS } from '../data/templatePresets'
import { useSampleResume } from '../composables/useSampleResume'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const isDev = import.meta.env.DEV
const { content: sampleContent, template: homeTemplate, displayName: sampleName, avatar: sampleAvatar } = useSampleResume()
const showcase = ['qinghe', 'qingwa', 'ceying', 'chengshi', 'moxian', 'chenxing', 'qihang', 'jianya']
  .map((code) => TEMPLATE_FALLBACKS.find((tpl) => tpl.code === code))
const activeFaq = ref('')

const startPath = computed(() => (auth.isLoggedIn ? '/dashboard' : '/login?mode=register'))

const features = [
  {
    index: '01',
    title: '内容有结构，换模板不丢字',
    text: '每一段履历都被拆成清晰的字段。从青禾换到侧影，只需要一秒钟，文字、顺序和隐藏状态全都保留。'
  },
  {
    index: '02',
    title: '填写像写字，保存才落笔',
    text: '内联展开的表单，不跳页、不打断。你可以同时打开多个模块，确认无误后再写入云端，并保留本地草稿。'
  },
  {
    index: '03',
    title: '分享有边界，隐私可收回',
    text: '分享链接可加密码、设有效期、隐藏手机号和邮箱。撤销后立即失效，不让一份简历在网络上失控。'
  }
]

const steps = [
  {
    no: '01',
    title: '挑一版适合你的版式',
    text: '30 套原创模板，覆盖校招、社招、技术与学术场景，多数在页眉留了证件照位置。所有模板永久免费，没有会员墙。',
    type: 'template'
  },
  {
    no: '02',
    title: '按模块把履历写清楚',
    text: '18 个内容模块，自带填写提示与空状态，还可以再加 5 个自定义模块。工作与项目支持富文本分点，日期采用统一格式。',
    type: 'editor'
  },
  {
    no: '03',
    title: '预览、诊断，然后放心投递',
    text: '在 A4 版面上检查结果，用确定性规则诊断内容，再用浏览器打印成 PDF 或生成可控分享链接。',
    type: 'preview'
  }
]

const faqs = [
  {
    q: '这个项目真的免费吗？',
    a: '是的。极简简历以 Apache License 2.0 开源，没有会员、付费模板或导出限制。你可以直接使用，也可以部署到自己的服务器。'
  },
  {
    q: '简历数据存在哪里？',
    a: '默认存储在你自己运行的后端 SQLite 数据库中，附件同样保存在本地目录；也可以切换到 MySQL 8。项目不依赖第三方 AI 或统计服务，数据不离开你的环境。'
  },
  {
    q: '可以导出 PDF 和 Word 吗？',
    a: '可以。服务器装有 Chrome / Edge / Chromium 时，预览页的「下载 PDF」会直接返回服务端渲染的 A4 PDF；没有可用浏览器时会自动回落到「打印 → 另存为 PDF」，排版完全一致。建议在打印窗口中关闭页眉和页脚。Word 导出在路线图中，当前不提供。'
  },
  {
    q: '为什么不是 1200 套模板？',
    a: '这是一套可维护的开源项目，目前提供 30 套由同一渲染引擎驱动的原创版式，保证每一套都真的能用。后续新增模板只需补充一份配置。'
  },
  {
    q: '诊断是 AI 生成的吗？',
    a: '不是。评分与建议来自确定性的规则引擎，覆盖完整度、岗位匹配、表达、量化与格式五个维度。它不会编造履历，也不会把你的简历发给外部模型。'
  },
  {
    q: '手机上能完成编辑吗？',
    a: '可以。工作台在窄屏下会把模块导航收成横向标签，表单变为单列，预览页支持缩放查看。附件上传、保存和分享在手机端同样可用。'
  }
]
</script>

<template>
  <div class="home">
    <section class="hero">
      <div class="hero__grid-lines" aria-hidden="true" />
      <div class="container hero__inner">
        <div class="hero__copy">
          <img class="hero__logo" src="/logo-lockup.png" alt="极简简历 who.minims.cn" width="720" height="217" />
          <div class="eyebrow">开源简历工作台 · OPEN SOURCE</div>
          <h1 class="hero__title serif">
            把履历写清楚，<br />
            <span>让机会看见你。</span>
          </h1>
          <p class="hero__lead">
            极简简历是一套开源、免费的在线简历工作台。结构化填写、A4 排版、可控分享——
            不注册会员，不喂给算法，只帮你把真实做过的事，写得体面一点。
          </p>
          <div class="hero__actions">
            <button class="m-btn m-btn--primary hero-btn" @click="router.push(startPath)">
              免费制作我的简历
              <el-icon><ArrowRight /></el-icon>
            </button>
            <router-link to="/templates" class="m-btn m-btn--ghost hero-btn">先看看模板</router-link>
          </div>
          <div class="hero__meta">
            <span><el-icon><Check /></el-icon> 永久免费</span>
            <span><el-icon><Check /></el-icon> Apache 2.0 开源</span>
            <span><el-icon><Check /></el-icon> 可自托管</span>
          </div>
          <div class="hero__stats">
            <div><strong>30</strong><span>原创模板</span></div>
            <div><strong>18</strong><span>内容模块</span></div>
            <div><strong>5</strong><span>诊断维度</span></div>
            <div><strong>0</strong><span>付费墙</span></div>
          </div>
        </div>

        <div class="hero__visual">
          <div class="hero__stack">
            <div class="hero__sheet hero__sheet--back" aria-hidden="true" />
            <div class="hero__sheet hero__sheet--mid" aria-hidden="true" />
            <div class="hero__paper">
              <PaperFrame
                :content="sampleContent"
                :template="homeTemplate"
                :avatar="sampleAvatar"
                :paged="false"
                :style-data="{ showBrandFooter: false, fontSizeStep: -1, spacingStep: -1 }"
                :max-scale="0.62"
              />
              <div class="hero__fade" aria-hidden="true" />
            </div>
          </div>

          <div class="hero__chips">
            <div class="hero-chip">
              <span class="hero-chip__dot" />
              <div>
                <strong>95 <small>分</small></strong>
                <span>示例简历 · 5 维诊断</span>
              </div>
            </div>
            <div class="hero-chip">
              <span class="swatch" style="background:#2f6f62" />
              <span class="swatch" style="background:#3d5a80" />
              <span class="swatch" style="background:#9a6a3f" />
              <span class="swatch" style="background:#b35c37" />
              <div>
                <strong>30 套</strong>
                <span>原创版式</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="hero__scroll">
        <span>向下滚动，看看它如何工作</span>
        <i />
      </div>
    </section>

    <div class="marquee" aria-hidden="true">
      <div class="marquee__track">
        <template v-for="copy in 2" :key="copy">
          <span v-for="tpl in TEMPLATE_FALLBACKS" :key="`${copy}-${tpl.code}`" class="marquee__item">
            <i :style="{ background: tpl.config.accent }" />
            {{ tpl.name }}
            <small>{{ tpl.category }}</small>
          </span>
        </template>
      </div>
    </div>

    <section class="values section">
      <div class="container">
        <div class="values__head" v-reveal>
          <div>
            <div class="eyebrow">NOT JUST A WORD DOCUMENT</div>
            <h2 class="display">不是把 Word 搬上网页，<br />而是把简历拆成可打磨的内容。</h2>
          </div>
          <p>
            内容和样式分离之后，修改一段履历不会推倒整页排版；换一套模板也不会让文字消失。
            这是极简简历所有功能的起点。
          </p>
        </div>
        <div class="values__grid">
          <article v-for="(feature, i) in features" :key="feature.index" class="value-card" v-reveal="i * 90">
            <span class="value-card__index mono">{{ feature.index }}</span>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.text }}</p>
            <div class="value-card__line" />
          </article>
        </div>
      </div>
    </section>

    <section class="templates-showcase section" v-reveal>
      <div class="container">
        <div class="section-heading">
          <div>
            <div class="eyebrow">OUR TEMPLATES</div>
            <h2>十二套版式，十二种语气。</h2>
          </div>
          <div class="showcase-aside">
            <p>全部由同一套结构化内容驱动。现在看到的每一页，都是真实可用的简历模板。</p>
            <router-link to="/templates" class="link-arrow">进入模板中心</router-link>
          </div>
        </div>
        <div class="showcase-grid">
          <button
            v-for="tpl in showcase"
            :key="tpl.code"
            class="showcase-card"
            type="button"
            @click="router.push('/templates')"
          >
            <TemplateThumb :content="sampleContent" :template="tpl" :avatar="sampleAvatar" />
            <span class="showcase-card__meta">
              <span>
                <strong>{{ tpl.name }}</strong>
                <small>{{ tpl.description }}</small>
              </span>
              <span class="showcase-card__category">{{ tpl.category }}</span>
            </span>
          </button>
        </div>
      </div>
    </section>

    <section id="how-it-works" class="steps section">
      <div class="container">
        <div class="section-heading">
          <div>
            <div class="eyebrow">THREE QUIET STEPS</div>
            <h2 class="display">把一件复杂的事，<br />拆成三个安静的动作。</h2>
          </div>
          <div class="showcase-aside">
            <p>
              不需要先学排版软件，也不需要把履历反复抄进不同的文档。
              从选择版式到生成分享链接，都在同一个工作台里完成。
            </p>
            <button class="m-btn m-btn--primary" @click="router.push(startPath)">
              开始写第一份简历
              <el-icon><ArrowRight /></el-icon>
            </button>
            <small v-if="isDev" class="steps__note">本地演示账号：demo / demo123</small>
          </div>
        </div>

        <div class="steps__list">
          <article v-for="(step, i) in steps" :key="step.no" class="step-card" v-reveal="i * 90">
            <div class="step-card__copy">
              <span class="step-card__no mono">{{ step.no }}</span>
              <h3>{{ step.title }}</h3>
              <p>{{ step.text }}</p>
            </div>

            <div class="step-visual" :class="`step-visual--${step.type}`">
              <template v-if="step.type === 'template'">
                <div class="mini-template" v-for="tpl in showcase.slice(0, 3)" :key="tpl.code">
                  <i :style="{ background: tpl.config.accent }" />
                  <span>{{ tpl.name }}</span>
                  <small>{{ tpl.category }}</small>
                </div>
              </template>
              <template v-else-if="step.type === 'editor'">
                <div class="mini-modules">
                  <span class="done">基本信息</span>
                  <span class="done">教育背景</span>
                  <span class="active">项目经历</span>
                  <span>个人技能</span>
                  <span>论文发表</span>
                  <span>自我评价</span>
                </div>
                <div class="mini-progress">
                  <span>完成度</span>
                  <div><i /></div>
                  <strong>82%</strong>
                </div>
              </template>
              <template v-else>
                <div class="mini-actions">
                  <span><el-icon><View /></el-icon> A4 预览</span>
                  <span><el-icon><MagicStick /></el-icon> 规则诊断</span>
                  <span><el-icon><Lock /></el-icon> 密码分享</span>
                  <span><el-icon><Files /></el-icon> 打印 PDF</span>
                </div>
              </template>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section id="privacy" class="privacy">
      <div class="container privacy__grid">
        <div class="privacy__copy">
          <div class="eyebrow eyebrow--light">PRIVACY BY DEFAULT</div>
          <h2 class="display">简历是你的，<br />不是平台的。</h2>
          <p>
            我们默认不索引、不追踪、不用你的履历训练模型。
            分享的边界由你亲手设置，也可以随时收回。
          </p>
          <ul class="privacy__list">
            <li>
              <span class="privacy__icon"><el-icon><Key /></el-icon></span>
              <span class="privacy__text"><strong>访问密码与有效期</strong>分享链接可设密码，可选择 7 天、30 天或永久有效。</span>
            </li>
            <li>
              <span class="privacy__icon"><el-icon><View /></el-icon></span>
              <span class="privacy__text"><strong>字段级隐藏</strong>手机号、邮箱、城市、主页可逐项隐藏，服务端裁剪后才返回。</span>
            </li>
            <li>
              <span class="privacy__icon"><el-icon><Lock /></el-icon></span>
              <span class="privacy__text"><strong>链接随时撤销</strong>撤销立即生效。旧链接只会看到失效提示，不会继续泄露内容。</span>
            </li>
            <li>
              <span class="privacy__icon"><el-icon><Files /></el-icon></span>
              <span class="privacy__text"><strong>数据在你手里</strong>默认 SQLite 单文件存储，附件落在本机；可完整迁移到 MySQL 8。</span>
            </li>
          </ul>
          <router-link to="/login?mode=register" class="privacy__cta">
            从一份可控的简历开始
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>

        <div class="share-demo">
          <div class="share-demo__header">
            <span class="share-demo__dot" />
            <span>分享设置</span>
            <span class="share-demo__badge">PRIVATE</span>
          </div>
          <div class="share-demo__row">
            <span>访问密码</span>
            <strong class="mono">••••••</strong>
          </div>
          <div class="share-demo__row">
            <span>有效期</span>
            <strong>30 天</strong>
          </div>
          <div class="share-demo__row">
            <span>手机号</span>
            <span class="off">已隐藏 <i /></span>
          </div>
          <div class="share-demo__row">
            <span>邮箱</span>
            <span class="off">已隐藏 <i /></span>
          </div>
          <div class="share-demo__row">
            <span>允许下载</span>
            <span class="on">开启</span>
          </div>
          <div class="share-demo__link">
            <span class="mono">miniwho.local/share/haojianli</span>
            <span class="share-demo__copy">已复制</span>
          </div>
          <div class="share-demo__paper">
            <span class="share-demo__name serif">{{ sampleName || '郝简历' }}</span>
            <span class="share-demo__mask">138 ···· 0000</span>
            <span class="share-demo__mask">lin.····@example.com</span>
          </div>
          <p class="share-demo__caption">访客看到的版本，联系方式在服务端已被裁掉。</p>
        </div>
      </div>
    </section>

    <section id="faq" class="faq section">
      <div class="container faq__grid">
        <div class="faq__intro" v-reveal>
          <div class="eyebrow">FAQ</div>
          <h2 class="display">你可能想先确认的几件事。</h2>
          <p>如果这里没有答案，可以在登录后的「帮助与反馈」里告诉我们。</p>
          <div class="faq__quote">
            <span class="serif">“</span>
            <p>一份好简历的标准，是让读的人迅速相信：这件事，你真的做过。</p>
          </div>
        </div>
        <el-collapse v-model="activeFaq" accordion class="faq__collapse" v-reveal>
          <el-collapse-item v-for="item in faqs" :key="item.q" :name="item.q" :title="item.q">
            <p>{{ item.a }}</p>
          </el-collapse-item>
        </el-collapse>
      </div>
    </section>

    <section class="final-cta">
      <div class="container final-cta__inner" v-reveal>
        <span class="final-cta__mark mono">MINIWHO / 2026</span>
        <h2 class="display">三十年后，你还会记得<br />第一份认真写过的简历。</h2>
        <p>现在开始，把它写得清楚一点。</p>
        <div class="final-cta__actions">
          <button class="m-btn m-btn--primary" @click="router.push(startPath)">
            {{ auth.isLoggedIn ? '回到我的简历' : '免费注册，立即开始' }}
            <el-icon><ArrowRight /></el-icon>
          </button>
          <router-link to="/templates" class="m-btn m-btn--ghost">浏览模板</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  overflow: hidden;
}

.hero {
  position: relative;
  padding: 66px 0 58px;
  border-bottom: 1px solid var(--line);
}

.hero__grid-lines {
  position: absolute;
  inset: 0;
  opacity: 0.34;
  background-image:
    linear-gradient(to right, rgba(210, 202, 186, 0.32) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(210, 202, 186, 0.22) 1px, transparent 1px);
  background-size: 96px 96px;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.7), transparent 58%);
  pointer-events: none;
}

.hero__inner {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(380px, 0.82fr);
  gap: 52px;
  align-items: center;
}

.hero__copy {
  padding-top: 8px;
  max-width: 650px;
}

.hero__logo {
  display: block;
  width: min(236px, 58%);
  height: auto;
  margin-bottom: 26px;
}

.hero__title {
  margin: 26px 0 24px;
  font-size: clamp(46px, 5.7vw, 78px);
  font-weight: 600;
  letter-spacing: -0.035em;
  line-height: 1.16;
}

.hero__title span {
  position: relative;
  color: var(--accent);
  white-space: nowrap;
}

.hero__title span::after {
  content: "";
  position: absolute;
  left: 0;
  right: 1%;
  bottom: 4px;
  height: 9px;
  background: rgba(194, 160, 100, 0.3);
  z-index: -1;
  transform: rotate(-0.6deg);
}

.hero__lead {
  max-width: 530px;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.95;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 32px;
}

.hero-btn {
  height: 50px;
  padding: 0 26px;
}

.hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 20px;
  color: var(--faint);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.hero__meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.hero__meta .el-icon {
  color: var(--accent);
  font-size: 12px;
}

.hero__stats {
  display: flex;
  gap: 36px;
  margin-top: 36px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}

.hero__stats div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hero__stats strong {
  font-family: var(--serif);
  font-size: 26px;
  font-weight: 600;
  line-height: 1;
}

.hero__stats span {
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0.05em;
}

.hero__visual {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.hero__stack {
  position: relative;
  width: min(404px, 100%);
  height: 512px;
  transform: rotate(-1.1deg);
}

.hero__sheet {
  position: absolute;
  inset: 0;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: var(--paper);
}

.hero__sheet--back {
  transform: translate(15px, 13px) rotate(2.1deg);
  opacity: 0.5;
}

.hero__sheet--mid {
  transform: translate(8px, 7px) rotate(1deg);
  opacity: 0.78;
}

.hero__paper {
  position: absolute;
  inset: 0;
  z-index: 2;
  overflow: hidden;
  padding: 12px 11px 0;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: var(--paper);
  box-shadow: 0 28px 60px -30px rgba(31, 38, 32, 0.34);
}

/* 纸面按整页高度裁切，用渐隐告诉读者「下面还有内容」，而不是把三页都摊开。 */
.hero__fade {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 92px;
  background: linear-gradient(to bottom, rgba(255, 254, 251, 0), var(--paper) 76%);
  pointer-events: none;
}

.hero__chips {
  display: flex;
  width: min(404px, 100%);
  gap: 12px;
  justify-content: space-between;
}

.hero-chip {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 13px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--paper);
  box-shadow: 0 14px 30px -24px rgba(31, 38, 32, 0.4);
}

.hero-chip div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.2;
}

.hero-chip strong {
  font-family: var(--serif);
  font-size: 17px;
  font-weight: 600;
}

.hero-chip strong small {
  font-family: var(--sans);
  font-size: 10px;
  font-weight: 400;
  color: var(--muted);
}

.hero-chip span {
  color: var(--muted);
  font-size: 10px;
}

.hero-chip__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 4px rgba(10, 125, 118, 0.12);
}

.swatch {
  width: 15px;
  height: 15px;
  border-radius: 50%;
  border: 2px solid #fff;
  margin-left: -6px;
  box-shadow: 0 0 0 1px rgba(31, 38, 32, 0.06);
}

.swatch:first-child {
  margin-left: 0;
}

.hero-chip > div strong {
  font-size: 12px;
}

.hero__scroll {
  position: absolute;
  left: 50%;
  bottom: 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.12em;
  transform: translateX(-50%);
}

.hero__scroll i {
  width: 30px;
  height: 1px;
  background: var(--line-strong);
}

.marquee {
  overflow: hidden;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
  background: var(--paper-deep);
}

.marquee__track {
  display: flex;
  width: max-content;
  animation: marquee 38s linear infinite;
}

.marquee:hover .marquee__track {
  animation-play-state: paused;
}

.marquee__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 30px;
  color: var(--ink-soft);
  font-family: var(--serif);
  font-size: 13px;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.marquee__item + .marquee__item::before {
  content: "·";
  margin-right: 22px;
  color: var(--line-strong);
}

.marquee__item i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.marquee__item small {
  color: var(--faint);
  font-family: var(--sans);
  font-size: 9px;
  letter-spacing: 0.1em;
}

@keyframes marquee {
  to { transform: translateX(-50%); }
}

.section {
  padding: 104px 0;
}

.values__head {
  display: grid;
  grid-template-columns: 1.35fr 0.65fr;
  gap: 60px;
  align-items: end;
  margin-bottom: 54px;
}

.values__head h2 {
  margin-top: 18px;
  font-size: clamp(30px, 3.6vw, 48px);
}

.values__head p {
  max-width: 390px;
  padding-bottom: 8px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.95;
}

.values__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  border-top: 1px solid var(--line);
}

.value-card {
  position: relative;
  padding: 34px 32px 38px;
  border-right: 1px solid var(--line);
}

.value-card:last-child {
  border-right: none;
}

.value-card__index {
  color: var(--copper);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.value-card h3 {
  margin: 26px 0 12px;
  font-family: var(--serif);
  font-size: 21px;
  font-weight: 600;
  line-height: 1.5;
}

.value-card p {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.value-card__line {
  width: 28px;
  height: 1px;
  margin-top: 26px;
  background: var(--accent);
  transition: width 0.35s ease;
}

.value-card:hover .value-card__line {
  width: 64px;
}

.values {
  background: var(--canvas);
}

.templates-showcase {
  border-top: 1px solid var(--line);
  background: var(--paper-deep);
}

.showcase-aside {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
}

.showcase-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 22px;
}

.showcase-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
}

.showcase-card__meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 0 2px;
}

.showcase-card__meta > span:first-child {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.showcase-card__meta strong {
  font-family: var(--serif);
  font-size: 16px;
  font-weight: 600;
}

.showcase-card__meta small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.showcase-card__category {
  flex-shrink: 0;
  color: var(--faint);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.steps {
  background:
    radial-gradient(700px 400px at 0% 100%, rgba(10, 125, 118, 0.06), transparent 60%),
    var(--canvas);
}

.steps__note {
  color: var(--faint);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.steps__list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.step-card {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 26px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(255, 254, 251, 0.65);
  transition: background 0.25s ease, border-color 0.25s ease, box-shadow 0.25s ease;
}

.step-card:hover {
  border-color: var(--line-strong);
  background: var(--paper);
  box-shadow: 0 18px 40px -32px rgba(31, 38, 32, 0.4);
}

.step-card__copy {
  flex: 1;
}

.step-card__no {
  color: var(--copper);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.step-card h3 {
  margin: 14px 0 9px;
  font-family: var(--serif);
  font-size: 19px;
  font-weight: 600;
  line-height: 1.5;
}

.step-card p {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.85;
}

.step-visual {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  min-width: 0;
  min-height: 172px;
  padding: 16px;
  border-radius: 8px;
  background: var(--paper-deep);
  border: 1px solid var(--line);
}

.mini-template {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--paper);
  color: var(--ink);
  font-size: 12px;
}

.mini-template > span {
  flex: 1;
  font-family: var(--serif);
  font-weight: 600;
}

.mini-template small {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.04em;
}

.mini-template i {
  width: 6px;
  height: 20px;
  border-radius: 2px;
}

.mini-modules {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.mini-modules span {
  padding: 5px 4px;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--faint);
  background: transparent;
  font-size: 9px;
  text-align: center;
}

.mini-modules .done {
  color: var(--muted);
  background: var(--paper);
}

.mini-modules .active {
  border-color: rgba(10, 125, 118, 0.3);
  background: var(--accent-soft);
  color: var(--accent);
}

.mini-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: var(--muted);
  font-size: 8px;
}

.mini-progress div {
  flex: 1;
  height: 3px;
  border-radius: 2px;
  background: var(--line);
  overflow: hidden;
}

.mini-progress div i {
  display: block;
  width: 82%;
  height: 100%;
  background: var(--accent);
}

.mini-progress strong {
  color: var(--accent);
  font-family: var(--mono);
  font-size: 9px;
}

.mini-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.mini-actions span {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--paper);
  color: var(--muted);
  font-size: 9px;
}

.mini-actions .el-icon {
  color: var(--accent);
  font-size: 11px;
}

.privacy {
  position: relative;
  overflow: hidden;
  padding: 104px 0;
  background: #17241f;
  color: #f1ede3;
}

.privacy::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(241, 237, 227, 0.14) 0.6px, transparent 0.6px);
  background-size: 5px 5px;
  opacity: 0.28;
  pointer-events: none;
}

.privacy::after {
  content: "";
  position: absolute;
  right: -180px;
  bottom: -320px;
  width: 640px;
  height: 640px;
  background: radial-gradient(circle, rgba(10, 125, 118, 0.34), transparent 66%);
  pointer-events: none;
}

.privacy__grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr 0.8fr;
  gap: 62px;
  align-items: center;
}

.eyebrow--light {
  color: #b8c8bf;
}

.eyebrow--light::before {
  background: #b8c8bf;
}

.privacy h2 {
  margin: 20px 0 20px;
  color: #f7f3ea;
  font-size: clamp(34px, 4vw, 54px);
}

.privacy__copy > p {
  max-width: 520px;
  color: rgba(241, 237, 227, 0.68);
  font-size: 13px;
  line-height: 1.95;
}

.privacy__list {
  margin: 32px 0 32px;
  padding: 0;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.privacy__list li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 17px 16px;
  border: 1px solid rgba(241, 237, 227, 0.1);
  border-radius: 10px;
  background: rgba(255, 254, 251, 0.03);
  transition: border-color 0.25s ease, background 0.25s ease;
}

.privacy__list li:hover {
  border-color: rgba(199, 164, 101, 0.4);
  background: rgba(255, 254, 251, 0.06);
}

.privacy__icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: rgba(199, 164, 101, 0.15);
  color: #d8b87b;
  font-size: 14px;
}

.privacy__text {
  min-width: 0;
  color: rgba(241, 237, 227, 0.66);
  font-size: 12px;
  line-height: 1.8;
}

.privacy__list strong {
  display: block;
  margin-bottom: 5px;
  color: #f7f3ea;
  font-size: 13px;
  font-weight: 500;
}

.privacy__cta {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  height: 46px;
  padding: 0 22px;
  border-radius: 8px;
  background: #f1ede3;
  color: #17241f;
  font-size: 13px;
  letter-spacing: 0.02em;
  transition: background 0.22s ease, transform 0.22s ease;
}

.privacy__cta:hover {
  background: #fffefb;
  transform: translateY(-1px);
}

.privacy__cta .el-icon {
  font-size: 14px;
}

.share-demo {
  position: relative;
  width: min(400px, 100%);
  margin-left: auto;
  padding: 22px;
  border: 1px solid rgba(241, 237, 227, 0.15);
  border-radius: 12px;
  background: rgba(255, 254, 251, 0.04);
  backdrop-filter: blur(8px);
}

.share-demo__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 15px;
  border-bottom: 1px solid rgba(241, 237, 227, 0.12);
  color: rgba(241, 237, 227, 0.8);
  font-size: 11px;
}

.share-demo__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c7a465;
}

.share-demo__badge {
  margin-left: auto;
  font-family: var(--mono);
  font-size: 8px;
  letter-spacing: 0.12em;
  color: rgba(241, 237, 227, 0.42);
}

.share-demo__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 0;
  border-bottom: 1px solid rgba(241, 237, 227, 0.07);
  color: rgba(241, 237, 227, 0.55);
  font-size: 11px;
}

.share-demo__row strong {
  color: rgba(241, 237, 227, 0.9);
  font-weight: 500;
}

.share-demo__row .off {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #d8b87b;
  font-size: 10px;
}

.share-demo__row .off i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #d8b87b;
}

.share-demo__row .on {
  color: #8db6a4;
  font-size: 10px;
}

.share-demo__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 15px 0;
  padding: 9px 11px;
  border: 1px dashed rgba(241, 237, 227, 0.2);
  border-radius: 5px;
  color: rgba(241, 237, 227, 0.4);
  font-size: 9px;
}

.share-demo__copy {
  color: #d8b87b;
}

.share-demo__paper {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  padding: 26px 20px 22px;
  border-radius: 4px;
  background: #faf7ef;
  color: #1d2722;
  overflow: hidden;
}

.share-demo__paper::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: #2f6f62;
}

.share-demo__name {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.share-demo__mask {
  padding: 2px 10px;
  border-radius: 2px;
  background: repeating-linear-gradient(135deg, #ece6d9 0 4px, #f4efe4 4px 8px);
  color: #8a8f88;
  font-family: var(--mono);
  font-size: 8px;
}

.share-demo__caption {
  margin-top: 12px;
  color: rgba(241, 237, 227, 0.35);
  font-size: 9px;
  text-align: center;
}

.faq {
  background: var(--canvas);
}

.faq__grid {
  display: grid;
  grid-template-columns: 0.78fr 1.22fr;
  gap: 90px;
  align-items: start;
}

.faq__intro h2 {
  margin: 20px 0 18px;
  font-size: clamp(28px, 3.2vw, 40px);
}

.faq__intro p {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.9;
}

.faq__quote {
  display: flex;
  gap: 10px;
  margin-top: 48px;
  padding-top: 24px;
  border-top: 1px solid var(--line);
}

.faq__quote span {
  color: var(--gold);
  font-size: 34px;
  line-height: 1;
}

.faq__quote p {
  color: var(--ink-soft);
  font-family: var(--serif);
  font-size: 14px;
  line-height: 1.9;
}

.faq__collapse {
  border-top: 1px solid var(--line);
  --el-collapse-border-color: var(--line);
  --el-collapse-header-bg-color: transparent;
  --el-collapse-content-bg-color: transparent;
}

.faq__collapse :deep(.el-collapse-item__header) {
  height: auto;
  padding: 22px 4px;
  background: transparent;
  color: var(--ink);
  font-family: var(--serif);
  font-size: 17px;
  font-weight: 500;
  line-height: 1.5;
}

.faq__collapse :deep(.el-collapse-item__wrap) {
  background: transparent;
}

.faq__collapse :deep(.el-collapse-item__content) {
  padding: 0 34px 24px 4px;
}

.faq__collapse :deep(.el-collapse-item__content p) {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.95;
}

.faq__collapse :deep(.el-collapse-item__arrow) {
  color: var(--muted);
}

.final-cta {
  padding: 100px 0 110px;
  border-top: 1px solid var(--line);
  background:
    radial-gradient(600px 300px at 50% 100%, rgba(10, 125, 118, 0.08), transparent 70%),
    var(--paper-deep);
  text-align: center;
}

.final-cta__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.final-cta__mark {
  color: var(--faint);
  font-size: 9px;
  letter-spacing: 0.18em;
}

.final-cta h2 {
  margin: 24px 0 18px;
  font-size: clamp(30px, 4vw, 50px);
}

.final-cta p {
  color: var(--muted);
  font-size: 13px;
}

.final-cta__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  margin-top: 32px;
}

@media (max-width: 1100px) {
  .hero__inner {
    grid-template-columns: 1fr 380px;
    gap: 24px;
  }

  .showcase-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 30px;
  }

  .steps__list {
    grid-template-columns: 1fr;
  }

  .step-card {
    flex-direction: row;
    align-items: stretch;
    gap: 20px;
  }

  .step-visual {
    width: 46%;
    flex-shrink: 0;
  }

  .mini-modules {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .privacy__grid {
    gap: 40px;
  }

  .privacy__list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .hero {
    padding: 56px 0 64px;
  }

  .hero__inner {
    grid-template-columns: 1fr;
    gap: 44px;
  }

  .hero__copy {
    max-width: 620px;
  }

  .hero__title {
    font-size: clamp(42px, 9vw, 62px);
  }

  .hero__visual {
    width: min(460px, 100%);
    margin: 0 auto;
  }

  .hero__scroll {
    display: none;
  }

  .values__head {
    grid-template-columns: 1fr;
    gap: 22px;
  }

  .values__grid {
    grid-template-columns: 1fr;
  }

  .value-card {
    border-right: none;
    border-bottom: 1px solid var(--line);
    padding: 26px 4px 30px;
  }

  .value-card:last-child {
    border-bottom: none;
  }

  .privacy__grid {
    grid-template-columns: 1fr;
  }

  .share-demo {
    margin: 0 auto;
  }

  .faq__grid {
    grid-template-columns: 1fr;
    gap: 42px;
  }

  .faq__quote {
    display: none;
  }
}

@media (max-width: 640px) {
  .section {
    padding: 72px 0;
  }

  .hero__stats {
    gap: 24px;
    flex-wrap: wrap;
  }

  .hero__stats strong {
    font-size: 22px;
  }

  .hero__stack,
  .hero__chips {
    width: min(340px, 100%);
  }

  .hero__stack {
    height: 432px;
  }

  .hero__chips {
    flex-direction: column;
  }

  .showcase-grid {
    grid-template-columns: 1fr;
    gap: 26px;
  }

  .step-card {
    flex-direction: column;
    padding: 20px;
  }

  .step-visual {
    width: auto;
    min-height: auto;
  }

  .privacy {
    padding: 72px 0;
  }

  .share-demo {
    padding: 16px;
  }

  .final-cta {
    padding: 72px 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .marquee__track {
    animation: none;
  }
}
</style>
