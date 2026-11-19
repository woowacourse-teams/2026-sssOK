import type { DownloadOutcome } from "@/features/download-media/model/types";
import { http, HttpResponse } from "msw";

import { MOCK_ROOM_CODES, MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { captureException } from "@/shared/lib";
import { PHOTO_PERMISSION_DENIED_MESSAGE } from "../lib/downloadErrorMessage";
import { saveBlob } from "../lib/saveBlob";
import { downloadMedia } from "./downloadMedia";
import type { DownloadMode, DownloadTarget } from "./types";

jest.mock("../lib/saveBlob", () => ({ saveBlob: jest.fn() }));
jest.mock("@/shared/lib", () => ({
  ...jest.requireActual("@/shared/lib"),
  captureException: jest.fn(),
}));

jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  POLL_INTERVAL_MS: 0,
  SHARE_BATCH_SIZE: 2,
}));

const TOKEN = "mock-token-10234";

const targetOf = (mediaId: number): DownloadTarget => ({
  mediaId,
  fileName: `IMG_${mediaId}.jpg`,
  size: 2912048,
  mimeType: "image/jpeg",
});

interface SentMessage {
  type: string;
  requestId: string;
  files?: { id: number; url: string; fileName: string; mimeType: string }[];
  file?: { id: number; url: string; fileName: string; mimeType: string };
}

const emit = (detail: object): boolean =>
  window.dispatchEvent(new CustomEvent("sssok:native", { detail }));

/**
 * 앱 웹뷰를 흉내 낸다. 웹이 보낸 메시지를 모으고, `respond` 가 앱의 답을 쏜다.
 * 답은 다음 틱에 보낸다 — 실제 앱도 메시지를 받은 뒤 비동기로 답한다.
 */
const asNativeApp = (respond: (message: SentMessage) => void = () => {}): SentMessage[] => {
  const sent: SentMessage[] = [];

  window.ReactNativeWebView = {
    postMessage: (raw: string) => {
      const message = JSON.parse(raw) as SentMessage;

      sent.push(message);
      setTimeout(() => respond(message), 0);
    },
  };
  window.sssokApp = { download: 1 };

  return sent;
};

const serveBatch = (excluded: number[] = []): void =>
  server.use(
    http.post(`${API_BASE_URL}/rooms/:roomId/downloads/batch`, async ({ request }) => {
      const { mediaIds } = (await request.json()) as { mediaIds: number[] };

      return HttpResponse.json({
        data: {
          files: mediaIds
            .filter((mediaId) => !excluded.includes(mediaId))
            .map((mediaId) => ({
              mediaId,
              fileName: `서버_${mediaId}.jpg`,
              downloadUrl: `https://storage.example.com/${mediaId}`,
              expiresAt: new Date(Date.now() + 300_000).toISOString(),
            })),
        },
      });
    }),
  );

const run = (targets: DownloadTarget[], mode: DownloadMode, extra = {}): Promise<DownloadOutcome> =>
  downloadMedia({ roomId: MOCK_ROOM_ID, targets, mode, token: TOKEN, ...extra });

beforeEach(() =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    body: JSON.stringify({ code: MOCK_ROOM_CODES.active }),
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  }),
);

afterEach(() => {
  delete window.ReactNativeWebView;
  delete window.sssokApp;
});

