import { shareRoomInviteLink } from "./shareRoomInviteLink";

const ROOM_CODE = "7K93QX2S";
const INVITE_URL = `${window.location.origin}/join/${ROOM_CODE}`;

const writeText = jest.fn<Promise<void>, [string]>();

const setNavigator = (key: "share", value: unknown): void => {
  Object.defineProperty(navigator, key, { value, configurable: true });
};

const setTouchDevice = (isTouch: boolean | undefined): void => {
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value:
      isTouch === undefined
        ? undefined
        : (query: string) => ({ matches: query === "(pointer: coarse)" && isTouch }),
  });
};

/** 앱이 메시지를 받으면 `answer` 로 답하는 가짜 웹뷰를 심는다. */
const installApp = ({ share, answer }: { share?: number; answer?: string }): jest.Mock => {
  const postMessage = jest.fn((raw: string) => {
    const { requestId } = JSON.parse(raw) as { requestId: string };
    if (answer === undefined) return;
    window.dispatchEvent(
      new CustomEvent("sssok:native", {
        detail: { type: "share/done", requestId, result: answer },
      }),
    );
  });
  window.ReactNativeWebView = { postMessage };
  window.sssokApp = share === undefined ? { download: 1 } : { download: 1, share };
  return postMessage;
};

beforeEach(() => {
  writeText.mockResolvedValue(undefined);
  Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
  setNavigator("share", undefined);
  setTouchDevice(undefined);
});

afterEach(() => {
  delete window.ReactNativeWebView;
  delete window.sssokApp;
});

describe("shareRoomInviteLink 브라우저", () => {
  it("터치 기기에서 navigator.share 가 있으면 공유 시트에 초대 링크를 넘긴다", async () => {
    const share = jest.fn().mockResolvedValue(undefined);
    setNavigator("share", share);
    setTouchDevice(true);

    await expect(shareRoomInviteLink(ROOM_CODE)).resolves.toBe("web");
    expect(share).toHaveBeenCalledWith({ url: INVITE_URL });
    expect(writeText).not.toHaveBeenCalled();
  });

  it("공유 시트를 닫으면 복사하지 않는다", async () => {
    setNavigator("share", jest.fn().mockRejectedValue(new DOMException("닫음", "AbortError")));
    setTouchDevice(true);

    await expect(shareRoomInviteLink(ROOM_CODE)).resolves.toBe("web");
    expect(writeText).not.toHaveBeenCalled();
  });

  it("공유가 거절되면 링크를 복사한다", async () => {
    setNavigator("share", jest.fn().mockRejectedValue(new DOMException("막힘", "NotAllowedError")));
    setTouchDevice(true);

    await expect(shareRoomInviteLink(ROOM_CODE)).resolves.toBe("clipboard");
    expect(writeText).toHaveBeenCalledWith(INVITE_URL);
  });

  it("PC 는 navigator.share 가 있어도 링크를 복사한다", async () => {
    const share = jest.fn();
    setNavigator("share", share);
    setTouchDevice(false);

    await expect(shareRoomInviteLink(ROOM_CODE)).resolves.toBe("clipboard");
    expect(share).not.toHaveBeenCalled();
    expect(writeText).toHaveBeenCalledWith(INVITE_URL);
  });

  it("navigator.share 가 없으면 링크를 복사한다", async () => {
    setTouchDevice(true);

    await expect(shareRoomInviteLink(ROOM_CODE)).resolves.toBe("clipboard");
    expect(writeText).toHaveBeenCalledWith(INVITE_URL);
  });

  it("복사도 실패하면 예외를 던진다", async () => {
    writeText.mockRejectedValueOnce(new Error("denied"));

    await expect(shareRoomInviteLink(ROOM_CODE)).rejects.toThrow("denied");
  });
});

describe("shareRoomInviteLink 앱", () => {
  it("앱이 공유 브리지를 알면 앱에 초대 링크 공유를 맡긴다", async () => {
    const postMessage = installApp({ share: 1, answer: "shared" });

    await expect(shareRoomInviteLink(ROOM_CODE)).resolves.toBe("app");
    expect(JSON.parse(postMessage.mock.calls[0][0] as string)).toEqual({
      type: "share/link",
      requestId: expect.stringMatching(/^[A-Za-z0-9-]{1,64}$/),
      url: INVITE_URL,
    });
    expect(writeText).not.toHaveBeenCalled();
  });

  it("앱에서 시트를 닫아도 복사하지 않는다", async () => {
    installApp({ share: 1, answer: "dismissed" });

    await expect(shareRoomInviteLink(ROOM_CODE)).resolves.toBe("app");
    expect(writeText).not.toHaveBeenCalled();
  });

  it("앱이 시트를 띄우지 못하면 링크를 복사한다", async () => {
    installApp({ share: 1, answer: "failed" });

    await expect(shareRoomInviteLink(ROOM_CODE)).resolves.toBe("clipboard");
    expect(writeText).toHaveBeenCalledWith(INVITE_URL);
  });

  it("다른 요청의 답은 무시한다", async () => {
    const postMessage = installApp({ share: 1 });
    const result = shareRoomInviteLink(ROOM_CODE);

    window.dispatchEvent(
      new CustomEvent("sssok:native", {
        detail: { type: "share/done", requestId: "other-1", result: "failed" },
      }),
    );
    const { requestId } = JSON.parse(postMessage.mock.calls[0][0] as string) as {
      requestId: string;
    };
    window.dispatchEvent(
      new CustomEvent("sssok:native", {
        detail: { type: "share/done", requestId, result: "shared" },
      }),
    );

    await expect(result).resolves.toBe("app");
    expect(writeText).not.toHaveBeenCalled();
  });

  it("공유 브리지를 모르는 예전 앱에는 메시지를 보내지 않고 링크를 복사한다", async () => {
    const postMessage = installApp({ answer: "shared" });

    await expect(shareRoomInviteLink(ROOM_CODE)).resolves.toBe("clipboard");
    expect(postMessage).not.toHaveBeenCalled();
    expect(writeText).toHaveBeenCalledWith(INVITE_URL);
  });
});
