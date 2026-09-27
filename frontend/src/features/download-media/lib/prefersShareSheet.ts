import { canShareFiles } from "./shareFiles";

const isIOSDevice = () => {
  const userAgent = navigator.userAgent;

  const isIPhone = userAgent.includes("iPhone");
  const isIPad = userAgent.includes("iPad");
  const isIPod = userAgent.includes("iPod");

  // 일부 iPad는 자신을 Mac으로 표시한다.
  const isIPadInDesktopMode = userAgent.includes("Macintosh") && navigator.maxTouchPoints > 1;

  return isIPhone || isIPad || isIPod || isIPadInDesktopMode;
};

/**
 * iOS에서만 파일 공유 시트를 사용한다.
 *
 * Android 공유 시트에서는 이미지를 갤러리에 직접 저장할 수 없어
 * 기존 개별 다운로드 방식을 사용한다.
 *
 * 공유 시트에 어떤 저장 기능이 있는지는 Web API로 확인할 수 없으므로
 * userAgent를 이용해 iOS를 구분한다.
 */
export const prefersShareSheet = () => {
  if (typeof window === "undefined" || typeof navigator === "undefined") {
    return false;
  }

  if (!isIOSDevice()) {
    return false;
  }

  return canShareFiles();
};
