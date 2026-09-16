SET NAMES utf8mb4;
USE stickybeak_payment;

-- ============================================================
-- 支付流水记录
-- ============================================================
CREATE TABLE IF NOT EXISTS t_payment_record (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    order_no             VARCHAR(32)   NOT NULL COMMENT '订单编号',
    provider             VARCHAR(16)   NOT NULL DEFAULT 'stripe' COMMENT '支付服务商 stripe/mock',
    payment_method       VARCHAR(16)   NOT NULL DEFAULT 'card' COMMENT 'card/alipay/wechat_pay',
    session_id           VARCHAR(128)  NULL     COMMENT 'Stripe Checkout Session ID',
    amount               DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    currency             VARCHAR(8)    NOT NULL DEFAULT 'AUD' COMMENT 'aud/cny',
    exchange_rate_at_pay DECIMAL(12,6) NOT NULL DEFAULT 1.000000,
    status               VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/succeeded/failed/refunded',
    transaction_id       VARCHAR(128)  NULL     COMMENT '外部交易单号 (PaymentIntent ID)',
    pay_time             DATETIME      NULL,
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_payment_order (order_no),
    INDEX idx_payment_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录';

-- ============================================================
-- 汇率字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS t_exchange_rate (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    base_currency  VARCHAR(8)    NOT NULL COMMENT '基准币种(AUD)',
    quote_currency VARCHAR(8)    NOT NULL COMMENT '换算币种(CNY)',
    rate           DECIMAL(12,6) NOT NULL COMMENT '当前汇率',
    fetched_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_currency_pair (base_currency, quote_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='汇率字典';

-- 预置种子汇率（AUD -> CNY: 4.750000）
INSERT INTO t_exchange_rate (base_currency, quote_currency, rate, fetched_at)
VALUES ('AUD', 'CNY', 4.750000, NOW())
ON DUPLICATE KEY UPDATE rate = 4.750000, fetched_at = NOW();

-- ============================================================
-- Webhook 幂等记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS t_payment_webhook (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    event_id    VARCHAR(128) NOT NULL COMMENT 'Stripe Event ID (唯一)',
    event_type  VARCHAR(64)  NOT NULL,
    payload     LONGTEXT     NOT NULL COMMENT '原始 Webhook 报文',
    processed   TINYINT      NOT NULL DEFAULT 0 COMMENT '0未处理 1已成功 2处理失败',
    error_msg   TEXT         NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook 幂等表';
