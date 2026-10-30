# 贡献指南

谢谢你想改进极简简历 MiniWho。这份文档只讲「怎么改起来、怎么提交」，功能范围与取舍见 [README](README.md)。

## 目录

- [先跑起来](#先跑起来)
- [测试](#测试)
- [后端代码约定](#后端代码约定)
- [前端代码约定](#前端代码约定)
- [模板包贡献](#模板包贡献)
- [文档同步](#文档同步)
- [提交规范](#提交规范)
- [提交 PR](#提交-pr)
- [行为准则](#行为准则)

## 先跑起来

环境要求：

- JDK 17+、Maven 3.8+
- Node.js 18+（推荐 20 / 22），npm 10+
- 可选：Docker 24+ 与 Docker Compose v2（只在验证镜像构建时需要）

后端（默认 SQLite，零配置）：

```bash
cd backend
mvn spring-boot:run
```

首次启动会创建 `backend/miniwho.db` 与 `backend/uploads/`、从模板包初始化 30 套官方模板，并播种 `demo` / `admin` 两个账号。服务端 PDF 导出需要本机有 Chrome / Edge / Chromium；没有也能开发，前端会自动回落浏览器打印，只是 `/api/health` 会显示 `DEGRADED`。只想跑接口时可以设置 `MINIWHO_PDF_ENABLED=false` 跳过探测。

前端：

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`，Vite 会把 `/api` 代理到 `http://localhost:8080`（改端口用 `VITE_PROXY_TARGET`）。

想验证单镜像或 MySQL 形态，直接照[新手应用镜像快速部署教程](docs/新手应用镜像快速部署教程.md)与[部署指南](docs/部署指南.md)走。

## 测试

后端（CI 发镜像前跑的就是这一条）：

```bash
mvn -B test --file backend/pom.xml
```

- `ArchitectureTest` 会检查分层与依赖方向，越层调用会直接失败；
- `HttpSmokeTest` 用真实 HTTP 覆盖登录、鉴权、内容宽度等主链路；
- 新增服务逻辑时优先补对应 `*Test`，而不是只靠手工点。

前端单测（vitest + jsdom）：

```bash
cd frontend
npm test
```

覆盖的是不依赖真实排版的纯逻辑：日期与文件大小格式化、内容宽度下发、模块目录的校验 / 裁剪 / 摘要规则。`utils/paperPagination.js` 依赖真实布局测量，不放进单测，由端到端与手工验证兜底。

端到端（Playwright）会自动拉起 Vite（5174）与后端（8081，独立 SQLite 库放在 `.tmp/e2e/`，不会碰开发数据），走一遍「登录 → 编辑基本信息 → 预览 → 下载 PDF → 创建分享链接 → 匿名访问并确认手机号被隐藏」：

```bash
cd frontend
npx playwright install chromium   # 首次运行需要
npm run test:e2e
```

默认使用上面装好的 Chromium；本机已经有 Chrome 时，可以设 `MINIWHO_E2E_CHANNEL=chrome` 跳过这次下载。服务端 PDF 引擎不可用时，PDF 用例会自动跳过。改界面仍然建议在浏览器里真的点一遍，并在 PR 里写清验证路径（页面、账号、操作、预期）。涉及分页、打印或模板版式的改动，请同时导出一次 PDF 对照预览。

## 后端代码约定

- **分层**：`controller` 只做参数绑定与权限注解，业务在 `service`，SQL 在 `mapper`；controller 不直接碰 mapper。
- **出参不用实体、不用 `Map`**：按领域放在 `dto` 包里（`ResumeDtos`、`AdminDtos`、`TemplateDtos` …），返回 `*Vo`。
- **权限**：管理员接口标 `@RequireAdmin`；普通资源的归属校验放在 service，越权一律按「不存在」返回 404。
- **异常**：业务失败抛 `BizException`（`badRequest` / `notFound` / `conflict` …），由 `GlobalExceptionHandler` 统一映射，不要在 controller 里手写响应。
- **表结构变更**：往 `db/schema-*.sql` 加列的同时，在 `SchemaMigrator` 里写幂等的 `ALTER TABLE ADD COLUMN`，保证存量库能升级；需要数据回填的迁移要能在启动时安全重跑。
- **模板相关**：模板包改动必须同步 `template-packages/` 下的 JSON 与迁移逻辑，且不能破坏包 CSS 依赖的 DOM 结构（见[模板主题开发指南](docs/模板主题开发指南.md)）。
- **不要顺手重构无关文件**：一个提交只解决一件事，格式化全文件会让 review 淹没真正的改动。

## 前端代码约定

- Vue 3 `<script setup>` + Composition API，状态进 Pinia，接口统一走 `src/api/`。
- 与渲染无关的计算（分页、日期、内容宽度、样式合并）放 `src/utils/`，保持纯函数，方便单测覆盖。
- 页面外壳统一用 `--content-width` 变量（后台「站点设置 → 内容宽度」可调），不要写死 1200px 或固定断点。
- 模板版式改动要同时确认：预览页、PDF 导出、打印分页三处一致。

## 模板包贡献

模板是一份 JSON（`theme` + 配置 + 可选的 `style`），服务端会做 schema 校验和 CSS 净化。投稿前请先：

1. 读[模板包格式](docs/模板包格式.md)与[模板主题开发指南](docs/模板主题开发指南.md)；
2. 走「我的模板 → 导入」自测一遍，确认能导入、能渲染、能导出 PDF、能正确分页；
3. 在 issue 里描述适用人群与设计思路，附一份示例数据下的截图和模板包文件。

官方模板名额有限，社区模板会先进模板中心（需作者分享），质量稳定的再考虑转正。

## 文档同步

改到下面这些地方时，同一个 PR 里把文档一起改掉：

| 改动 | 同步位置 |
| --- | --- |
| 接口路径、参数、错误码 | `docs/API.md` |
| 环境变量、端口、部署步骤 | `docs/部署指南.md`、`.env.example`、`docker-compose.yml` |
| 版本号 | `backend/pom.xml`、`frontend/package.json`、`docs/API.md` 的版本行（文档里的镜像统一写 `latest`，不写具体版本） |
| 用户可见的行为变化 | `docs/使用教程.md` |

## 提交规范

- **提交信息用英文**，祈使句，主题行不超过 72 字符、结尾不加句号；
- 有语境前缀优先用：`feat:` `fix:` `refactor:` `docs:` `build:` `chore:` `test:` `perf:`；
- 一个提交只做一件事；正文写「为什么」，不要逐行复述 diff；
- **不要在提交信息、代码注释或文档里加入 AI / 工具署名**（`Co-Authored-By`、`Generated with …`、模型名、emoji 签名等一律不要）；
- 不要把 `.tmp/`、`target/`、`dist/`、日志、SQLite 文件、`uploads/`、`.env` 提交进仓库。

## 提交 PR

1. Fork 后开一个语义清晰的分支（`fix/share-token-expiry`、`feat/template-gallery`）；
2. 确认 `mvn -B test --file backend/pom.xml` 通过，前端 `npm run build` 能过；
3. PR 描述里写：改了什么、为什么改、怎么验证（命令或页面路径 + 截图）；
4. 涉及界面改动的附前后对比截图；涉及导出的附 PDF 或截图；
5. 不要在 PR 里夹带无关的格式化或依赖升级。

## 行为准则

- 对事不对人，技术争论可以尖锐，语气保持礼貌；
- 不接受人身攻击、歧视性言论、骚扰，也不接受把 issue 当广告位；
- 涉及安全问题时按 [SECURITY.md](SECURITY.md) 走私密通道，不要公开贴利用细节。
