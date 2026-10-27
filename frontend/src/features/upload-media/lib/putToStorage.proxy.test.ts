import { putToStorage } from "./putToStorage";

// 앱 확인용 개발 서버(`pnpm start:app`)로 띄운 상태를 흉내 낸다.
jest.mock("@/shared/config", () => ({
  ...jest.requireActual("@/shared/config"),
  STORAGE_PROXY_PATH: "/__storage",
}));

const UPLOAD_URL =
  "https://account.r2.cloudflarestorage.com/sssok-dev/rooms/1/a.jpg?X-Amz-Signature=abc";

describe("putToStorage — 앱 확인용 개발 서버", () => {
  afterEach(() => jest.restoreAllMocks());

  it("R2 대신 개발 서버의 스토리지 프록시로 보내고, 서명은 그대로 싣는다", () => {
    // 보낼 주소만 본다. 실제 전송은 막는다.
    const open = jest.spyOn(XMLHttpRequest.prototype, "open").mockImplementation(() => {});
    jest.spyOn(XMLHttpRequest.prototype, "setRequestHeader").mockImplementation(() => {});
    jest.spyOn(XMLHttpRequest.prototype, "send").mockImplementation(() => {});

    void putToStorage({
      url: UPLOAD_URL,
      headers: { "Content-Type": "image/jpeg" },
      file: new File(["bytes"], "a.jpg", { type: "image/jpeg" }),
    });

    expect(open).toHaveBeenCalledWith(
      "PUT",
      "/__storage/account.r2.cloudflarestorage.com/sssok-dev/rooms/1/a.jpg?X-Amz-Signature=abc",
    );
  });
});
