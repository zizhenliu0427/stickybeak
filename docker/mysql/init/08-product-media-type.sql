-- ============================================================================
-- 商品媒体类型：t_product_image 支持视频（2026-09-17）
-- media_type: image（默认）/ video
-- 幂等：列已存在则跳过（存量库用本脚本；全新安装走 03-product-schema.sql）
-- ============================================================================

SET NAMES utf8mb4;
USE stickybeak_product;

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'stickybeak_product'
      AND TABLE_NAME = 't_product_image'
      AND COLUMN_NAME = 'media_type'
);

SET @sql := IF(
    @col_exists = 0,
    'ALTER TABLE t_product_image ADD COLUMN media_type VARCHAR(16) NOT NULL DEFAULT ''image'' COMMENT ''媒体类型 image/video'' AFTER url',
    'SELECT ''media_type already exists'' AS info'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
