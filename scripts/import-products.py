#!/usr/bin/env python3
"""
Sprint 2 / Issue 2.2 — 真实商品数据导入脚本

从 classified/商品/*/info.json 读取商品数据 → 生成 SQL INSERT 语句。
图片从本地文件复制到 stickybeak-frontend/public/products/ 目录。

用法：
  python scripts/import-products.py
  # 然后执行生成的 SQL：
  docker cp scripts/generated-product-data.sql sb-mysql:/tmp/
  docker exec sb-mysql mysql -uroot -proot stickybeak_product -e "source /tmp/generated-product-data.sql"
"""

import json
import os
import re
import shutil
import random
import hashlib
from pathlib import Path

# ============================================================================
# 配置
# ============================================================================
DATA_SOURCE = Path(r"C:\Users\lzz28\gdcup-fridge-magnets\classified\商品")
PROJECT_ROOT = Path(__file__).resolve().parent.parent
FRONTEND_PUBLIC = PROJECT_ROOT / "stickybeak-frontend" / "public" / "products"
OUTPUT_SQL = PROJECT_ROOT / "scripts" / "generated-product-data.sql"

# 分类映射：关键词 → category slug
CATEGORY_KEYWORDS = {
    "bus-sign": [
        "公交", "路牌", "站牌", "UNSW", "USYD", "UQ", "UTS", "ANU", "麦考瑞",
        "莫那什", "新南", "悉大", "悉尼大学", "墨大", "墨尔本大学", "大学",
        "bus", "370", "392", "891", "train station"
    ],
    "supermarket": [
        "超市", "扣死", "窝窝屎", "Coles", "Woolworths", "special", "半价",
        "打折", "IGA", "Aldi", "costco"
    ],
    "train": [
        "火车", "电车", "地铁", "T9", "T4", "T2", "T8", "tram", "metro",
        "light rail", "轻轨", "城铁", "opal"
    ],
    "bird": [
        "小鸟", "大葵", "鹦鹉", "蒜苗鸡", "cockatoo", "galah", "kookaburra",
        "鸟", "ibis", "magpie", "lorikeet"
    ],
    "booze": [
        "酒", "啤酒", "wine", "beer", "干杯", "VB", "Carlton",
        "酒鬼", "酒瓶", "红酒", "白葡萄"
    ],
    "phonecase": [
        "手机壳", "phone case", "壳", "保护套", "挂件", "钥匙扣", "周边"
    ],
}

# 默认分类（无法匹配时）
DEFAULT_CATEGORY = "bus-sign"


def slugify(text: str) -> str:
    """生成 URL-safe slug"""
    # 保留字母数字和中文
    text = text.lower().strip()
    # 移除 emoji
    text = re.sub(r'[^\w\s\u4e00-\u9fff-]', '', text)
    text = re.sub(r'[\s_]+', '-', text)
    text = text.strip('-')
    if not text:
        text = 'product'
    return text[:150]


def guess_category(title: str, desc: str, tags: list) -> str:
    """根据标题/描述/标签关键词猜测分类"""
    combined = f"{title} {desc} {' '.join(tags)}".lower()
    scores = {}
    for cat_slug, keywords in CATEGORY_KEYWORDS.items():
        score = sum(1 for kw in keywords if kw.lower() in combined)
        if score > 0:
            scores[cat_slug] = score
    if scores:
        return max(scores, key=scores.get)
    return DEFAULT_CATEGORY


def generate_price() -> str:
    """随机生成价格 $9.90 ~ $39.90"""
    base = random.choice([990, 1290, 1490, 1590, 1890, 1990, 2490, 2590, 2990, 3290, 3490, 3990])
    return f"{base / 100:.2f}"


def escape_sql(s: str) -> str:
    """转义 SQL 字符串"""
    if s is None:
        return ''
    return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "").replace("\t", "\\t")


