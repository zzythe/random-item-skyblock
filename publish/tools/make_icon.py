"""Draw the 512x512 pack icon as original 64x64 pixel art, upscaled with nearest-neighbour.

Motif: a floating skyblock island with a glowing question-mark cube above it,
and a red and a blue team flag. No third-party art is used.
Usage: python publish/tools/make_icon.py publish/icon.png [extra 128px copy for the jar]
"""
from pathlib import Path
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

N = 64
rng = random.Random(7)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def jitter(c, amount):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c)


def iso_cube(px, cx, top_y, half_w, side_h, top, left, right, texture=0):
    """Draw a 2:1 isometric cube. cx/top_y = top vertex, half_w = half width of the top diamond."""
    half_h = half_w // 2
    for y in range(top_y, top_y + 2 * half_h + side_h + 1):
        for x in range(cx - half_w, cx + half_w):
            dx = x + 0.5 - cx
            # Top diamond
            ty = y + 0.5 - top_y
            if abs(dx) / half_w + abs(ty - half_h) / half_h <= 1.0:
                px[x, y] = jitter(top, texture)
                continue
            # Side faces: below the lower edges of the diamond, above the bottom edges
            edge = half_h + half_h * (1 - abs(dx) / half_w)  # lower edge of diamond
            if edge <= ty <= edge + side_h:
                px[x, y] = jitter(left if dx < 0 else right, texture)


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else 'publish/icon.png')
    img = Image.new('RGB', (N, N))
    px = img.load()
    # Sky: dusk-blue to warm horizon, stepped for a pixel look.
    for y in range(N):
        t = (y // 4) / (N // 4 - 1)
        c = lerp((28, 44, 96), (92, 160, 214), t)
        for x in range(N):
            px[x, y] = c
    # Glow behind the cube: warm rings, brightening rather than greying the sky.
    glow = Image.new('L', (N, N), 0)
    ImageDraw.Draw(glow).ellipse((12, 0, 52, 34), fill=255)
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    gp = glow.load()
    for y in range(N):
        for x in range(N):
            a = (gp[x, y] // 52) / 5
            if a:
                c = px[x, y]
                px[x, y] = tuple(min(255, round(c[i] + (t - c[i]) * a * 0.7)) for i, t in enumerate((120, 150, 200)))
                if a > 0.55:
                    px[x, y] = lerp(px[x, y], (255, 214, 120), (a - 0.55) * 0.9)
    # Distant mini island for depth.
    iso_cube(px, 8, 44, 4, 2, (98, 170, 80), (110, 78, 52), (86, 60, 40))
    for i, y in enumerate(range(50, 53)):
        for x in range(8 - 3 + i, 8 + 3 - i):
            px[x, y] = (96, 88, 84)
    iso_cube(px, 57, 40, 3, 2, (98, 170, 80), (110, 78, 52), (86, 60, 40))
    # Island underside: tapering, jagged dirt then stone.
    cx, top, hw, side = 32, 31, 19, 6
    half_h = hw // 2
    for x in range(cx - hw + 1, cx + hw - 1):
        dx = abs(x + 0.5 - cx)
        edge = top + half_h + half_h * (1 - dx / hw) + side
        depth = (hw - dx) * 0.75 + rng.choice([-1, 0, 0, 1])
        for y in range(int(edge) - 1, min(64, int(edge + depth))):
            d = y - edge
            base = (122, 86, 56) if d < 2 else (118, 114, 110) if d < depth - 3 else (92, 90, 88)
            if x >= cx:
                base = tuple(int(v * 0.76) for v in base)
            px[x, y] = jitter(base, 8)
    # Island body: one big grass block.
    iso_cube(px, cx, top, hw, side, (104, 188, 76), (140, 100, 64), (108, 74, 48), texture=10)
    for x in range(cx - hw, cx + hw):
        dx = x + 0.5 - cx
        edge = top + hw // 2 + (hw // 2) * (1 - abs(dx) / hw)
        for y in range(int(edge) + 1, int(edge) + 3):
            px[x, y] = jitter((84, 158, 62) if dx < 0 else (62, 126, 48), 5)
    # Team flags: red on the left corner, blue on the right corner.
    for (fx, color, d) in ((16, (220, 56, 50), 1), (47, (58, 112, 236), -1)):
        for y in range(28, 41):
            px[fx, y] = (66, 48, 36)
        for y in range(28, 32):
            for k in range(1, 6):
                px[fx + d * k, y] = color
    # The cube: gold, floating above the island centre.
    ccx, ctop, hw2, sh = 32, 3, 12, 12
    gold = [(255, 224, 96), (240, 170, 38), (200, 126, 22)]
    iso_cube(px, ccx, ctop, hw2, sh, *gold)
    mask = [[px[x, y] in gold for x in range(N)] for y in range(N)]
    for y in range(N):
        for x in range(N):
            if not mask[y][x] and any(0 <= y + dy < N and 0 <= x + dx < N and mask[y + dy][x + dx]
                                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (58, 32, 8)
    for x in range(ccx - hw2, ccx + hw2):
        dx = x + 0.5 - ccx
        y = int(ctop + hw2 // 2 + (hw2 // 2) * (1 - abs(dx) / hw2))
        px[x, y] = (255, 246, 196)
    for y in range(ctop + hw2, ctop + hw2 + sh):
        px[ccx, y] = (255, 236, 150)
    # Upright question marks, one per side face.
    glyph = ["0110", "1001", "0001", "0010", "0100", "0000", "0100"]
    for ox, oy, col in ((ccx - 8, ctop + 11, (96, 48, 6)), (ccx + 4, ctop + 11, (72, 34, 4))):
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == '1':
                    px[ox + gx, oy + gy] = col
    # Sparkles.
    for (x, y) in [(6, 6), (57, 8), (52, 30), (9, 25), (58, 20)]:
        px[x, y] = (255, 250, 220)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            px[x + dx, y + dy] = lerp(px[x + dx, y + dy], (255, 240, 170), 0.55)
    big = img.resize((512, 512), Image.NEAREST)
    out.parent.mkdir(parents=True, exist_ok=True)
    big.save(out, optimize=True)
    for extra in sys.argv[2:]:
        img.resize((128, 128), Image.NEAREST).save(extra, optimize=True)
    print('wrote', out, out.stat().st_size, 'bytes')


if __name__ == '__main__':
    main()
