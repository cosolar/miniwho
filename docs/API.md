# 极简简历 MiniWho API 文档

面向需要二次开发、编写脚本或对接前端的开发者。所有接口均以 `/api` 为前缀，默认返回 JSON（附件预览与下载除外）。

- 后端版本：`1.0.1`
- 默认地址：`http://localhost:8080/api`
- 鉴权方式：Sa-Token，请求头 `satoken`
- 数据格式：`application/json; charset=utf-8`
- 时间格式：毫秒时间戳（`Long`），时区固定 `Asia/Shanghai`

---

## 1. 通用约定

### 1.1 响应信封

成功：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "id": 1
  }
}
```

失败（HTTP 状态码同时为 4xx / 5xx）：

```json
{
  "code": "BAD_REQUEST",
  "message": "模块不存在，请先添加模块",
  "data": null
}
```

前端 Axios 封装在 `code === "OK"` 时直接返回 `data`，否则抛出带 `code` / `message` / `details` 的错误。

> 序列化配置是 `default-property-inclusion: non_null`：**值为 `null` 的字段整个不出现在响应里**（不是 `"email": null`）。本文档各处的示例 JSON 因此可能缺少可选字段，客户端一律按「字段可能不存在」处理。

### 1.2 鉴权

登录或注册成功后，响应中的 `token` 即后续请求凭证；`tokenName` 固定为 `satoken`。

```http
satoken: 6f1c2b7e-...
```

等价于：

```http
satoken: <token>
```

不要使用 `Authorization: Bearer`。令牌默认有效期 7 天，保存在后端进程内存中；**后端重启后所有令牌失效，需要重新登录**。

公开接口（无需令牌）：

- `GET /health`
- `POST /auth/login`
- `POST /auth/register`（需管理员在「认证设置」打开用户名注册，否则 403）
- `POST /auth/email-code`、`POST /auth/register-email`（需打开邮箱注册且 SMTP 已配置，否则 403）
- `GET /auth/oauth/github`、`GET /auth/oauth/github/callback`（GitHub 登录的两个浏览器跳转入口，只回 302，不给 XHR 用）
- `GET /public/auth-config`（登录页据此决定渲染哪些注册 / 登录入口）
- `GET /templates`、`GET /templates/{code}`
- `GET /templates/{code}/export`（官方包，以及作者允许导出的已分享包；其余情况要登录且是作者或管理员）
- `GET /public/shares/{token}`
- `POST /public/shares/{token}/unlock`
- `GET /public/sample-resume`（首页与模板中心用的全站示例简历，未设置时返回 `data: null`）

除此之外的 `/api/**` 均要求登录；`/api/admin/**` 还要求当前用户 `role=admin`。

### 1.3 错误码

| HTTP | `code` | 说明 |
| --- | --- | --- |
| 200 | `OK` | 成功 |
| 400 | `BAD_REQUEST` | 业务参数不合法 |
| 400 | `VALIDATION_ERROR` | Bean Validation 失败，`data` 为字段 → 错误信息 |
| 400 | `INVALID_BODY` | 请求体不是合法 JSON |
| 400 | `FILE_TOO_LARGE` | 上传文件超过框架限制 |
| 400 | `TEMPLATE_PACKAGE_INVALID` | 模板包校验未通过，`data.problems` 列出全部问题 |
| 401 | `UNAUTHORIZED` | 未登录、令牌无效或已过期 |
| 401 | `LOGIN_FAILED` | 用户名或密码错误 |
| 401 | `SHARE_PASSWORD_REQUIRED` | 分享需要访问密码 |
| 401 | `SHARE_PASSWORD_INVALID` | 分享密码错误 |
| 403 | `FORBIDDEN` | 无权限（非管理员访问后台、账号被冻结、导出别人未开放的模板包、选用别人未分享的模板） |
| 404 | `NOT_FOUND` | 资源不存在或不属于当前用户 |
| 405 | `METHOD_NOT_ALLOWED` | 请求方法与接口不匹配 |
| 409 | `RESUME_VERSION_CONFLICT` | 内容版本冲突，`data.serverVersion` 为服务端最新版本 |
| 409 | `TEMPLATE_CODE_TAKEN` | 导入包的编码已被别人占用，`data.code` 是冲突编码 |
| 409 | `TEMPLATE_IN_USE` | 模板仍被简历使用，`data.resumeCount` 是在用数量 |
| 410 | `SHARE_REVOKED` | 分享已撤销，或简历已删除 |
| 410 | `SHARE_EXPIRED` | 分享已过期 |
| 429 | `TOO_MANY_REQUESTS` | 触发限流：公开接口按 IP（登录 10 / 注册与验证码 5 / OAuth 20 次每分钟），验证码另按邮箱限（60 秒 1 封、每小时 5 封），PDF 排队超时也算在内 |
| 502 | `MAIL_SEND_FAILED` | SMTP 拒发或连接失败，前端提示「邮件发送失败，请稍后再试或联系管理员」 |
| 503 | `PDF_ENGINE_UNAVAILABLE` | 服务端没有可用的无头浏览器或未配置渲染地址，请改用浏览器打印 |
| 503 | `MAIL_NOT_CONFIGURED` | 邮件通道未就绪：后台与环境变量都没填 host / 发件地址（正常路径下 2.1 会先把这条通道报成不可用） |
| 500 | `INTERNAL_ERROR` | 未预期的服务端错误 |

> 公开分享接口返回 401 时，前端不会像普通接口那样跳转登录页，而是展示密码输入框。

### 1.4 通用字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 自增主键 |
| `createdAt` / `updatedAt` | number | 毫秒时间戳 |
| `language` | string | `zh` 或 `en` |
| `contentVersion` | number | 简历内容版本，每次写操作 +1 |
| `templateCode` | string | 模板编码，如 `qinghe` |
| `role` | string | `user` / `admin` |
| `status` | number | 用户：`1` 正常、`2` 冻结；模板：`1` 启用、`2` 停用；反馈：`0` 待处理、`1` 已处理 |

---

## 2. 认证与个人资料

三种注册 / 登录方式（用户名注册、邮箱注册、GitHub 登录）都由管理后台的「认证设置」控制，并且要对应通道已经配置好才会真正开放；通道既能用环境变量配，也能直接在同一页的「通道配置」里填，保存即生效（见 10.10）。除用户名密码登录外，所有注册入口在被管理员关闭时统一返回 `403 FORBIDDEN`。

### 2.1 注册 / 登录方式配置（无需登录）

```http
GET /api/public/auth-config
```

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "usernameRegister": false,
    "emailRegister": true,
    "githubLogin": true
  }
}
```

登录页据此渲染入口：`usernameRegister` / `emailRegister` 决定注册页里出现哪几种表单，`githubLogin` 决定是否显示 GitHub 按钮。三个字段都是**开关与通道就绪状态的交集**——通道没配好时即使管理员打开了开关也返回 `false`，前台不会出现点了必失败的按钮。

响应带 `Cache-Control: private, max-age=30`，管理员改完设置后登录页最多 30 秒才变化。

### 2.2 用户名注册

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "username": "alice_2026",
  "password": "secret123",
  "nickname": "Alice"
}
```

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `username` | 是 | 3–20 位字母、数字、下划线，全局唯一 |
| `password` | 是 | 6–32 位 |
| `nickname` | 否 | ≤ 50 字符；为空时使用用户名 |

**该接口默认关闭**（`site_settings.register_username` 出厂为 `false`），未开放时返回 `403`「管理员未开放用户名注册，请改用邮箱注册或联系管理员」。

注册成功会自动登录，并创建一份空白简历（标题「我的第一份简历 / Untitled Resume」，模板 `qinghe`）。

响应 `LoginResponse`：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "token": "uuid",
    "tokenName": "satoken",
    "user": {
      "id": 2,
      "username": "alice_2026",
      "nickname": "Alice",
      "role": "user",
      "status": 1,
      "hasPassword": true,
      "emailVerified": false,
      "createdAt": 1792646790000
    },
    "resumeId": 3
  }
}
```

### 2.3 发送注册验证码

```http
POST /api/auth/email-code
Content-Type: application/json
```

```json
{ "email": "me@example.com" }
```

`data` 为 `null`。邮件由标准 SMTP 发出（通道配置取自后台「认证设置」，其次 `MINIWHO_MAIL_*`，见部署指南 2.4），正文是 6 位数字验证码，10 分钟内有效。

限制与失败码：

| 情况 | 响应 |
| --- | --- |
| 管理员未开放邮箱注册，或 SMTP 未配置 | `403` 「管理员未开放邮箱注册」 |
| 该邮箱已注册 | `400` 「该邮箱已注册，请直接登录；忘记密码请联系管理员重置」（**没有找回密码功能**） |
| 60 秒内重复申请 | `429` 「验证码刚刚发送过，请一分钟后再试」 |
| 同一邮箱一小时内超过 5 封 | `429` 「这个邮箱申请验证码太频繁，请一小时后再试」 |
| 发信失败 | `502` 「邮件发送失败，请稍后再试或联系管理员」 |

另叠加按 IP 的公开接口限流。验证码只在库里存 BCrypt 哈希，明文不落库、不落日志。

### 2.4 邮箱注册

```http
POST /api/auth/register-email
Content-Type: application/json
```

```json
{
  "email": "me@example.com",
  "password": "secret123",
  "code": "802142",
  "nickname": "Alice"
}
```

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `email` | 是 | 合法邮箱格式，≤ 100 字符，全局唯一；比较时统一转小写去空格 |
| `password` | 是 | 6–32 位 |
| `code` | 是 | 6 位数字，需与 `email-code` 发出的那封一致 |
| `nickname` | 否 | ≤ 50 字符；为空时取由邮箱前缀分配的用户名 |

响应同 `LoginResponse`，注册成功即自动登录并创建第一份简历；该账号的 `emailVerified` 直接是 `true`——邮箱本来就是验证码确认过的。

验证码错误 `400`「验证码不正确」，同一封最多试 5 次，超过即作废；验证通过后立刻删除，同一条验证码用第二次必然失败。过期返回 `400`「验证码已过期，请重新获取」。

`username` 由邮箱前缀派生：转小写、非 `[a-z0-9_]` 的字符换成 `_`、截断到 20 字符、不足 3 位补 `_`；与已有用户名冲突时追加 100–999 的随机后缀重试，因此用户名注册关闭也不影响邮箱注册，且这个用户名只是内部标识，界面展示的是昵称。

### 2.5 登录

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "demo",
  "password": "demo123"
}
```

`username` 可以是用户名，也可以是**注册时用的邮箱**（含 `@` 时按邮箱查，忽略大小写与首尾空格）。响应同上。`resumeId` 为该用户最近更新的简历 ID；没有任何简历时为 `0`。账号被冻结时返回 `403 FORBIDDEN`。

密码校验对用户名注册的账号同样通过 `BCrypt.checkpw` 完成；GitHub 自动建出来的账号 `hasPassword` 为 `false`，密码位是随机占位哈希，用它登录必然失败。

### 2.6 GitHub 登录（浏览器跳转）

这两个接口不是给 XHR 用的，浏览器直接导航，服务端只回 `302`。

```http
GET /api/auth/oauth/github
```

302 到 GitHub 的授权页（作用域 `read:user user:email`）。未开放或凭据缺失时同样回 302，指向 `<站点对外地址>/login#oauth_error=1`——浏览器导航渲染不了 JSON，失败也只能用跳转表达。

下面的 `<站点对外地址>` 就是 10.10 里那一项：后台填过用后台的，没填用 `MINIWHO_PUBLIC_BASE_URL`，改完立即生效。

```http
GET /api/auth/oauth/github/callback?code=<code>&state=<state>
```

服务端用 `code` 换 token、取 GitHub 用户与**主邮箱**，然后按「渠道身份 → 已验证邮箱关联 → 自动建号」的顺序定位账号：

- 成功：302 到 `<站点对外地址>/login#satoken=<token>`。令牌走 URL 片段，不进服务器访问日志，前端读到手立刻从地址栏抹掉。
- 任何失败（用户在 GitHub 点了取消、`code` 缺失或已被用过、`state` 缺失 / 过期 / 已被核销、换不出 token）：302 到 `<站点对外地址>/login#oauth_error=1`，由登录页提示。

`state` 是服务端一次性内存值，10 分钟过期、核销即失效，重放与伪造都直接失败。

拿到令牌后按普通登录态调用其他接口即可，无需再走一次 `/auth/login`；前端会调 `/auth/me` 补全用户信息。

首次登录自动建号：用户名 `github_<GitHub 用户名>`（清洗与冲突处理和 2.4 同一套规则）、`hasPassword=false`、昵称取 GitHub 显示名；若 GitHub 主邮箱在库里没人占用，会一并写入并标记 `emailVerified=true`。自动建号同样会创建第一份简历。

### 2.7 退出登录

```http
POST /api/auth/logout
satoken: <token>
```

`data` 为 `null`。

### 2.8 当前用户

```http
GET /api/auth/me
satoken: <token>
```

返回 `UserVo`：

```json
{
  "id": 1,
  "username": "demo",
  "nickname": "郝简历",
  "email": "demo@miniwho.local",
  "role": "user",
  "status": 1,
  "hasPassword": true,
  "emailVerified": false,
  "createdAt": 1790000000000
}
```

| 字段 | 说明 |
| --- | --- |
| `hasPassword` | 是否有可用密码。`false` 表示这是第三方登录自动建的号，个人中心因此不显示「修改密码」 |
| `emailVerified` | 邮箱是否走过验证码。只有 `true` 的邮箱才会被 GitHub 登录用来关联已有账号 |

### 2.9 更新资料

```http
PUT /api/auth/profile
satoken: <token>
Content-Type: application/json
```

```json
{
  "nickname": "新的昵称",
  "email": "me@example.com"
}
```

两个字段都可选；传入 `nickname` 时不能为空，`email` 需符合邮箱格式，≤ 100 字符，且不能撞上其他账号已占用的邮箱（`400`「该邮箱已被其他账号使用」）。返回更新后的 `UserVo`。

**改邮箱会把 `emailVerified` 归零**：旧地址上的验证码证明不了新地址。归零后该账号不再能被 GitHub 登录按邮箱关联，直到它通过邮箱注册链路重新确认。

### 2.10 修改密码

```http
PUT /api/auth/password
satoken: <token>
Content-Type: application/json
```

```json
{
  "oldPassword": "secret123",
  "newPassword": "newSecret456"
}
```

| 字段 | 规则 |
| --- | --- |
| `oldPassword` | 必填，需与当前密码一致 |
| `newPassword` | 6–32 位，不能与原密码相同 |

成功返回 `data: null`。第三方登录建的账号（`hasPassword=false`）返回 `400`「该账号通过第三方登录，尚未设置密码」。

改密成功后服务端会立即吊销该账号的**全部**登录态，包括发起这次请求的令牌，前端需要重新登录。

---

## 3. 模板

### 3.1 模板列表（公开）

```http
GET /api/templates
```

返回启用中的**官方模板**（`source = builtin`）加上**别人已分享到模板中心**的导入包（`source = imported` 且 `visibility = public`），按 `sortWeight` 升序。自己导入但没分享的模板只走 3.2。`TemplateVo`：

```json
{
  "code": "qinghe",
  "name": "青禾",
  "description": "发丝线分隔的排字版式，浅灰标签，近单色，适合通用岗位。",
  "category": "简约",
  "tags": ["单栏", "排字", "通用"],
  "config": {
    "layout": "minimal",
    "accent": "#2b3137",
    "font": "sans",
    "sectionStyle": "line",
    "density": "standard",
    "free": true,
    "photo": true
  },
  "usageCount": 10,
  "version": "2.0.0",
  "author": "MiniWho",
  "source": "builtin",
  "visibility": "private",
  "exportable": true
}
```

`visibility` 只对导入包有意义（官方模板恒为 `private`，因为它由应用发版管理）；`exportable` 决定别人能否把这个导入包下载走，见 3.4 与 3.7。`usageCount` 是**当前**还有几份简历在用这个模板，不是历史累计——新建、换模板、删除简历都会重算它，所以它和 3.4 里 `TEMPLATE_IN_USE` 的判定永远是同一个数。

导入模板的 `config` 会多出两个键，含义见 [模板包格式](模板包格式.md)：

```json
{
  "scopeClass": "tw-lin--mudan",
  "styleCss": ".tw-lin--mudan .paper-section__title {\n  color: #7c3aed;\n}\n"
}
```

`styleCss` 是服务端**净化后重新生成**的 CSS，渲染端只读它，绝不读包原文。

官方模板编码：`qinghe`、`moxian`、`ceying`、`chengshi`、`jianya`、`qiuye`、`jishu`、`xueshu`、`chenxing`、`qihang`、`qingwa`、`jinshi`。

### 3.2 我的模板（需登录）

```http
GET /api/templates/mine
```

返回当前账号导入过的模板包，按更新时间倒序，元素同 `TemplateVo`（`source = "imported"`，含 `visibility` 与 `exportable`）。「我的模板」页（`/my-templates`）就是它的数据源，导入 / 导出 / 分享 / 删除都在这一个页面里完成。

### 3.3 导入模板包（需登录）

```http
POST /api/templates/import
Content-Type: application/json

{ "api": 1, "id": { "namespace": "lin", "code": "mudan", "version": "1.0.0" }, "...": "见 docs/模板包格式.md" }
```

请求体就是模板包本身，不需要再套一层信封。校验分两关：字段与渲染器能力（`TemplatePackageCodec`）、CSS 净化（`TemplateCssSanitizer`），**所有问题一次返回**：

```json
{
  "code": "TEMPLATE_PACKAGE_INVALID",
  "message": "模板包 api=2，当前渲染器只支持 api=1；缺少字段 meta.name",
  "data": { "problems": ["模板包 api=2，当前渲染器只支持 api=1", "缺少字段 meta.name"] }
}
```

落库编码为 `namespace + "--" + code`（例：`lin--mudan`），因此两个人用同一个 `code` 也能共存。`namespace` 为 `official` 的包会被拒绝。

- 同作者重复导入同一个包：按版本升级，原地覆盖同一条记录；分享状态与导出权限沿用上次的设置，不会被重置。
- 编码已被别人或官方占用：`409 TEMPLATE_CODE_TAKEN`。

返回导入后的 `TemplateVo`。新导入的模板默认 `visibility = private`，只有作者自己看得见、用得着。

> 校验失败时，逐条问题放在错误响应的 `data.problems` 里（`ApiResponse` 复用 `data` 字段承载 details），前端据此弹出清单。

### 3.4 导出模板包

```http
GET /api/templates/{code}/export
```

返回**美化过的包原文**（不是 `ApiResponse` 信封），带 `Content-Disposition: attachment; filename="<code>-<version>.json"`。谁能取走原文由模板来源与作者开关决定，判定顺序如下：

| 模板 | 未登录 | 其他登录用户 | 作者本人 / 管理员 |
| --- | --- | --- | --- |
| 官方（`builtin`） | 可以 | 可以 | 可以 |
| 已分享且 `exportable = true` | 可以 | 可以 | 可以 |
| 已分享但 `exportable = false` | `401` | `403` | 可以 |
| 未分享的导入包 | `401` | `403` | 可以 |

前端在 `exportable = false` 时把下载按钮换成「仅可使用」，但真正的关卡在服务端。

### 3.5 删除导入的模板（需登录）

```http
DELETE /api/templates/{code}
```

只能删 `source = "imported"` 且属于自己（或是管理员）的模板。官方模板只能在后台停用。仍有简历在使用时返回 `409 TEMPLATE_IN_USE`，`data.resumeCount` 是在用的简历数。

### 3.6 模板详情（公开）

```http
GET /api/templates/{code}
```

返回单个 `TemplateVo`。模板不存在或已停用时返回 `404 NOT_FOUND`。这里不按分享状态设卡：作者取消分享后，别人那份还在用它得能取到渲染配置，纸面才不会悄悄退回默认版式。

### 3.7 分享到模板中心 / 取消分享（需登录）

```http
PUT /api/templates/{code}/share
Content-Type: application/json

{ "shared": true, "exportable": false }
```

只有 `source = "imported"` 且属于自己（或是管理员）的模板能设置，官方模板返回 `400`。

- `shared`：`true` 写入 `visibility = public` 并进模板中心，`false` 回到 `private`、从公开列表消失。
- `exportable`：只在已分享时有意义，决定别人能否把包原文下载走；省略时按 `true` 处理。前端两个开关一次提交，取消分享时也会把当前导出权限原样带上，免得再次分享后设置被悄悄改掉。

返回更新后的 `TemplateVo`。取消分享不影响别人已经在使用它的简历：那些简历继续按原样渲染，也仍能保存样式，只是不能被子后新建或换用（`403 FORBIDDEN`）。

---

## 4. 简历

### 4.1 数据模型

`ResumeSummary`（列表项）：

```json
{
  "id": 1,
  "title": "郝简历 · 后端开发工程师",
  "language": "zh",
  "templateCode": "qinghe",
  "templateName": "青禾",
  "score": 92,
  "completeness": 100,
  "updatedAt": 1792646790000,
  "targetJob": "后端开发工程师"
}
```

`ResumeDetail`：

```json
{
  "id": 1,
  "title": "郝简历 · 后端开发工程师",
  "language": "zh",
  "templateCode": "qinghe",
  "templateName": "青禾",
  "source": "sample",
  "content": { "schemaVersion": 1, "language": "zh", "sections": [] },
  "modulePrefs": {
    "order": ["basic_info", "job_intention", "education"],
    "hidden": ["paper_publication"],
    "customTitles": {}
  },
  "style": {
    "templateCode": "qinghe",
    "filter": "standard",
    "fontSizeStep": 0,
    "spacingStep": 0,
    "showBrandFooter": true
  },
  "contentVersion": 12,
  "completeness": 100,
  "score": 92,
  "scoreUpdatedAt": 1792646790000,
  "createdAt": 1790000000000,
  "updatedAt": 1792646790000,
  "avatar": {
    "shape": "rect",
    "url": "/api/public/avatars/1?token=<签名链接>",
    "sizeBytes": 37423,
    "updatedAt": 1792662105397
  }
}
```

`source` 为 `blank` 或 `sample`。`avatar` 在未上传照片时整个字段省略（响应使用 `non_null` 序列化）。`url` 是带 HMAC 签名、默认 6 小时失效的公开读取地址，可直接放进 `<img>`；签名过期后重新调用详情或工作台接口即可拿到新链接。

### 4.2 简历列表

```http
GET /api/resumes
satoken: <token>
```

返回当前用户的 `ResumeSummary[]`，按 `updatedAt` 倒序。每个用户最多 50 份。

### 4.3 创建简历

```http
POST /api/resumes
satoken: <token>
Content-Type: application/json
```

```json
{
  "title": "我的简历",
  "language": "zh",
  "templateCode": "qinghe",
  "useSample": false
}
```

| 字段 | 说明 |
| --- | --- |
| `title` | 可选；为空用「未命名简历 / Untitled Resume」，超过 50 字符会截断 |
| `language` | `zh`（默认）或 `en` |
| `templateCode` | 默认 `qinghe`；必须是存在且启用中的模板，别人未分享的导入包不能用（`403`） |
| `useSample` | 仅 `zh` 且为 `true` 时使用内置示例内容；英文忽略 |

返回新建的 `ResumeDetail`。空白简历默认包含 11 个核心模块，其中 `paper_publication` 默认隐藏。`contentVersion` 初始为 `0`。

### 4.4 简历详情

```http
GET /api/resumes/{id}
satoken: <token>
```

返回 `ResumeDetail`。非本人简历统一返回 `404`。

### 4.5 工作台数据

```http
GET /api/resumes/{id}/workbench
satoken: <token>
```

```json
{
  "resume": { "...": "ResumeDetail" },
  "attachments": [
    {
      "id": 12,
      "fileName": "作品集.pdf",
      "mime": "application/pdf",
      "sizeBytes": 102400,
      "createdAt": 1792646790000
    }
  ]
}
```

### 4.6 重命名简历

```http
PUT /api/resumes/{id}
satoken: <token>
Content-Type: application/json
```

```json
{ "title": "投递字节 · 后端" }
```

标题必填，1–50 字符。返回 `ResumeDetail`。此接口不使用 `contentVersion`，后写覆盖先写。

### 4.7 复制简历

```http
POST /api/resumes/{id}/duplicate
satoken: <token>
```

返回新的 `ResumeDetail`。副本仅复制正文、模块偏好与样式，不复制附件、分享链接和诊断报告；`contentVersion` 重置为 `0`。

### 4.8 删除简历

```http
DELETE /api/resumes/{id}
satoken: <token>
```

返回 `data: null`。删除时同时撤销该简历所有有效分享链接，并清理附件记录与磁盘文件、诊断报告记录，无法从界面恢复。

### 4.9 保存模块

```http
PUT /api/resumes/{id}/sections/{sectionType}
satoken: <token>
Content-Type: application/json
```

```json
{
  "section": {
    "id": "sec_work_experience",
    "type": "work_experience",
    "title": "工作经历",
    "visible": true,
    "order": 3,
    "meta": {},
    "items": [
      {
        "id": "itm_abc123",
        "fields": {
          "company": "某科技公司",
          "position": "后端开发工程师",
          "workType": "全职",
          "city": "上海",
          "department": "交易平台",
          "period": { "start": "2023-04", "end": "", "current": true },
          "content": "<p>主导订单查询链路重构，P95 从 820ms 降到 210ms。</p>"
        }
      }
    ]
  },
  "contentVersion": 12
}
```

规则：

- `sectionType` 必须与 `section.type` 一致，且属于支持的 18 类模块或 `custom_1`–`custom_5`。
- 服务端会忽略客户端提交的 `id`、`title`、`order`、`visible`，保留原值；修改标题/顺序/显隐请使用模块偏好接口。
- 富文本字段会经过 jsoup 白名单清洗；单条目模块只保留第一条内容。
- 自定义模块最多 10 条；其他模块最多 20 条。
- `contentVersion` 必须等于服务端当前版本，否则返回 `409 RESUME_VERSION_CONFLICT`。
- 成功返回 `SaveResult`：

```json
{
  "contentVersion": 13,
  "savedAt": 1792646790000,
  "completeness": 96,
  "score": 91
}
```

### 4.10 保存模块偏好

```http
PATCH /api/resumes/{id}/module-prefs
satoken: <token>
Content-Type: application/json
```

```json
{
  "order": ["basic_info", "job_intention", "education", "work_experience"],
  "hidden": ["paper_publication"],
  "customTitles": {
    "education": "学习经历",
    "custom_1": "志愿服务"
  },
  "contentVersion": 13
}
```

- `order` 必须与当前简历的全部模块类型完全一致（数量、元素都一致，不可重复）。
- `hidden` 中不能包含 `basic_info` 与 `education`。
- `customTitles` 可只传需要改名的模块；仅可选模块会应用自定义标题，普通模块的标题不会改变。
- 标题最长 30 字符，自定义模块最长 20 字符；超长会截断。
- 返回 `SaveResult`。

### 4.11 添加可选模块

```http
POST /api/resumes/{id}/modules
satoken: <token>
Content-Type: application/json
```

```json
{
  "section": {
    "id": "sec_competition",
    "type": "competition",
    "title": "竞赛经历",
    "visible": true,
    "order": 0,
    "meta": {},
    "items": []
  },
  "contentVersion": 13
}
```

- 只允许添加可选模块：`postgraduate_info`、`competition`、`award`、`certificate`、`journal_paper`、`hobby`、`custom_1`–`custom_5`。
- 同一类型只能存在一个；核心模块不能通过此接口添加。
- 自定义模块总计最多 5 个，每模块最多 10 条，标题 ≤ 20 字符（取自 `meta.customTitle` 或 `title`）。
- 添加后模块自动出现在末尾并设为可见；`order` / `hidden` 同步更新。
- 返回完整 `ResumeDetail`（不是 `SaveResult`）。

### 4.12 移除可选模块

```http
DELETE /api/resumes/{id}/modules/{sectionType}?version=14
satoken: <token>
```

`version` 为当前 `contentVersion`。仅可选模块可删除，核心模块返回 400。返回完整 `ResumeDetail`，内容与偏好同步移除。

### 4.13 保存样式与模板

```http
PUT /api/resumes/{id}/style
satoken: <token>
Content-Type: application/json
```

```json
{
  "templateCode": "ceying",
  "filter": "soft",
  "fontSizeStep": 1,
  "spacingStep": -1,
  "showBrandFooter": false
}
```

所有字段可选：

| 字段 | 取值 |
| --- | --- |
| `templateCode` | 已存在的模板编码；换成别人未分享的导入包会被拒绝（`403`），沿用当前模板不受分享状态影响 |
| `filter` | `standard`、`soft`、`mono` |
| `fontSizeStep` | -2 到 2 |
| `spacingStep` | -2 到 2 |
| `showBrandFooter` | boolean |

此接口不使用 `contentVersion`，后写覆盖先写，返回 `ResumeDetail`。

### 4.14 上传简历照片

```http
POST /api/resumes/{id}/avatar
satoken: <token>
Content-Type: multipart/form-data

file: <二进制>
```

照片走独立通道，不占用附件额度，也不写进简历正文：

- 仅接受真实编码的 JPEG / PNG（按文件头判断，不看扩展名与 MIME）；
- 单张不超过 3MB，长边低于 80px 的图片被拒绝；
- 服务端解码后按所选形状裁剪区域降采样到长边 600px 以内并重编码，透明通道保留为 PNG，其余统一输出 JPEG；
- 每份简历只保留一张照片，新上传会覆盖并清理旧文件；复制简历会连同照片一起复制。

返回 `AvatarVo`：

```json
{
  "present": true,
  "shape": "rect",
  "url": "/api/public/avatars/1?token=<签名链接>",
  "sizeBytes": 37423,
  "updatedAt": 1792662105397
}
```

常见错误（均为 `400 BAD_REQUEST`）：`请选择要上传的头像图片`、`头像图片不能超过 3MB`、`头像仅支持 JPG 或 PNG 图片`、`图片无法解析，请换一张 JPG 或 PNG`、`头像尺寸太小，建议不低于 400×500 像素`。

### 4.15 照片信息

```http
GET /api/resumes/{id}/avatar/info
satoken: <token>
```

返回 `AvatarVo`；未上传时 `present` 为 `false`，其余字段省略。用于前端在签名链接过期后重新获取。

### 4.16 设置照片形状

```http
PUT /api/resumes/{id}/avatar-shape
satoken: <token>
Content-Type: application/json

{ "shape": "circle" }
```

`shape` 只能是 `circle` 或 `rect`，仅改变展示形状，不重新编码图片。返回 `AvatarVo`。

### 4.17 删除照片

```http
DELETE /api/resumes/{id}/avatar
satoken: <token>
```

删除磁盘文件并清空 `avatar_key`，返回 `present: false` 的 `AvatarVo`；此前签发的链接立即失效。

### 4.18 读取照片（登录态）

```http
GET /api/resumes/{id}/avatar
satoken: <token>
```

直接返回图片二进制（`image/jpeg` 或 `image/png`，附 `Cache-Control: private, max-age=3600` 与 `X-Content-Type-Options: nosniff`）。仅用于调试与自检，页面展示请使用 8.5 的签名链接。

### 4.19 上传条目图片（院校 / 公司 logo）

```http
POST /api/resumes/{id}/media
satoken: <token>
Content-Type: multipart/form-data

file: <二进制>
```

教育背景与工作经历的条目可以各挂一张 logo，走独立通道，不占附件额度。服务端解码后降采样到长边 256px 以内重编码（透明通道保留 PNG，其余统一 JPEG），单张不超过 2MB，长边低于 64px 被拒绝。

返回 `ItemMediaVo`：

```json
{ "key": "media/1/4f1c…e0d.png", "url": "/api/public/media/1/4f1c…e0d.png", "sizeBytes": 14161 }
```

**调用方要把 `key`（不是 `url`）写进对应条目的 `fields.logo`，再用 4.9 保存模块落库**，`url` 只用于上传后的即时预览。原因：编辑器会把整份 content 原样存回去，任何服务端注入的临时链接都会在下次保存时被写进数据。

文件生命周期由服务端管理，前端不需要（也不应该）主动删除：

- 换图或移除只改 `fields.logo`；每次内容落库后，服务端扫描 `media/{resumeId}/` 目录，删掉已不被整份 content 引用、且已超过 10 分钟宽限期的文件。宽限期是为了保护「刚上传、还没点保存模块」的图片。
- 复制简历时 logo 会连同文件一起复制到新简历的目录并改写副本里的 `key`，两份简历此后各删各的，互不影响。
- 删除简历时该简历的整个 `media/{resumeId}/` 目录被清空。

常见错误（均为 `400 BAD_REQUEST`）：`请选择要上传的图片`、`图片不能超过 2MB`、`只支持 JPG 或 PNG 图片`、`图片无法解析，请换一张 JPG 或 PNG`、`图片尺寸太小，建议不低于 128×128 像素`。

### 4.20 服务端 PDF 导出

```http
GET /api/resumes/{id}/pdf?templateCode=chenxing&filter=standard&fontSizeStep=0&spacingStep=0&showBrandFooter=true
satoken: <token>
```

参数全部可选，缺省时使用简历已保存的样式；仅这四个参数会被接受，其余样式字段无法通过查询串注入。

成功时返回 `application/pdf` 附件（`Content-Disposition: attachment; filename="简历标题.pdf"; filename*=UTF-8''…`，文件名已剔除路径与非法字符并截断到 40 字），`Cache-Control: private, no-store`。

导出由本机 Chrome / Edge / Chromium 无头打印前端导出页生成，与预览使用同一套 A4 排版；结果按「简历内容版本 + 更新时间 + 照片更新时间 + 模板 + 样式」缓存 24 小时，命中缓存时不再重新渲染。照片变更会立即使旧缓存失效。

引擎不可用时返回 `503`：

```json
{ "code": "PDF_ENGINE_UNAVAILABLE", "message": "当前环境没有可用的无头浏览器，请改用浏览器打印导出" }
```

前端捕获该错误码后会自动回落到浏览器打印，不会中断下载。

### 4.21 导出引擎状态

```http
GET /api/pdf/status
satoken: <token>
```

```json
{ "available": true, "engine": "chrome-headless", "message": null }
```

`engine` 为 `chrome-headless` 或 `none`；`available` 为 `false` 时 `message` 给出提示。前端据此决定是否显示「下载 PDF」按钮。

---

## 5. 简历内容 JSON

### 5.1 结构

```json
{
  "schemaVersion": 1,
  "language": "zh",
  "sections": [
    {
      "id": "sec_basic_info",
      "type": "basic_info",
      "title": "基本信息",
      "visible": true,
      "order": 0,
      "meta": {},
      "items": [
        {
          "id": "itm_basic_info",
          "fields": {
            "name": "郝简历",
            "gender": "男",
            "showGender": true,
            "phone": "13800000000",
            "email": "lin@example.com",
            "identity": "职场人",
            "city": "上海",
            "birthday": "1998-06",
            "showAge": false,
            "nativePlace": "浙江杭州",
            "ethnicity": "汉族",
            "politics": "中共党员",
            "maritalStatus": "未婚",
            "website": "https://example.com"
          }
        }
      ]
    }
  ]
}
```

- 模块顺序以 `modulePrefs.order` 与 `section.order` 为准；渲染时优先 `modulePrefs.order`。
- 单条目模块：`basic_info`、`job_intention`、`skills_summary`、`self_evaluation`、`paper_publication`、`postgraduate_info`、`hobby`。
- 多条目模块：其余核心模块与全部可选模块。
- `meta.displayMode` 仅用于 `job_intention`，建议值 `title_only`（姓名下方）或 `full`（独立模块）。

### 5.2 字段约定

| 结构 | 格式 |
| --- | --- |
| 时间区间 `period` | `{"start":"YYYY-MM","end":"YYYY-MM","current":false}`；`current=true` 表示至今 |
| 标签 `courses` 等 | 字符串数组 |
| 富文本 | `content` / `description` / `note` / `summary` 支持 `<p> <br> <strong> <b> <em> <i> <u> <s> <ul> <ol> <li>`，其余标签与属性会被移除 |
| URL | `link` / `website` / `github` 必须以 `http://` 或 `https://` 开头，≤ 500 字符 |
| 开关 | `showGender`、`showAge`、`salaryNegotiable` 等为 boolean |
| 条目图片 `logo` | `education` / `work_experience` 条目的可选字段，存 4.19 返回的存储 key（`media/{resumeId}/{uuid}.{ext}`），不存 URL；渲染时由前端推导成 `/api/public/media/...`。字段值不受白名单限制（`fields.*` 任意键都会被保留），但只有这个约定键会被版面消费 |

单个文本字段最长 20,000 字符；对象字段数最多 60；数组最多 50 项；嵌套深度最多 8 层；整份内容 JSON 最大 1MB。

### 5.3 模块类型参考

| type | 名称 | 条目 | 说明 |
| --- | --- | --- | --- |
| `basic_info` | 基本信息 | 单条 | 不可隐藏 |
| `job_intention` | 求职意向 | 单条 | 支持两种展示方式 |
| `education` | 教育背景 | 多条 | 不可隐藏；条目可挂院校 `logo` |
| `work_experience` | 工作经历 | 多条 | 条目可挂公司 `logo` |
| `project_experience` | 项目经历 | 多条 | |
| `campus_experience` | 在校经历 | 多条 | |
| `skills_summary` | 个人技能 | 单条 | 富文本 |
| `skill_proficiency` | 技能熟练度 | 多条 | 前端最多 8 项 |
| `paper_publication` | 论文发表 | 单条 | 默认隐藏 |
| `portfolio` | 作品集 | 多条 | |
| `self_evaluation` | 自我评价 | 单条 | |
| `postgraduate_info` | 考研信息 | 单条 | 可选 |
| `competition` | 竞赛经历 | 多条 | 可选 |
| `award` | 获奖情况 | 多条 | 可选 |
| `certificate` | 资格证书 | 多条 | 可选 |
| `journal_paper` | 论文期刊 | 多条 | 可选 |
| `hobby` | 兴趣爱好 | 单条 | 可选 |
| `custom_1`–`custom_5` | 自定义模块 | 多条 | 可选，最多 5 个、每模块 10 条；旧数据可能为 `custom` |

字段定义以前端 `frontend/src/data/sectionCatalog.js` 为准确来源。

---

## 6. 附件

### 6.1 附件列表

```http
GET /api/resumes/{id}/attachments
satoken: <token>
```

返回 `AttachmentVo[]`：

```json
[
  {
    "id": 12,
    "fileName": "作品集.pdf",
    "mime": "application/pdf",
    "sizeBytes": 102400,
    "createdAt": 1792646790000
  }
]
```

### 6.2 上传附件

```http
POST /api/resumes/{id}/attachments
satoken: <token>
Content-Type: multipart/form-data
```

表单字段：`file`。

限制：

- 单份简历最多 3 个附件；
- 单文件 ≤ 10MB，请求总大小 ≤ 25MB；
- 允许 `jpg`、`jpeg`、`png`、`pdf`、`doc`、`docx`、`txt`；
- `jpg` / `png` / `pdf` 会校验文件头，扩展名与内容不一致时返回 400。

返回新附件 `AttachmentVo`。

### 6.3 删除附件

```http
DELETE /api/attachments/{attachmentId}
satoken: <token>
```

返回 `data: null`，并尽量删除服务器文件。

### 6.4 预览 / 下载附件

```http
GET /api/attachments/{attachmentId}/file
satoken: <token>
```

返回二进制流，不是 JSON 信封。响应头：

- `Content-Type` 为实际 MIME；
- PDF、图片、纯文本使用 `Content-Disposition: inline`，其他格式为 `attachment`；
- 包含 `Cache-Control: private, no-store`、`X-Content-Type-Options: nosniff`、`Content-Security-Policy: sandbox`。

因为需要 `satoken` 请求头，不能把该 URL 直接作为 `<a href>` 或 `<img src>` 使用；前端通过带鉴权的请求获取 Blob 后再生成对象 URL。

---

## 7. 简历诊断

### 7.1 生成报告

```http
POST /api/resumes/{id}/report
satoken: <token>
```

每次调用都会生成一份新报告，并同步更新简历的 `score`、`completeness`、`scoreUpdatedAt`。

```json
{
  "id": 9,
  "resumeId": 1,
  "contentVersion": 13,
  "totalScore": 91,
  "grade": "优秀",
  "dimensions": [
    {
      "key": "completeness",
      "label": "完整度",
      "score": 25,
      "max": 25,
      "summary": "联系方式与核心模块已经比较完整。"
    }
  ],
  "issues": [
    {
      "id": "issue_1",
      "sectionType": "work_experience",
      "fieldKey": "content",
      "severity": "high",
      "title": "没有可展示的履历",
      "detail": "工作、项目或在校经历是简历的主体内容。",
      "suggestion": "从最相关的一段履历开始填写，优先写清职责与结果。"
    }
  ],
  "createdAt": 1792646790000,
  "current": true
}
```

- 维度固定为 5 项：`completeness`（25）、`job_match`（25）、`expression`（20）、`quantification`（15）、`format`（15）。
- 等级：`优秀` ≥ 90、`良好` ≥ 75、`待提升` ≥ 60、其余 `需打磨`。
- `issues` 按 `high` → `medium` → `low` 排序，最多 12 条；`sectionType` 可用于跳转到对应模块。
- 评分完全基于本地规则，不调用外部 AI，同一内容结果可复现。

### 7.2 最新报告

```http
GET /api/resumes/{id}/report/latest
satoken: <token>
```

返回最近一次生成的 `ReportVo`；从未生成时 `data` 为 `null`（`code` 仍为 `OK`）。

`current` 表示报告生成时的内容版本是否仍等于简历当前版本。内容保存后旧报告的 `current` 变为 `false`，应提示重新诊断。

---

## 8. 分享

### 8.1 创建分享链接

```http
POST /api/resumes/{id}/shares
satoken: <token>
Content-Type: application/json
```

```json
{
  "password": "test1234",
  "expiresInDays": 30,
  "allowDownload": true,
  "hiddenFields": ["phone", "email", "city", "website"]
}
```

| 字段 | 默认 | 规则 |
| --- | --- | --- |
| `password` | 空 | 可空；非空时 4–20 字符，BCrypt 存储 |
| `expiresInDays` | `30` | `0` 永久；`1–365` 天 |
| `allowDownload` | `false` | 是否显示打印 / 下载入口 |
| `hiddenFields` | `["phone","email"]` | 可隐藏 `phone`、`email`、`wechat`、`qq`、`city`、`website` |

`hiddenFields` 传空数组表示不隐藏可选字段；无论是否传入，`idNumber` 都会在分享响应中移除。

**每份简历同时只保留一个有效分享链接**。创建新链接会自动撤销之前的有效链接。返回 `ShareVo`：

```json
{
  "id": 3,
  "token": "Yk9v...43字符随机串",
  "resumeTitle": "郝简历 · 后端开发工程师",
  "expiresAt": 1795238790000,
  "allowDownload": true,
  "hiddenFields": ["phone", "email", "city", "website"],
  "hasPassword": true,
  "viewCount": 0,
  "createdAt": 1792646790000
}
```

分享访问地址：`/s/{token}`（前端路由）。

### 8.2 当前有效分享

```http
GET /api/resumes/{id}/shares
satoken: <token>
```

返回未撤销且未过期的 `ShareVo[]`，通常为 0 或 1 条。

### 8.3 撤销分享

```http
DELETE /api/shares/{shareId}
satoken: <token>
```

仅创建者可撤销；成功返回 `data: null`。撤销后公开链接立即返回 `410 SHARE_REVOKED`。

### 8.4 公开访问

```http
GET /api/public/shares/{token}
```

无需登录。若链接有密码：

```json
{
  "code": "SHARE_PASSWORD_REQUIRED",
  "message": "请输入访问密码",
  "data": null
}
```

HTTP 401。输入密码：

```http
POST /api/public/shares/{token}/unlock
Content-Type: application/json
```

```json
{ "password": "test1234" }
```

密码错误返回 `401 SHARE_PASSWORD_INVALID`；链接过期或撤销返回 `410 SHARE_EXPIRED` / `410 SHARE_REVOKED`。

成功返回 `PublicShareVo`：

```json
{
  "token": "Yk9v...",
  "title": "郝简历 · 后端开发工程师",
  "language": "zh",
  "templateCode": "qinghe",
  "content": { "schemaVersion": 1, "language": "zh", "sections": [] },
  "style": { "templateCode": "qinghe", "filter": "standard" },
  "templateConfig": { "layout": "minimal", "accent": "#2f6f62" },
  "template": { "code": "qinghe", "name": "青禾", "category": "简约" },
  "allowDownload": true,
  "expiresAt": 1795238790000,
  "updatedAt": 1792646790000,
  "ownerName": "郝简历",
  "brandFooter": true,
  "avatar": { "shape": "circle", "url": "/api/public/avatars/1?token=<签名链接>", "sizeBytes": 37423, "updatedAt": 1792662105397 }
}
```

- 隐藏字段在服务端从 `content.sections[basic_info].items[].fields` 中移除，访客无法通过接口还原。
- 每次成功获取或解锁都会使 `viewCount +1`。
- `expiresAt=null` 表示永久有效。
- 分享页只读，不返回附件、诊断报告或用户登录信息；仅返回分享者昵称 `ownerName` 作为署名。
- `avatar` 在未上传照片时省略；分享页与预览页使用同一份照片与形状渲染照片版式。

### 8.5 公开媒体、导出载荷与示例简历（无需登录）

这几个接口挂在 `/api/public/**` 下，靠签名、随机文件名或一次性票据鉴权，用于 `<img>` 与服务端无头浏览器渲染。

```http
GET /api/public/avatars/{resumeId}?token=<签名链接>
```

返回照片二进制。`token` 是 `HMAC-SHA256` 签名的短时链接（作用域 `avatar`，默认 6 小时），签名无效返回 `403 FORBIDDEN`，过期返回 `403 FORBIDDEN`（`签名链接已过期，请刷新页面`），简历或照片不存在返回 `404`。密钥来自 `MINIWHO_MEDIA_SECRET`，未设置时每次启动随机生成，因此重启后旧链接失效。

```http
GET /api/public/media/{resumeId}/{name}
```

返回条目 logo 二进制（`image/jpeg` / `image/png`，`Cache-Control: public, max-age=86400, immutable`）。这条链接不带签名：`name` 是 128 位随机 UUID，本身就是不可枚举的能力凭证，而链接必须由前端从 `fields.logo` 推导——签名链接一旦存进 content 就会在下次保存时被写回数据。文件名不符合 UUID 格式或穿越目录返回 `400 BAD_REQUEST`，文件不存在返回 `404`。

```http
GET /api/public/exports/{token}
```

返回服务端 PDF 渲染所需的一次性载荷（`ExportPayloadVo`：标题、语言、模板编码与名称、内容、样式、模板配置、照片、更新时间）。`token` 由 4.20 在导出时签发，5 分钟过期且只绑定该简历与模板，过期或伪造返回 `404 NOT_FOUND`（`导出链接无效或已过期，请重新发起导出`）。

```http
GET /api/public/sample-resume
```

返回管理员标记为「首页示例」的那一份简历，供首页 hero、模板中心缩略图与放大预览共用，响应带 `Cache-Control: max-age=60, public`，所以后台改完最多 60 秒才反映到前台。全站只允许一份，没有设置时返回 `data: null`，前端回落到打包的出厂示例。

```json
{
  "templateCode": "qinghe",
  "content": { "schemaVersion": 1, "language": "zh", "sections": [] }
}
```

这个接口不校验登录，因此只输出渲染版面必需的两个字段：不带 `userId`、标题、样式偏好、照片和分享配置。示例简历由 10.7 设置。

---

## 9. 意见反馈

```http
POST /api/feedback
satoken: <token>
Content-Type: application/json
```

```json
{
  "content": "希望支持导出 Word。",
  "contact": "me@example.com"
}
```

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `content` | 是 | 1–2000 字符 |
| `contact` | 否 | ≤ 200 字符 |

成功返回 `data: null`。反馈仅登录用户可提交，管理员在后台查看与回复。

---

## 10. 管理后台

以下接口均要求登录用户 `role=admin`，否则返回 `403 FORBIDDEN`。

### 10.1 运营概览

```http
GET /api/admin/stats
```

```json
{
  "users": 12,
  "resumes": 20,
  "shares": 5,
  "reports": 8,
  "templates": 8,
  "feedbackPending": 2,
  "newUsers7d": 3,
  "newResumes7d": 7,
  "recentUsers": [
    {
      "id": 12,
      "username": "alice",
      "nickname": "Alice",
      "role": "user",
      "createdAt": 1792646790000
    }
  ]
}
```

`templates` 为启用中的模板数量，`recentUsers` 最多 6 条。

### 10.2 用户列表

```http
GET /api/admin/users
```

返回最近 200 个用户：

```json
[
  {
    "id": 1,
    "username": "demo",
    "nickname": "郝简历",
    "email": "demo@miniwho.local",
    "emailVerified": false,
    "role": "user",
    "status": 1,
    "createdAt": 1790000000000,
    "resumeCount": 1
  }
]
```

响应里 `emailVerified` 是布尔值；没绑定邮箱的账号不出现 `email` 字段（见 1.1）。只有已验证的邮箱才会被 GitHub 登录用来关联账号，存量库里手工填写的邮箱一律是 `false`。

### 10.3 冻结 / 恢复用户

```http
PUT /api/admin/users/{id}/status
Content-Type: application/json
```

```json
{ "status": 2 }
```

`1` 正常、`2` 冻结；不能修改当前登录账号自身。冻结时会立即注销该用户的所有会话，后续请求返回 401；恢复后需重新登录。返回 `data: null`。

### 10.4 模板列表

```http
GET /api/admin/templates
```

返回全部模板实体（含停用模板与用户导入的模板包），字段包括：

```json
{
  "id": 1,
  "code": "qinghe",
  "name": "青禾",
  "description": "留白充足的单栏版式...",
  "category": "简约",
  "tags": "单栏,校招,社招,通用",
  "config": "{...JSON 字符串...}",
  "pkg": "{...模板包原文（紧凑 JSON）...}",
  "namespace": "official",
  "version": "1.0.0",
  "author": "MiniWho",
  "source": "builtin",
  "api": 1,
  "checksum": "d5698f...",
  "ownerUserId": null,
  "status": 1,
  "sortWeight": 10,
  "usageCount": 12,
  "createdAt": 1790000000000,
  "updatedAt": 1790000000000
}
```

注意：管理接口返回的 `tags` 是逗号分隔字符串，`config` 是 JSON 字符串；公开模板接口返回的 `tags` 是数组、`config` 是对象。`source` 为 `imported` 的行带有 `ownerUserId`，属于导入者的私有模板。

### 10.5 启用 / 停用模板

```http
PUT /api/admin/templates/{code}/status
Content-Type: application/json
```

```json
{ "status": 2 }
```

`1` 启用、`2` 停用。停用后公开模板列表不再显示，新建简历也不能选择；已使用该模板的简历仍可正常渲染。返回 `data: null`。

### 10.6 导出官方模板包（备份）

```http
GET /api/admin/templates/export
```

把随应用发版进来的全部内置模板打包成一个 JSON 数组返回（美化排版，带 `Content-Disposition: attachment`），用于部署迁移时一次性备份。用户导入的私有模板不在其中——它们是账号级数据，由作者自己用 3.4 导出。

### 10.7 首页示例简历

```http
GET  /api/admin/sample
PUT  /api/admin/resumes/{id}/sample
Content-Type: application/json
```

```json
{ "sample": true }
```

`GET` 返回当前被标为示例的那份简历（`resumeId`、`title`、`templateCode`、`ownerId`、`owner`、`updatedAt`），未设置时 `data: null`。`PUT` 写 `resumes.is_sample`，全站只允许一份：设新的会自动清掉旧的，取消时只去标记、不删简历。设置不会碰简历的 `updated_at`，以免搅乱作者在「我的简历」里看到的排序。

被标上的简历会通过 8.5 的无鉴权接口公开显示内容，所以这个开关只给管理员，前端也带二次确认。删除该简历后标记随之失效，前台回落到打包的出厂示例。

### 10.8 反馈列表

```http
GET /api/admin/feedback
```

按待处理优先、时间倒序返回最近 200 条：

```json
[
  {
    "id": 1,
    "userId": 1,
    "content": "希望支持导出 Word 和版本历史。",
    "contact": "demo@miniwho.local",
    "status": 0,
    "reply": null,
    "createdAt": 1790000000000,
    "updatedAt": null
  }
]
```

### 10.9 处理反馈

```http
PUT /api/admin/feedback/{id}
Content-Type: application/json
```

```json
{
  "status": 1,
  "reply": "已在路线图中，感谢反馈。"
}
```

`status` 可选 `0` / `1`；`reply` ≤ 2000 字符。字段省略时保留原值。返回 `data: null`。

### 10.10 注册 / 登录方式

```http
GET /api/admin/settings
```

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "usernameRegister": false,
    "emailRegister": true,
    "githubLogin": false,
    "mailConfigured": true,
    "githubConfigured": false,
    "githubCallbackUrl": "https://who.example.com/api/auth/oauth/github/callback",
    "publicBaseUrl": "https://who.example.com",
    "mail": {
      "host": "smtp.qq.com",
      "port": "465",
      "username": "you@qq.com",
      "from": "you@qq.com",
      "fromName": "极简简历",
      "ssl": true,
      "starttls": false,
      "passwordSet": true,
      "fromAdmin": false
    },
    "github": {
      "clientId": "",
      "publicBaseUrl": "https://who.example.com",
      "secretSet": false,
      "fromAdmin": false
    }
  }
}
```

前三个字段是管理员存的**意愿**，`mailConfigured` / `githubConfigured` 是通道就绪状态。前台登录页看到的是两者交集，也就是 2.1 的返回值。`githubCallbackUrl` 由站点对外地址拼出，直接抄进 GitHub OAuth App 的回调地址即可；对外地址留空时 `githubCallbackUrl` 与 `publicBaseUrl` 都是 `null`，按 1.1 的规则这两个字段干脆不出现在响应里，后台因此显示「未配置」。

`mail` / `github` 回显的是**当前实际生效**的通道配置（后台填过的项优先，没填的项回落到环境变量），管理员据此知道下一步该填什么：

| 字段 | 说明 |
| --- | --- |
| `passwordSet` / `secretSet` | 授权码 / Client Secret 是否已配。**只报有无，不回显内容** |
| `fromAdmin` | 这一组当前值来自哪里：`true` 表示后台填过（存 `site_settings`），`false` 表示来自环境变量 |

`mail.host` / `port` / `username` / `from` / `fromName` 与 `github.clientId` 会原样回显——这些本身不是机密，且要留痕管理员才敢改；未配置时是空串。

```http
PUT /api/admin/settings
Content-Type: application/json
```

```json
{
  "emailRegister": true,
  "mailHost": "smtp.qq.com",
  "mailPort": "465",
  "mailUsername": "you@qq.com",
  "mailPassword": "<邮箱授权码>",
  "mailFrom": "you@qq.com",
  "mailFromName": "极简简历",
  "mailSsl": true,
  "mailStarttls": false,
  "githubClientId": "Iv1.xxxx",
  "githubClientSecret": "xxxx",
  "publicBaseUrl": "https://who.example.com"
}
```

| 字段 | 说明 |
| --- | --- |
| `usernameRegister` | 用户名注册开关，无外部依赖 |
| `emailRegister` | 邮箱注册开关，`true` 时要求邮件通道就绪 |
| `githubLogin` | GitHub 登录开关，`true` 时要求 GitHub 通道就绪 |
| `mailHost` / `mailPort` / `mailUsername` / `mailPassword` / `mailFrom` / `mailFromName` / `mailSsl` / `mailStarttls` | SMTP 通道，与 `MINIWHO_MAIL_*` 一一对应 |
| `githubClientId` / `githubClientSecret` / `publicBaseUrl` | GitHub OAuth 通道，对应 `MINIWHO_GITHUB_CLIENT_ID` / `MINIWHO_GITHUB_CLIENT_SECRET` / `MINIWHO_PUBLIC_BASE_URL` |

开关与凭据能在同一次请求里一起提交，也能只提交其中几项：

- 三个开关与每一项凭据都可为 `null` / 省略，表示本次不改动该项；字符串传**空串**表示清空该项，清空后这一项重新回落到环境变量。
- 服务端按「先校验入参 → 再存凭据 → 最后判开关」处理，所以填完凭据的同时打开对应开关可以一次成功；入参不合法时整条请求在任何写入之前就被拒绝。
- `mailPort` 非空时必须是 `1`–`65535` 的整数，否则 `400`。
- `publicBaseUrl` 非空时必须以 `http://` 或 `https://` 开头，否则 `400`——它同时是登录完成后浏览器被送回的位置，接受相对地址等于开放重定向。
- 打开一个通道仍未就绪的开关会被拒绝：`400` 并说明还缺哪些项（后台填或环境变量填都算数）。

响应是保存后的最新配置，格式同上。

**保存即生效，不需要重启后端**：发信与 OAuth 每次调用都现取当前配置。开关与凭据都存在 `site_settings` 表（`register_username` / `register_email` / `github_login` / `mail_*` / `github_*` / `public_base_url`），读进内存后有缓存；绕过接口直接改库需要重启后端才生效。凭据在库里是明文，谁能读到这张库谁就能拿到它们，因此不要把数据库暴露到公网。2.1 的公开配置另有 30 秒浏览器缓存，管理员保存后登录页最多 30 秒才变化。

---

## 11. 快速调用示例

```bash
BASE=http://localhost:8080/api

# 登录，复制返回的 data.token
curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}'

TOKEN='粘贴 token'

# 获取简历列表
curl -s "$BASE/resumes" -H "satoken: $TOKEN"

# 获取工作台
curl -s "$BASE/resumes/1/workbench" -H "satoken: $TOKEN"

# 生成诊断报告
curl -s -X POST "$BASE/resumes/1/report" -H "satoken: $TOKEN"

# 创建分享链接
curl -s -X POST "$BASE/resumes/1/shares" \
  -H "satoken: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"password":"test1234","expiresInDays":30,"allowDownload":true}'
```

---

## 12. 业务限制汇总

| 项目 | 限制 |
| --- | --- |
| 用户名 | 3–20 位字母、数字、下划线 |
| 登录 / 注册密码 | 6–32 位 |
| 每用户简历 | 50 份 |
| 单份简历模块 | 30 个 |
| 单模块条目 | 20 条（自定义模块 10 条） |
| 自定义模块 | 5 个（`custom_1`–`custom_5`） |
| 模块标题 | 30 字符；自定义模块 20 字符 |
| 简历标题 | 50 字符 |
| 内容 JSON | 1MB |
| 单字段文本 | 20,000 字符 |
| 附件 | 3 个 / 单文件 10MB / 总请求 25MB |
| 简历照片 | 每份 1 张 / 单文件 3MB / JPEG 与 PNG / 长边不低于 80px / 落盘长边 ≤600px |
| 条目 logo | 每个条目 1 张 / 单文件 2MB / JPEG 与 PNG / 长边不低于 64px / 落盘长边 ≤256px / 未引用且超过 10 分钟的内容落库时回收 |
| 照片签名链接 | 默认 6 小时失效，密钥 `MINIWHO_MEDIA_SECRET` |
| PDF 导出票据 | 5 分钟一次性，绑定简历与模板 |
| PDF 结果缓存 | 24 小时，按内容与样式版本区分 |
| PDF 渲染超时 | 默认 45 秒，`MINIWHO_PDF_TIMEOUT` |
| 分享密码 | 4–20 字符 |
| 分享有效期 | 1–365 天，或 `0` 永久 |
| 分享链接 | 每份简历同时 1 个有效链接 |
| 反馈内容 | 2,000 字符 |
| 登录令牌 | 7 天，进程内存储 |
| 注册验证码 | 6 位数字 / 10 分钟有效 / 验证通过即删除 / 同一封最多试错 5 次 / 超过 24 小时的记录发信时清理 |
| 验证码发信（按邮箱） | 60 秒 1 封、每小时最多 5 封 |
| 注册与验证码（按 IP，每分钟） | 登录 10 次、用户名注册与邮箱注册各 5 次、验证码 5 次、GitHub 跳转与回调合计 20 次 |
| GitHub `state` | 一次性，10 分钟过期，内存存储、上限 10000 条，核销即失效 |
| 用户名 | 3–20 位字母、数字、下划线；邮箱注册与第三方建号自动分配，冲突时追加随机后缀 |
| SMTP 超时 | 连接 5 秒、读写 10 秒；失败只回 502，日志不落收件地址 |

---

## 13. 相关文档

- [项目说明](../README.md)
- [使用教程](使用教程.md)
- [部署指南](部署指南.md)
- 前端接口封装：[`frontend/src/api/index.js`](../frontend/src/api/index.js)
- 模块字段定义：[`frontend/src/data/sectionCatalog.js`](../frontend/src/data/sectionCatalog.js)
