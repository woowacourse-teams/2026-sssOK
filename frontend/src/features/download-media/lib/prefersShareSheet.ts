import { canShareFiles } from "./shareFiles";

/**
 * 낱장 저장을 **공유 시트로 처리해야 하는 기기인지** 본다.
 *
 * 폰에서 낱장을 `<a download>` 로 저장하면 첫 장만 받아진다. 브라우저가 짧은 간격의 연속
 * 다운로드를 막기 때문인데, iOS 사파리는 프롬프트조차 없이 조용히 버려서 **몇 장이 저장됐는지
 * 셀 수도 없다.** 게다가 폰에서 낱장으로 받고 싶은 사람의 목적지는 파일 앱이 아니라 사진첩이다.
 * 그래서 폰에서는 낱장 저장 자리를 "사진첩에 저장" 이 대신한다.
 *
 * **`canShareFiles()` 하나로 가르면 안 된다.** 윈도우 크롬·엣지도 파일 공유를 지원해서,
 * 그것만 보면 데스크톱 사용자가 다운로드 폴더 대신 공유 시트로 끌려간다.
 * 그래서 파일 공유가 되는지와 **터치가 주 입력인지**를 함께 본다.
 *
 * `navigator.userAgentData.mobile` 이 제일 깔끔하지만 사파리에 없어서,
 * 정작 이 분기가 가장 필요한 아이폰에서 쓸 수 없다.
 */
export const prefersShareSheet = () => {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false;
  }

  // 터치 노트북·윈도우 태블릿이 공유 시트로 가는 것은 감수한다 — 둘 다 시트가 열리기는 한다.
  return canShareFiles() && window.matchMedia("(pointer: coarse)").matches;
};
