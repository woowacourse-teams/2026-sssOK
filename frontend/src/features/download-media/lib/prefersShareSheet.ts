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

export const prefersShareSheet = () => {
  if (typeof window === "undefined" || typeof navigator === "undefined") {
    return false;
  }

  // Android와 PC는 공유 시트 대신 개별 다운로드를 사용한다.
  if (!isIOSDevice()) {
    return false;
  }

  // iOS라도 파일 공유를 지원할 때만 공유 시트를 사용한다.
  if (!canShareFiles()) {
    return false;
  }

  return true;
};
