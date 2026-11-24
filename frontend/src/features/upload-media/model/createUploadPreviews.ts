interface PreviewJob {
  id: number;
  file: File;
  resolve: (blob: Blob | undefined) => void;
}

/** 업로드 한 판에서 Worker 하나를 재사용한다. 실패·미지원·취소는 원본 미리보기로 대체한다. */
export const createUploadPreviews = (
  signal?: AbortSignal,
): {
  create(file: File): Promise<Blob | undefined>;
  dispose(): void;
} => {
  let worker: Worker | undefined;
  let stopped = false;
  let nextId = 0;
  let active: PreviewJob | undefined;
  let timeout: ReturnType<typeof setTimeout> | undefined;
  const waiting: PreviewJob[] = [];

  const dispose = (): void => {
    stopped = true;
    clearTimeout(timeout);
    worker?.terminate();
    worker = undefined;
    active?.resolve(undefined);
    active = undefined;
    waiting.splice(0).forEach((job) => job.resolve(undefined));
    signal?.removeEventListener("abort", dispose);
  };

  const pump = (): void => {
    if (stopped || active || waiting.length === 0) return;
    try {
      if (!worker) {
        worker = new Worker("/workers/upload-preview.js");
        worker.onmessage = (event: MessageEvent<{ id: number; blob: Blob | null }>): void => {
          if (!active || event.data.id !== active.id) return;
          clearTimeout(timeout);
          active.resolve(event.data.blob instanceof Blob ? event.data.blob : undefined);
          active = undefined;
          pump();
        };
        worker.onerror = dispose;
        worker.onmessageerror = dispose;
      }
      active = waiting.shift();
      if (!active) return;
      timeout = setTimeout(dispose, 10000);
      worker.postMessage({ id: active.id, file: active.file });
    } catch {
      dispose();
    }
  };

  signal?.addEventListener("abort", dispose, { once: true });
  if (signal?.aborted || typeof Worker === "undefined") dispose();

  return {
    create(file): Promise<Blob | undefined> {
      // 영상과 애니메이션 GIF는 기존 동작 유지. 빈 MIME은 확장자로 사진 여부를 보완한다.
      if (stopped || !/\.(jpe?g|png)$/i.test(file.name)) return Promise.resolve(undefined);
      return new Promise((resolve) => {
        waiting.push({ id: nextId++, file, resolve });
        pump();
      });
    },
    dispose,
  };
};
