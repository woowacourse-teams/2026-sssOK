import { MOCK_ROOM_ID } from "@/mocks/handlers/room";
import { API_BASE_URL } from "@/shared/config";
import { putToStorage } from "./putToStorage";

const HOST_TOKEN = "Bearer mock-token-10234";

interface Issued {
  mediaId: number;
  uploadUrl: string;
  headers: Record<string, string>;
}

/** 업로드는 참여자만 부를 수 있어서 모든 흐름이 입장부터 시작한다. */
const enterRoom = async () => {
  await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/members`, {
    method: "POST",
    headers: { Authorization: HOST_TOKEN },
  });
};

/** 발급까지 해서 쏠 수 있는 URL 한 건을 만들어 준다. */
const issueOne = async (fileName = "제주-해변.jpg"): Promise<Issued> => {
  await enterRoom();

  const response = await fetch(`${API_BASE_URL}/rooms/${MOCK_ROOM_ID}/media/upload-urls`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: HOST_TOKEN },
    body: JSON.stringify({ files: [{ fileName, mimeType: "image/jpeg", size: 1024 }] }),
  });

  return (await response.json()).data.issued[0] as Issued;
};

const imageFile = (fileName = "제주-해변.jpg") =>
  new File(["사진 바이트"], fileName, { type: "image/jpeg" });

describe("putToStorage", () => {
  it("발급받은 URL 로 보내면 성공한다", async () => {
    const issued = await issueOne();

    const result = await putToStorage({
      url: issued.uploadUrl,
      headers: issued.headers,
      file: imageFile(),
    });

    expect(result).toEqual({ type: "success" });
  });

  it("발급 응답의 headers 를 그대로 싣지 않으면 403 이 온다", async () => {
    const issued = await issueOne();

    const result = await putToStorage({
      url: issued.uploadUrl,
      // 서명에 Content-Type 이 들어 있어 값이 다르면 스토리지가 거절한다.
      headers: { "Content-Type": "image/png" },
      file: imageFile(),
    });

    expect(result).toEqual({ type: "failure", status: 403 });
  });

  it("만료된 URL 은 403 을 상태 코드로 돌려준다 — 던지지 않는다", async () => {
    const issued = await issueOne("__expired__제주-해변.jpg");

    const result = await putToStorage({
      url: issued.uploadUrl,
      headers: issued.headers,
      file: imageFile(),
    });

    expect(result).toEqual({ type: "failure", status: 403 });
  });

  it("스토리지가 5xx 로 깨져도 실패를 값으로 돌려준다", async () => {
    const issued = await issueOne("__fail__제주-해변.jpg");

    const result = await putToStorage({
      url: issued.uploadUrl,
      headers: issued.headers,
      file: imageFile(),
    });

    expect(result).toEqual({ type: "failure", status: 500 });
  });

  /**
   * 목의 XHR 은 `abort()` 를 무시하고 끝까지 진행한다 (readyState 가 1 에 멈추고 load 가 뜬다).
   * 그래서 "결과가 aborted 냐" 는 여기서 확인할 수 없다 — 실기기에서 봐야 한다.
   * 대신 우리가 책임지는 부분, 즉 **중단 신호를 요청까지 실제로 전달하는지**를 확인한다.
   */
  it("중단 신호를 받으면 진행 중인 요청을 실제로 끊는다", async () => {
    const issued = await issueOne();
    const controller = new AbortController();
    const abort = jest.spyOn(XMLHttpRequest.prototype, "abort");

    const pending = putToStorage({
      url: issued.uploadUrl,
      headers: issued.headers,
      file: imageFile(),
      signal: controller.signal,
    });

    controller.abort();

    expect(abort).toHaveBeenCalledTimes(1);

    await pending;
    abort.mockRestore();
  });

  it("끝난 요청에는 중단 신호가 더 이상 닿지 않는다", async () => {
    const issued = await issueOne();
    const controller = new AbortController();
    const abort = jest.spyOn(XMLHttpRequest.prototype, "abort");

    await putToStorage({
      url: issued.uploadUrl,
      headers: issued.headers,
      file: imageFile(),
      signal: controller.signal,
    });

    // 30장이 signal 하나를 나눠 쓴다. 끝난 요청의 청취자가 남으면 계속 쌓인다.
    controller.abort();

    expect(abort).not.toHaveBeenCalled();
    abort.mockRestore();
  });

  it("이미 중단된 signal 이면 요청을 시작하지도 않는다", async () => {
    const issued = await issueOne();

    const result = await putToStorage({
      url: issued.uploadUrl,
      headers: issued.headers,
      file: imageFile(),
      signal: AbortSignal.abort(),
    });

    expect(result).toEqual({ type: "aborted" });
  });
});