describe("downloadMedia — 앱 웹뷰", () => {
  it("바이트를 받지 않고 서명 URL·서버가 정한 이름·목록의 MIME 을 앱에 넘긴다", async () => {
    serveBatch();
    const sent = asNativeApp(({ requestId }) =>
      emit({ type: "download/done", requestId, result: "completed", failed: [] }),
    );

    const outcome = await run([targetOf(1), targetOf(2), targetOf(3)], "share");

    expect(outcome).toEqual({ type: "saved", savedCount: 3, failed: [] });
    expect(sent[0]).toMatchObject({
      type: "download/save",
      files: [
        {
          id: 1,
          url: "https://storage.example.com/1",
          fileName: "서버_1.jpg",
          mimeType: "image/jpeg",
        },
        { id: 2 },
        { id: 3 },
      ],
    });
    expect(saveBlob).not.toHaveBeenCalled();
  });

  it("묶음 기준을 넘어도 나누지 않고 한 번에 저장한다", async () => {
    serveBatch();
    asNativeApp(({ requestId }) =>
      emit({ type: "download/done", requestId, result: "completed", failed: [] }),
    );

    const outcome = await run([targetOf(1), targetOf(2), targetOf(3)], "individual");

    expect(outcome).toMatchObject({ type: "saved", savedCount: 3 });
  });

  it("앱이 알린 진행률과 저장 완료를 그대로 흘리고, 길이를 모르면 목록 크기로 센다", async () => {
    serveBatch();
    asNativeApp(({ requestId }) => {
      emit({ type: "download/progress", requestId, id: 1, loaded: 100, total: -1 });
      emit({ type: "download/saved", requestId, id: 1 });
      emit({ type: "download/done", requestId, result: "completed", failed: [] });
    });
    const onProgress = jest.fn();
    const onDownloaded = jest.fn();

    await run([targetOf(1)], "share", { onProgress, onDownloaded });

    expect(onProgress).toHaveBeenCalledWith({ mediaId: 1, loaded: 100, total: 2912048 });
    expect(onDownloaded).toHaveBeenCalledWith(1);
  });

  it("못 받은 것과 서버가 뺀 것만 실패로 남기고 나머지는 저장으로 센다", async () => {
    serveBatch([3]);
    asNativeApp(({ requestId }) =>
      emit({
        type: "download/done",
        requestId,
        result: "completed",
        failed: [{ id: 2, status: 403 }],
      }),
    );

    const outcome = await run([targetOf(1), targetOf(2), targetOf(3)], "share");

    expect(outcome).toEqual({
      type: "saved",
      savedCount: 1,
      failed: [
        { mediaId: 3, fileName: "IMG_3.jpg", status: 404 },
        { mediaId: 2, fileName: "IMG_2.jpg", status: 403 },
      ],
    });
  });

  /**
   * 앱은 상태 코드가 없는 실패를 모두 0 으로 넘긴다. 단계가 없으면 받기가 끊긴 것과
   * 사진첩이 거절한 것을 가를 수 없고, 웹이 남기지 않으면 원인이 어디에도 없다.
   */
  it("앱이 알려준 단계를 실패에 싣고 원래 메시지로 예외를 남긴다", async () => {
    serveBatch();
    asNativeApp(({ requestId }) =>
      emit({
        type: "download/done",
        requestId,
        result: "completed",
        failed: [
          { id: 1, status: 0, stage: "save", message: "PHPhotosErrorDomain error 3302" },
          { id: 2, status: 0, stage: "download", message: "The network connection was lost." },
        ],
      }),
    );

    const outcome = await run([targetOf(1), targetOf(2), targetOf(3)], "share");

    expect(outcome).toEqual({
      type: "saved",
      savedCount: 1,
      failed: [
        { mediaId: 1, fileName: "IMG_1.jpg", status: 0, stage: "save" },
        { mediaId: 2, fileName: "IMG_2.jpg", status: 0, stage: "download" },
      ],
    });
    expect(captureException).toHaveBeenCalledWith(
      new Error("PHPhotosErrorDomain error 3302"),
      expect.objectContaining({
        operation: "download.native_save",
        status: 0,
        code: "NATIVE_SAVE_FAILED",
      }),
    );
    expect(captureException).toHaveBeenCalledWith(
      new Error("The network connection was lost."),
      expect.objectContaining({ code: "NATIVE_DOWNLOAD_FAILED" }),
    );
  });

  it("단계를 모르는 예전 앱이나 모르는 단계가 와도 답을 버리지 않는다", async () => {
    serveBatch();
    asNativeApp(({ requestId }) =>
      emit({
        type: "download/done",
        requestId,
        result: "completed",
        failed: [
          { id: 1, status: 0 },
          { id: 2, status: 0, stage: "teleport" },
        ],
      }),
    );

    const outcome = await run([targetOf(1), targetOf(2)], "share");

    expect(outcome).toEqual({
      type: "empty",
      failed: [
        { mediaId: 1, fileName: "IMG_1.jpg", status: 0 },
        { mediaId: 2, fileName: "IMG_2.jpg", status: 0 },
      ],
    });
    expect(captureException).toHaveBeenCalledWith(
      expect.any(Error),
      expect.objectContaining({ code: "NATIVE_UNKNOWN_FAILED" }),
    );
  });

  it("사진첩 권한을 거절하면 다시 눌러도 같은 실패로 알린다", async () => {
    serveBatch();
    asNativeApp(({ requestId }) =>
      emit({ type: "download/done", requestId, result: "permissionDenied", failed: [] }),
    );

    const outcome = await run([targetOf(1)], "share");

    expect(outcome).toEqual({
      type: "failed",
      reason: PHOTO_PERMISSION_DENIED_MESSAGE,
      isRetryable: false,
    });
  });

  it("취소하면 앱에 그만두라고 알리고 aborted 로 끝난다", async () => {
    serveBatch();
    const controller = new AbortController();
    const sent = asNativeApp((message) => {
      if (message.type === "download/save") controller.abort();
    });

    const outcome = await run([targetOf(1)], "share", { signal: controller.signal });

    expect(outcome).toEqual({ type: "aborted" });
    expect(sent.map(({ type }) => type)).toEqual(["download/save", "download/cancel"]);
    expect(sent[1].requestId).toBe(sent[0].requestId);
  });

  it("다른 판의 답에는 반응하지 않는다", async () => {
    serveBatch();
    asNativeApp(({ requestId }) => {
      emit({ type: "download/done", requestId: "other", result: "permissionDenied", failed: [] });
      emit({ type: "download/done", requestId, result: "completed", failed: [] });
    });

    const outcome = await run([targetOf(1)], "share");

    expect(outcome).toMatchObject({ type: "saved", savedCount: 1 });
  });

  it("zip 은 다 묶인 서명 URL 을 앱의 공유 시트로 넘긴다", async () => {
    server.use(
      http.get("https://picsum.photos/seed/:seed/:width/:height", () =>
        HttpResponse.arrayBuffer(new ArrayBuffer(8), { headers: { "Content-Type": "image/jpeg" } }),
      ),
    );
    const sent = asNativeApp(({ requestId }) =>
      emit({ type: "download/done", requestId, result: "completed", failed: [] }),
    );

    const outcome = await run([targetOf(5000), targetOf(5001)], "zip");

    expect(outcome).toMatchObject({ type: "saved" });
    expect(sent[0]).toMatchObject({
      type: "download/share",
      file: { mimeType: "application/zip", fileName: expect.stringMatching(/\.zip$/) },
    });
    expect(saveBlob).not.toHaveBeenCalled();
  });

  it("브릿지를 모르는 예전 앱이면 앱에 맡기지 않고 기존 방식으로 받는다", async () => {
    serveBatch();
    const sent = asNativeApp();

    delete window.sssokApp;
    server.use(
      http.get("https://storage.example.com/:mediaId", () =>
        HttpResponse.arrayBuffer(new ArrayBuffer(8), { headers: { "Content-Type": "image/jpeg" } }),
      ),
    );

    const outcome = await run([targetOf(1)], "individual");

    expect(outcome).toMatchObject({ type: "saved", savedCount: 1 });
    expect(sent).toHaveLength(0);
    expect(saveBlob).toHaveBeenCalledTimes(1);
  });
});

