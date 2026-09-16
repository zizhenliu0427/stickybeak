#!/usr/bin/env python3
"""
Generate PWA Icons for StickyBeak
Produces:
  - public/icons/icon-192.png
  - public/icons/icon-512.png
  - public/icons/icon-maskable-512.png
  - public/icons/apple-touch-icon.png
  - public/favicon.ico
  - public/favicon.svg
"""

import os
from PIL import Image, ImageDraw

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "..", "stickybeak-frontend", "public")
ICONS_DIR = os.path.join(OUTPUT_DIR, "icons")
os.makedirs(ICONS_DIR, exist_ok=True)

# Brand Colors
BG_COLOR = (61, 128, 102)        # #3d8066 Eucalyptus Green
MAGNET_RED = (224, 122, 95)      # #e07a5f Terracotta / Sunset Coral
SILVER_TIP = (226, 232, 240)     # #e2e8f0 Silver tips
WHITE = (255, 255, 255)
DARK_OUTLINE = (30, 64, 52)      # Deep shadow

def draw_magnet_icon(size, is_maskable=False, is_apple=False):
    # Render at 4x scale for pristine antialiasing
    scale = 4
    canvas_size = size * scale
    
    # Base image
    img = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Determine background shape
    if is_maskable or is_apple:
        # Full bleed background
        draw.rectangle([0, 0, canvas_size, canvas_size], fill=BG_COLOR)
    else:
        # Rounded squircle background
        r = canvas_size * 0.22
        draw.rounded_rectangle([0, 0, canvas_size, canvas_size], radius=r, fill=BG_COLOR)

    # Center magnet symbol
    # Safe zone factor: maskable icons need content within central 70-80%
    content_scale = 0.58 if is_maskable else 0.68
    
    cx, cy = canvas_size / 2, canvas_size / 2
    m_w = canvas_size * content_scale
    m_h = canvas_size * content_scale
    
    # Magnet U-Shape Parameters
    left = cx - m_w / 2
    right = cx + m_w / 2
    top = cy - m_h / 2
    bottom = cy + m_h / 2
    
    thickness = m_w * 0.28
    
    # Outer arc + Inner arc (Horseshoe)
    outer_box = [left, top + thickness, right, bottom]
    inner_box = [left + thickness, top + thickness, right - thickness, bottom - thickness]
    
    # Outer bottom semicircle
    draw.pieslice(outer_box, start=0, end=180, fill=MAGNET_RED)
    
    # Left & right vertical legs of the magnet
    draw.rectangle([left, top, left + thickness, bottom - m_h * 0.25], fill=MAGNET_RED)
    draw.rectangle([right - thickness, top, right, bottom - m_h * 0.25], fill=MAGNET_RED)
    
    # Inner cutout (green background)
    draw.pieslice(inner_box, start=0, end=180, fill=BG_COLOR)
    draw.rectangle([left + thickness, top, right - thickness, bottom - thickness - m_h * 0.25], fill=BG_COLOR)
    
    # Silver magnetic tips at top
    tip_height = thickness * 0.8
    draw.rectangle([left, top, left + thickness, top + tip_height], fill=SILVER_TIP)
    draw.rectangle([right - thickness, top, right, top + tip_height], fill=SILVER_TIP)
    
    # Sparkle / attraction star at top center
    star_cx = cx
    star_cy = top + tip_height / 2
    star_r = thickness * 0.35
    draw.line([star_cx - star_r, star_cy, star_cx + star_r, star_cy], fill=WHITE, width=int(scale * 3))
    draw.line([star_cx, star_cy - star_r, star_cx, star_cy + star_r], fill=WHITE, width=int(scale * 3))
    
    # Downsample with Lanczos filter for smooth antialiasing
    final_img = img.resize((size, size), Image.Resampling.LANCZOS)
    return final_img

# 1. 192x192
icon_192 = draw_magnet_icon(192)
icon_192.save(os.path.join(ICONS_DIR, "icon-192.png"), "PNG")
print("[OK] Generated icon-192.png")

# 2. 512x512
icon_512 = draw_magnet_icon(512)
icon_512.save(os.path.join(ICONS_DIR, "icon-512.png"), "PNG")
print("[OK] Generated icon-512.png")

# 3. 512x512 maskable (Android adaptive safe margin)
icon_maskable = draw_magnet_icon(512, is_maskable=True)
icon_maskable.save(os.path.join(ICONS_DIR, "icon-maskable-512.png"), "PNG")
print("[OK] Generated icon-maskable-512.png")

# 4. Apple Touch Icon 180x180
icon_apple = draw_magnet_icon(180, is_apple=True)
icon_apple.save(os.path.join(ICONS_DIR, "apple-touch-icon.png"), "PNG")
print("[OK] Generated apple-touch-icon.png")

# 5. Favicon.ico (contains 16, 32, 48)
icon_48 = draw_magnet_icon(48)
icon_32 = draw_magnet_icon(32)
icon_16 = draw_magnet_icon(16)
icon_48.save(
    os.path.join(OUTPUT_DIR, "favicon.ico"),
    format="ICO",
    sizes=[(16, 16), (32, 32), (48, 48)],
    append_images=[icon_32, icon_16]
)
print("[OK] Generated favicon.ico")

# 6. Favicon.svg
svg_content = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
  <rect width="100" height="100" rx="22" fill="#3d8066" />
  <!-- Horseshoe Magnet -->
  <path d="M 22 26 L 38 26 L 38 60 A 12 12 0 0 0 62 60 L 62 26 L 78 26 L 78 60 A 28 28 0 0 1 22 60 Z" fill="#e07a5f" />
  <!-- Silver Tips -->
  <rect x="22" y="26" width="16" height="12" fill="#e2e8f0" rx="2" />
  <rect x="62" y="26" width="16" height="12" fill="#e2e8f0" rx="2" />
  <!-- Spark -->
  <path d="M 50 24 L 50 38 M 43 31 L 57 31" stroke="#ffffff" stroke-width="3" stroke-linecap="round" />
</svg>'''

with open(os.path.join(OUTPUT_DIR, "favicon.svg"), "w", encoding="utf-8") as f:
    f.write(svg_content)
print("[OK] Generated favicon.svg")

print("All PWA icons generated successfully!")
