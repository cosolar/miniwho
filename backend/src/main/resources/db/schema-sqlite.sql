CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50),
    email         VARCHAR(100),
    role          VARCHAR(20)  NOT NULL DEFAULT 'user',
    status        INTEGER      NOT NULL DEFAULT 1,
    created_at    BIGINT,
    updated_at    BIGINT
);

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
