import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { saveBlob } from "../lib/saveBlob";
import { downloadMedia } from "./downloadMedia";
import type { DownloadTarget } from "./types";

// jsdom 에는 URL.createObjectURL 이 없다. 저장이 몇 번 어떤 이름으로 불렸는지만 본다.
jest.mock("../lib/saveBlob", () => ({ saveBlob: jest.fn() }));

// 저장 간격과 폴링 간격을 실제로 기다리면 검사마다 몇 초씩 잡아먹는다.
jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  INDIVIDUAL_SAVE_GAP_MS: 0,
  POLL_INTERVAL_MS: 0,
}));

const TOKEN = "mock-token-10234";
const saveBlobMock = saveBlob as jest.MockedFunction<typeof saveBlob>;

const targetOf = (mediaId: number): DownloadTarget => ({
  mediaId,
  fileName: `IMG_${mediaId}.jpg`,
  size: 2912048,
  mimeType: "image/jpeg",
});

const enterRoom = () =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  });

const run = (targets: DownloadTarget[], mode: "individual" | "zip", signal?: AbortSignal) =>
  downloadMedia({ roomId: MOCK_ROOM_ID, targets, mode, token: TOKEN, signal });

/** 단건 다운로드를 가로채 상태를 골라 내준다. 목 픽스처의 원본 URL 에 기대지 않는다. */
const serveSingle = ({
  statusOf = () => 200,
  delayMs = 0,
}: { statusOf?: (mediaId: number) => number; delayMs?: number } = {}) => {
  let running = 0;
  let peak = 0;

  server.use(
    // 개별 저장은 서명 URL 을 먼저 한 번에 받는다. 목에서는 아래 GET 이 그 자리다.
    http.post(`${API_BASE_URL}/rooms/:roomId/downloads/batch`, async ({ request, params }) => {
      const { mediaIds } = (await request.json()) as { mediaIds: number[] };

      return HttpResponse.json({
        data: {
          files: mediaIds.map((mediaId) => ({
            mediaId,
            fileName: `IMG_${mediaId}.jpg`,
            downloadUrl: `${API_BASE_URL}/rooms/${params.roomId}/downloads/media/${mediaId}`,
            expiresAt: new Date(Date.now() + 300_000).toISOString(),
          })),
        },
      });
    }),

    http.get(`${API_BASE_URL}/rooms/:roomId/downloads/media/:mediaId`, async ({ params }) => {
      const status = statusOf(Number(params.mediaId));

      if (status !== 200) {
        return HttpResponse.json({ code: "ERR", message: "" }, { status });
      }

      running += 1;
      peak = Math.max(peak, running);

      if (delayMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      }

      running -= 1;

      return new HttpResponse(new Blob(["bytes"]), {
        headers: { "Content-Type": "image/jpeg" },
      });
    }),
  );

  return { peakOf: () => peak };
};

/**
 * 목의 압축 워커가 원본을 실제로 받아 zip 을 묶는다. 사진 원본은 `picsum.photos` 를
 * 가리키는데 테스트 환경에는 그 호스트가 없다. **여기서만** 바이트를 내준다 —
 * 전역 핸들러로 두면 브라우저에서 갤러리 썸네일까지 가짜가 된다.
 */
const serveOriginals = () =>
  server.use(
    http.get(
      "https://picsum.photos/seed/:seed/:width/:height",
      () =>
        new HttpResponse(new Blob(["original-bytes"]), {
          headers: { "Content-Type": "image/jpeg" },
        }),
    ),
  );

beforeEach(enterRoom);

describe("downloadMedia — 개별 저장", () => {
  it("고른 장수만큼 받아 각각 저장한다", async () => {
    serveSingle();

    const outcome = await run([targetOf(5000), targetOf(5001), targetOf(5002)], "individual");

    expect(outcome).toEqual({ type: "saved", savedCount: 3, failed: [] });
    expect(saveBlobMock).toHaveBeenCalledTimes(3);
  });

  /**
   * 한꺼번에 다 열면 회선을 나눠 갖느라 전부 느려지고 폰에서는 연결 수가 막힌다.
   * 상수를 바꾸면 이 검사가 따라 움직여야 한다.
   */
  it("동시에 여는 요청이 DOWNLOAD_CONCURRENCY 를 넘지 않는다", async () => {
    const { peakOf } = serveSingle({ delayMs: 5 });
    const targets = [5000, 5001, 5002, 5003, 5004, 5005, 5006].map(targetOf);

    await run(targets, "individual");

    expect(peakOf()).toBeLessThanOrEqual(4);
  });

  // 30장 중 하나가 깨졌다고 나머지 29장까지 멈추면 안 된다.
  it("일부가 실패해도 나머지를 계속 받고 실패는 값으로 모은다", async () => {
    serveSingle({ statusOf: (mediaId) => (mediaId === 5001 ? 409 : 200) });

    const outcome = await run([targetOf(5000), targetOf(5001), targetOf(5002)], "individual");

    expect(outcome).toEqual({
      type: "saved",
      savedCount: 2,
      failed: [{ mediaId: 5001, fileName: "IMG_5001.jpg", status: 409 }],
    });
    expect(saveBlobMock).toHaveBeenCalledTimes(2);
  });

  it("한 장도 못 받으면 empty 로 실패 목록만 남긴다", async () => {
    serveSingle({ statusOf: () => 404 });

    const outcome = await run([targetOf(5000), targetOf(5001)], "individual");

    expect(outcome).toMatchObject({ type: "empty" });
    expect(saveBlobMock).not.toHaveBeenCalled();
  });

  // 취소를 실패로 세면 "2장 실패" 같은 거짓 보고가 나간다.
  it("이미 중단된 신호면 아무것도 받지 않고 aborted 다", async () => {
    serveSingle();

    const outcome = await run([targetOf(5000)], "individual", AbortSignal.abort());

    expect(outcome).toEqual({ type: "aborted" });
    expect(saveBlobMock).not.toHaveBeenCalled();
  });
});

