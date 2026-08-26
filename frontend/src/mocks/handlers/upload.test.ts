import { API_BASE_URL } from "@/shared/config";
import { MOCK_ROOM_CODES, MOCK_ROOM_ID } from "./room";
import { MOCK_FOLDER_IDS, MOCK_R2_BASE_URL, UPLOAD_MOCK_MARKERS } from "./upload";

/** 방장 토큰. 방 목 데이터의 hostId 와 같은 회원이다. */
const HOST_TOKEN = "Bearer mock-token-10234";
/** 방장이 아닌 참여자 토큰. */
const GUEST_TOKEN = "Bearer mock-token-10235";

const jsonHeaders = (token: string) => ({
  "Content-Type": "application/json",
  Authorization: token,
});

interface Issued {
  mediaId: number;
  fileName: string;
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  expiresIn: number;
}

/** 이름·크기만 다르게 준 요청 파일. 목은 확장자와 크기만 본다. */
const file = (fileName: string, size = 2 * 1024 * 1024, mimeType = "image/jpeg") => ({
  fileName,
  mimeType,
  size,
});

const roomIdOf = async (code: string) =>
  (await (await fetch(`${API_BASE_URL}/rooms/${code}`)).json()).data.roomId as number;

/** 업로드는 참여자만 부를 수 있어서 모든 흐름이 입장부터 시작한다. */
const enterRoom = async (roomId = MOCK_ROOM_ID, token = HOST_TOKEN) => {
  await fetch(`${API_BASE_URL}/rooms/${roomId}/members`, {
    method: "POST",
    headers: { Authorization: token },
  });

  return roomId;
};

interface IssueOptions {
  token?: string;
  roomId?: number;
  folderIds?: number[];
}

const issueUploadUrls = (
  files: ReturnType<typeof file>[],
  { token = HOST_TOKEN, roomId = MOCK_ROOM_ID, folderIds }: IssueOptions = {},
) =>
  fetch(`${API_BASE_URL}/rooms/${roomId}/media/upload-urls`, {
    method: "POST",
    headers: jsonHeaders(token),
    body: JSON.stringify(folderIds === undefined ? { files } : { files, folderIds }),
  });

const issueOne = async (target = file("제주-해변.jpg"), options: IssueOptions = {}) => {
  const body = await (await issueUploadUrls([target], options)).json();

  return body.data.issued[0] as Issued;
};

const BYTES = new Uint8Array([1, 2, 3]);

/** headers 를 넘기지 않으면 발급받은 헤더를 그대로 싣는다 — 정상 흐름이 기본값이다. */
const putToR2 = (issued: Issued, headers: Record<string, string> = issued.headers, body = BYTES) =>
  fetch(issued.uploadUrl, { method: "PUT", headers, body: body as BodyInit | null });

const registerMedia = (mediaIds: number[], token = HOST_TOKEN, roomId = MOCK_ROOM_ID) =>
  fetch(`${API_BASE_URL}/rooms/${roomId}/media`, {
    method: "POST",
    headers: jsonHeaders(token),
    body: JSON.stringify({ mediaIds }),
  });

const reissueUploadUrl = (
  mediaId: number,
  {
    token = HOST_TOKEN,
    roomId = MOCK_ROOM_ID,
    size,
  }: { token?: string; roomId?: number; size?: number } = {},
) =>
  fetch(`${API_BASE_URL}/rooms/${roomId}/media/${mediaId}/upload-url`, {
    method: "POST",
    headers: jsonHeaders(token),
    body: size === undefined ? undefined : JSON.stringify({ size }),
  });

/** 발급받은 적 없는 키를 가리키는 URL. 쿼리(서명·만료)는 진짜 것을 그대로 쓴다. */
const withUnknownKey = (uploadUrl: string) =>
  uploadUrl.replace(/\/rooms\/\d+\/[^?]+/, `/rooms/${MOCK_ROOM_ID}/발급한적없음.jpg`);

beforeEach(async () => {
  await enterRoom();
});

