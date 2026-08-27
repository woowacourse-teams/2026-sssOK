import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { API_BASE_URL } from "@/shared/config";
import { putToStorage } from "../lib/putToStorage";
import { issueUploadUrls } from "./issueUploadUrls";
import { registerMedia } from "./registerMedia";
import { reissueUploadUrl } from "./reissueUploadUrl";

const TOKEN = "mock-token-10234";

const requestFile = (fileName: string, size = 1024) => ({
  fileName,
  mimeType: "image/jpeg",
  size,
});

const imageFile = (fileName: string) => new File(["사진"], fileName, { type: "image/jpeg" });

/** 업로드는 참여자만 부를 수 있어서 모든 흐름이 입장부터 시작한다. */
const enterRoom = async () => {
  await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
};

describe("issueUploadUrls", () => {
  it("고른 파일 전체를 한 번에 보내고 issued 와 rejected 로 갈라 받는다", async () => {
    await enterRoom();

    const { issued, rejected } = await issueUploadUrls(
      MOCK_ROOM_ID,
      {
        files: [requestFile("첫째.jpg"), requestFile("메모.txt"), requestFile("둘째.jpg")],
      },
      TOKEN,
    );

    // 거절된 자리를 건너뛰고 원본 순서가 유지된다 — 짝짓기가 이 순서에 기댄다.
    expect(issued.map((one) => one.fileName)).toEqual(["첫째.jpg", "둘째.jpg"]);
    expect(rejected).toEqual([
      expect.objectContaining({ fileName: "메모.txt", code: "UNSUPPORTED_FILE_TYPE" }),
    ]);
  });

  it("PUT 에 그대로 실을 headers 를 함께 내려준다", async () => {
    await enterRoom();

    const { issued } = await issueUploadUrls(
      MOCK_ROOM_ID,
      { files: [requestFile("첫째.jpg")] },
      TOKEN,
    );

    expect(issued[0].headers).toEqual({ "Content-Type": "image/jpeg" });
    expect(issued[0].method).toBe("PUT");
  });

  it("입장하지 않은 방이면 ApiError 로 던진다", async () => {
    await expect(
      issueUploadUrls(MOCK_ROOM_ID, { files: [requestFile("첫째.jpg")] }, TOKEN),
    ).rejects.toMatchObject({ status: 403, code: "NOT_ROOM_MEMBER" });
  });
});

describe("registerMedia", () => {
  it("PUT 이 끝난 미디어를 등록하면 registered 로 돌아온다", async () => {
    await enterRoom();
    const { issued } = await issueUploadUrls(
      MOCK_ROOM_ID,
      { files: [requestFile("첫째.jpg")] },
      TOKEN,
    );

    await putToStorage({
      url: issued[0].uploadUrl,
      headers: issued[0].headers,
      file: imageFile("첫째.jpg"),
    });

    const { registered, failed } = await registerMedia(
      MOCK_ROOM_ID,
      { mediaIds: [issued[0].mediaId] },
      TOKEN,
    );

    expect(failed).toEqual([]);
    // 워커가 아직 안 돌아서 PROCESSING 이다. 썸네일은 비어 있다.
    expect(registered[0]).toMatchObject({ mediaId: issued[0].mediaId, status: "PROCESSING" });
  });

  it("PUT 하지 않은 미디어는 failed 로 갈라져 온다 — 요청 전체가 깨지지 않는다", async () => {
    await enterRoom();
    const { issued } = await issueUploadUrls(
      MOCK_ROOM_ID,
      { files: [requestFile("첫째.jpg")] },
      TOKEN,
    );

    const { registered, failed } = await registerMedia(
      MOCK_ROOM_ID,
      { mediaIds: [issued[0].mediaId] },
      TOKEN,
    );

    expect(registered).toEqual([]);
    expect(failed[0]).toMatchObject({ code: "UPLOAD_NOT_COMPLETED" });
  });
});

describe("reissueUploadUrl", () => {
  it("mediaId 는 그대로 두고 새 URL 과 retryCount 를 준다", async () => {
    await enterRoom();
    const { issued } = await issueUploadUrls(
      MOCK_ROOM_ID,
      { files: [requestFile("첫째.jpg")] },
      TOKEN,
    );

    const reissued = await reissueUploadUrl(MOCK_ROOM_ID, issued[0].mediaId, {}, TOKEN);

    expect(reissued.mediaId).toBe(issued[0].mediaId);
    expect(reissued.uploadUrl).not.toBe(issued[0].uploadUrl);
    expect(reissued).toMatchObject({ retryCount: 1, maxRetryCount: 5 });
  });

  it("서버 한도를 넘기면 429 로 던진다", async () => {
    await enterRoom();
    const { issued } = await issueUploadUrls(
      MOCK_ROOM_ID,
      { files: [requestFile("첫째.jpg")] },
      TOKEN,
    );

    for (let count = 0; count < 5; count += 1) {
      await reissueUploadUrl(MOCK_ROOM_ID, issued[0].mediaId, {}, TOKEN);
    }

    await expect(
      reissueUploadUrl(MOCK_ROOM_ID, issued[0].mediaId, {}, TOKEN),
    ).rejects.toMatchObject({ status: 429, code: "UPLOAD_RETRY_EXCEEDED" });
  });
});