describe("downloadMedia — 앱 웹뷰, 요청 나누기", () => {
  const fourTargets = [targetOf(1), targetOf(2), targetOf(3), targetOf(4)];
  const idsOf = (message: SentMessage): number[] => (message.files ?? []).map(({ id }) => id);

  it("3장 이하는 요청 하나로 보낸다", async () => {
    serveBatch();
    const sent = asNativeApp(({ requestId }) =>
      emit({ type: "download/done", requestId, result: "completed", failed: [] }),
    );

    await run([targetOf(1), targetOf(2), targetOf(3)], "share");

    expect(sent).toHaveLength(1);
  });

  it("4장 이상은 번갈아 두 요청으로 나누고, 두 번째는 첫 요청이 받기 시작한 뒤에 보낸다", async () => {
    serveBatch();
    const sent = asNativeApp(({ requestId, files }) => {
      const [firstFile] = files ?? [];

      emit({ type: "download/progress", requestId, id: firstFile.id, loaded: 1, total: 10 });
      setTimeout(
        () => emit({ type: "download/done", requestId, result: "completed", failed: [] }),
        0,
      );
    });

    const outcome = await run(fourTargets, "share");

    expect(outcome).toEqual({ type: "saved", savedCount: 4, failed: [] });
    expect(sent.map(idsOf)).toEqual([
      [1, 3],
      [2, 4],
    ]);
    expect(sent[0].requestId).not.toBe(sent[1].requestId);
  });

  it("첫 요청이 사진첩 권한을 거절당하면 두 번째 요청을 보내지 않는다", async () => {
    serveBatch();
    const sent = asNativeApp(({ requestId }) =>
      emit({ type: "download/done", requestId, result: "permissionDenied", failed: [] }),
    );

    const outcome = await run(fourTargets, "share");

    expect(outcome).toEqual({
      type: "failed",
      reason: PHOTO_PERMISSION_DENIED_MESSAGE,
      isRetryable: false,
    });
    expect(sent).toHaveLength(1);
  });

  it("두 요청의 실패를 합쳐 알린다", async () => {
    serveBatch();
    asNativeApp(({ requestId, files }) => {
      const [firstFile] = files ?? [];

      emit({ type: "download/saved", requestId, id: firstFile.id });
      setTimeout(
        () =>
          emit({
            type: "download/done",
            requestId,
            result: "completed",
            failed: [{ id: firstFile.id + 2, status: 403, stage: "download" }],
          }),
        0,
      );
    });

    const outcome = await run(fourTargets, "share");

    expect(outcome).toEqual({
      type: "saved",
      savedCount: 2,
      failed: [
        { mediaId: 3, fileName: "IMG_3.jpg", status: 403, stage: "download" },
        { mediaId: 4, fileName: "IMG_4.jpg", status: 403, stage: "download" },
      ],
    });
  });

  it("취소하면 두 요청 모두에 그만두라고 알린다", async () => {
    serveBatch();
    const controller = new AbortController();
    const sent = asNativeApp((message) => {
      if (message.type !== "download/save") return;

      if (message.files?.[0].id === 1) {
        emit({
          type: "download/progress",
          requestId: message.requestId,
          id: 1,
          loaded: 1,
          total: 10,
        });
        return;
      }

      controller.abort();
    });

    const outcome = await run(fourTargets, "share", { signal: controller.signal });

    expect(outcome).toEqual({ type: "aborted" });
    const cancelled = sent
      .filter(({ type }) => type === "download/cancel")
      .map(({ requestId }) => requestId);
    const saved = sent
      .filter(({ type }) => type === "download/save")
      .map(({ requestId }) => requestId);

    expect(cancelled.sort()).toEqual(saved.sort());
  });
});
