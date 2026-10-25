"""Assign 265 listings to visual SKUs. Does not touch the database."""
from __future__ import annotations

import json
import re
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
data = json.loads((HERE / "listings.json").read_text(encoding="utf-8"))
products = data["products"]
by_id = {p["productId"]: p for p in products}

# Visual SKUs confirmed from covers. Combo/collection posts are tagged separately.
SKUS = [
    # supermarket
    {"id": "COL-SPE", "family": "超市价签", "name": "Coles SPECIAL 无糖可乐 2 for $5.20", "print": "Coca-Cola Zero Sugar · Any 2 for $5.20", "conf": "confirmed"},
    {"id": "COL-HALF", "family": "超市价签", "name": "Coles 1/2 PRICE 抹茶冰淇淋 $6", "print": "Connoisseur Matcha Green Tea&White Choc · WAS $12", "conf": "confirmed"},
    {"id": "COL-DOWN", "family": "超市价签", "name": "Coles DOWN DOWN 橄榄油 $16", "print": "Classic Mild Olive Oil 1 litre", "conf": "confirmed"},
    {"id": "COL-CLR", "family": "超市价签", "name": "Coles REDUCED TO CLEAR 脱脂奶 $3.10", "print": "Reduced Fat Milk 2 litre", "conf": "confirmed"},
    {"id": "WOW-QS", "family": "超市价签", "name": "Woolies Quick Sale 烤鸡 $6.38", "print": "Country Style Roast Chicken", "conf": "confirmed"},
    {"id": "WOW-CLR", "family": "超市价签", "name": "Woolies Clearance 购物袋 $2", "print": "Classic Tote Bag · SAVE $0", "conf": "confirmed"},
    {"id": "WOW-SPE", "family": "超市价签", "name": "Woolies Special 姜汁啤酒 $5.20", "print": "Brewed Drink Ginger Beer 4x375mL", "conf": "confirmed"},
    {"id": "WOW-HALF", "family": "超市价签", "name": "Woolies 1/2 Price Tim Tam $2.50", "print": "Arnotts Tim Tam Original 200g", "conf": "confirmed"},
    # birds
    {"id": "BIRD-STOP", "family": "小鸟路牌", "name": "NO STOPPING 葵花凤头鹦鹉", "print": "红底 · 鹦鹉偷可颂", "conf": "confirmed"},
    {"id": "BIRD-FEED", "family": "小鸟路牌", "name": "DO NOT FEED 银鸥", "print": "黄三角 · 海鸥抢薯条", "conf": "confirmed"},
    {"id": "BIRD-PELI", "family": "小鸟路牌", "name": "PELICAN PARKING 鹈鹕", "print": "蓝底 PALICAN PARKING 8:30–5:30（印刷拼写如此）", "conf": "confirmed"},
    {"id": "BIRD-BIN", "family": "小鸟路牌", "name": "NO BIN CHICKEN 白鹮", "print": "白底禁翻垃圾桶", "conf": "confirmed"},
    # campus
    {"id": "BUS-UNSW", "family": "校园公交", "name": "UNSW 站 2031164", "print": "370 Coogee / 390X Bondi Jn / 348 PoW / 392 Little Bay", "conf": "confirmed"},
    {"id": "BUS-USYD-A", "family": "校园公交", "name": "USYD City Rd 站 205038 · 旧线路", "print": "423 / 426 / 428 / 430", "conf": "confirmed"},
    {"id": "BUS-USYD-B", "family": "校园公交", "name": "USYD City Rd 站 205038 · 370版", "print": "461X Burwood / 370 Coogee / 428 / 430", "conf": "confirmed"},
    {"id": "BUS-UTS", "family": "校园公交", "name": "UTS Broadway 站 2022117", "print": "412 Martin Place / 440 Bondi Jn / 430", "conf": "confirmed"},
    {"id": "BUS-UQ-A", "family": "校园公交", "name": "UQ Lakes Stop A", "print": "TransLink 蓝 · M2/169/179/29", "conf": "confirmed"},
    {"id": "BUS-UQ-B", "family": "校园公交", "name": "UQ Lakes stop B", "print": "TransLink 紫 · 66/139/M2/169", "conf": "confirmed"},
    {"id": "BUS-UQ-CH", "family": "校园公交", "name": "UQ Chancellor's Place", "print": "TransLink 深蓝 Zone D · 412/411/428/414", "conf": "confirmed"},
    {"id": "BUS-ANU", "family": "校园公交", "name": "ANU ACT STOP 4529", "print": "R3/R4/R7/R10/R2/R6", "conf": "confirmed"},
    {"id": "BUS-MON", "family": "校园公交", "name": "Monash University", "print": "862/733/900/601 · PTV 橙 A", "conf": "confirmed"},
    {"id": "BUS-MQ", "family": "校园公交", "name": "Macquarie Centre 公交 211316", "print": "197/410/550/611", "conf": "confirmed"},
    {"id": "METRO-MQ", "family": "校园公交", "name": "Macquarie Uni 地铁站牌", "print": "横条 Metro 线 · Sydneham→Macquarie Park", "conf": "confirmed"},
    # tram
    {"id": "TRAM-61", "family": "墨尔本电车", "name": "Melbourne Uni Swanston St · 6/1", "print": "6 Flinders Street / 1 State Library", "conf": "confirmed"},
    {"id": "TRAM-7216", "family": "墨尔本电车", "name": "Melbourne Uni Swanston St · 72/16", "print": "72 Flinders Street / 16 Town Hall", "conf": "confirmed"},
    # train — dedicated product shots
    {"id": "TRN-T8-MAC", "family": "悉尼火车", "name": "T8 Macarthur via Airport X", "print": "机场线往 Macarthur", "conf": "confirmed"},
    {"id": "TRN-T8-CC", "family": "悉尼火车", "name": "T8 City Circle via Airport X", "print": "机场线往 City Circle", "conf": "confirmed"},
    {"id": "TRN-T9-GOR", "family": "悉尼火车", "name": "T9 Gordon via Central", "print": "Epping…Central · Platform 2", "conf": "confirmed"},
    {"id": "TRN-T9-HOR", "family": "悉尼火车", "name": "T9 Hornsby via Central", "print": "与 Gordon 成对出现的另一向", "conf": "confirmed"},
    {"id": "TRN-T9-WYN", "family": "悉尼火车", "name": "T9 Wynyard 晚点梗", "print": "Depart 3hr 30min / Hornsby 5hr", "conf": "confirmed"},
    {"id": "TRN-T7", "family": "悉尼火车", "name": "T7 Olympic Park 演唱会专列", "print": "Tay Tay Express · Accor/Giant/Qudos/Engie", "conf": "confirmed"},
    {"id": "TRN-T2-CC", "family": "悉尼火车", "name": "T2 City Circle via Museum", "print": "四件套封面之一", "conf": "confirmed"},
    {"id": "TRN-T2-PAR", "family": "悉尼火车", "name": "T2 Parramatta", "print": "仅十件合集冰箱照", "conf": "collection-only"},
    {"id": "TRN-T4-BON", "family": "悉尼火车", "name": "T4 Bondi Junction", "print": "仅十件合集冰箱照", "conf": "collection-only"},
    {"id": "TRN-T4-WAT", "family": "悉尼火车", "name": "T4 Waterfall", "print": "仅十件合集冰箱照", "conf": "collection-only"},
    # signs
    {"id": "SGN-ALC", "family": "交通指示牌", "name": "ALCOHOL FREE AREA", "print": "白底禁酒标", "conf": "confirmed"},
    {"id": "SGN-SPD", "family": "交通指示牌", "name": "YOUR SPEED WILL BE CHECKED", "print": "白底蓝相机方牌", "conf": "confirmed"},
    {"id": "SGN-WW", "family": "交通指示牌", "name": "WRONG WAY GO BACK", "print": "红底白字", "conf": "confirmed"},
    {"id": "SGN-ROO", "family": "交通指示牌", "name": "袋鼠警告菱形牌", "print": "黄底黑袋鼠", "conf": "confirmed"},
    {"id": "BTN-BLU", "family": "交通指示牌", "name": "过马路按钮 · 蓝底白箭头", "print": "3D 黑壳圆钮", "conf": "confirmed"},
    {"id": "BTN-WHT", "family": "交通指示牌", "name": "过马路按钮 · 白底箭头", "print": "3D 黑壳，封面成对出现", "conf": "confirmed"},
    {"id": "SGN-HELL", "family": "交通指示牌", "name": "MAY GO TO HELL 小红牌", "print": "接触片里多次出现，无单独上新帖", "conf": "needs-confirm"},
    {"id": "SGN-NSW", "family": "交通指示牌", "name": "SPEED CAMERAS USED IN NSW", "print": "封面是实景路牌，印刷磁贴待确认", "conf": "needs-confirm"},
    # achievements
    {"id": "ACH-COOK", "family": "留子成就", "name": "新手厨师", "print": "触发烟雾警报器 · 86.4%", "conf": "confirmed"},
    {"id": "ACH-FISH", "family": "留子成就", "name": "浑水摸鱼", "print": "计重土豆时加入生姜 · 25.6%", "conf": "confirmed"},
    {"id": "ACH-DEAL", "family": "留子成就", "name": "精打细算", "print": "打折商品 · 49.8%", "conf": "confirmed"},
    {"id": "ACH-FARE", "family": "留子成就", "name": "慈禧已付", "print": "逃票梗 · 70.6%", "conf": "confirmed"},
    {"id": "ACH-ROOM", "family": "留子成就", "name": "天崩开局", "print": "无家具房间 · 10.5%", "conf": "confirmed"},
    # slogan
    {"id": "SLG-NW", "family": "生活标语", "name": "NO WORRIES", "print": "彩色字母拼贴", "conf": "confirmed"},
]

