/* 원본을 변경하지 않고 목록용 WebP만 생성한다. 요청은 클라이언트에서 직렬화한다. */
self.onmessage = async ({ data: { id, file } }) => {
  let bitmap;
  let canvas;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
    const width = Math.min(400, bitmap.width);
    const height = Math.max(1, Math.round((bitmap.height * width) / bitmap.width));
    canvas = new OffscreenCanvas(width, height);
    const context = canvas.getContext("2d");
    if (!context) throw new Error("Canvas unavailable");
    context.drawImage(bitmap, 0, 0, width, height);
    const blob = await canvas.convertToBlob({ type: "image/webp", quality: 0.8 });
    if (blob.type !== "image/webp") throw new Error("WebP unavailable");
    self.postMessage({ id, blob });
  } catch {
    self.postMessage({ id, blob: null });
  } finally {
    bitmap?.close();
    if (canvas) {
      canvas.width = 1;
      canvas.height = 1;
    }
  }
};
