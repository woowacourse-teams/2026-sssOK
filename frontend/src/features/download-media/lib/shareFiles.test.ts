import { canShareFiles, shareFiles } from "./shareFiles";

/**
 * 실기기(아이폰 사파리·안드로이드 크롬)에서 확인할 수 없는 분기를 여기서 고정한다.
 * 개발 환경 브라우저에는 Web Share API 자체가 없어서, 이 검사가 #123 을 지키는 유일한 자리다.
 */

const fileOf = (name = "IMG_0001.jpg") =>
  new File([new Uint8Array(4)], name, { type: "image/jpeg" });

/** 기기가 무엇을 지원하는지 흉내낸다. `undefined` 는 API 자체가 없는 기기다. */
const withNavigator = ({
  canShare,
  share,
}: {
  canShare?: (data: { files: File[] }) => boolean;
  share?: (data: { files: File[] }) => Promise<void>;
}) => {
  Object.defineProperty(navigator, "canShare", { value: canShare, configurable: true });
  Object.defineProperty(navigator, "share", { value: share, configurable: true });
};

const abortError = () => Object.assign(new Error("사용자가 닫음"), { name: "AbortError" });

afterEach(() => withNavigator({ canShare: undefined, share: undefined }));

describe("canShareFiles", () => {
  it("공유 API 가 없는 기기에서는 거짓이다", () => {
    withNavigator({ canShare: undefined });

    expect(canShareFiles()).toBe(false);
  });

  /**
   * 맥 크롬이 여기 걸린다. `navigator.share` 는 있는데 파일은 못 받는다.
   * 존재 여부만 보고 판단하면 버튼이 떠 있는데 눌러도 아무 일이 없다.
   */
  it("공유는 되지만 파일은 못 받는 기기에서는 거짓이다", () => {
    withNavigator({ canShare: () => false, share: async () => {} });

    expect(canShareFiles()).toBe(false);
  });

  it("파일 공유를 받는 기기에서만 참이다", () => {
    withNavigator({ canShare: () => true, share: async () => {} });

    expect(canShareFiles()).toBe(true);
  });
});

describe("shareFiles", () => {
  it("지원하지 않는 기기에서는 시트를 열지 않는다", async () => {
    const share = jest.fn();

    withNavigator({ canShare: () => false, share });

    expect(await shareFiles([fileOf()])).toEqual({ type: "unsupported" });
    expect(share).not.toHaveBeenCalled();
  });

  it("넘길 파일이 없으면 시트를 열지 않는다", async () => {
    const share = jest.fn();

    withNavigator({ canShare: () => true, share });

    expect(await shareFiles([])).toEqual({ type: "unsupported" });
    expect(share).not.toHaveBeenCalled();
  });

  // 기기마다 한 번에 받는 장수 한계가 다르다. 못 받겠다고 하면 저장 방식을 바꿔야 한다.
  it("이 묶음은 못 받겠다고 하면 unsupported 다", async () => {
    const share = jest.fn();

    withNavigator({ canShare: ({ files }) => files.length <= 1, share });

    expect(await shareFiles([fileOf("a.jpg"), fileOf("b.jpg")])).toEqual({ type: "unsupported" });
    expect(share).not.toHaveBeenCalled();
  });

  it("시트가 열리고 저장되면 shared 다", async () => {
    const share = jest.fn().mockResolvedValue(undefined);

    withNavigator({ canShare: () => true, share });

    expect(await shareFiles([fileOf()])).toEqual({ type: "shared" });
    expect(share).toHaveBeenCalledTimes(1);
  });

  /**
   * 사용자가 시트를 그냥 닫은 것은 실패가 아니다 (#123 완료 조건).
   * 실패로 세면 "저장 못 했어요" 안내가 뜨고, 재시도를 권하게 된다.
   */
  it("시트를 그냥 닫으면 실패가 아니라 dismissed 다", async () => {
    withNavigator({ canShare: () => true, share: jest.fn().mockRejectedValue(abortError()) });

    expect(await shareFiles([fileOf()])).toEqual({ type: "dismissed" });
  });

  it("닫은 것이 아닌 진짜 오류는 그대로 던진다", async () => {
    withNavigator({
      canShare: () => true,
      share: jest.fn().mockRejectedValue(new Error("공유 실패")),
    });

    await expect(shareFiles([fileOf()])).rejects.toThrow("공유 실패");
  });
});
