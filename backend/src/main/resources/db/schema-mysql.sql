CREATE TABLE IF NOT EXISTS users (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50),
    email         VARCHAR(100),
    role          VARCHAR(20)  NOT NULL DEFAULT 'user',
    status        INT          NOT NULL DEFAULT 1,
    created_at    BIGINT,
    updated_at    BIGINT,
    UNIQUE KEY uk_users_username (username)
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
