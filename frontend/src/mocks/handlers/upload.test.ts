import { API_BASE_URL } from "@/shared/config";
import { MOCK_ROOM_ID } from "./room";
import { MOCK_R2_BASE_URL, UPLOAD_MOCK_MARKERS } from "./upload";

const AUTH_HEADERS = {
  "Content-Type": "application/json",
  Authorization: "Bearer mock-token-10234",
};

/** 이름·크기만 다르게 준 요청 파일. 목은 확장자와 크기만 본다. */
const file = (fileName: string, size = 2 * 1024 * 1024, contentType = "image/jpeg") => ({
  fileName,
  contentType,
  size,
});

interface IssueOptions {
  headers?: Record<string, string>;
  roomId?: number;
}

const issueUploadUrls = (
  files: ReturnType<typeof file>[],
  { headers = AUTH_HEADERS, roomId = MOCK_ROOM_ID }: IssueOptions = {},
) =>
  fetch(`${API_BASE_URL}/rooms/${roomId}/media/upload-urls`, {
    method: "POST",
    headers,
    body: JSON.stringify({ folderId: null, files }),
  });

const issueOne = async (target = file("제주-해변.jpg")) => {
  const body = await (await issueUploadUrls([target])).json();

  return body.data[0] as { uploadUrl: string; storageKey: string; contentType: string };
};

/** 헤더를 넘기지 않으면 Content-Type 없이 나간다 — 헤더 생략 자체를 확인할 수 있어야 한다. */
const putToR2 = (uploadUrl: string, headers: Record<string, string> = {}) =>
  fetch(uploadUrl, { method: "PUT", headers, body: new Uint8Array([1, 2, 3]) });

