import { prefersShareSheet } from "./prefersShareSheet";

/**
 * 폰에서 낱장 저장 자리를 사진첩이 대신하는 분기다 (#122 완료 조건).
 * 실기기 없이 이 갈림길을 확인할 수 있는 유일한 자리다.
 */

const asDevice = ({ coarsePointer, fileShare }: { coarsePointer: boolean; fileShare: boolean }) => {
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: (query: string) => ({ matches: query.includes("coarse") && coarsePointer }),
  });
  Object.defineProperty(navigator, "canShare", {
    configurable: true,
    value: fileShare ? () => true : undefined,
  });
};

describe("prefersShareSheet", () => {
  it("폰에서는 참이다 — 터치가 주 입력이고 파일 공유도 된다", () => {
    asDevice({ coarsePointer: true, fileShare: true });

    expect(prefersShareSheet()).toBe(true);
  });

  /**
   * 윈도우 크롬·엣지도 파일 공유를 지원한다. 그것만 보고 가르면
   * 데스크톱 사용자가 다운로드 폴더 대신 공유 시트로 끌려간다.
   */
  it("파일 공유가 되는 데스크톱은 거짓이다 — 마우스가 주 입력이다", () => {
    asDevice({ coarsePointer: false, fileShare: true });

    expect(prefersShareSheet()).toBe(false);
  });

  it("터치 기기라도 파일 공유가 안 되면 거짓이다", () => {
    asDevice({ coarsePointer: true, fileShare: false });

    expect(prefersShareSheet()).toBe(false);
  });

  it("matchMedia 가 없는 환경에서는 거짓이다", () => {
    Object.defineProperty(window, "matchMedia", { configurable: true, value: undefined });

    expect(prefersShareSheet()).toBe(false);
  });
});
