CREATE TABLE IF NOT EXISTS users (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    username       VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    nickname       VARCHAR(50),
    email          VARCHAR(100),
    role           VARCHAR(20)  NOT NULL DEFAULT 'user',
    status         INT          NOT NULL DEFAULT 1,
    -- 第三方登录的账号没有可用密码，占位哈希 + 这个标记一起用，改密码入口据此隐藏。
    has_password   TINYINT      NOT NULL DEFAULT 1,
    -- 只有走过邮箱验证码的邮箱才算已验证：GitHub 关联本地账号只认已验证的邮箱。
    email_verified TINYINT      NOT NULL DEFAULT 0,
    created_at     BIGINT,
    updated_at     BIGINT,
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS templates (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    category    VARCHAR(30),
    tags        VARCHAR(500),
    config      LONGTEXT,
    pkg         LONGTEXT,
    namespace   VARCHAR(60)  NOT NULL DEFAULT 'official',
    version     VARCHAR(30)  NOT NULL DEFAULT '1.0.0',
    author      VARCHAR(100),
    source      VARCHAR(20)  NOT NULL DEFAULT 'builtin',
    api         INT NOT NULL DEFAULT 1,
    checksum    VARCHAR(64),
    owner_user_id BIGINT,
    visibility  VARCHAR(20)  NOT NULL DEFAULT 'private',
    exportable  INT NOT NULL DEFAULT 1,
    status      INT NOT NULL DEFAULT 1,
    sort_weight INT NOT NULL DEFAULT 0,
    usage_count INT NOT NULL DEFAULT 0,
    created_at  BIGINT,
    updated_at  BIGINT,
    UNIQUE KEY uk_templates_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resumes (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    title            VARCHAR(100) NOT NULL,
    language         VARCHAR(8)   NOT NULL DEFAULT 'zh',
    template_code    VARCHAR(50)  NOT NULL DEFAULT 'qinghe',
    source           VARCHAR(20)  NOT NULL DEFAULT 'blank',
    content          LONGTEXT     NOT NULL,
    module_prefs     LONGTEXT     NOT NULL,
    style            LONGTEXT     NOT NULL,
    avatar_key       VARCHAR(500),
    avatar_shape     VARCHAR(20)  NOT NULL DEFAULT 'rect',
    content_version  INT          NOT NULL DEFAULT 0,
    completeness     INT          NOT NULL DEFAULT 0,
    score            INT,
    score_updated_at BIGINT,
    lifecycle_status INT          NOT NULL DEFAULT 1,
    is_sample        INT          NOT NULL DEFAULT 0,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    created_at       BIGINT,
    updated_at       BIGINT,
    KEY idx_resumes_user (user_id, deleted, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_attachments (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    resume_id   BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    mime        VARCHAR(100),
    size_bytes  BIGINT,
    created_at  BIGINT,
    KEY idx_attachments_resume (resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_shares (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    resume_id      BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    token          VARCHAR(80) NOT NULL,
    password_hash  VARCHAR(100),
    expires_at     BIGINT,
    allow_download TINYINT NOT NULL DEFAULT 0,
    hidden_fields  TEXT,
    view_count     BIGINT NOT NULL DEFAULT 0,
    revoked_at     BIGINT,
    created_at     BIGINT,
    UNIQUE KEY uk_shares_token (token),
    KEY idx_shares_resume (resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resume_reports (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    resume_id       BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    content_version INT NOT NULL,
    total_score     INT NOT NULL,
    grade           VARCHAR(20),
    dimensions      LONGTEXT,
    issues          LONGTEXT,
    created_at      BIGINT,
    KEY idx_reports_resume (resume_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feedback (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT,
    content    VARCHAR(2000) NOT NULL,
    contact    VARCHAR(200),
    status     INT NOT NULL DEFAULT 0,
    reply      VARCHAR(2000),
    created_at BIGINT,
    updated_at BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 站点统计只存累计值，在线人数由应用内存里的心跳窗口计算，不落库。
CREATE TABLE IF NOT EXISTS site_stats (
    stat_key   VARCHAR(50) PRIMARY KEY,
    stat_value BIGINT      NOT NULL DEFAULT 0,
    updated_at BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 管理员可调的站点开关：值是字符串，按 key 解释成布尔/文本，缺 key 时用代码里的默认值。
CREATE TABLE IF NOT EXISTS site_settings (
    setting_key   VARCHAR(60) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 外部身份：provider 区分渠道，同一个渠道内 provider_user_id 唯一。
-- 单独建表而不是往 users 上加列，是为了以后加渠道不用再动账号表。
CREATE TABLE IF NOT EXISTS user_identities (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(64)  NOT NULL,
    provider_login   VARCHAR(100),
    email            VARCHAR(100),
    created_at       BIGINT,
    UNIQUE KEY uk_identity_provider (provider, provider_user_id),
    KEY idx_identities_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 邮箱验证码：只存哈希，用完或过期即删，避免明文验证码在库里留着。
CREATE TABLE IF NOT EXISTS email_codes (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    email      VARCHAR(100) NOT NULL,
    purpose    VARCHAR(20)  NOT NULL,
    code_hash  VARCHAR(100) NOT NULL,
    expires_at BIGINT       NOT NULL,
    attempts   INT          NOT NULL DEFAULT 0,
    created_at BIGINT,
    KEY idx_email_codes_email (email, purpose, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