describe("downloadMedia — zip", () => {
  beforeEach(serveOriginals);

  it("잡을 만들고 READY 까지 되물은 뒤 zip 하나를 저장한다", async () => {
    const outcome = await run([targetOf(5000), targetOf(5001)], "zip");

    expect(outcome).toMatchObject({ type: "saved" });
    // 낱장이 아니라 zip 하나다.
    expect(saveBlobMock).toHaveBeenCalledTimes(1);
    expect(saveBlobMock.mock.calls[0][1]).toMatch(/\.zip$/);
    // 빈 껍데기가 아니라 원본이 실제로 들어간 zip 이다.
    expect(saveBlobMock.mock.calls[0][0].size).toBeGreaterThan(0);
  });

  it("zip 을 고르면 단건 다운로드 요청이 나가지 않는다", async () => {
    const singles: number[] = [];

    server.use(
      http.get(`${API_BASE_URL}/rooms/:roomId/downloads/media/:mediaId`, ({ params }) => {
        singles.push(Number(params.mediaId));

        return new HttpResponse(new Blob(["x"]));
      }),
    );

    await run([targetOf(5000), targetOf(5001)], "zip");

    expect(singles).toHaveLength(0);
  });

  /**
   * 429 는 동시 잡 상한에 걸린 것이다. 되물을 잡 번호 자체가 없으므로
   * 폴링을 시작하면 안 된다 (#121 완료 조건).
   */
  it("잡을 만들지 못하면 폴링을 시작하지 않고 사유를 돌려준다", async () => {
    const polls: string[] = [];

    server.use(
      http.post(`${API_BASE_URL}/rooms/:roomId/downloads/zip`, () =>
        HttpResponse.json({ code: "RATE_LIMITED", message: "" }, { status: 429 }),
      ),
      http.get(`${API_BASE_URL}/rooms/:roomId/downloads/zip/:jobId`, ({ params }) => {
        polls.push(String(params.jobId));

        return HttpResponse.json({ data: {} });
      }),
    );

    const outcome = await run([targetOf(5000)], "zip");

    expect(outcome).toEqual({
      type: "failed",
      reason: "받는 중인 요청이 많아요",
      isRetryable: true,
    });
    expect(polls).toHaveLength(0);
  });

  it("기한이 지난 잡은 그 사유로 알린다", async () => {
    server.use(
      http.post(`${API_BASE_URL}/rooms/:roomId/downloads/zip`, () =>
        HttpResponse.json({ code: "EXPIRED", message: "" }, { status: 410 }),
      ),
    );

    expect(await run([targetOf(5000)], "zip")).toMatchObject({ reason: "기한이 지났어요" });
  });

  it("압축이 실패하면 서버가 준 사유를 그대로 전한다", async () => {
    server.use(
      http.get(`${API_BASE_URL}/rooms/:roomId/downloads/zip/:jobId`, () =>
        HttpResponse.json({
          data: {
            jobId: "dl_000001",
            status: "FAILED",
            progress: 40,
            mediaCount: 1,
            fileName: "ShareDrop_7K93QX2S.zip",
            downloadUrl: null,
            expiresAt: null,
            failureReason: "원본을 읽지 못했습니다",
          },
        }),
      ),
    );

    expect(await run([targetOf(5000)], "zip")).toEqual({
      type: "failed",
      reason: "원본을 읽지 못했습니다",
      isRetryable: true,
    });
    expect(saveBlobMock).not.toHaveBeenCalled();
  });
});

