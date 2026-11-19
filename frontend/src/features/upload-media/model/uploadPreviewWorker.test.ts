import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";

const code = readFileSync(resolve(process.cwd(), "public/workers/upload-preview.js"), "utf8");

it("Worker에서 원본을 한 번 디코딩하고 WebP를 생성한 뒤 픽셀 리소스를 해제한다", async () => {
  const file = new File(["original"], "photo.jpg");
  const blob = new Blob(["small"], { type: "image/webp" });
  const close = jest.fn();
  const bitmap = { width: 3000, height: 4000, close };
  const decode = jest.fn(async () => bitmap);
  const draw = jest.fn();
  const encode = jest.fn(async () => blob);
  const canvas = {
    width: 400,
    height: 533,
    getContext: () => ({ drawImage: draw }),
    convertToBlob: encode,
  };
  const Canvas = jest.fn(() => canvas);
  const self = {
    onmessage: undefined as unknown as (event: object) => Promise<void>,
    postMessage: jest.fn(),
  };
  runInNewContext(code, { self, createImageBitmap: decode, OffscreenCanvas: Canvas });
  await self.onmessage({ data: { id: 7, file } });
  expect(decode).toHaveBeenCalledWith(file, { imageOrientation: "from-image" });
  expect(Canvas).toHaveBeenCalledWith(400, 533);
  expect(encode).toHaveBeenCalledWith({ type: "image/webp", quality: 0.8 });
  expect(self.postMessage).toHaveBeenCalledWith({ id: 7, blob });
  expect(close).toHaveBeenCalledTimes(1);
  expect(canvas.width).toBe(1);
});

it("디코딩 실패 시 원본 표시로 대체할 수 있도록 null을 반환한다", async () => {
  const self = {
    onmessage: undefined as unknown as (event: object) => Promise<void>,
    postMessage: jest.fn(),
  };
  runInNewContext(code, {
    self,
    createImageBitmap: async () => {
      throw new Error("decode failed");
    },
  });
  await self.onmessage({ data: { id: 1, file: new File(["broken"], "broken.jpg") } });
  expect(self.postMessage).toHaveBeenCalledWith({ id: 1, blob: null });
});
