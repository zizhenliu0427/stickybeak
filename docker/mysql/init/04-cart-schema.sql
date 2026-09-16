SET NAMES utf8mb4;
USE stickybeak_cart;

-- ============================================================
-- 购物车（游客 session_id / 登录 user_id 二选一）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_cart (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    user_id     BIGINT       NULL     COMMENT '登录用户ID，游客为NULL',
    session_id  VARCHAR(64)  NULL     COMMENT '游客session UUID，登录用户为NULL',
    is_deleted  TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user    (user_id),
    UNIQUE KEY uk_cart_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

-- ============================================================
-- 购物车条目
-- ============================================================
CREATE TABLE IF NOT EXISTS t_cart_item (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    cart_id      BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    qty          INT           NOT NULL DEFAULT 1,
    price_at_add DECIMAL(10,2) NOT NULL COMMENT '加购时价格快照(AUD)',
    is_deleted   TINYINT       NOT NULL DEFAULT 0,
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_product (cart_id, product_id),
    INDEX idx_cart_item_cart   (cart_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车条目';

-- ============================================================
-- 心愿单（仅登录用户）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_wishlist (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    product_id  BIGINT   NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    INDEX idx_wishlist_user   (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心愿单';
