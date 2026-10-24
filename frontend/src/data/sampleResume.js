/**
 * 出厂默认示例，仅作兜底初值：运行时首页与模板中心读的是后端 /api/public/sample-resume
 * （管理员在工作台把某份简历设为「首页示例」），接口成功即覆盖这里的内容。
 * 想改线上看到的示例，去改那份简历，不是改这里。
 */
export const sampleContent = {
  schemaVersion: 1,
  language: 'zh',
  sections: [
    {
      id: 'sec_basic_info',
      type: 'basic_info',
      title: '基本信息',
      visible: true,
      order: 0,
      meta: {},
      items: [{
        id: 'itm_basic',
        fields: {
          name: '林知远',
          gender: '男',
          showGender: true,
          phone: '13800000000',
          email: 'lin.zhiyuan@example.com',
          identity: '应届生',
          city: '北京',
          birthday: '2001-03',
          showAge: true,
          website: 'https://example.com/linzhiyuan'
        }
      }]
    },
    {
      id: 'sec_job_intention',
      type: 'job_intention',
      title: '求职意向',
      visible: true,
      order: 1,
      meta: { displayMode: 'title_only' },
      items: [{
        id: 'itm_intention',
        fields: {
          jobTitle: 'AI Agent Harness 研究员',
          city: '北京',
          salaryMin: '30000',
          salaryMax: '45000',
          salaryNegotiable: false,
          status: '在职，月内到岗'
        }
      }]
    },
    {
      id: 'sec_education',
      type: 'education',
      title: '教育背景',
      visible: true,
      order: 2,
      meta: {},
      items: [
        {
          id: 'itm_edu_master',
          fields: {
            school: '山河大学',
            major: '软件工程（智能体方向）',
            degree: '硕士',
            schoolTag: '双一流',
            period: { start: '2023-09', end: '2026-06', current: false },
            gpa: '3.9 / 4.0',
            rank: '前 5%',
            courses: ['大模型智能体', '强化学习', '自然语言处理', '软件工程'],
            note: '硕士课题：面向长程软件任务的 Agent Harness 评测与优化；一等学业奖学金（2024、2025）。'
          }
        },
        {
          id: 'itm_edu_bachelor',
          fields: {
            school: '山河大学',
            major: '计算机科学与技术',
            degree: '本科',
            schoolTag: '双一流',
            period: { start: '2019-09', end: '2023-06', current: false },
            gpa: '3.8 / 4.0',
            rank: '前 10%',
            courses: ['数据结构', '操作系统', '数据库系统', '分布式系统', '机器学习'],
            note: '国家奖学金（2021）；CCPC 省级银奖；校级优秀毕业生。'
          }
        }
      ]
    },
    {
      id: 'sec_work_experience',
      type: 'work_experience',
      title: '工作经历',
      visible: true,
      order: 3,
      meta: {},
      items: [
        {
          id: 'itm_work_fulltime',
          fields: {
            company: '极简软件股份有限公司',
            position: 'AI Agent Harness 研究员',
            workType: '全职',
            period: { start: '2026-07', end: '', current: true },
            city: '北京',
            department: 'AI 研发中心',
            content: '<ul><li>负责 Agent Harness 的评测与训练闭环，设计 6 类工具沙箱与 120+ 长程任务，单次全量回归从 3.5 小时压缩到 40 分钟。</li><li>搭建轨迹回放与失败归因流水线，定位上下文腐化、工具误用等高频问题，模型任务完成率从 54% 提升到 71%。</li><li>与训练团队共建 20 万条高质量轨迹，SFT 后单任务平均步数下降 28%，端到端推理成本下降 35%。</li><li>维护内部 Harness SDK 与接入文档，支持 5 个业务团队落地，Agent 功能灰度覆盖率达到 100%。</li></ul>'
          }
        },
        {
          id: 'itm_work_intern',
          fields: {
            company: '极简软件股份有限公司',
            position: 'AI Agent 研究实习生',
            workType: '实习',
            period: { start: '2025-03', end: '2026-05', current: false },
            city: '北京',
            department: 'AI 研发中心',
            content: '<ul><li>复现并改造 SWE-bench 风格评测流程，补齐容器化执行与补丁验证，评测结果可复现率从 82% 提升到 98%。</li><li>设计“规划-检索-编辑-验证”多智能体代码修复框架，在 300 道真实工单上解决率达到 61%，较单 Agent 基线高 14 个百分点。</li><li>完成 4 轮消融实验与 2 万字实验报告，沉淀为组内 Agent 评测规范，并被 2026 届校招评测沿用。</li></ul>'
          }
        }
      ]
    },
    {
      id: 'sec_project_experience',
      type: 'project_experience',
      title: '项目经历',
      visible: true,
      order: 4,
      meta: {},
      items: [
        {
          id: 'itm_proj_harness',
          fields: {
            name: 'Agent Harness 长程任务评测平台',
            role: '核心开发',
            period: { start: '2025-09', end: '2026-05', current: false },
            content: '<ul><li>设计基于容器的任务运行时，统一 8 类工具接口与 3 种反馈信号，支持 200+ 任务并行回放。</li><li>建立 pass@1、执行步数、推理成本与故障恢复率四维指标，输出可视化对比报告，支撑 6 次模型版本决策。</li><li>引入断点续跑与轨迹压缩，单轮全量评测成本从 420 元降到 180 元，回归周期从 2 天缩短到 4 小时。</li></ul>'
          }
        },
        {
          id: 'itm_proj_multiagent',
          fields: {
            name: '多智能体代码修复系统',
            role: '核心开发',
            period: { start: '2025-03', end: '2025-09', current: false },
            content: '<ul><li>构建规划、检索、编辑、验证四个协作角色，通过共享黑板传递上下文，累计修复真实工单 300+。</li><li>设计补丁级沙箱验证与失败回退策略，减少 70% 的无效提交，单工单平均耗时下降 36%。</li><li>在内部评测集上取得 61% 解决率，相关报告被采纳为 Agent 岗位校招评测基线。</li></ul>'
          }
        },
        {
          id: 'itm_proj_order',
          fields: {
            name: '星图订单中台',
            role: '后端开发实习生',
            period: { start: '2022-03', end: '2023-06', current: false },
            content: '<ul><li>参与订单创建、拆单与履约模块，服务 6 条业务线，日处理订单 30 万单。</li><li>引入状态机与领域事件，跨团队联调周期从 3 周缩短到 5 天；建立灰度发布与回滚预案，上线 12 个版本未发生 P0 故障。</li></ul>'
          }
        }
      ]
    },
    {
      id: 'sec_campus_experience',
      type: 'campus_experience',
      title: '在校经历',
      visible: true,
      order: 5,
      meta: {},
      items: [
        {
          id: 'itm_campus_lab',
          fields: {
            organization: '山河大学智能体实验室',
            role: '研究助理',
            subtype: '科研项目',
            period: { start: '2024-03', end: '2025-06', current: false },
            content: '<p>参与国家自然科学基金面上项目“复杂任务下的大模型智能体规划”，负责评测框架与实验设计，产出 2 篇论文（1 篇 CCF-B 一作）。</p>'
          }
        },
        {
          id: 'itm_campus_union',
          fields: {
            organization: '山河大学计算机学院学生会 · 技术部',
            role: '部长',
            subtype: '学生职务',
            period: { start: '2020-09', end: '2021-06', current: false },
            content: '<p>带领 12 人小组维护学院活动报名与签到系统，累计服务 5000+ 名学生；组织 6 场技术分享，覆盖 800+ 人次。</p>'
          }
        },
        {
          id: 'itm_campus_award',
          fields: {
            organization: '国家奖学金',
            role: '本科生',
            subtype: '奖学金',
            period: { start: '2021-10', end: '', current: false },
            content: '<p>专业前 3%，连续两年获校级一等奖学金；获 CCPC 省级银奖。</p>'
          }
        }
      ]
    },
    {
      id: 'sec_skills_summary',
      type: 'skills_summary',
      title: '个人技能',
      visible: true,
      order: 6,
      meta: {},
      items: [{
        id: 'itm_skills',
        fields: {
          content: '<p>Python / Java / TypeScript；熟悉 LLM Agent、工具调用、上下文工程与多智能体协作；具备容器沙箱、轨迹回放、评测指标设计与大规模实验分析经验；熟悉 PyTorch、Transformers、LangGraph 与主流 Agent SDK。</p>'
        }
      }]
    },
    {
      id: 'sec_skill_proficiency',
      type: 'skill_proficiency',
      title: '技能熟练度',
      visible: true,
      order: 7,
      meta: { display: 'progress' },
      items: [
        { id: 'itm_skill_python', fields: { name: 'Python', level: '精通' } },
        { id: 'itm_skill_harness', fields: { name: 'Agent Harness', level: '精通' } },
        { id: 'itm_skill_eval', fields: { name: '大模型评测', level: '熟练' } },
        { id: 'itm_skill_java', fields: { name: 'Java', level: '熟练' } },
        { id: 'itm_skill_dist', fields: { name: '分布式系统', level: '熟练' } },
        { id: 'itm_skill_writing', fields: { name: '英文技术写作', level: '熟练' } }
      ]
    },
    {
      id: 'sec_paper_publication',
      type: 'paper_publication',
      title: '论文发表',
      visible: true,
      order: 8,
      meta: {},
      items: [{
        id: 'itm_paper',
        fields: {
          content: '<ul><li>林知远, 等. <strong>HarnessBench：面向长程软件任务的智能体执行框架评测</strong>. CCF-B 类会议, 2026.（第一作者）</li><li>林知远, 等. <strong>多智能体代码修复中的验证反馈机制</strong>. CCF-C 类会议, 2025.（第二作者）</li></ul>'
        }
      }]
    },
    {
      id: 'sec_portfolio',
      type: 'portfolio',
      title: '作品集',
      visible: true,
      order: 9,
      meta: {},
      items: [
        {
          id: 'itm_portfolio_miniwho',
          fields: {
            title: 'MiniWho · 极简简历',
            link: 'https://example.com/miniwho',
            content: '<p>开源 A4 简历工作台，负责 Vue 前端、Spring Boot 后端与多页 PDF 分页引擎，累计 1.2k Star。</p>'
          }
        },
        {
          id: 'itm_portfolio_bench',
          fields: {
            title: 'HarnessBench 评测集',
            link: 'https://example.com/harnessbench',
            content: '<p>开源的 Agent 长程任务评测集，包含 200+ 任务、容器运行时与可视化对比报告。</p>'
          }
        }
      ]
    },
    {
      id: 'sec_self_evaluation',
      type: 'self_evaluation',
      title: '自我评价',
      visible: true,
      order: 10,
      meta: {},
      items: [{
        id: 'itm_self',
        fields: {
          content: '<p>2026 届硕士，研究方向为 AI Agent Harness 与长程任务评测。在极简软件完成从研究实习到全职的连续一年半实践，主导评测平台与多智能体代码修复系统，习惯用可复现的实验和数据驱动优化。工程基础扎实，能独立完成从问题定义、实验设计到工具落地的闭环。</p>'
        }
      }]
    }
  ]
}