SKU_IDS = {s["id"] for s in SKUS}

# Non-SKU buckets
OTHER = {
    "LOOKBOOK": "合集/种草帖（多 SKU 同框，不单独成货）",
    "TUTORIAL": "教程帖，不是商品",
    "PHONECASE": "手机壳道具（CASETiFY G'DAY SYDNEY + 磁贴吸附）",
    "MISFILE": "错分品类，实物仍是已有 SKU",
}


def n(s: str) -> str:
    return re.sub(r"\s+", "", s).lower()


def classify(p: dict) -> tuple[list[str], str | None]:
    """Return (sku ids featured), other_bucket."""
    name = p["name"]
    cat = p["category"]
    pid = p["productId"]
    t = n(name)

    # tutorials / phone / misfile first
    if pid == 1000143 or "diy" in t or "教程" in name:
        return [], "TUTORIAL"
    if pid == 1000199 or "手机壳" in name:
        return [], "PHONECASE"
    if pid == 1000007:
        return ["BIRD-BIN"], "MISFILE"

    skus: list[str] = []

    # birds
    if cat == "小鸟冰箱贴" or any(k in name for k in ("葵", "鸥", "鹈鹕", "垃圾桶", "NO STOPPING", "小鸟", "蒜苗鸡", "薯条刺客")):
        if any(k in name for k in ("四大天王", "抢食系列", "土著小鸟", "贪吃", "真土澳特色", "澳洲特产", "邻居")):
            skus += ["BIRD-STOP", "BIRD-FEED", "BIRD-PELI", "BIRD-BIN"]
        elif any(k in name for k in ("垃圾桶", "扒垃圾桶", "BIN")):
            skus.append("BIRD-BIN")
        elif any(k in name for k in ("薯条", "鸥", "FEED", "码头")):
            skus.append("BIRD-FEED")
        elif any(k in name for k in ("鹈鹕", "PELI", "PALICAN")):
            skus.append("BIRD-PELI")
        elif any(k in name for k in ("葵", "NO STOPPING", "不能停", "可颂", "蒜苗")):
            skus.append("BIRD-STOP")
        else:
            skus += ["BIRD-STOP", "BIRD-FEED", "BIRD-PELI", "BIRD-BIN"]

    # supermarket
    if cat == "超市冰箱贴" or any(k in t for k in ("coles", "扣死", "woolies", "窝窝", "wws", "超市")):
        mentions_down = any(k in name for k in ("上新", "DOWN", "橄榄", "牛奶", "大合集", "新冰箱贴周边", "土澳超市冰箱贴上新"))
        if pid in (1000010, 1000017, 1000019):
            skus += ["COL-DOWN", "COL-CLR", "WOW-QS", "WOW-CLR"]
        elif pid == 1000015:
            skus += ["WOW-QS", "WOW-CLR", "WOW-SPE", "WOW-HALF"]
        elif pid == 1000005:
            skus += ["COL-SPE", "COL-HALF"]
        elif "1/2" in name or "半价" in name or "冰淇淋" in name or "巧克力" in name or "timtam" in t:
            if "扣死" in name and "wool" not in t and "窝窝" not in name and "wws" not in t:
                skus.append("COL-HALF")
            elif any(k in name for k in ("窝窝", "wool", "wws", "巧克力")) and "扣死" not in name and "coles" not in t:
                skus.append("WOW-HALF")
            else:
                skus += ["COL-HALF", "WOW-HALF"]
        elif "special" in t or "可乐" in name:
            if any(k in name for k in ("窝窝", "wool", "姜")) and "扣死" not in name:
                skus.append("WOW-SPE")
            else:
                skus.append("COL-SPE")
        elif any(k in name for k in ("窝窝", "wool", "wws")) and "扣死" not in name and "coles" not in t:
            skus += ["WOW-SPE", "WOW-HALF"]
        elif "扣死" in name or "coles" in t:
            skus += ["COL-SPE", "COL-HALF"]
        else:
            skus += ["COL-SPE", "COL-HALF", "WOW-SPE", "WOW-HALF"]
        if mentions_down and pid not in (1000010, 1000017, 1000019):
            pass

    # tram
    if cat == "电车路牌" or "电车" in name or "墨大" in name or "墨尔本大学" in name:
        skus += ["TRAM-61", "TRAM-7216"]

    # train
    if cat == "火车地铁路牌" or any(k in name for k in ("火车", "T9", "T8", "T7", "T2", "T4", "Wynyard", "Hornsby")):
        if "T7" in name or "演唱会" in name or "Blink" in name or "泰勒" in name:
            skus.append("TRN-T7")
        if "机场" in name or "T8" in name:
            skus += ["TRN-T8-MAC", "TRN-T8-CC"]
        if "Hornsby" in name or "Gordon" in name or "双向T9" in name or "T9火车" in name:
            skus += ["TRN-T9-GOR", "TRN-T9-HOR"]
        if "晚点" in name or "挤死人" in name:
            skus.append("TRN-T9-WYN")
        if "T9_T8_T2_T7" in name or "T9_T7" in name:
            skus += ["TRN-T9-GOR", "TRN-T7", "TRN-T2-CC", "TRN-T8-MAC"]
        if "大合集" in name or "定制" in name:
            skus += ["TRN-T8-MAC", "TRN-T8-CC", "TRN-T9-GOR", "TRN-T9-HOR", "TRN-T2-PAR", "TRN-T4-BON", "TRN-T4-WAT"]
        if not skus:
            skus += ["TRN-T9-GOR", "TRN-T9-HOR"]

    # campus
    if cat == "校园公交路牌" or any(k in name for k in ("UNSW", "USYD", "UTS", "UQ", "ANU", "莫那", "莫纳", "麦考瑞", "悉大", "新南", "昆士兰", "澳国立")):
        if any(k in name for k in ("八大", "大合集", "超全", "那么多", "集齐", "校园路牌冰箱贴系列", "独属于留子")):
            return list(dict.fromkeys(skus + [
                "BUS-UNSW", "BUS-USYD-A", "BUS-USYD-B", "BUS-UTS", "BUS-UQ-A", "BUS-UQ-B",
                "BUS-UQ-CH", "BUS-ANU", "BUS-MON", "BUS-MQ", "METRO-MQ", "TRAM-61", "TRAM-7216",
            ])), "LOOKBOOK"
        if "UNSW" in name or "新南" in name:
            skus.append("BUS-UNSW")
        if "UTS" in name:
            skus.append("BUS-UTS")
        if "UQ" in name or "昆士兰" in name:
            if "Chancellor" in name:
                skus.append("BUS-UQ-CH")
            elif "紫" in name:
                skus.append("BUS-UQ-B")
            else:
                skus += ["BUS-UQ-A", "BUS-UQ-B", "BUS-UQ-CH"]
        if "ANU" in name or "澳国立" in name or "堪培拉" in name:
            skus.append("BUS-ANU")
        if "莫那" in name or "莫纳" in name or "Monash" in name:
            skus.append("BUS-MON")
        if "麦考瑞" in name or "Macquarie" in name:
            if "地铁" in name and "公交" not in name:
                skus.append("METRO-MQ")
            elif "地铁" in name:
                skus += ["BUS-MQ", "METRO-MQ"]
            else:
                skus.append("BUS-MQ")
        if "USYD" in name or "悉大" in name or "悉尼大学" in name:
            if "370" in name or "461X" in name:
                skus.append("BUS-USYD-B")
            else:
                skus.append("BUS-USYD-A")

    # signs
    if cat == "交通指示牌" or any(k in name for k in ("Alcohol", "酒鬼", "喝酒", "Wrong", "袋鼠", "超速", "按钮", "Speed", "车速")):
        if "按钮" in name:
            skus += ["BTN-BLU", "BTN-WHT"]
        if any(k in name for k in ("Alcohol", "酒鬼", "喝酒", "酒水", "酒局", "禁酒")):
            skus.append("SGN-ALC")
        if any(k in name for k in ("Wrong", "掉头", "此路不通")):
            skus.append("SGN-WW")
        if "袋鼠" in name:
            skus.append("SGN-ROO")
        if any(k in name for k in ("Speed", "超速", "车速", "逮到")):
            skus.append("SGN-SPD")
            if pid == 1000264:
                skus.append("SGN-NSW")

    # achievements
    if cat == "留子成就" or "成就" in name or "烟雾" in name:
        skus += ["ACH-COOK", "ACH-FISH", "ACH-DEAL", "ACH-FARE", "ACH-ROOM"]
        if "烟雾" in name:
            skus = ["ACH-COOK"]

    # slogan
    if cat == "生活标语" or "noworries" in t or "不要紧" in name:
        skus.append("SLG-NW")

    # collections
    if cat == "商品合集" or any(k in name for k in ("大合集", "大全", "分享", "推荐", "元素满满", "不能错过", "回🇦🇺", "四个")):
        if not skus:
            return [], "LOOKBOOK"
        return list(dict.fromkeys(skus)), "LOOKBOOK"

    skus = [s for s in dict.fromkeys(skus) if s in SKU_IDS]
    if not skus:
        if cat == "商品合集":
            return [], "LOOKBOOK"
        return [], "LOOKBOOK"
    # single-sku post vs combo
    other = None
    if len(skus) >= 4:
        other = "LOOKBOOK"
    return skus, other


