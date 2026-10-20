// Generates the PWA icons as PNGs without native dependencies: a rounded
// institutional-blue tile with a white document-and-check glyph, rasterised
// from simple geometry and encoded with node:zlib.
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';

const BLUE = [11, 79, 108];
const GOLD = [232, 177, 14];
const WHITE = [255, 255, 255];

function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(size, pixel) {
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0;
    for (let x = 0; x < size; x++) {
      const [r, g, b, a] = pixel(x / size, y / size);
      const o = y * (size * 4 + 1) + 1 + x * 4;
      raw[o] = r; raw[o + 1] = g; raw[o + 2] = b; raw[o + 3] = a;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
function inRoundRect(x, y, x0, y0, x1, y1, r) {
  if (x < x0 || x > x1 || y < y0 || y > y1) return false;
  const cx = Math.min(Math.max(x, x0 + r), x1 - r), cy = Math.min(Math.max(y, y0 + r), y1 - r);
  return (x - cx) ** 2 + (y - cy) ** 2 <= r * r;
}
function distToSegment(px, py, ax, ay, bx, by) {
  const t = Math.max(0, Math.min(1, ((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / ((bx - ax) ** 2 + (by - ay) ** 2)));
  return Math.hypot(px - (ax + t * (bx - ax)), py - (ay + t * (by - ay)));
}
// pad: glyph inset (maskable icons keep content inside the 80% safe zone)
function glyph(pad, fullBleed) {
  return (x, y) => {
    const bg = fullBleed ? true : inRoundRect(x, y, 0.02, 0.02, 0.98, 0.98, 0.2);
    if (!bg) return [0, 0, 0, 0];
    const s = (v) => pad + v * (1 - 2 * pad);
    if (inRoundRect(x, y, s(0.24), s(0.16), s(0.76), s(0.84), 0.04 * (1 - 2 * pad))) {
      // check mark
      const w = 0.055 * (1 - 2 * pad);
      if (distToSegment(x, y, s(0.34), s(0.52), s(0.46), s(0.64)) < w ||
          distToSegment(x, y, s(0.46), s(0.64), s(0.67), s(0.38)) < w) return [...GOLD, 255];
      // text lines
      for (const ly of [0.26, 0.74]) {
        if (Math.abs(y - s(ly)) < 0.018 * (1 - 2 * pad) && x > s(0.32) && x < s(0.68)) return [...BLUE, 255];
      }
      return [...WHITE, 255];
    }
    return [...BLUE, 255];
  };
}
mkdirSync('public/icons', { recursive: true });
for (const size of [64, 192, 512]) writeFileSync(`public/icons/icon-${size}.png`, png(size, glyph(0.08, false)));
writeFileSync('public/icons/maskable-512.png', png(512, glyph(0.18, true)));
writeFileSync('public/icons/apple-touch-icon.png', png(180, glyph(0.12, true)));
console.log('icons written');
