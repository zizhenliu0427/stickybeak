-- ============================================================================
-- Sprint 2 / Issue 2.1 — stickybeak_product schema + seed categories
-- Conventions: t_ prefix, snake_case, BIGINT PK, is_deleted logical delete,
--              create_time/update_time, idx_<table>_<column> index names.
-- t_product.id is app-assigned snowflake (MyBatis Plus ASSIGN_ID);
-- internal tables use AUTO_INCREMENT.
-- ============================================================================

USE stickybeak_product;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- t_category（商品分类）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_category (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64) NOT NULL                COMMENT '分类名称',
    slug       VARCHAR(64) NOT NULL                COMMENT 'URL slug',
    parent_id  BIGINT      DEFAULT NULL            COMMENT '父分类 ID（预留多级）',
    sort_order INT         NOT NULL DEFAULT 0      COMMENT '排序权重',
    is_deleted TINYINT     NOT NULL DEFAULT 0,
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_slug (slug),
    KEY idx_category_parent (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '商品分类';

-- ----------------------------------------------------------------------------
-- t_product（商品）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_product (
    id              BIGINT        NOT NULL              COMMENT '雪花 ID（应用侧 ASSIGN_ID）',
    name            VARCHAR(128)  NOT NULL              COMMENT '商品名',
    slug            VARCHAR(160)  NOT NULL              COMMENT 'SEO URL slug',
    description     TEXT          DEFAULT NULL           COMMENT '商品描述（取自笔记正文）',
    category_id     BIGINT        NOT NULL              COMMENT '所属分类',
    price           DECIMAL(10,2) NOT NULL              COMMENT '价格（AUD）',
    stock           INT           NOT NULL DEFAULT 0    COMMENT '库存数量',
    sales           INT           NOT NULL DEFAULT 0    COMMENT '销量',
    source_note_id  VARCHAR(32)   DEFAULT NULL          COMMENT '溯源：小红书笔记 ID',
    metadata        JSON          DEFAULT NULL          COMMENT '扩展属性（系列/尺寸/材质等）',
    featured        TINYINT       NOT NULL DEFAULT 0    COMMENT '1=精选推荐',
    status          TINYINT       NOT NULL DEFAULT 0    COMMENT '0 上架 1 下架',
    is_deleted      TINYINT       NOT NULL DEFAULT 0,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_slug (slug),
    KEY idx_product_category_status (category_id, status),
    KEY idx_product_sales (sales),
    KEY idx_product_source (source_note_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '商品';

-- ----------------------------------------------------------------------------
-- t_product_image（商品图片）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_product_image (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    product_id  BIGINT       NOT NULL              COMMENT '所属商品',
    url         VARCHAR(512) NOT NULL              COMMENT '图片 URL',
    sort_order  INT          NOT NULL DEFAULT 0    COMMENT '排序',
    is_cover    TINYINT      NOT NULL DEFAULT 0    COMMENT '1=封面图',
    is_deleted  TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_image_product (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '商品图片';

-- ----------------------------------------------------------------------------
-- t_tag（标签）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_tag (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL              COMMENT '标签名',
    is_deleted  TINYINT     NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '标签';

-- ----------------------------------------------------------------------------
-- t_product_tag_rel（商品-标签关联）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_product_tag_rel (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    product_id  BIGINT   NOT NULL,
    tag_id      BIGINT   NOT NULL,
    is_deleted  TINYINT  NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_tag (product_id, tag_id),
    KEY idx_product_tag_tag (tag_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '商品-标签关联';

-- ----------------------------------------------------------------------------
-- t_stock_hold（库存预占，Sprint 5 使用，此处预建空表）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_stock_hold (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    product_id  BIGINT   NOT NULL,
    order_no    VARCHAR(64) NOT NULL             COMMENT '关联订单号',
    qty         INT      NOT NULL                COMMENT '预占数量',
    status      TINYINT  NOT NULL DEFAULT 0      COMMENT '0 占用中 1 已扣减 2 已释放',
    expire_at   DATETIME NOT NULL                COMMENT '超时释放时间（默认 15min）',
    is_deleted  TINYINT  NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_stock_hold_product (product_id),
    KEY idx_stock_hold_order (order_no),
    KEY idx_stock_hold_expire (expire_at, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '库存预占';

-- ----------------------------------------------------------------------------
-- 种子分类（幂等，slug 对齐前端 mock）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO t_category (name, slug, sort_order) VALUES
    ('大学公交路牌', 'bus-sign',    1),
    ('超市系列',     'supermarket', 2),
    ('火车电车',     'train',       3),
    ('小鸟路牌',     'bird',        4),
    ('酒鬼系列',     'booze',       5),
    ('手机壳周边',   'phonecase',   6);