rows = []
sku_posts = defaultdict(list)
buckets = defaultdict(list)

for p in products:
    skus, other = classify(p)
    rec = {
        "productId": p["productId"],
        "noteId": p["noteId"],
        "name": p["name"],
        "category": p["category"],
        "skus": skus,
        "bucket": other or ("SINGLE" if len(skus) == 1 else "COMBO"),
    }
    rows.append(rec)
    for s in skus:
        sku_posts[s].append(p["productId"])
    buckets[rec["bucket"]].append(p["productId"])

for s in SKUS:
    s["listingCount"] = len(sku_posts[s["id"]])
    s["exampleIds"] = sku_posts[s["id"]][:8]

unassigned = [r for r in rows if not r["skus"] and r["bucket"] not in ("TUTORIAL", "PHONECASE")]
report = {
    "listings": len(products),
    "confirmedSkus": sum(1 for s in SKUS if s["conf"] == "confirmed"),
    "collectionOnlySkus": sum(1 for s in SKUS if s["conf"] == "collection-only"),
    "needsConfirmSkus": sum(1 for s in SKUS if s["conf"] == "needs-confirm"),
    "buckets": {k: len(v) for k, v in buckets.items()},
    "skus": SKUS,
    "assignments": rows,
}
(HERE / "sku-proposal.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print("confirmed", report["confirmedSkus"], "collection-only", report["collectionOnlySkus"], "needs", report["needsConfirmSkus"])
print("buckets", report["buckets"])
print("unassigned", len(unassigned))
for r in unassigned[:20]:
    print(" ", r["productId"], r["category"], r["name"][:40])
