-- Cover rows must sort first: storefront uses images[0], not is_cover.
-- After SKU merge some products had both cover.webp and the chosen jpg at sort_order 0.

UPDATE t_product_image i
JOIN (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY product_id
           ORDER BY is_cover DESC, sort_order ASC, id ASC
         ) - 1 AS new_sort
  FROM t_product_image
  WHERE is_deleted = 0
) x ON i.id = x.id
SET i.sort_order = x.new_sort;

-- Listed SKUs share the same create_time after import, so LIMIT/OFFSET repeats rows.
UPDATE t_product p
JOIN (
  SELECT id, ROW_NUMBER() OVER (ORDER BY id ASC) AS rn
  FROM t_product
  WHERE status = 0 AND is_deleted = 0
) t ON p.id = t.id
SET p.create_time = DATE_ADD('2026-01-01 00:00:00', INTERVAL t.rn MINUTE);
