import { createUploadPreviews } from "./createUploadPreviews";

class PreviewWorker {
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: (() => void) | null = null;
  onmessageerror: (() => void) | null = null;
  postMessage = jest.fn();
  terminate = jest.fn();
  reply(blob: Blob | null): void {
    const calls = this.postMessage.mock.calls;
    const { id } = calls[calls.length - 1][0];
    this.onmessage?.({ data: { id, blob } } as MessageEvent);
  }
}
const originalWorker = globalThis.Worker;
let instance: PreviewWorker;
const image = new File(["original"], "photo.jpg", { type: "image/jpeg" });
beforeEach(() => {
  instance = new PreviewWorker();
  globalThis.Worker = jest.fn(() => instance) as unknown as typeof Worker;
});
afterEach(() => {
  globalThis.Worker = originalWorker;
  jest.useRealTimers();
});

it("한 장씩 Worker를 재사용하며 파일 실패 후에도 다음 파일을 처리한다", async () => {
  const previews = createUploadPreviews();
  const first = previews.create(image);
  const next = previews.create(image);
  expect(instance.postMessage).toHaveBeenCalledTimes(1);
  instance.reply(null);
  await expect(first).resolves.toBeUndefined();
  expect(instance.postMessage).toHaveBeenCalledTimes(2);
  const blob = new Blob(["thumbnail"], { type: "image/webp" });
  instance.reply(blob);
  await expect(next).resolves.toBe(blob);
  previews.dispose();
  expect(instance.terminate).toHaveBeenCalledTimes(1);
});

it("취소 시 실행·대기 작업을 모두 해제하고 Worker를 종료한다", async () => {
  const controller = new AbortController();
  const previews = createUploadPreviews(controller.signal);
  const jobs = [previews.create(image), previews.create(image)];
  controller.abort();
  await expect(Promise.all(jobs)).resolves.toEqual([undefined, undefined]);
  expect(instance.terminate).toHaveBeenCalledTimes(1);
  await expect(previews.create(image)).resolves.toBeUndefined();
});

it("응답 없는 Worker는 시간 제한 후 원본으로 대체한다", async () => {
  jest.useFakeTimers();
  const previews = createUploadPreviews();
  const jobs = [previews.create(image), previews.create(image)];
  await jest.advanceTimersByTimeAsync(10000);
  await expect(Promise.all(jobs)).resolves.toEqual([undefined, undefined]);
  expect(instance.terminate).toHaveBeenCalledTimes(1);
});

it("Worker 실행 실패 시 대기 파일도 원본으로 대체한다", async () => {
  const previews = createUploadPreviews();
  const jobs = [previews.create(image), previews.create(image)];
  instance.onerror?.();
  await expect(Promise.all(jobs)).resolves.toEqual([undefined, undefined]);
});

it("미지원 브라우저·영상·GIF에서는 Worker를 실행하지 않는다", async () => {
  const previews = createUploadPreviews();
  await expect(previews.create(new File(["video"], "video.mov"))).resolves.toBeUndefined();
  await expect(previews.create(new File(["gif"], "animated.gif"))).resolves.toBeUndefined();
  expect(globalThis.Worker).not.toHaveBeenCalled();
  previews.dispose();
  globalThis.Worker = undefined as unknown as typeof Worker;
  await expect(createUploadPreviews().create(image)).resolves.toBeUndefined();
});