def main():
    if not DATA_SOURCE.exists():
        print(f"ERROR: 数据源目录不存在: {DATA_SOURCE}")
        return

    # 创建图片输出目录
    FRONTEND_PUBLIC.mkdir(parents=True, exist_ok=True)

    # 收集所有商品数据
    products = []
    all_tags = set()
    dirs = sorted(DATA_SOURCE.iterdir())

    for d in dirs:
        if not d.is_dir():
            continue
        info_path = d / "info.json"
        if not info_path.exists():
            continue

        try:
            with open(info_path, "r", encoding="utf-8") as f:
                info = json.load(f)
        except (json.JSONDecodeError, UnicodeDecodeError) as e:
            print(f"  SKIP {d.name}: {e}")
            continue

        title = info.get("title", "").strip()
        if not title:
            continue

        note_id = info.get("id", "")
        desc = info.get("desc", "")
        tags = info.get("tags", [])
        index = info.get("index", 0)

        # 读正文文件（如果有）
        fulltext_path = d / "正文.txt"
        if fulltext_path.exists() and info.get("hasFullText"):
            try:
                desc = fulltext_path.read_text(encoding="utf-8").strip()
            except Exception:
                pass

        slug = slugify(title)
        # 确保 slug 唯一
        existing_slugs = {p["slug"] for p in products}
        if slug in existing_slugs:
            slug = f"{slug}-{note_id[:8]}"

        category = guess_category(title, desc, tags)
        price = generate_price()
        stock = random.randint(20, 80)
        sales = random.randint(0, 200)
        featured = 1 if info.get("sticky", False) else 0

        # 处理图片：复制本地文件到 public/products/{note_id}/
        image_files = []
        product_img_dir = FRONTEND_PUBLIC / note_id
        product_img_dir.mkdir(parents=True, exist_ok=True)

        # 封面图
        cover_files = list(d.glob("封面.*"))
        # 编号图片
        numbered = sorted(d.glob("[0-9][0-9].*"))

        all_images = []
        if cover_files:
            src = cover_files[0]
            dst = product_img_dir / f"cover{src.suffix}"
            shutil.copy2(src, dst)
            rel_path = f"/products/{note_id}/cover{src.suffix}"
            all_images.append({"url": rel_path, "is_cover": 1})

        for img in numbered:
            dst = product_img_dir / img.name
            shutil.copy2(img, dst)
            rel_path = f"/products/{note_id}/{img.name}"
            all_images.append({"url": rel_path, "is_cover": 0})

        # 如果没有封面但有编号图，第一张设为封面
        if all_images and all(i["is_cover"] == 0 for i in all_images):
            all_images[0]["is_cover"] = 1

        all_tags.update(tags)

        products.append({
            "slug": slug,
            "name": title,
            "desc": desc,
            "category": category,
            "price": price,
            "stock": stock,
            "sales": sales,
            "source_note_id": note_id,
            "featured": featured,
            "tags": tags,
            "images": all_images,
            "index": index,
        })

    # ========================================================================
    # 生成 SQL
    # ========================================================================
    lines = []
    lines.append("-- ============================================================")
    lines.append("-- 自动生成：import-products.py")
    lines.append(f"-- 商品数：{len(products)}，标签数：{len(all_tags)}")
    lines.append("-- ============================================================")
    lines.append("")
    lines.append("USE stickybeak_product;")
    lines.append("SET NAMES utf8mb4;")
    lines.append("")

    # 1. 标签
    lines.append("-- Tags")
    for tag in sorted(all_tags):
        lines.append(f"INSERT IGNORE INTO t_tag (name) VALUES ('{escape_sql(tag)}');")
    lines.append("")

    # 2. 分类 slug → ID 映射（查询用变量）
    cat_slugs = sorted(set(p["category"] for p in products))

    # 3. 商品 + 图片 + 标签关联
    lines.append("-- Products")
    for i, p in enumerate(products):
        # 使用 index 作为雪花 ID 的一部分（简化，实际生产用 app 生成）
        product_id = 1000000 + i + 1

        lines.append(f"-- [{i+1}] {p['name'][:40]}")
        lines.append(
            f"INSERT INTO t_product (id, name, slug, description, category_id, price, stock, sales, source_note_id, featured, status) "
            f"SELECT {product_id}, '{escape_sql(p['name'])}', '{escape_sql(p['slug'])}', "
            f"'{escape_sql(p['desc'][:5000])}', c.id, {p['price']}, {p['stock']}, {p['sales']}, "
            f"'{escape_sql(p['source_note_id'])}', {p['featured']}, 0 "
            f"FROM t_category c WHERE c.slug = '{p['category']}' "
            f"ON DUPLICATE KEY UPDATE name = VALUES(name);"
        )

        # 图片
        for j, img in enumerate(p["images"]):
            lines.append(
                f"INSERT IGNORE INTO t_product_image (product_id, url, sort_order, is_cover) "
                f"VALUES ({product_id}, '{escape_sql(img['url'])}', {j}, {img['is_cover']});"
            )

        # 标签关联
        for tag in p["tags"]:
            lines.append(
                f"INSERT IGNORE INTO t_product_tag_rel (product_id, tag_id) "
                f"SELECT {product_id}, t.id FROM t_tag t WHERE t.name = '{escape_sql(tag)}';"
            )
        lines.append("")

    # 写入 SQL 文件
    with open(OUTPUT_SQL, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    # ========================================================================
    # 统计
    # ========================================================================
    cat_counts = {}
    total_images = 0
    for p in products:
        cat_counts[p["category"]] = cat_counts.get(p["category"], 0) + 1
        total_images += len(p["images"])

    print(f"\n[OK] Import complete!")
    print(f"   Products: {len(products)}")
    print(f"   Tags:     {len(all_tags)}")
    print(f"   Images:   {total_images}")
    print(f"   Categories:")
    for cat, count in sorted(cat_counts.items(), key=lambda x: -x[1]):
        print(f"     {cat}: {count}")
    print(f"\n   SQL file:  {OUTPUT_SQL}")
    print(f"   Image dir: {FRONTEND_PUBLIC}")
    print(f"\n   Next steps:")
    print(f"   docker cp scripts/generated-product-data.sql sb-mysql:/tmp/")
    print(f'   docker exec sb-mysql mysql -uroot -proot stickybeak_product -e "source /tmp/generated-product-data.sql"')


if __name__ == "__main__":
    random.seed(42)  # 可重复
    main()
