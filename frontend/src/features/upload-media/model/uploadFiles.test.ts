import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { MOCK_R2_BASE_URL } from "@/mocks/handlers/upload";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { uploadFiles } from "./uploadFiles";

// 백오프를 실제로 기다리면 테스트마다 2초씩 잡아먹는다. 기다림 자체는 따로 확인한다.
jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  RETRY_BACKOFF_MS: [0, 0],
}));

const TOKEN = "mock-token-10234";

/** 내용 길이를 다르게 줘서, 어느 파일이 어느 URL 로 갔는지 바이트 수로 구별한다. */
const fileOf = (fileName: string, byteLength: number, type = "image/jpeg") =>
  new File(["x".repeat(byteLength)], fileName, { type });

const enterRoom = () =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  });

const run = (files: File[], options: Partial<Parameters<typeof uploadFiles>[0]> = {}) =>
  uploadFiles({ roomId: MOCK_ROOM_ID, token: TOKEN, files, ...options });

interface PutRecord {
  key: string;
  byteLength: number;
  headers: Record<string, string>;
}

/**
 * 스토리지 PUT 을 가로채 무엇이 어떻게 왔는지 기록한다.
 *
 * 가로채면 목이 업로드 바이트를 기록하지 못해 완료 등록이 `UPLOAD_NOT_COMPLETED` 로 떨어진다.
 * 그래서 이 헬퍼를 쓰는 검사는 등록 결과가 아니라 **PUT 자체**만 본다.
 */
const interceptPuts = ({
  statusOf = () => 200,
  delayMs = 0,
}: { statusOf?: (key: string) => number; delayMs?: number } = {}) => {
  const records: PutRecord[] = [];
  let running = 0;
  let peak = 0;

  server.use(
    http.put(`${MOCK_R2_BASE_URL}/*`, async ({ request }) => {
      running += 1;
      peak = Math.max(peak, running);

      const key = new URL(request.url).pathname;
      const body = await request.arrayBuffer();

      records.push({
        key,
        byteLength: body.byteLength,
        headers: Object.fromEntries(request.headers),
      });

      if (delayMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      }

      running -= 1;

      return new HttpResponse(null, { status: statusOf(key) });
    }),
  );

  return { records, peakConcurrency: () => peak };
};

/** 어떤 주소로 요청이 몇 번 나갔는지 센다. */
const countRequests = (match: string) => {
  const counter = { count: 0 };

  server.events.on("request:start", ({ request }) => {
    if (new URL(request.url).pathname.endsWith(match)) {
      counter.count += 1;
    }
  });

  return counter;
};

afterEach(() => server.events.removeAllListeners());

