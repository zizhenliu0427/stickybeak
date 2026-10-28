"""
封面视觉归并：dHash 近重 + 按品类拼接触片，供人工确认 SKU。
不改数据库。
"""
from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parent.parent
SQL = REPO / "scripts" / "generated-product-data.sql"
IMG_ROOT = REPO / "stickybeak-frontend" / "public" / "products"
INDEX = Path("/data/classified/分类索引.json") if Path("/data/classified/分类索引.json").exists() else Path(r"C:\Users\lzz28\gdcup-fridge-magnets\classified\分类索引.json")
OUT_DIR = REPO / "scripts" / "sku-review"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def dhash(path: Path, size: int = 16) -> int:
    im = Image.open(path).convert("L").resize((size + 1, size), Image.Resampling.LANCZOS)
    pixels = list(im.getdata())
    bits = 0
    for y in range(size):
        row = y * (size + 1)
        for x in range(size):
            bits = (bits << 1) | (1 if pixels[row + x] > pixels[row + x + 1] else 0)
    return bits


def hamming(a: int, b: int) -> int:
    return (a ^ b).bit_count()


def cover_path(note_id: str) -> Path | None:
    d = IMG_ROOT / note_id
    if not d.is_dir():
        return None
    for name in ("01.jpg", "cover.webp", "cover.jpg"):
        p = d / name
        if p.exists():
            return p
    extras = sorted(d.glob("*.jpg")) + sorted(d.glob("*.webp"))
    return extras[0] if extras else None


def parse_sql() -> list[dict]:
    text = SQL.read_text(encoding="utf-8")
    rows = []
    blocks = re.split(r"\n(?=-- \[)", text)
    for b in blocks:
        hm = re.match(r"-- \[(\d+)\] (.+)", b)
        nm = re.search(r", '([0-9a-f]{24})', [01], 0 FROM t_category", b)
        pm = re.search(r"SELECT (\d+),", b)
        if not (hm and nm and pm):
            continue
        rows.append(
            {
                "productId": int(pm.group(1)),
                "index": int(hm.group(1)),
                "name": hm.group(2).strip(),
                "noteId": nm.group(1),
            }
        )
    return rows


def load_categories() -> dict[str, str]:
    data = json.loads(INDEX.read_text(encoding="utf-8"))
    out = {}
    for bucket in data:
        cat = bucket.get("category") or bucket.get("bucket")
        for n in bucket.get("notes") or []:
            out[n["id"]] = cat
    return out


def union_find(n: int):
    p = list(range(n))

    def find(i):
        while p[i] != i:
            p[i] = p[p[i]]
            i = p[i]
        return i

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            p[rb] = ra

    return find, union


def contact_sheet(items: list[dict], title: str, out: Path, cols: int = 8, cell: int = 180):
    if not items:
        return
    rows = (len(items) + cols - 1) // cols
    label_h = 36
    W, H = cols * cell, rows * (cell + label_h)
    canvas = Image.new("RGB", (W, H), (24, 24, 24))
    draw = ImageDraw.Draw(canvas)
    try:
        font = ImageFont.truetype("arial.ttf", 12)
    except OSError:
        font = ImageFont.load_default()
    for i, it in enumerate(items):
        r, c = divmod(i, cols)
        x, y = c * cell, r * (cell + label_h)
        try:
            im = Image.open(it["cover"]).convert("RGB")
            im.thumbnail((cell - 8, cell - 8))
            ox = x + (cell - im.width) // 2
            oy = y + (cell - 8 - im.height) // 2
            canvas.paste(im, (ox, oy))
        except Exception:
            draw.rectangle((x + 4, y + 4, x + cell - 4, y + cell - 8), outline=(80, 80, 80))
        cap = f"{it['productId']-1000000:03d} {it['name'][:18]}"
        draw.text((x + 4, y + cell - 4), cap, fill=(220, 220, 220), font=font)
    canvas.save(out, quality=85)
    print(f"sheet {title}: {out.name} ({len(items)} thumbs)")


def main():
    cats = load_categories()
    products = parse_sql()
    print(f"parsed {len(products)} products")
    hashed = []
    for p in products:
        p["category"] = cats.get(p["noteId"], "未分类")
        p["cover"] = str(cover_path(p["noteId"]) or "")
        p["hash"] = None
        if p["cover"]:
            try:
                p["hash"] = dhash(Path(p["cover"]))
                hashed.append(p)
            except Exception as e:
                print("hash fail", p["noteId"], e)

    n = len(hashed)
    find, union = union_find(n)
    THRESH = 12
    for i in range(n):
        for j in range(i + 1, n):
            if hamming(hashed[i]["hash"], hashed[j]["hash"]) <= THRESH:
                union(i, j)
    clusters = defaultdict(list)
    for i, p in enumerate(hashed):
        clusters[find(i)].append(p["productId"])

    by_cat: dict[str, list] = defaultdict(list)
    for p in products:
        by_cat[p["category"]].append(p)

    report = {
        "listings": len(products),
        "hashed": n,
        "nearDupPhotoClusters": sorted(
            [ids for ids in clusters.values() if len(ids) > 1],
            key=len,
            reverse=True,
        ),
        "byCategory": {k: len(v) for k, v in sorted(by_cat.items(), key=lambda x: -len(x[1]))},
        "products": [
            {
                "productId": p["productId"],
                "name": p["name"],
                "noteId": p["noteId"],
                "category": p["category"],
                "cover": p["cover"],
            }
            for p in products
        ],
    }
    (OUT_DIR / "listings.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("near-dup photo clusters:", len(report["nearDupPhotoClusters"]))
    for ids in report["nearDupPhotoClusters"][:20]:
        names = [next(p["name"] for p in products if p["productId"] == i)[:40] for i in ids]
        print(" ", ids, " | ", " || ".join(names))

    for cat, items in sorted(by_cat.items(), key=lambda x: -len(x[1])):
        safe = re.sub(r"[^\w\u4e00-\u9fff-]+", "_", cat)[:40]
        contact_sheet(items, cat, OUT_DIR / f"sheet-{safe}.jpg")


if __name__ == "__main__":
    sys.exit(main() or 0)