/**
 * #146 — 아이폰에서 공유 시트는 떴는데 사진첩에 아무것도 안 남던 건.
 *
 * 스토리지가 `Content-Type` 을 이미지로 안 내주면 `blob.type` 이 빈 문자열이 아니라
 * `application/octet-stream` 이라 폴백이 걸리지 않는다. 그대로 넘긴 File 을 iOS 는
 * 이미지로 보지 않아 공유 시트에 **"이미지 저장" 항목을 아예 띄우지 않는다** —
 * 시트는 열리므로 프론트에서는 성공으로 보이고, 사용자만 저장이 안 된다.
 */
describe("downloadMedia — 사진첩에 저장", () => {
  const sharedFilesOf = async (contentType: string) => {
    const shared: File[] = [];

    server.use(
      http.post(`${API_BASE_URL}/rooms/:roomId/downloads/batch`, async ({ request, params }) => {
        const { mediaIds } = (await request.json()) as { mediaIds: number[] };

        return HttpResponse.json({
          data: {
            files: mediaIds.map((mediaId) => ({
              mediaId,
              fileName: `IMG_${mediaId}.jpg`,
              downloadUrl: `${API_BASE_URL}/rooms/${params.roomId}/downloads/media/${mediaId}`,
              expiresAt: new Date(Date.now() + 300_000).toISOString(),
            })),
          },
        });
      }),

      http.get(
        `${API_BASE_URL}/rooms/:roomId/downloads/media/:mediaId`,
        () => new HttpResponse(new Blob(["bytes"]), { headers: { "Content-Type": contentType } }),
      ),
    );

    Object.defineProperty(navigator, "canShare", { value: () => true, configurable: true });
    Object.defineProperty(navigator, "share", {
      value: async ({ files }: { files: File[] }) => void shared.push(...files),
      configurable: true,
    });

    const outcome = await downloadMedia({
      roomId: MOCK_ROOM_ID,
      targets: [targetOf(5000)],
      mode: "share",
      token: TOKEN,
    });

    return { outcome, shared };
  };

  afterEach(() => {
    Object.defineProperty(navigator, "canShare", { value: undefined, configurable: true });
    Object.defineProperty(navigator, "share", { value: undefined, configurable: true });
  });

  it("스토리지가 이미지 MIME 을 내주면 그대로 넘긴다", async () => {
    const { outcome, shared } = await sharedFilesOf("image/jpeg");

    expect(outcome).toEqual({ type: "saved", savedCount: 1, failed: [] });
    expect(shared[0].type).toBe("image/jpeg");
    expect(shared[0].name).toBe("IMG_5000.jpg");
  });

  it("스토리지가 octet-stream 을 내줘도 목록이 알려준 MIME 으로 넘긴다", async () => {
    const { shared } = await sharedFilesOf("application/octet-stream");

    expect(shared[0].type).toBe("image/jpeg");
  });
});

/**
 * 같은 이름으로 올라간 사진을 함께 고른 경우. 서버가 `DownloadFileNames.deduplicate` 로
 * "이름 (1)" 을 붙여 내려주는데, 프론트가 목록의 이름을 그대로 쓰면 그 처리가 버려진다.
 * 개별 저장은 뒤엣것이 앞엣것을 덮고, 공유 시트에는 같은 이름 둘이 한 번에 넘어간다.
 */
describe("downloadMedia — 이름이 겹치는 사진", () => {
  const serveDuplicates = () =>
    server.use(
      http.post(`${API_BASE_URL}/rooms/:roomId/downloads/batch`, async ({ request, params }) => {
        const { mediaIds } = (await request.json()) as { mediaIds: number[] };

        return HttpResponse.json({
          data: {
            files: mediaIds.map((mediaId, index) => ({
              mediaId,
              // 서버가 정리해 준 이름. 둘째부터 " (1)" 이 붙는다.
              fileName: index === 0 ? "사진.jpg" : `사진 (${index}).jpg`,
              downloadUrl: `${API_BASE_URL}/rooms/${params.roomId}/downloads/media/${mediaId}`,
              expiresAt: new Date(Date.now() + 300_000).toISOString(),
            })),
          },
        });
      }),

      http.get(
        `${API_BASE_URL}/rooms/:roomId/downloads/media/:mediaId`,
        () => new HttpResponse(new Blob(["bytes"]), { headers: { "Content-Type": "image/jpeg" } }),
      ),
    );

  /** 목록에는 둘 다 같은 이름으로 있다. 서버가 준 이름을 써야만 갈린다. */
  const sameName = (mediaId: number): DownloadTarget => ({
    ...targetOf(mediaId),
    fileName: "사진.jpg",
  });

  it("개별 저장은 서버가 정리해 준 이름으로 저장한다", async () => {
    serveDuplicates();

    await run([sameName(5000), sameName(5001)], "individual");

    expect(saveBlobMock.mock.calls.map(([, name]) => name)).toEqual(["사진.jpg", "사진 (1).jpg"]);
  });
});
