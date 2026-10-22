-- 闹钟 App 虚拟商品商城 DDL
-- 表名统一 mall_ 前缀：order 是 MySQL 保留字，用反引号会污染每条手写 SQL。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS acm_mall DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE acm_mall;

CREATE TABLE IF NOT EXISTS mall_user
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email         VARCHAR(128)    NOT NULL COMMENT '登录邮箱',
    password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希（60 字符，留余量）',
    nickname      VARCHAR(64)     NOT NULL,
    status        VARCHAR(16)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL|DISABLED',
    create_time   DATETIME        NOT NULL,
    update_time   DATETIME        NOT NULL,
    deleted       TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='商城用户';

CREATE TABLE IF NOT EXISTS mall_product
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name           VARCHAR(128)    NOT NULL,
    description    VARCHAR(512)    NULL,
    category       VARCHAR(16)     NOT NULL COMMENT 'WORD_BOOK|APP_SKIN',
    price          DECIMAL(10, 2)  NOT NULL COMMENT '售价（元）',
    original_price DECIMAL(10, 2)  NULL COMMENT '划线原价（元）',
    cover_url      VARCHAR(255)    NULL,
    word_count     INT             NULL COMMENT '单词本专用：词数',
    skin_style     VARCHAR(32)     NULL COMMENT '皮肤专用：风格标识',
    on_sale        TINYINT         NOT NULL DEFAULT 1,
    sort_no        INT             NOT NULL DEFAULT 0,
    create_time    DATETIME        NOT NULL,
    update_time    DATETIME        NOT NULL,
    deleted        TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product_list (category, on_sale, sort_no),
    CONSTRAINT chk_product_price CHECK (price >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='虚拟商品';

CREATE TABLE IF NOT EXISTS mall_order
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no      VARCHAR(32)     NOT NULL COMMENT '商户订单号，等于支付宝 out_trade_no',
    user_id       BIGINT UNSIGNED NOT NULL,
    product_id    BIGINT UNSIGNED NOT NULL,
    product_name  VARCHAR(128)    NOT NULL COMMENT '下单时快照',
    category      VARCHAR(16)     NOT NULL COMMENT '下单时快照',
    unit_price    DECIMAL(10, 2)  NOT NULL COMMENT '下单时快照',
    quantity      INT             NOT NULL DEFAULT 1 COMMENT '本演示固定为 1',
    pay_amount    DECIMAL(10, 2)  NOT NULL COMMENT '应付总额快照',
    status        VARCHAR(24)     NOT NULL DEFAULT 'WAITING_PAY' COMMENT 'WAITING_PAY|PAID|CLOSED|REFUNDED',
    expire_time   DATETIME        NOT NULL,
    paid_time     DATETIME        NULL,
    closed_time   DATETIME        NULL,
    refunded_time DATETIME        NULL,
    create_time   DATETIME        NOT NULL,
    update_time   DATETIME        NOT NULL,
    deleted       TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_user (user_id, status, id),
    KEY idx_order_scan (status, expire_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='订单';

CREATE TABLE IF NOT EXISTS mall_payment
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id       BIGINT UNSIGNED NOT NULL,
    out_trade_no   VARCHAR(32)     NOT NULL COMMENT '商户订单号',
    trade_no       VARCHAR(64)     NULL COMMENT '支付宝交易号；唯一索引拦同一笔交易付两个单',
    channel        VARCHAR(16)     NOT NULL DEFAULT 'ALIPAY',
    trade_status   VARCHAR(32)     NULL COMMENT '支付宝原始状态',
    total_amount   DECIMAL(10, 2)  NOT NULL,
    refund_amount  DECIMAL(10, 2)  NOT NULL DEFAULT 0,
    buyer_user_id  VARCHAR(32)     NULL COMMENT '支付宝买家 2088 开头',
    gmt_payment    DATETIME        NULL,
    last_sync_time DATETIME        NULL,
    notify_content TEXT            NULL COMMENT '验签通过的回调原文，留审计',
    create_time    DATETIME        NOT NULL,
    update_time    DATETIME        NOT NULL,
    deleted        TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_out_trade_no (out_trade_no),
    UNIQUE KEY uk_pay_trade_no (trade_no),
    KEY idx_pay_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='支付流水';

CREATE TABLE IF NOT EXISTS mall_refund
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id       BIGINT UNSIGNED NOT NULL,
    out_trade_no   VARCHAR(32)     NOT NULL,
    out_request_no VARCHAR(32)     NOT NULL COMMENT '退款请求号，幂等键',
    refund_amount  DECIMAL(10, 2)  NOT NULL,
    reason         VARCHAR(128)    NULL,
    status         VARCHAR(16)     NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING|SUCCESS|FAIL',
    fund_change    TINYINT         NOT NULL DEFAULT 0,
    trade_no       VARCHAR(64)     NULL,
    gmt_refund     DATETIME        NULL,
    raw_response   VARCHAR(1024)   NULL,
    create_time    DATETIME        NOT NULL,
    update_time    DATETIME        NOT NULL,
    deleted        TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_out_request_no (out_request_no),
    KEY idx_refund_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='退款流水';

CREATE TABLE IF NOT EXISTS mall_entitlement
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL,
    product_id   BIGINT UNSIGNED NOT NULL,
    category     VARCHAR(16)     NOT NULL,
    order_id     BIGINT UNSIGNED NOT NULL COMMENT '由哪一单发放',
    out_trade_no VARCHAR(32)     NOT NULL,
    status       VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|REVOKED',
    granted_time DATETIME        NOT NULL,
    revoked_time DATETIME        NULL,
    create_time  DATETIME        NOT NULL,
    update_time  DATETIME        NOT NULL,
    deleted      TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 发货去重的最后一道锁：同一用户同一商品只有一行
    UNIQUE KEY uk_ent_user_product (user_id, product_id),
    KEY idx_ent_user (user_id, category, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户已购内容（解锁的单词本/皮肤）';
