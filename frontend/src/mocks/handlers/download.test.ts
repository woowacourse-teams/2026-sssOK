import { http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";
import { server } from "../server";
import { MOCK_ROOM_ID } from "./room";

const TOKEN = "Bearer mock-token-10234";
const OTHER_TOKEN = "Bearer mock-token-99";

/** 다운로드는 참여자만 할 수 있다. 목은 입장한 방을 기억하므로 먼저 들어가야 한다. */
const join = (token = TOKEN) =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: token },
  });

const downloadOne = (mediaId: number, token: string | null = TOKEN) =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/downloads/media/${mediaId}`, {
    // 브라우저는 302 를 자동으로 따라가지만, 여기서는 Location 자체를 확인해야 한다.
    redirect: "manual",
    headers: token === null ? undefined : { Authorization: token },
  });

const createJob = (body: object, token = TOKEN) =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/downloads/zip`, {
    method: "POST",
    headers: { Authorization: token, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

const jobStatus = (jobId: string, token = TOKEN) =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/downloads/zip/${jobId}`, {
    headers: { Authorization: token },
  });

/**
 * 목 워커가 원본을 실제로 받아 zip 을 만든다. 테스트에서 바깥 망을 타면 안 되므로
 * 원본 자리에 작은 바이트를 세워둔다.
 */
const stubOriginals = () =>
  server.use(
    http.get("https://picsum.photos/*", () => HttpResponse.arrayBuffer(new ArrayBuffer(64))),
    http.get("https://cdn.example.com/*", () => HttpResponse.arrayBuffer(new ArrayBuffer(64))),
  );

/** READY 나 FAILED 가 될 때까지 기다린다. 목은 금방 끝나서 몇 번이면 충분하다. */
const waitForSettled = async (jobId: string) => {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const body = await (await jobStatus(jobId)).json();

    if (["READY", "FAILED", "EXPIRED"].includes(body.data.status)) {
      return body.data;
    }

    await new Promise((resolve) => setTimeout(resolve, 25));
  }

  throw new Error("압축 잡이 끝나지 않았다");
};

/** 갤러리 목록의 첫 이미지. B-6 대상으로 쓴다. */
const anImageId = async () => {
  const body = await (
    await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/media`, {
      headers: { Authorization: TOKEN },
    })
  ).json();

  return body.data.items.find((item: { type: string }) => item.type === "IMAGE").mediaId as number;
};

beforeEach(async () => {
  await join();
});

describe("GET /rooms/{roomId}/media/{mediaId}/download — 단건 다운로드 (B-6)", () => {
  it("바이트를 프록시하지 않고 302 로 스토리지를 가리킨다", async () => {
    const response = await downloadOne(await anImageId());

    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toEqual(expect.any(String));
  });

  it("서명 URL 에 원본 파일명을 담은 Content-Disposition 이 실린다", async () => {
    const location = (await downloadOne(await anImageId())).headers.get("Location") ?? "";
    const disposition = decodeURIComponent(
      new URL(location).searchParams.get("response-content-disposition") ?? "",
    );

    // ASCII 폴백과 RFC 5987 을 함께 넣는다 — 한쪽만 쓰면 브라우저별로 한글 이름이 깨진다.
    expect(disposition).toContain('attachment; filename="');
    expect(disposition).toContain("filename*=UTF-8''");
  });

  it("없는 미디어는 404 MEDIA_NOT_FOUND 다", async () => {
    const response = await downloadOne(999999);

    expect(response.status).toBe(404);
    expect((await response.json()).code).toBe("MEDIA_NOT_FOUND");
  });

  it("토큰이 없으면 401 이다", async () => {
    expect((await downloadOne(await anImageId(), null)).status).toBe(401);
  });
});

