CREATE TABLE IF NOT EXISTS users (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    nickname       VARCHAR(50),
    email          VARCHAR(100),
    role           VARCHAR(20)  NOT NULL DEFAULT 'user',
    status         INTEGER      NOT NULL DEFAULT 1,
    -- 第三方登录的账号没有可用密码，占位哈希 + 这个标记一起用，改密码入口据此隐藏。
    has_password   INTEGER      NOT NULL DEFAULT 1,
    -- 只有走过邮箱验证码的邮箱才算已验证：GitHub 关联本地账号只认已验证的邮箱。
    email_verified INTEGER      NOT NULL DEFAULT 0,
    created_at     BIGINT,
    updated_at     BIGINT
);
-- 邮箱既是登录凭据也是账号归属依据，必须唯一；NULL 不受约束，第三方账号可以不留邮箱。
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email ON users(email);

CREATE TABLE IF NOT EXISTS templates (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    category    VARCHAR(30),
    tags        VARCHAR(500),
    config      TEXT,
    pkg         TEXT,
    namespace   VARCHAR(60)  NOT NULL DEFAULT 'official',
    version     VARCHAR(30)  NOT NULL DEFAULT '1.0.0',
    author      VARCHAR(100),
    source      VARCHAR(20)  NOT NULL DEFAULT 'builtin',
    api         INTEGER NOT NULL DEFAULT 1,
    checksum    VARCHAR(64),
    owner_user_id BIGINT,
    visibility  VARCHAR(20)  NOT NULL DEFAULT 'private',
    exportable  INTEGER NOT NULL DEFAULT 1,
    status      INTEGER NOT NULL DEFAULT 1,
    sort_weight INTEGER NOT NULL DEFAULT 0,
    usage_count INTEGER NOT NULL DEFAULT 0,
    created_at  BIGINT,
    updated_at  BIGINT
);

CREATE TABLE IF NOT EXISTS resumes (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id           INTEGER NOT NULL,
    title             VARCHAR(100) NOT NULL,
    language          VARCHAR(8)   NOT NULL DEFAULT 'zh',
    template_code     VARCHAR(50)  NOT NULL DEFAULT 'qinghe',
    source            VARCHAR(20)  NOT NULL DEFAULT 'blank',
    content           TEXT         NOT NULL,
    module_prefs      TEXT         NOT NULL,
    style             TEXT         NOT NULL,
    avatar_key        VARCHAR(500),
    avatar_shape      VARCHAR(20)  NOT NULL DEFAULT 'rect',
    content_version   INTEGER      NOT NULL DEFAULT 0,
    completeness      INTEGER      NOT NULL DEFAULT 0,
    score             INTEGER,
    score_updated_at  BIGINT,
    lifecycle_status  INTEGER      NOT NULL DEFAULT 1,
    is_sample         INTEGER      NOT NULL DEFAULT 0,
    deleted           INTEGER      NOT NULL DEFAULT 0,
    created_at        BIGINT,
    updated_at        BIGINT
);
CREATE INDEX IF NOT EXISTS idx_resumes_user ON resumes(user_id, deleted, updated_at DESC);
-- is_sample 不建索引：存量库只做 ADD COLUMN，写在脚本里的索引会让新库与升级库的 schema 分叉。

CREATE TABLE IF NOT EXISTS resume_attachments (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    resume_id   INTEGER NOT NULL,
    user_id     INTEGER NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    mime        VARCHAR(100),
    size_bytes  BIGINT,
    created_at  BIGINT
);
CREATE INDEX IF NOT EXISTS idx_attachments_resume ON resume_attachments(resume_id);

CREATE TABLE IF NOT EXISTS resume_shares (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    resume_id      INTEGER NOT NULL,
    user_id        INTEGER NOT NULL,
    token          VARCHAR(80) NOT NULL UNIQUE,
    password_hash  VARCHAR(100),
    expires_at     BIGINT,
    allow_download INTEGER NOT NULL DEFAULT 0,
    hidden_fields  TEXT,
    view_count     BIGINT NOT NULL DEFAULT 0,
    revoked_at     BIGINT,
    created_at     BIGINT
);
CREATE INDEX IF NOT EXISTS idx_shares_resume ON resume_shares(resume_id);
CREATE INDEX IF NOT EXISTS idx_shares_token ON resume_shares(token);

CREATE TABLE IF NOT EXISTS resume_reports (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    resume_id       INTEGER NOT NULL,
    user_id         INTEGER NOT NULL,
    content_version INTEGER NOT NULL,
    total_score     INTEGER NOT NULL,
    grade           VARCHAR(20),
    dimensions      TEXT,
    issues          TEXT,
    created_at      BIGINT
);
CREATE INDEX IF NOT EXISTS idx_reports_resume ON resume_reports(resume_id, created_at DESC);

CREATE TABLE IF NOT EXISTS feedback (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER,
    content    VARCHAR(2000) NOT NULL,
    contact    VARCHAR(200),
    status     INTEGER NOT NULL DEFAULT 0,
    reply      VARCHAR(2000),
    created_at BIGINT,
    updated_at BIGINT
);

-- 站点统计只存累计值，在线人数由应用内存里的心跳窗口计算，不落库。
CREATE TABLE IF NOT EXISTS site_stats (
    stat_key   VARCHAR(50) PRIMARY KEY,
    stat_value BIGINT      NOT NULL DEFAULT 0,
    updated_at BIGINT
);

-- 管理员可调的站点开关：值是字符串，按 key 解释成布尔/文本，缺 key 时用代码里的默认值。
CREATE TABLE IF NOT EXISTS site_settings (
    setting_key   VARCHAR(60)  PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    BIGINT
);

-- 外部身份：provider 区分渠道，同一个渠道内 provider_user_id 唯一。
-- 单独建表而不是往 users 上加列，是为了以后加渠道不用再动账号表。
CREATE TABLE IF NOT EXISTS user_identities (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER      NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(64)  NOT NULL,
    provider_login   VARCHAR(100),
    email            VARCHAR(100),
    created_at       BIGINT,
    UNIQUE (provider, provider_user_id)
);
CREATE INDEX IF NOT EXISTS idx_identities_user ON user_identities(user_id);

-- 邮箱验证码：只存哈希，用完或过期即删，避免明文验证码在库里留着。
CREATE TABLE IF NOT EXISTS email_codes (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    email      VARCHAR(100) NOT NULL,
    purpose    VARCHAR(20)  NOT NULL,
    code_hash  VARCHAR(100) NOT NULL,
    expires_at BIGINT       NOT NULL,
    attempts   INTEGER      NOT NULL DEFAULT 0,
    created_at BIGINT
);
CREATE INDEX IF NOT EXISTS idx_email_codes_email ON email_codes(email, purpose, created_at DESC);
