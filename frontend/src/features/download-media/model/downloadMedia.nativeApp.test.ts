import { http, HttpResponse } from "msw";

import { MOCK_ROOM_CODES, MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { PHOTO_PERMISSION_DENIED_MESSAGE } from "../lib/downloadErrorMessage";
import { saveBlob } from "../lib/saveBlob";
import { downloadMedia } from "./downloadMedia";
import type { DownloadMode, DownloadTarget } from "./types";

jest.mock("../lib/saveBlob", () => ({ saveBlob: jest.fn() }));

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

const emit = (detail: object) => window.dispatchEvent(new CustomEvent("sssok:native", { detail }));

/**
 * 앱 웹뷰를 흉내 낸다. 웹이 보낸 메시지를 모으고, `respond` 가 앱의 답을 쏜다.
 * 답은 다음 틱에 보낸다 — 실제 앱도 메시지를 받은 뒤 비동기로 답한다.
 */
const asNativeApp = (respond: (message: SentMessage) => void = () => {}) => {
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

const serveBatch = (excluded: number[] = []) =>
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

const run = (targets: DownloadTarget[], mode: DownloadMode, extra = {}) =>
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
