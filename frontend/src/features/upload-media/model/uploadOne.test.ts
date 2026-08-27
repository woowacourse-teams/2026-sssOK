import { http, HttpResponse } from "msw";

import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { MOCK_R2_BASE_URL } from "@/mocks/handlers/upload";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { issueUploadUrls } from "../api/issueUploadUrls";
import { reissueUploadUrl } from "../api/reissueUploadUrl";
import { uploadOne } from "./uploadOne";

// 백오프를 실제로 기다리면 테스트 하나가 2초를 잡아먹는다. 기다림 자체는
// waitUnlessAborted.test.ts 에서 따로 확인한다.
jest.mock("../config", () => ({
  ...jest.requireActual("../config"),
  RETRY_BACKOFF_MS: [0, 0],
}));

const TOKEN = "mock-token-10234";

const imageFile = (fileName: string) => new File(["사진"], fileName, { type: "image/jpeg" });

const enterRoom = async () => {
  await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
};

const issueOne = async (fileName: string) => {
  await enterRoom();

  const { issued } = await issueUploadUrls(
    MOCK_ROOM_ID,
    { files: [{ fileName, mimeType: "image/jpeg", size: 1024 }] },
    TOKEN,
  );

  return issued[0];
};

/**
 * 스토리지 PUT 을 가로채 응답을 고정하고 호출 횟수를 센다.
 *
 * 목 표식(`__fail__`)은 최초 발급에만 걸려서 "계속 깨지는" 상황을 못 만든다.
 * 그리고 MSW 가 `XMLHttpRequest` 를 자기 클래스로 갈아끼우며 `send` 를 덮어써서,
 * 프로토타입 스파이로는 횟수를 셀 수 없다. 그래서 핸들러 쪽에서 센다.
 */
const interceptPut = (status: number) => {
  const puts = { count: 0 };

  server.use(
    http.put(`${MOCK_R2_BASE_URL}/*`, () => {
      puts.count += 1;

      return new HttpResponse(null, { status });
    }),
  );

  return puts;
};

const run = (issued: Awaited<ReturnType<typeof issueOne>>, signal?: AbortSignal) =>
  uploadOne({
    roomId: MOCK_ROOM_ID,
    token: TOKEN,
    issued,
    file: imageFile(issued.fileName),
    signal,
  });

describe("uploadOne", () => {
  it("한 번에 올라가면 mediaId 를 돌려준다", async () => {
    const issued = await issueOne("해변.jpg");

    expect(await run(issued)).toEqual({ ok: true, mediaId: issued.mediaId });
  });

  it("PUT 이 5xx 로 깨지면 새 URL 을 받아 다시 올린다", async () => {
    // 표식은 최초 발급에만 걸린다 — 재발급본은 멀쩡해서 재시도 성공까지 따라갈 수 있다.
    const issued = await issueOne("__fail__해변.jpg");

    expect(await run(issued)).toEqual({ ok: true, mediaId: issued.mediaId });
  });

  it("URL 이 이미 만료됐어도 재발급받아 올린다", async () => {
    const issued = await issueOne("__expired__해변.jpg");

    expect(await run(issued)).toEqual({ ok: true, mediaId: issued.mediaId });
  });

  it("계속 깨지면 최초 1번 + 재시도 2번까지만 하고 실패로 끝난다", async () => {
    const issued = await issueOne("해변.jpg");
    const puts = interceptPut(500);

    const result = await run(issued);

    expect(puts.count).toBe(3);
    expect(result).toEqual({
      ok: false,
      failure: expect.objectContaining({ mediaId: issued.mediaId, code: "UPLOAD_FAILED" }),
    });
  });

  it("서버 재발급 한도를 넘기면 그 자리에서 멈춘다", async () => {
    const issued = await issueOne("해변.jpg");

    // 서버 한도(5회)를 미리 소진시켜, 다음 재발급이 429 로 오게 만든다.
    for (let count = 0; count < 5; count += 1) {
      await reissueUploadUrl(MOCK_ROOM_ID, issued.mediaId, {}, TOKEN);
    }

    const puts = interceptPut(500);

    const result = await run(issued);

    // 429 를 받은 뒤로는 더 쏘지 않는다.
    expect(puts.count).toBe(1);
    expect(result).toEqual({
      ok: false,
      failure: expect.objectContaining({ code: "UPLOAD_RETRY_EXCEEDED" }),
    });
  });

  it("이미 중단된 뒤라면 요청을 보내지도 않는다", async () => {
    const issued = await issueOne("해변.jpg");
    const puts = interceptPut(200);

    const result = await run(issued, AbortSignal.abort());

    expect(puts.count).toBe(0);
    expect(result).toEqual({
      ok: false,
      failure: expect.objectContaining({ code: "UPLOAD_ABORTED" }),
    });
  });

  it("실패에 원본 File 을 실어 준다 — 재시도가 이걸 그대로 다시 올린다", async () => {
    const issued = await issueOne("해변.jpg");
    const file = imageFile("해변.jpg");
    interceptPut(500);

    const result = await uploadOne({ roomId: MOCK_ROOM_ID, token: TOKEN, issued, file });

    if (result.ok) throw new Error("PUT 을 500 으로 막았으니 실패해야 한다");

    expect(result.failure.file).toBe(file);
  });

  it("실패해도 던지지 않는다 — 나머지 파일이 계속돼야 한다", async () => {
    const issued = await issueOne("해변.jpg");
    interceptPut(500);

    await expect(run(issued)).resolves.toMatchObject({ ok: false });
  });
});