const completeUpload = (storageKeys: string[], headers: Record<string, string> = AUTH_HEADERS) =>
  fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/media/complete`, {
    method: "POST",
    headers,
    body: JSON.stringify({ storageKeys }),
  });

describe("POST /rooms/{roomId}/media/upload-urls 목 핸들러", () => {
  it("요청한 파일 수만큼 uploadUrl·storageKey·contentType 을 내려준다", async () => {
    const response = await issueUploadUrls([file("한라산.jpg"), file("성산일출봉.png")]);
    const body = await response.json();

    expect(response.status).toBe(201);
    expect(body.data).toHaveLength(2);
    body.data.forEach((issued: Record<string, unknown>) => {
      expect(issued.uploadUrl).toEqual(expect.stringContaining(MOCK_R2_BASE_URL));
      expect(issued.storageKey).toEqual(expect.any(String));
      expect(issued.contentType).toEqual(expect.any(String));
    });
  });

  it("contentType 은 확장자로 정한다 — 요청에 실린 값은 쓰지 않는다", async () => {
    const issued = await issueOne(file("성산일출봉.png", 1024, "application/octet-stream"));

    expect(issued.contentType).toBe("image/png");
  });

  it("허용하지 않는 확장자는 400 UNSUPPORTED_MEDIA_TYPE 으로 응답한다", async () => {
    const response = await issueUploadUrls([file("아이폰-원본.heic")]);
    const body = await response.json();

    expect(response.status).toBe(400);
    expect(body.code).toBe("UNSUPPORTED_MEDIA_TYPE");
  });

  it("확장자가 아예 없는 이름도 400 UNSUPPORTED_MEDIA_TYPE 으로 응답한다", async () => {
    const response = await issueUploadUrls([file("확장자없음")]);

    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("UNSUPPORTED_MEDIA_TYPE");
  });

  it("사진이 10MB 를 넘으면 413 FILE_TOO_LARGE 로 응답한다", async () => {
    const response = await issueUploadUrls([file("원본.jpg", 10 * 1024 * 1024 + 1)]);
    const body = await response.json();

    expect(response.status).toBe(413);
    expect(body.code).toBe("FILE_TOO_LARGE");
  });

  it("영상이 1GB 를 넘으면 413 FILE_TOO_LARGE 로 응답한다", async () => {
    const response = await issueUploadUrls([file("행사.mp4", 1024 * 1024 * 1024 + 1)]);
    const body = await response.json();

    expect(response.status).toBe(413);
    expect(body.code).toBe("FILE_TOO_LARGE");
  });

  it("사진 10MB·영상 1GB 까지는 그대로 발급한다", async () => {
    const response = await issueUploadUrls([
      file("딱-맞는-사진.jpg", 10 * 1024 * 1024),
      file("딱-맞는-영상.mp4", 1024 * 1024 * 1024),
    ]);

    expect(response.status).toBe(201);
    expect((await response.json()).data).toHaveLength(2);
  });

  it("한 파일이라도 걸리면 나머지도 발급하지 않는다", async () => {
    await issueUploadUrls([file("한라산.jpg"), file("아이폰-원본.heic")]);

    // 앞 파일까지 발급됐다면 확정이 400(업로드 미완료)으로 갈렸을 것이다
    const response = await completeUpload(["rooms/5031/mock-upload-1.jpg"]);

    expect(response.status).toBe(404);
  });

  it("인증 없이 발급을 요청하면 401 로 거절한다", async () => {
    const response = await issueUploadUrls([file("한라산.jpg")], {
      headers: { "Content-Type": "application/json" },
    });

    expect(response.status).toBe(401);
  });

  it("존재하지 않는 방 번호로 발급을 요청하면 404 로 거절한다", async () => {
    const response = await issueUploadUrls([file("한라산.jpg")], { roomId: 99999 });

    expect(response.status).toBe(404);
  });
});

describe("PUT presigned URL 목 핸들러", () => {
  it("발급값과 같은 Content-Type 으로 PUT 하면 200 이다", async () => {
    const issued = await issueOne();
    const response = await putToR2(issued.uploadUrl, { "Content-Type": issued.contentType });

    expect(response.status).toBe(200);
  });

  it("Content-Type 이 발급값과 다르면 403 이다", async () => {
    const issued = await issueOne();
    const response = await putToR2(issued.uploadUrl, {
      "Content-Type": "application/octet-stream",
    });

    expect(response.status).toBe(403);
  });

  it("Content-Type 헤더를 빼면 403 이다", async () => {
    const issued = await issueOne();
    const response = await putToR2(issued.uploadUrl);

    expect(response.status).toBe(403);
  });

  it("만료된 URL 로 PUT 하면 403 이다", async () => {
    const issued = await issueOne(file(`제주-해변${UPLOAD_MOCK_MARKERS.expiredUrl}.jpg`));
    const response = await putToR2(issued.uploadUrl, { "Content-Type": issued.contentType });

    expect(response.status).toBe(403);
  });

  it("발급한 적 없는 storageKey 로 PUT 하면 403 이다", async () => {
    const issued = await issueOne();
    const unknownUrl = issued.uploadUrl.replace(issued.storageKey, "rooms/5031/없는키.jpg");
    const response = await putToR2(unknownUrl, { "Content-Type": issued.contentType });

    expect(response.status).toBe(403);
  });

  it("Authorization 을 함께 보내면 403 이다 — presigned URL 은 서명이 이미 실려 있다", async () => {
    const issued = await issueOne();
    const response = await putToR2(issued.uploadUrl, {
      "Content-Type": issued.contentType,
      Authorization: "Bearer mock-token-10234",
    });

    expect(response.status).toBe(403);
  });

  it("실패 표식이 든 이름은 PUT 이 500 으로 실패한다", async () => {
    const issued = await issueOne(file(`제주-해변${UPLOAD_MOCK_MARKERS.putFailure}.jpg`));
    const response = await putToR2(issued.uploadUrl, { "Content-Type": issued.contentType });

    expect(response.status).toBe(500);
  });
});

describe("POST /rooms/{roomId}/media/complete 목 핸들러", () => {
  it("PUT 까지 끝난 파일은 완료 확정이 성공한다", async () => {
    const issued = await issueOne();

    await putToR2(issued.uploadUrl, { "Content-Type": issued.contentType });

    const response = await completeUpload([issued.storageKey]);
    const body = await response.json();

    expect(response.status).toBe(201);
    expect(body.data).toHaveLength(1);
    expect(body.data[0].storageKey).toBe(issued.storageKey);
    expect(body.data[0].status).toBe("COMPLETED");
  });

  it("PUT 이 끝나지 않은 storageKey 로 확정하면 400 UPLOAD_NOT_COMPLETED 로 거절한다", async () => {
    const issued = await issueOne();
    const response = await completeUpload([issued.storageKey]);
    const body = await response.json();

    expect(response.status).toBe(400);
    expect(body.code).toBe("UPLOAD_NOT_COMPLETED");
  });

  it("PUT 이 500 으로 실패한 파일도 확정할 수 없다", async () => {
    const issued = await issueOne(file(`제주-해변${UPLOAD_MOCK_MARKERS.putFailure}.jpg`));

    await putToR2(issued.uploadUrl, { "Content-Type": issued.contentType });

    const response = await completeUpload([issued.storageKey]);

    expect(response.status).toBe(400);
  });

  it("한 건이라도 끝나지 않았으면 배치 전체를 거절한다", async () => {
    const body = await (await issueUploadUrls([file("한라산.jpg"), file("성산일출봉.png")])).json();
    const [first, second] = body.data as {
      uploadUrl: string;
      storageKey: string;
      contentType: string;
    }[];

    await putToR2(first.uploadUrl, { "Content-Type": first.contentType });

    const response = await completeUpload([first.storageKey, second.storageKey]);

    expect(response.status).toBe(400);
  });

  it("발급한 적 없는 storageKey 로 확정하면 404 로 거절한다", async () => {
    const response = await completeUpload(["rooms/5031/없는키.jpg"]);
    const body = await response.json();

    expect(response.status).toBe(404);
    expect(body.code).toBe("STORED_FILE_NOT_FOUND");
  });

  it("인증 없이 확정하면 401 로 거절한다", async () => {
    const response = await completeUpload(["rooms/5031/mock-upload-1.jpg"], {
      "Content-Type": "application/json",
    });

    expect(response.status).toBe(401);
  });
});
