"""Apply visual SKU merge to stickybeak_product.

Defaults confirmed with the shop owner:
- 44 listed SKUs (USYD two prints, UQ three stops, two tram prints, two crossing buttons)
- T2 Parramatta / T4 Bondi / T4 Waterfall not listed
- MAY GO TO HELL not listed; NSW speed-camera photo merged into YOUR SPEED WILL BE CHECKED
- Phone case + DIY tutorial unlisted
- Lookbooks / combo posts unlisted
- E2E test products unlisted

Does not delete rows (orders/carts may still reference old ids).
"""
from __future__ import annotations

import json
from pathlib import Path

HERE = Path(__file__).resolve().parent

# sku_id, keep_product_id, category slug, name, slug, description, featured, cover_url or None
SKUS: list[dict] = [
    {
        "sku": "COL-SPE",
        "id": 1000001,
        "cat": "supermarket",
        "name": "Coles SPECIAL 无糖可乐 2 for $5.20",
        "slug": "coles-special-coke-2-for-520",
        "desc": "Coles SPECIAL 价签冰箱贴。印刷：Coca-Cola Zero Sugar Soft Drink · Any 2 for $5.20。",
        "featured": 1,
    },
    {
        "sku": "COL-HALF",
        "id": 1000204,
        "cat": "supermarket",
        "name": "Coles 1/2 PRICE 抹茶冰淇淋 $6",
        "slug": "coles-half-price-connoisseur-6",
        "desc": "Coles 1/2 PRICE 价签冰箱贴。印刷：Connoisseur Matcha Green Tea & White Choc · WAS $12 SAVE $6。",
        "featured": 0,
    },
    {
        "sku": "COL-DOWN",
        "id": 1000010,
        "cat": "supermarket",
        "name": "Coles DOWN DOWN 橄榄油 $16",
        "slug": "coles-down-down-olive-oil",
        "desc": "Coles DOWN DOWN 价签冰箱贴。印刷：Classic Mild Olive Oil 1 litre · $16。澳洲限定款。",
        "featured": 0,
    },
    {
        "sku": "COL-CLR",
        "id": 1000017,
        "cat": "supermarket",
        "name": "Coles REDUCED TO CLEAR 脱脂奶 $3.10",
        "slug": "coles-reduced-to-clear-milk",
        "desc": "Coles REDUCED TO CLEAR 价签冰箱贴。印刷：Reduced Fat Milk 2 litre · $3.10。澳洲限定款。",
        "featured": 0,
    },
    {
        "sku": "WOW-QS",
        "id": 1000015,
        "cat": "supermarket",
        "name": "Woolies Quick Sale 烤鸡 $6.38",
        "slug": "woolies-quick-sale-roast-chicken",
        "desc": "Woolworths Quick Sale 价签冰箱贴。印刷：Country Style Roast Chicken · $6.38。",
        "featured": 0,
    },
    {
        "sku": "WOW-CLR",
        "id": 1000019,
        "cat": "supermarket",
        "name": "Woolies Clearance 购物袋 $2",
        "slug": "woolies-clearance-tote",
        "desc": "Woolworths Clearance 价签冰箱贴。印刷：Classic Tote Bag · $2 SAVE $0。",
        "featured": 0,
    },
    {
        "sku": "WOW-SPE",
        "id": 1000183,
        "cat": "supermarket",
        "name": "Woolies Special 姜汁啤酒 $5.20",
        "slug": "woolies-special-ginger-beer",
        "desc": "Woolworths Special 价签冰箱贴。印刷：Brewed Drink Ginger Beer 4x375mL · $5.20。",
        "featured": 0,
    },
    {
        "sku": "WOW-HALF",
        "id": 1000215,
        "cat": "supermarket",
        "name": "Woolies 1/2 Price Tim Tam $2.50",
        "slug": "woolies-half-price-tim-tam",
        "desc": "Woolworths 1/2 Price 价签冰箱贴。印刷：Arnotts Tim Tam Original 200g · $2.50。",
        "featured": 1,
    },
    {
        "sku": "BIRD-STOP",
        "id": 1000070,
        "cat": "bird",
        "name": "NO STOPPING 葵花凤头鹦鹉",
        "slug": "bird-no-stopping-cockatoo",
        "desc": "红底路牌冰箱贴。印刷：NO STOPPING · 葵花凤头鹦鹉偷可颂。",
        "featured": 1,
    },
    {
        "sku": "BIRD-FEED",
        "id": 1000014,
        "cat": "bird",
        "name": "DO NOT FEED 银鸥",
        "slug": "bird-do-not-feed-gull",
        "desc": "黄三角警告冰箱贴。印刷：DO NOT FEED · 银鸥抢薯条。",
        "featured": 0,
    },
    {
        "sku": "BIRD-PELI",
        "id": 1000066,
        "cat": "bird",
        "name": "PELICAN PARKING 鹈鹕",
        "slug": "bird-pelican-parking",
        "desc": "蓝底停车牌冰箱贴。印刷：PALICAN PARKING 8:30–5:30 MON–SUN（印刷拼写如此）· FISH FREE。",
        "featured": 0,
    },
    {
        "sku": "BIRD-BIN",
        "id": 1000007,
        "cat": "bird",
        "name": "NO BIN CHICKEN 白鹮",
        "slug": "bird-no-bin-chicken",
        "desc": "白底禁令冰箱贴。印刷：NO BIN CHICKEN · 白鹮翻垃圾桶。",
        "featured": 0,
    },
    {
        "sku": "BUS-UNSW",
        "id": 1000002,
        "cat": "bus-sign",
        "name": "UNSW 公交站 2031164",
        "slug": "bus-unsw-2031164",
        "desc": "新南威尔士大学站牌冰箱贴。线路：370 Coogee / 390X Bondi Junction / 348 Prince of Wales Hospital / 392 Little Bay。",
        "featured": 1,
    },
    {
        "sku": "BUS-USYD-A",
        "id": 1000229,
        "cat": "bus-sign",
        "name": "USYD City Rd 站 205038 · 423/426",
        "slug": "bus-usyd-city-rd-423",
        "desc": "悉尼大学 City Rd 站牌（旧线路版）。印刷：423 / 426 / 428 / 430 · bus stop 205038。",
        "featured": 0,
    },
    {
        "sku": "BUS-USYD-B",
        "id": 1000223,
        "cat": "bus-sign",
        "name": "USYD City Rd 站 205038 · 370/461X",
        "slug": "bus-usyd-city-rd-370",
        "desc": "悉尼大学 City Rd 站牌（370 版）。印刷：461X Burwood / 370 Coogee / 428 / 430 · bus stop 205038。",
        "featured": 0,
    },
    {
        "sku": "BUS-UTS",
        "id": 1000231,
        "cat": "bus-sign",
        "name": "UTS Broadway 站 2022117",
        "slug": "bus-uts-broadway",
        "desc": "UTS Broadway 站牌冰箱贴。线路：412 Martin Place / 440 Bondi Junction / 430 All Destinations。",
        "featured": 0,
    },
    {
        "sku": "BUS-UQ-A",
        "id": 1000104,
        "cat": "bus-sign",
        "name": "UQ Lakes Stop A",
        "slug": "bus-uq-lakes-stop-a",
        "desc": "昆士兰大学 Lakes Stop A 站牌。TransLink 蓝底：M2 / 169 / 179 / 29。",
        "featured": 0,
    },
    {
        "sku": "BUS-UQ-B",
        "id": 1000118,
        "cat": "bus-sign",
        "name": "UQ Lakes stop B 紫色版",
        "slug": "bus-uq-lakes-stop-b",
        "desc": "昆士兰大学 Lakes stop B 站牌。TransLink 紫底：66 / 139 / M2 / 169。",
        "featured": 0,
    },
    {
        "sku": "BUS-UQ-CH",
        "id": 1000137,
        "cat": "bus-sign",
        "name": "UQ Chancellor's Place",
        "slug": "bus-uq-chancellors-place",
        "desc": "昆士兰大学 Chancellor's Place 站牌。Zone D：412 / 411 / 428 / 414。",
        "featured": 0,
    },
    {
        "sku": "BUS-ANU",
        "id": 1000088,
        "cat": "bus-sign",
        "name": "ANU ACT STOP 4529",
        "slug": "bus-anu-stop-4529",
        "desc": "澳国立大学站牌冰箱贴。ACT Government STOP 4529 · R3 / R4 / R7 / R10 / R2 / R6。",
        "featured": 0,
    },
    {
        "sku": "BUS-MON",
        "id": 1000086,
        "cat": "bus-sign",
        "name": "Monash University 公交站",
        "slug": "bus-monash-university",
        "desc": "莫纳什大学站牌冰箱贴。线路：862 / 733 / 900 / 601。",
        "featured": 0,
    },
    {
        "sku": "BUS-MQ",
        "id": 1000131,
        "cat": "bus-sign",
        "name": "Macquarie Centre 公交 211316",
        "slug": "bus-macquarie-centre",
        "desc": "麦考瑞 Centre 公交站牌。线路：197 / 410 / 550 / 611 · bus stop 211316。",
        "featured": 0,
    },
    {
        "sku": "METRO-MQ",
        "id": 1000111,
        "cat": "bus-sign",
        "name": "Macquarie Uni 地铁站牌",
        "slug": "metro-macquarie-uni",
        "desc": "麦考瑞大学地铁站名牌冰箱贴。横条 Metro 线路图。",
        "featured": 0,
    },
    {
        "sku": "TRAM-61",
        "id": 1000004,
        "cat": "tram",
        "name": "墨大电车 Swanston St · 6/1",
        "slug": "tram-unimelb-6-1",
        "desc": "墨尔本大学 Swanston St 电车站牌。Route 6 Flinders Street / Route 1 State Library。",
        "featured": 1,
    },
    {
        "sku": "TRAM-7216",
        "id": 1000127,
        "cat": "tram",
        "name": "墨大电车 Swanston St · 72/16",
        "slug": "tram-unimelb-72-16",
        "desc": "墨尔本大学 Swanston St 电车站牌。Route 72 Flinders Street / Route 16 Town Hall。",
        "featured": 0,
    },
    {
        "sku": "TRN-T8-MAC",
        "id": 1000094,
        "cat": "train",
        "name": "T8 Macarthur via Airport X",
        "slug": "train-t8-macarthur",
        "desc": "悉尼火车下一班车冰箱贴。T8 Macarthur via Airport X。",
        "featured": 0,
    },
    {
        "sku": "TRN-T8-CC",
        "id": 1000087,
        "cat": "train",
        "name": "T8 City Circle via Airport X",
        "slug": "train-t8-city-circle",
        "desc": "悉尼火车下一班车冰箱贴。T8 City Circle via Airport X。",
        "featured": 0,
    },
    {
        "sku": "TRN-T9-GOR",
        "id": 1000008,
        "cat": "train",
        "name": "T9 Gordon via Central",
        "slug": "train-t9-gordon",
        "desc": "悉尼火车下一班车冰箱贴。T9 Gordon via Central。",
        "featured": 1,
    },
    {
        "sku": "TRN-T9-HOR",
        "id": 1000090,
        "cat": "train",
        "name": "T9 Hornsby via Central",
        "slug": "train-t9-hornsby",
        "desc": "悉尼火车下一班车冰箱贴。T9 Hornsby via Central。",
        "featured": 0,
    },
    {
        "sku": "TRN-T9-WYN",
        "id": 1000158,
        "cat": "train",
        "name": "T9 Wynyard 晚点梗",
        "slug": "train-t9-wynyard-late",
        "desc": "悉尼火车下一班车冰箱贴（晚点梗）。T9 Wynyard via Central · Departs 3hr 30min。",
        "featured": 0,
    },
    {
        "sku": "TRN-T7",
        "id": 1000124,
        "cat": "train",
        "name": "T7 Olympic Park 演唱会专列",
        "slug": "train-t7-olympic-park",
        "desc": "悉尼火车下一班车冰箱贴。T7 Olympic Park Tay Tay Express · Accor / Giant / Qudos / Engie。",
        "featured": 0,
    },
    {
        "sku": "TRN-T2-CC",
        "id": 1000155,
        "cat": "train",
        "name": "T2 City Circle via Museum",
        "slug": "train-t2-city-circle",
        "desc": "悉尼火车下一班车冰箱贴。T2 City Circle via Museum。",
        "featured": 0,
    },
    {
        "sku": "SGN-ALC",
        "id": 1000079,
        "cat": "road-sign",
        "name": "ALCOHOL FREE AREA",
        "slug": "sign-alcohol-free-area",
        "desc": "白底禁酒路牌冰箱贴。印刷：ALCOHOL FREE AREA。",
        "featured": 0,
    },
    {
        "sku": "SGN-SPD",
        "id": 1000258,
        "cat": "road-sign",
        "name": "YOUR SPEED WILL BE CHECKED",
        "slug": "sign-speed-will-be-checked",
        "desc": "测速相机路牌冰箱贴。印刷：YOUR SPEED WILL BE CHECKED。",
        "featured": 0,
    },
    {
        "sku": "SGN-WW",
        "id": 1000256,
        "cat": "road-sign",
        "name": "WRONG WAY GO BACK",
        "slug": "sign-wrong-way-go-back",
        "desc": "红底逆行路牌冰箱贴。印刷：WRONG WAY / GO BACK。",
        "featured": 0,
    },
    {
        "sku": "SGN-ROO",
        "id": 1000260,
        "cat": "road-sign",
        "name": "袋鼠警告菱形牌",
        "slug": "sign-kangaroo-warning",
        "desc": "黄底袋鼠警告冰箱贴。",
        "featured": 0,
    },
    {
        "sku": "BTN-BLU",
        "id": 1000157,
        "cat": "road-sign",
        "name": "过马路按钮 · 蓝底白箭头",
        "slug": "btn-crossing-blue",
        "desc": "澳洲过马路按钮冰箱贴。蓝底白箭头、黑壳圆钮。",
        "featured": 0,
    },
    {
        "sku": "BTN-WHT",
        "id": 1000162,
        "cat": "road-sign",
        "name": "过马路按钮 · 白底箭头",
        "slug": "btn-crossing-white",
        "desc": "澳洲过马路按钮冰箱贴。白底箭头配色。",
        "featured": 0,
    },
    {
        "sku": "ACH-COOK",
        "id": 1000057,
        "cat": "achievement",
        "name": "留子成就 · 新手厨师",
        "slug": "ach-rookie-chef",
        "desc": "留子成就冰箱贴。新手厨师：触发烟雾警报器 · 86.4% 的玩家拥有此成就。",
        "featured": 0,
        "cover": "/products/6a71d6a4000000002500568f/06.jpg",
    },
    {
        "sku": "ACH-FISH",
        "id": 1000059,
        "cat": "achievement",
        "name": "留子成就 · 浑水摸鱼",
        "slug": "ach-muddy-waters",
        "desc": "留子成就冰箱贴。浑水摸鱼：计重土豆时加入生姜 · 25.6% 的玩家拥有此成就。",
        "featured": 0,
        "cover": "/products/6a71d6a4000000002500568f/03.jpg",
    },
    {
        "sku": "ACH-DEAL",
        "id": 1000054,
        "cat": "achievement",
        "name": "留子成就 · 精打细算",
        "slug": "ach-bargain-hunter",
        "desc": "留子成就冰箱贴。精打细算：连续一周购买超市打折商品 · 49.8% 的玩家拥有此成就。",
        "featured": 0,
        "cover": "/products/6a71d6a4000000002500568f/02.jpg",
    },
    {
        "sku": "ACH-FARE",
        "id": 1000024,
        "cat": "achievement",
        "name": "留子成就 · 慈禧已付",
        "slug": "ach-fare-evasion",
        "desc": "留子成就冰箱贴。慈禧已付：乘坐公共交通时逃票 · 70.6% 的玩家拥有此成就。",
        "featured": 0,
        "cover": "/products/6a71d6a4000000002500568f/04.jpg",
    },
    {
        "sku": "ACH-ROOM",
        "id": 1000266,
        "cat": "achievement",
        "name": "留子成就 · 天崩开局",
        "slug": "ach-empty-room",
        "desc": "留子成就冰箱贴。天崩开局：落地入住无家具、无水电网络房间 · 10.5% 的玩家拥有此成就。",
        "featured": 0,
        "clone_from": 1000024,
        "cover": "/products/6a71d6a4000000002500568f/05.jpg",
    },
    {
        "sku": "SLG-NW",
        "id": 1000077,
        "cat": "slogan",
        "name": "NO WORRIES",
        "slug": "slogan-no-worries",
        "desc": "彩色字母拼贴冰箱贴。印刷：NO WORRIES。",
        "featured": 1,
    },
]


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def build_sql() -> str:
    keep_ids = [s["id"] for s in SKUS]
    keep_list = ",".join(str(i) for i in keep_ids)
    lines = [
        "-- Visual SKU merge. Generated by apply-sku-merge.py",
        "USE stickybeak_product;",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "",
        "-- 1) Unlist everything currently on the shop floor",
        "UPDATE t_product SET status = 1, featured = 0 WHERE is_deleted = 0;",
        "",
        "-- 2) Clone 天崩开局 from the achievement set (no dedicated listing)",
        """INSERT INTO t_product (id, name, slug, description, category_id, price, stock, sales, source_note_id, metadata, featured, status, is_deleted)
SELECT 1000266, 'placeholder', 'ach-empty-room-tmp', NULL, category_id, price, stock, 0, source_note_id, NULL, 0, 1, 0
FROM t_product WHERE id = 1000024
ON DUPLICATE KEY UPDATE update_time = VALUES(update_time);""",
        """INSERT INTO t_product_image (product_id, url, media_type, sort_order, is_cover, is_deleted)
SELECT 1000266, url, media_type, sort_order, 0, 0
FROM t_product_image
WHERE product_id = 1000024 AND is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM t_product_image x WHERE x.product_id = 1000266 AND x.url = t_product_image.url AND x.is_deleted = 0
  );""",
        """INSERT IGNORE INTO t_product_tag_rel (product_id, tag_id)
SELECT 1000266, tag_id FROM t_product_tag_rel WHERE product_id = 1000024;""",
        "",
        "-- 3) Promote canonical SKUs",
    ]
    for s in SKUS:
        meta = esc(json.dumps({"sku": s["sku"], "print": s["name"]}, ensure_ascii=False))
        lines.append(
            f"""UPDATE t_product p
JOIN t_category c ON c.slug = '{s["cat"]}'
SET p.name = '{esc(s["name"])}',
    p.slug = '{esc(s["slug"])}',
    p.description = '{esc(s["desc"])}',
    p.category_id = c.id,
    p.metadata = '{meta}',
    p.featured = {s["featured"]},
    p.status = 0
WHERE p.id = {s["id"]};"""
        )
        cover = s.get("cover")
        if cover:
            lines.append(f"UPDATE t_product_image SET is_cover = 0 WHERE product_id = {s['id']} AND is_deleted = 0;")
            lines.append(
                f"""INSERT INTO t_product_image (product_id, url, media_type, sort_order, is_cover, is_deleted)
SELECT {s["id"]}, '{esc(cover)}', 'image', 0, 1, 0 FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM t_product_image WHERE product_id = {s["id"]} AND url = '{esc(cover)}' AND is_deleted = 0
);"""
            )
            lines.append(
                f"UPDATE t_product_image SET is_cover = 1, sort_order = 0 WHERE product_id = {s['id']} AND url = '{esc(cover)}' AND is_deleted = 0;"
            )
        lines.append("")

    lines += [
        f"-- Keep listed count check: {len(SKUS)} ids",
        f"-- Listed ids: {keep_list}",
        "COMMIT;",
        "",
        "SELECT COUNT(*) AS listed FROM t_product WHERE status = 0 AND is_deleted = 0;",
        "SELECT id, name, slug, status, featured FROM t_product WHERE status = 0 AND is_deleted = 0 ORDER BY id;",
    ]
    return "\n".join(lines)


def main() -> None:
    sql = build_sql()
    out = HERE / "apply-sku-merge.sql"
    out.write_text(sql, encoding="utf-8")
    (HERE / "canonical-skus.json").write_text(
        json.dumps(SKUS, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("wrote", out, "skus", len(SKUS))


if __name__ == "__main__":
    main()
