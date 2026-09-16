SET NAMES utf8mb4;
USE stickybeak_order;

-- ============================================================
-- 订单主表
-- ============================================================
CREATE TABLE IF NOT EXISTS t_order (
    id                   BIGINT        NOT NULL COMMENT '雪花ID',
    order_no             VARCHAR(32)   NOT NULL COMMENT '业务单号(日期+雪花)',
    user_id              BIGINT        NOT NULL COMMENT '下单用户ID',
    address_snapshot     JSON          NOT NULL COMMENT '下单收货地址快照',
    total_amount         DECIMAL(10,2) NOT NULL COMMENT '总金额(基准币种AUD)',
    pay_amount           DECIMAL(10,2) NOT NULL COMMENT '实际支付金额',
    currency             VARCHAR(8)    NOT NULL DEFAULT 'AUD' COMMENT '支付币种 AUD/CNY',
    exchange_rate_at_pay DECIMAL(12,6) NOT NULL DEFAULT 1.000000 COMMENT '支付时汇率快照',
    status               VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/paid/processing/shipped/completed/cancelled',
    pay_time             DATETIME      NULL     COMMENT '支付成功时间',
    ship_time            DATETIME      NULL     COMMENT '发货时间',
    complete_time        DATETIME      NULL     COMMENT '完成时间',
    remark               VARCHAR(255)  NULL     COMMENT '订单备注',
    is_deleted           TINYINT       NOT NULL DEFAULT 0,
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_order_user (user_id),
    INDEX idx_order_status (status),
    INDEX idx_order_user_status_create (user_id, status, create_time),
    INDEX idx_order_status_create (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- ============================================================
-- 订单条目快照表（名称、图片、价格全部快照）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_order_item (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    order_id      BIGINT        NOT NULL COMMENT '订单ID',
    product_id    BIGINT        NOT NULL COMMENT '商品ID',
    product_name  VARCHAR(128)  NOT NULL COMMENT '下单时商品名称',
    product_image VARCHAR(255)  NOT NULL COMMENT '下单时封面图',
    price         DECIMAL(10,2) NOT NULL COMMENT '下单时单价(AUD)',
    qty           INT           NOT NULL DEFAULT 1 COMMENT '购买数量',
    is_deleted    TINYINT       NOT NULL DEFAULT 0,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_item_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单条目快照表';

-- ============================================================
-- 订单状态流转历史表
-- ============================================================
CREATE TABLE IF NOT EXISTS t_order_status_history (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL,
    from_status   VARCHAR(16)  NOT NULL,
    to_status     VARCHAR(16)  NOT NULL,
    operator_id   BIGINT       NULL,
    operator_role VARCHAR(32)  NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_history_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态历史';