describe("uploadFiles", () => {
  it("파일이 몇 장이든 발급은 한 번만 부른다", async () => {
    await enterRoom();
    const issueRequests = countRequests("/media/upload-urls");

    await run([fileOf("첫째.jpg", 3), fileOf("둘째.jpg", 3), fileOf("셋째.jpg", 3)]);

    expect(issueRequests.count).toBe(1);
  });

  it("거절된 파일을 업로드가 끝나기 전에 먼저 알린다", async () => {
    await enterRoom();
    const onRejected = jest.fn();
    let putSeen = false;

    server.use(
      http.put(`${MOCK_R2_BASE_URL}/*`, () => {
        putSeen = true;

        return new HttpResponse(null, { status: 200 });
      }),
    );

    await run([fileOf("메모.txt", 3, "text/plain"), fileOf("첫째.jpg", 3)], {
      onRejected: (rejected) => {
        // 첫 PUT 이 나가기도 전에 불려야 한다.
        expect(putSeen).toBe(false);
        onRejected(rejected);
      },
    });

    expect(onRejected).toHaveBeenCalledWith([
      expect.objectContaining({ fileName: "메모.txt", code: "UNSUPPORTED_FILE_TYPE" }),
    ]);
  });

  it("발급을 통과한 목록을 첫 PUT 전에 한 번 알린다", async () => {
    await enterRoom();
    const onStarted = jest.fn();
    let putSeen = false;

    server.use(
      http.put(`${MOCK_R2_BASE_URL}/*`, () => {
        putSeen = true;

        return new HttpResponse(null, { status: 200 });
      }),
    );

    await run([fileOf("메모.txt", 3, "text/plain"), fileOf("첫째.jpg", 5)], {
      onStarted: (targets) => {
        // 진행 바의 분모가 잡히는 시점이다. 한 장이라도 나간 뒤면 늦다.
        expect(putSeen).toBe(false);
        onStarted(targets);
      },
    });

    // 거절된 메모.txt 는 빠지고, 올라갈 파일만 크기와 함께 온다.
    expect(onStarted).toHaveBeenCalledTimes(1);
    expect(onStarted).toHaveBeenCalledWith([
      expect.objectContaining({ fileName: "첫째.jpg", size: 5 }),
    ]);
  });

  it("PUT 이 끝난 파일만, 끝나는 대로 하나씩 알린다", async () => {
    await enterRoom();
    interceptPuts({ statusOf: (key) => (key.endsWith(".png") ? 500 : 200) });
    const onUploaded = jest.fn();

    await run([fileOf("첫째.jpg", 3), fileOf("둘째.png", 7, "image/png")], { onUploaded });

    // 재시도를 다 쓰고 실패한 파일은 세지 않는다. 등록을 기다리지도 않는다.
    expect(onUploaded).toHaveBeenCalledTimes(1);
    expect(onUploaded).toHaveBeenCalledWith(expect.objectContaining({ fileName: "첫째.jpg" }));
  });

  it("거절된 파일은 쏘지 않고 나머지만 올린다", async () => {
    await enterRoom();
    const { records } = interceptPuts();

    await run([
      fileOf("첫째.jpg", 3),
      fileOf("메모.txt", 5, "text/plain"),
      fileOf("셋째.png", 7, "image/png"),
    ]);

    // 어떤 파일이 어떤 발급에 붙었는지는 pairWithFiles.test.ts 가 직접 본다 —
    // jsdom 은 File 본문을 전송하지 않아 여기서는 바이트로 구별할 수 없다.
    expect(records).toHaveLength(2);
    expect(records.map((record) => record.key.slice(-4)).sort()).toEqual([".jpg", ".png"]);
  });

  it("발급받은 headers 를 그대로 싣고 토큰은 붙이지 않는다", async () => {
    await enterRoom();
    const { records } = interceptPuts();

    await run([fileOf("첫째.jpg", 3)]);

    expect(records[0].headers["content-type"]).toBe("image/jpeg");
    expect(records[0].headers.authorization).toBeUndefined();
  });

  it("동시에 올라가는 개수가 설정값을 넘지 않는다", async () => {
    await enterRoom();
    const { peakConcurrency } = interceptPuts({ delayMs: 5 });

    await run(Array.from({ length: 8 }, (_, index) => fileOf(`사진${index}.jpg`, 3)));

    expect(peakConcurrency()).toBe(3);
  });

  it("한 파일이 계속 깨져도 나머지는 끝까지 올라간다", async () => {
    await enterRoom();
    const { records } = interceptPuts({
      statusOf: (key) => (key.endsWith(".png") ? 500 : 200),
    });

    const result = await run([fileOf("첫째.jpg", 3), fileOf("둘째.png", 7, "image/png")]);

    // 깨진 파일만 재시도를 다 쓰고, 멀쩡한 파일은 한 번에 끝났다.
    expect(records.filter((record) => record.key.endsWith(".png"))).toHaveLength(3);
    expect(records.filter((record) => record.key.endsWith(".jpg"))).toHaveLength(1);
    expect(result.failed).toContainEqual(
      expect.objectContaining({ fileName: "둘째.png", code: "UPLOAD_FAILED" }),
    );
  });

  it("PUT 이 끝난 파일만 모아 등록하고 결과를 세 갈래로 돌려준다", async () => {
    await enterRoom();

    const result = await run([fileOf("첫째.jpg", 3), fileOf("메모.txt", 5, "text/plain")]);

    expect(result.registered).toHaveLength(1);
    expect(result.registered[0]).toMatchObject({ fileName: "첫째.jpg", status: "PROCESSING" });
    expect(result.failed).toEqual([]);
    expect(result.rejected).toHaveLength(1);
  });

  it("중단해도 이미 올라간 파일은 등록한다", async () => {
    await enterRoom();
    const controller = new AbortController();
    let putCount = 0;

    server.events.on("request:start", ({ request }) => {
      if (request.method === "PUT") {
        putCount += 1;
        // 첫 파일이 올라간 직후 취소를 누른 상황.
        if (putCount === 1) controller.abort();
      }
    });

    const result = await run(
      Array.from({ length: 5 }, (_, index) => fileOf(`사진${index}.jpg`, 3)),
      { signal: controller.signal },
    );

    // 목은 xhr.abort() 를 무시해서, 이미 출발한 PUT 은 끝까지 간다. 몇 장이 걸리는지는
    // 동시 실행 개수에 달렸으므로 장수를 못 박지 않고 갈라진 모양만 본다.
    expect(result.registered.length).toBeGreaterThan(0);
    expect(result.failed.length).toBeGreaterThan(0);
    expect(result.failed.every((failure) => failure.code === "UPLOAD_ABORTED")).toBe(true);
    expect(result.registered.length + result.failed.length).toBe(5);
  });

  it("등록이 이미 완료됐다고 답하면 실패로 세지 않는다", async () => {
    await enterRoom();

    server.use(
      http.post(`${API_BASE_URL}/rooms/:roomId/media`, () =>
        HttpResponse.json(
          {
            data: {
              registered: [],
              failed: [
                { mediaId: 5012, code: "UPLOAD_ALREADY_COMPLETED", message: "이미 등록됐습니다" },
              ],
            },
          },
          { status: 201 },
        ),
      ),
    );

    const result = await run([fileOf("첫째.jpg", 3)]);

    expect(result.failed).toEqual([]);
  });
});