describe("POST /rooms/{roomId}/downloads — zip 요청 (B-7-1)", () => {
  it("압축을 기다리지 않고 202 와 잡 번호를 먼저 돌려준다", async () => {
    stubOriginals();

    const response = await createJob({ mediaIds: [await anImageId()] });
    const body = await response.json();

    expect(response.status).toBe(202);
    expect(body.data).toEqual(
      expect.objectContaining({
        jobId: expect.any(String),
        status: expect.any(String),
        mediaCount: 1,
        totalSize: expect.any(Number),
        // backend DownloadFileNames.zipNameOf 와 같은 형식이다.
        fileName: expect.stringMatching(/^ShareDrop_.+\.zip$/),
      }),
    );
  });

  it("mediaIds 와 folderId 를 함께 보내면 400 INVALID_PARAM 이다", async () => {
    const response = await createJob({ mediaIds: [await anImageId()], folderId: 31 });

    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("INVALID_PARAM");
  });

  it("mediaIds 가 1000개를 넘으면 400 TOO_MANY_FILES 다", async () => {
    const response = await createJob({ mediaIds: Array.from({ length: 1001 }, (_, i) => i) });

    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("TOO_MANY_FILES");
  });

  it("중복은 상한을 세기 전에 걷어낸다 — 같은 번호 1001개는 통과한다", async () => {
    stubOriginals();

    const mediaId = await anImageId();
    const response = await createJob({ mediaIds: Array.from({ length: 1001 }, () => mediaId) });

    expect(response.status).toBe(202);
    expect((await response.json()).data.mediaCount).toBe(1);
  });

  it("없는 폴더는 404 FOLDER_NOT_FOUND 다", async () => {
    const response = await createJob({ folderId: 999999 });

    expect(response.status).toBe(404);
    expect((await response.json()).code).toBe("FOLDER_NOT_FOUND");
  });

  it("유효한 대상이 하나도 없으면 404 MEDIA_NOT_FOUND 다", async () => {
    const response = await createJob({ mediaIds: [111, 222] });

    expect(response.status).toBe(404);
    expect((await response.json()).code).toBe("MEDIA_NOT_FOUND");
  });
});

describe("GET /rooms/{roomId}/downloads/zip/{jobId} — 상태 조회 (B-7-2)", () => {
  it("READY 가 되면 downloadUrl 과 expiresAt 이 채워진다", async () => {
    stubOriginals();

    const { data: job } = await (await createJob({ mediaIds: [await anImageId()] })).json();
    const settled = await waitForSettled(job.jobId);

    expect(settled.status).toBe("READY");
    expect(settled.progress).toBe(100);
    expect(settled.downloadUrl).toEqual(expect.any(String));
    expect(settled.expiresAt).toEqual(expect.any(String));
    expect(settled.failureReason).toBeNull();
  });

  it("READY 전에는 downloadUrl 이 null 이다 — 미리 받아가지 못한다", async () => {
    stubOriginals();

    const { data: job } = await (await createJob({ mediaIds: [await anImageId()] })).json();
    const first = await (await jobStatus(job.jobId)).json();

    if (first.data.status !== "READY") {
      expect(first.data.downloadUrl).toBeNull();
      expect(first.data.expiresAt).toBeNull();
    }

    await waitForSettled(job.jobId);
  });

  it("남이 만든 잡은 403 이다", async () => {
    stubOriginals();

    const { data: job } = await (await createJob({ mediaIds: [await anImageId()] })).json();

    await join(OTHER_TOKEN);

    const response = await jobStatus(job.jobId, OTHER_TOKEN);

    expect(response.status).toBe(403);
    expect((await response.json()).code).toBe("FORBIDDEN");

    await waitForSettled(job.jobId);
  });

  it("없는 잡 번호는 404 DOWNLOAD_NOT_FOUND 다", async () => {
    const response = await jobStatus("dl_없는번호");

    expect(response.status).toBe(404);
    expect((await response.json()).code).toBe("DOWNLOAD_NOT_FOUND");
  });
});

describe("압축 결과", () => {
  it("downloadUrl 로 실제 zip 을 받을 수 있다", async () => {
    stubOriginals();

    const { data: job } = await (await createJob({ mediaIds: [await anImageId()] })).json();
    const settled = await waitForSettled(job.jobId);
    const zip = new Uint8Array(await (await fetch(settled.downloadUrl)).arrayBuffer());

    // local file header 로 시작해야 압축 도구가 연다.
    expect(new DataView(zip.buffer).getUint32(0, true)).toBe(0x04034b50);
  });

  it("받지 못한 원본은 빼고 나머지로 묶는다", async () => {
    // 영상 원본만 실패하게 둔다. 이미지만 zip 에 들어가야 한다.
    server.use(
      http.get("https://picsum.photos/*", () => HttpResponse.arrayBuffer(new ArrayBuffer(64))),
      http.get("https://cdn.example.com/*", () => HttpResponse.error()),
    );

    const items = await (
      await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/media`, {
        headers: { Authorization: TOKEN },
      })
    ).json();

    const { data: job } = await (
      await createJob({
        mediaIds: items.data.items.map((item: { mediaId: number }) => item.mediaId),
      })
    ).json();
    const settled = await waitForSettled(job.jobId);

    // 대상 수는 고른 그대로지만, 실제로 담긴 것은 받아진 것뿐이다.
    expect(settled.status).toBe("READY");
    expect(settled.mediaCount).toBe(items.data.items.length);
  });
});
