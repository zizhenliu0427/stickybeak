-- ============================================================================
-- Sprint 1 / Issue 1.1 — stickybeak_auth schema + seed roles
-- Conventions: t_ prefix, snake_case, BIGINT PK, is_deleted logical delete,
--              create_time/update_time, idx_<table>_<column> index names.
-- t_user.id is app-assigned snowflake (MyBatis Plus ASSIGN_ID);
-- internal tables use AUTO_INCREMENT.
-- ============================================================================

USE stickybeak_auth;

-- ----------------------------------------------------------------------------
-- t_user
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user (
    id            BIGINT       NOT NULL                COMMENT '雪花 ID（应用侧 ASSIGN_ID）',
    email         VARCHAR(64)  NOT NULL                COMMENT '登录邮箱',
    password_hash VARCHAR(128) NOT NULL                COMMENT 'bcrypt',
    nickname      VARCHAR(32)  DEFAULT NULL            COMMENT '昵称',
    phone         VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    avatar_url    VARCHAR(255) DEFAULT NULL            COMMENT '头像 URL',
    status        TINYINT      NOT NULL DEFAULT 0      COMMENT '0 正常 1 禁用',
    is_deleted    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户';

-- ----------------------------------------------------------------------------
-- t_role / t_user_role（RBAC，多对多）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32) NOT NULL                COMMENT '角色码：customer/admin/sysadmin',
    name        VARCHAR(64) NOT NULL                COMMENT '显示名',
    is_deleted  TINYINT     NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='角色';

CREATE TABLE IF NOT EXISTS t_user_role (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    role_id     BIGINT   NOT NULL,
    is_deleted  TINYINT  NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role_user_role (user_id, role_id),
    KEY idx_user_role_role (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户-角色';

-- ----------------------------------------------------------------------------
-- t_refresh_token（只存哈希；轮换 + 复用检测）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_refresh_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL                COMMENT '所属用户',
    token_hash  VARCHAR(128) NOT NULL                COMMENT 'SHA-256 哈希，防库泄',
    expires_at  DATETIME     NOT NULL                COMMENT '过期时间（7 天）',
    revoked     TINYINT      NOT NULL DEFAULT 0      COMMENT '1=已吊销（轮换/登出/复用检测）',
    is_deleted  TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='刷新令牌';

-- ----------------------------------------------------------------------------
-- t_address（一人多地址，真实物流预留）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_address (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    receiver    VARCHAR(64)  NOT NULL                COMMENT '收件人',
    phone       VARCHAR(20)  NOT NULL                COMMENT '联系电话',
    country     VARCHAR(64)  NOT NULL DEFAULT 'Australia',
    state       VARCHAR(64)  NOT NULL                COMMENT '州/省，如 NSW',
    city        VARCHAR(64)  NOT NULL,
    postcode    VARCHAR(16)  NOT NULL,
    detail      VARCHAR(255) NOT NULL                COMMENT '详细地址',
    is_default  TINYINT      NOT NULL DEFAULT 0      COMMENT '1=默认地址',
    is_deleted  TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_address_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='收货地址';

-- ----------------------------------------------------------------------------
-- 种子角色（幂等）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO t_role (code, name) VALUES
    ('customer', 'Customer'),
    ('admin',    'Admin'),
    ('sysadmin', 'System Admin');
