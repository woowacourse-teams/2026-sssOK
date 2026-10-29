/** 쏙 앱의 WebView 브릿지 존재 여부로 앱 환경을 구분한다. */
export const isAppWebView = () =>
  typeof window !== "undefined" && window.ReactNativeWebView !== undefined;
