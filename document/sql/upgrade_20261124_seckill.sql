-- ----------------------------
-- 秒杀模块增量脚本
-- 日期：2026-11-24
-- 说明：新增秒杀活动表、秒杀商品表；订单复用 oms_order / oms_order_item（order_type=1）
-- ----------------------------

CREATE TABLE IF NOT EXISTS `sms_seckill_activity` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL COMMENT '活动名称',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `status` int(1) NOT NULL DEFAULT 0 COMMENT '0->未开始；1->进行中；2->已结束',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_start_time` (`status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

CREATE TABLE IF NOT EXISTS `sms_seckill_sku` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activity_id` bigint(20) NOT NULL COMMENT '活动id',
  `sku_id` bigint(20) NOT NULL COMMENT 'pms_sku_stock.id',
  `product_id` bigint(20) NOT NULL COMMENT '商品id',
  `seckill_price` decimal(10,2) NOT NULL COMMENT '秒杀价',
  `seckill_stock` int(11) NOT NULL COMMENT '秒杀独立库存',
  `limit_count` int(11) NOT NULL DEFAULT 1 COMMENT '每人限购数量',
  `status` int(1) NOT NULL DEFAULT 0 COMMENT '0->未启用；1->启用',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_sku` (`activity_id`, `sku_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';
