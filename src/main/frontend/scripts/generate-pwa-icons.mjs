// One-off generator for the PWA install icons (ui-11) — draws the app's own diamond mark
// (matching the header logo) as a simple raster icon, since no image-editing tool is
// available in this environment. Run once (`node scripts/generate-pwa-icons.mjs`); the
// output PNGs are committed to public/, this script is not part of the build.
import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";

const BG = [0x22, 0x1a, 0x2e]; // --bg
const FG = [0xe8, 0xa8, 0x7c]; // --accent

function crc32(buf) {
  let c;
  const table = crc32.table ?? (crc32.table = (() => {
    const t = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      t[n] = c;
    }
    return t;
  })());
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type, "ascii");
  const lenBuf = Buffer.alloc(4);
  lenBuf.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
}

function drawIcon(size) {
  // A diamond (rotated square) inscribed in the icon, same silhouette as the header's "◆".
  const pixels = Buffer.alloc(size * size * 4);
  const cx = size / 2;
  const cy = size / 2;
  const r = size * 0.32;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const dx = Math.abs(x + 0.5 - cx);
      const dy = Math.abs(y + 0.5 - cy);
      const inDiamond = dx + dy <= r;
      const [red, green, blue] = inDiamond ? FG : BG;
      const i = (y * size + x) * 4;
      pixels[i] = red;
      pixels[i + 1] = green;
      pixels[i + 2] = blue;
      pixels[i + 3] = 255;
    }
  }
  return pixels;
}

function encodePng(size) {
  const pixels = drawIcon(size);
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0; // filter: none
    pixels.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
  }
  const idat = deflateSync(raw);

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type: RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  return Buffer.concat([
    signature,
    chunk("IHDR", ihdr),
    chunk("IDAT", idat),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

mkdirSync(new URL("../public", import.meta.url), { recursive: true });
for (const size of [192, 512]) {
  const png = encodePng(size);
  writeFileSync(new URL(`../public/icon-${size}.png`, import.meta.url), png);
  console.log(`wrote public/icon-${size}.png (${png.length} bytes)`);
}