describe("POST /rooms/{roomId}/media/upload-urls — 업로드 URL 발급", () => {
  it("응답은 data 로 한 겹 감싸여 issued·rejected 로 나뉘어 온다", async () => {
    const response = await issueUploadUrls([file("한라산.jpg")]);
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body).toHaveProperty("data");
    expect(body.data).toHaveProperty("issued");
    expect(body.data).toHaveProperty("rejected");
  });

  it("발급 항목은 mediaId·uploadUrl·method·headers·expiresIn 을 갖는다", async () => {
    const issued = await issueOne();

    expect(issued.mediaId).toEqual(expect.any(Number));
    expect(issued.uploadUrl).toEqual(expect.stringContaining(MOCK_R2_BASE_URL));
    expect(issued.method).toBe("PUT");
    expect(issued.headers).toEqual({ "Content-Type": "image/jpeg" });
    expect(issued.expiresIn).toBe(600);
  });

  it("요청한 파일 수만큼, 요청 순서 그대로 발급한다", async () => {
    const body = await (await issueUploadUrls([file("한라산.jpg"), file("성산일출봉.png")])).json();

    expect(body.data.issued).toHaveLength(2);
    expect(body.data.issued.map((issued: Issued) => issued.fileName)).toEqual([
      "한라산.jpg",
      "성산일출봉.png",
    ]);
  });

  it("mediaId 는 파일마다 다르다", async () => {
    const body = await (await issueUploadUrls([file("한라산.jpg"), file("성산일출봉.png")])).json();
    const mediaIds = body.data.issued.map((issued: Issued) => issued.mediaId);

    expect(new Set(mediaIds).size).toBe(2);
  });

  it("타입은 확장자로 정한다 — 요청에 실린 mimeType 은 쓰지 않는다", async () => {
    const issued = await issueOne(file("성산일출봉.png", 1024, "application/octet-stream"));

    expect(issued.headers["Content-Type"]).toBe("image/png");
  });

  it("허용하지 않는 확장자는 rejected 로 갈리고 나머지는 그대로 발급된다", async () => {
    const body = await (
      await issueUploadUrls([file("한라산.jpg"), file("아이폰-원본.heic")])
    ).json();

    expect(body.data.issued).toHaveLength(1);
    expect(body.data.rejected).toEqual([
      {
        fileName: "아이폰-원본.heic",
        code: "UNSUPPORTED_MEDIA_TYPE",
        message: expect.any(String),
      },
    ]);
  });

  it("확장자가 아예 없는 이름도 rejected UNSUPPORTED_MEDIA_TYPE 이다", async () => {
    const body = await (await issueUploadUrls([file("확장자없음")])).json();

    expect(body.data.issued).toHaveLength(0);
    expect(body.data.rejected[0].code).toBe("UNSUPPORTED_MEDIA_TYPE");
  });

  it("사진이 10MB 를 넘으면 rejected FILE_TOO_LARGE 다", async () => {
    const body = await (await issueUploadUrls([file("원본.jpg", 10 * 1024 * 1024 + 1)])).json();

    expect(body.data.rejected[0].code).toBe("FILE_TOO_LARGE");
  });

  it("영상이 1GB 를 넘으면 rejected FILE_TOO_LARGE 다", async () => {
    const body = await (await issueUploadUrls([file("행사.mp4", 1024 * 1024 * 1024 + 1)])).json();

    expect(body.data.rejected[0].code).toBe("FILE_TOO_LARGE");
  });

  it("사진 10MB·영상 1GB 까지는 그대로 발급한다", async () => {
    const body = await (
      await issueUploadUrls([
        file("딱-맞는-사진.jpg", 10 * 1024 * 1024),
        file("딱-맞는-영상.mp4", 1024 * 1024 * 1024),
      ])
    ).json();

    expect(body.data.issued).toHaveLength(2);
    expect(body.data.rejected).toHaveLength(0);
  });

  it("크기가 0 이하인 파일은 rejected INVALID_PARAM 이다", async () => {
    const body = await (await issueUploadUrls([file("빈파일.jpg", 0)])).json();

    expect(body.data.rejected[0].code).toBe("INVALID_PARAM");
  });

  it("files 가 비어 있으면 요청 전체가 400 INVALID_PARAM 이다", async () => {
    const response = await issueUploadUrls([]);

    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("INVALID_PARAM");
  });

  it("아는 폴더로 발급하면 통과한다", async () => {
    const response = await issueUploadUrls([file("한라산.jpg")], {
      folderIds: [MOCK_FOLDER_IDS[0]],
    });

    expect(response.status).toBe(200);
  });

  it("없는 폴더로 발급하면 404 FOLDER_NOT_FOUND 다", async () => {
    const response = await issueUploadUrls([file("한라산.jpg")], { folderIds: [9999] });

    expect(response.status).toBe(404);
    expect((await response.json()).code).toBe("FOLDER_NOT_FOUND");
  });

  it("인증 없이 부르면 401 이다", async () => {
    const response = await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/media/upload-urls`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ files: [file("한라산.jpg")] }),
    });

    expect(response.status).toBe(401);
  });

  it("존재하지 않는 방이면 404 ROOM_NOT_FOUND 다", async () => {
    const response = await issueUploadUrls([file("한라산.jpg")], { roomId: 99999 });

    expect(response.status).toBe(404);
    expect((await response.json()).code).toBe("ROOM_NOT_FOUND");
  });

  it("만료된 방이면 410 ROOM_EXPIRED 다", async () => {
    const roomId = await enterRoom(await roomIdOf(MOCK_ROOM_CODES.expired));

    const response = await issueUploadUrls([file("한라산.jpg")], { roomId });

    expect(response.status).toBe(410);
    expect((await response.json()).code).toBe("ROOM_EXPIRED");
  });

  it("삭제된 방이면 410 ROOM_ALREADY_DELETED 다", async () => {
    const roomId = await enterRoom(await roomIdOf(MOCK_ROOM_CODES.deleted));

    const response = await issueUploadUrls([file("한라산.jpg")], { roomId });

    expect(response.status).toBe(410);
    expect((await response.json()).code).toBe("ROOM_ALREADY_DELETED");
  });

  it("입장하지 않은 방이면 403 ROOM_MEMBERSHIP_REQUIRED 다", async () => {
    const roomId = await roomIdOf(MOCK_ROOM_CODES.second);

    const response = await issueUploadUrls([file("한라산.jpg")], { roomId });

    expect(response.status).toBe(403);
    expect((await response.json()).code).toBe("ROOM_MEMBERSHIP_REQUIRED");
  });

  it("방장만 올릴 수 있는 방에 참여자가 부르면 403 UPLOAD_NOT_ALLOWED 다", async () => {
    const roomId = await enterRoom(await roomIdOf(MOCK_ROOM_CODES.hostOnly), GUEST_TOKEN);

    const response = await issueUploadUrls([file("한라산.jpg")], { roomId, token: GUEST_TOKEN });

    expect(response.status).toBe(403);
    expect((await response.json()).code).toBe("UPLOAD_NOT_ALLOWED");
  });

  it("방장만 올릴 수 있는 방이어도 방장은 발급받는다", async () => {
    const roomId = await enterRoom(await roomIdOf(MOCK_ROOM_CODES.hostOnly));

    const response = await issueUploadUrls([file("한라산.jpg")], { roomId });

    expect(response.status).toBe(200);
  });
});

describe("스토리지 PUT", () => {
  it("발급받은 headers 를 그대로 실으면 200 이다", async () => {
    const issued = await issueOne();

    expect((await putToR2(issued)).status).toBe(200);
  });

  it("Content-Type 이 발급값과 다르면 403 이다", async () => {
    const issued = await issueOne();

    const response = await putToR2(issued, { "Content-Type": "application/octet-stream" });

    expect(response.status).toBe(403);
  });

  it("Content-Type 헤더를 빼면 403 이다", async () => {
    const issued = await issueOne();

    expect((await putToR2(issued, {})).status).toBe(403);
  });

  it("만료된 URL 로 PUT 하면 403 이다", async () => {
    const issued = await issueOne(file(`제주-해변${UPLOAD_MOCK_MARKERS.expiredUrl}.jpg`));

    expect((await putToR2(issued)).status).toBe(403);
  });

  it("발급받은 적 없는 키로 PUT 하면 403 이다", async () => {
    const issued = await issueOne();

    const response = await putToR2({ ...issued, uploadUrl: withUnknownKey(issued.uploadUrl) });

    expect(response.status).toBe(403);
  });

  it("Authorization 을 함께 보내면 403 이다 — presigned URL 은 서명이 이미 실려 있다", async () => {
    const issued = await issueOne();

    const response = await putToR2(issued, { ...issued.headers, Authorization: HOST_TOKEN });

    expect(response.status).toBe(403);
  });

  it("실패 표식이 든 이름은 PUT 이 500 으로 깨진다", async () => {
    const issued = await issueOne(file(`제주-해변${UPLOAD_MOCK_MARKERS.putFailure}.jpg`));

    expect((await putToR2(issued)).status).toBe(500);
  });
});

describe("POST /rooms/{roomId}/media — 완료 등록", () => {
  it("PUT 까지 끝난 미디어는 201 로 등록된다", async () => {
    const issued = await issueOne();

    await putToR2(issued);

    const response = await registerMedia([issued.mediaId]);
    const body = await response.json();

    expect(response.status).toBe(201);
    expect(body.data.registered).toHaveLength(1);
    expect(body.data.failed).toHaveLength(0);
    expect(body.data.registered[0].status).toBe("PROCESSING");
  });

  it("등록된 미디어는 공통 Media 객체 모양을 갖는다", async () => {
    const issued = await issueOne(file("한라산.jpg"), { folderIds: [MOCK_FOLDER_IDS[0]] });

    await putToR2(issued);

    const body = await (await registerMedia([issued.mediaId])).json();

    expect(body.data.registered[0]).toEqual(
      expect.objectContaining({
        mediaId: issued.mediaId,
        type: "IMAGE",
        fileName: "한라산.jpg",
        mimeType: "image/jpeg",
        folderIds: [MOCK_FOLDER_IDS[0]],
        uploaderId: 10234,
        // 워커가 만드는 값이라 PROCESSING 동안은 비어 있다
        thumbnailUrl: null,
        originalUrl: null,
      }),
    );
  });

  it("영상은 type 이 VIDEO 이고 duration 이 실린다", async () => {
    const issued = await issueOne(file("행사.mp4", 1024, "video/mp4"));

    await putToR2(issued);

    const body = await (await registerMedia([issued.mediaId])).json();

    expect(body.data.registered[0].type).toBe("VIDEO");
    expect(body.data.registered[0].duration).toEqual(expect.any(Number));
  });

  it("uploaderName 은 인증할 때 준 이름을 따른다", async () => {
    const auth = await (
      await fetch(`${API_BASE_URL}/auth/anonymous`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ nickname: "로지" }),
      })
    ).json();
    const token = `Bearer ${auth.data.accessToken}`;

    await enterRoom(MOCK_ROOM_ID, token);

    const issued = await issueOne(file("한라산.jpg"), { token });

    await putToR2(issued);

    const body = await (await registerMedia([issued.mediaId], token)).json();

    expect(body.data.registered[0].uploaderName).toBe("로지");
  });

  it("PUT 이 끝나지 않은 미디어는 failed UPLOAD_NOT_COMPLETED 다", async () => {
    const issued = await issueOne();

    const body = await (await registerMedia([issued.mediaId])).json();

    expect(body.data.registered).toHaveLength(0);
    expect(body.data.failed[0]).toEqual(
      expect.objectContaining({ mediaId: issued.mediaId, code: "UPLOAD_NOT_COMPLETED" }),
    );
  });

  it("0바이트로 PUT 한 미디어도 failed UPLOAD_NOT_COMPLETED 다", async () => {
    const issued = await issueOne();

    // 스토리지는 빈 PUT 도 200 을 주고 0바이트 객체를 만든다. 등록에서 걸러야 한다.
    expect((await fetch(issued.uploadUrl, { method: "PUT", headers: issued.headers })).status).toBe(
      200,
    );

    const body = await (await registerMedia([issued.mediaId])).json();

    expect(body.data.failed[0].code).toBe("UPLOAD_NOT_COMPLETED");
  });

  it("PUT 이 500 으로 깨진 미디어도 등록되지 않는다", async () => {
    const issued = await issueOne(file(`제주-해변${UPLOAD_MOCK_MARKERS.putFailure}.jpg`));

    await putToR2(issued);

    const body = await (await registerMedia([issued.mediaId])).json();

    expect(body.data.failed[0].code).toBe("UPLOAD_NOT_COMPLETED");
  });

  it("없는 mediaId 는 failed MEDIA_NOT_FOUND 다", async () => {
    const body = await (await registerMedia([999999])).json();

    expect(body.data.failed[0].code).toBe("MEDIA_NOT_FOUND");
  });

  it("성공과 실패가 섞이면 registered·failed 로 나뉘어 함께 온다", async () => {
    const body = await (await issueUploadUrls([file("한라산.jpg"), file("성산일출봉.png")])).json();
    const [first, second] = body.data.issued as Issued[];

    await putToR2(first);

    const registerBody = await (await registerMedia([first.mediaId, second.mediaId])).json();

    expect(registerBody.data.registered.map((m: { mediaId: number }) => m.mediaId)).toEqual([
      first.mediaId,
    ]);
    expect(registerBody.data.failed.map((f: { mediaId: number }) => f.mediaId)).toEqual([
      second.mediaId,
    ]);
  });

  it("이미 등록한 미디어를 다시 등록하면 failed UPLOAD_ALREADY_COMPLETED 다", async () => {
    const issued = await issueOne();

    await putToR2(issued);
    await registerMedia([issued.mediaId]);

    const body = await (await registerMedia([issued.mediaId])).json();

    expect(body.data.failed[0].code).toBe("UPLOAD_ALREADY_COMPLETED");
  });

  it("남이 발급받은 mediaId 가 섞이면 요청 전체가 403 MEDIA_FORBIDDEN 이다", async () => {
    const issued = await issueOne();

    await enterRoom(MOCK_ROOM_ID, GUEST_TOKEN);

    const response = await registerMedia([issued.mediaId], GUEST_TOKEN);

    expect(response.status).toBe(403);
    expect((await response.json()).code).toBe("MEDIA_FORBIDDEN");
  });

  it("mediaIds 가 비어 있으면 400 INVALID_PARAM 이다", async () => {
    const response = await registerMedia([]);

    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("INVALID_PARAM");
  });
});

describe("POST /rooms/{roomId}/media/{mediaId}/upload-url — 재발급", () => {
  it("만료로 깨진 뒤 재발급받아 올리면 등록까지 성공한다", async () => {
    const issued = await issueOne(file(`제주-해변${UPLOAD_MOCK_MARKERS.expiredUrl}.jpg`));

    expect((await putToR2(issued)).status).toBe(403);

    const reissued = (await (await reissueUploadUrl(issued.mediaId)).json()).data as Issued;

    expect((await putToR2(reissued)).status).toBe(200);

    const body = await (await registerMedia([issued.mediaId])).json();

    expect(body.data.registered).toHaveLength(1);
  });

  it("PUT 이 500 으로 깨진 미디어도 재발급받으면 성공한다 — 표식은 최초 발급에만 걸린다", async () => {
    const issued = await issueOne(file(`제주-해변${UPLOAD_MOCK_MARKERS.putFailure}.jpg`));

    expect((await putToR2(issued)).status).toBe(500);

    const reissued = (await (await reissueUploadUrl(issued.mediaId)).json()).data as Issued;

    expect((await putToR2(reissued)).status).toBe(200);
  });

  it("mediaId 는 그대로고 업로드 주소만 새로 나온다", async () => {
    const issued = await issueOne();

    const body = await (await reissueUploadUrl(issued.mediaId)).json();

    expect(body.data.mediaId).toBe(issued.mediaId);
    expect(body.data.fileName).toBe(issued.fileName);
    expect(body.data.uploadUrl).not.toBe(issued.uploadUrl);
  });

  it("옛 URL 로 뒤늦게 PUT 해도 재발급본을 덮지 않는다", async () => {
    const issued = await issueOne();
    const reissued = (await (await reissueUploadUrl(issued.mediaId)).json()).data as Issued;

    // 스토리지는 아직 유효한 서명이라 받아준다. 다만 고아 객체가 될 뿐이다.
    expect((await putToR2(issued)).status).toBe(200);

    const stale = await (await registerMedia([issued.mediaId])).json();

    expect(stale.data.failed[0].code).toBe("UPLOAD_NOT_COMPLETED");

    await putToR2(reissued);

    const fresh = await (await registerMedia([issued.mediaId])).json();

    expect(fresh.data.registered).toHaveLength(1);
  });

  it("retryCount 가 호출마다 늘고 maxRetryCount 를 함께 알려준다", async () => {
    const issued = await issueOne();

    const first = await (await reissueUploadUrl(issued.mediaId)).json();
    const second = await (await reissueUploadUrl(issued.mediaId)).json();

    expect(first.data.retryCount).toBe(1);
    expect(second.data.retryCount).toBe(2);
    expect(second.data.maxRetryCount).toBe(5);
  });

  it("한도를 넘겨 재발급하면 429 UPLOAD_RETRY_EXCEEDED 다", async () => {
    const issued = await issueOne();

    for (let attempt = 0; attempt < 5; attempt++) {
      expect((await reissueUploadUrl(issued.mediaId)).status).toBe(200);
    }

    const response = await reissueUploadUrl(issued.mediaId);

    expect(response.status).toBe(429);
    expect((await response.json()).code).toBe("UPLOAD_RETRY_EXCEEDED");
  });

  it("바디 없이 불러도 된다", async () => {
    const issued = await issueOne();

    expect((await reissueUploadUrl(issued.mediaId)).status).toBe(200);
  });

  it("재압축한 크기를 함께 보내면 그 값으로 갱신된다", async () => {
    const issued = await issueOne();

    const reissued = (await (await reissueUploadUrl(issued.mediaId, { size: 3512004 })).json())
      .data as Issued;

    await putToR2(reissued);

    const body = await (await registerMedia([issued.mediaId])).json();

    expect(body.data.registered[0].size).toBe(3512004);
  });

  it("크기가 0 이하면 400 INVALID_PARAM 이다", async () => {
    const issued = await issueOne();

    const response = await reissueUploadUrl(issued.mediaId, { size: 0 });

    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("INVALID_PARAM");
  });

  it("바뀐 크기가 한도를 넘으면 413 FILE_TOO_LARGE 다", async () => {
    const issued = await issueOne();

    const response = await reissueUploadUrl(issued.mediaId, { size: 10 * 1024 * 1024 + 1 });

    expect(response.status).toBe(413);
    expect((await response.json()).code).toBe("FILE_TOO_LARGE");
  });

  it("이미 등록이 끝난 미디어는 409 UPLOAD_ALREADY_COMPLETED 다", async () => {
    const issued = await issueOne();

    await putToR2(issued);
    await registerMedia([issued.mediaId]);

    const response = await reissueUploadUrl(issued.mediaId);

    expect(response.status).toBe(409);
    expect((await response.json()).code).toBe("UPLOAD_ALREADY_COMPLETED");
  });

  it("남이 발급받은 미디어는 방장이라도 403 MEDIA_FORBIDDEN 이다", async () => {
    await enterRoom(MOCK_ROOM_ID, GUEST_TOKEN);

    const issued = await issueOne(file("한라산.jpg"), { token: GUEST_TOKEN });

    // 방장 토큰으로 남의 예약을 재발급 시도한다
    const response = await reissueUploadUrl(issued.mediaId);

    expect(response.status).toBe(403);
    expect((await response.json()).code).toBe("MEDIA_FORBIDDEN");
  });

  it("없는 mediaId 는 404 MEDIA_NOT_FOUND 다", async () => {
    const response = await reissueUploadUrl(999999);

    expect(response.status).toBe(404);
    expect((await response.json()).code).toBe("MEDIA_NOT_FOUND");
  });
});
