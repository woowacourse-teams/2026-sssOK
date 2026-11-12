export const isMobileBrowser = (userAgent: string, touchPoints: number): boolean =>
  /Android|iPhone|iPad|iPod/i.test(userAgent) || (/Macintosh/i.test(userAgent) && touchPoints > 1);

export function buildInviteAppUrl(invite: URL, userAgent: string): string {
  if (/Android/i.test(userAgent)) {
    // HTTPS App Link를 처리하는 버전만 실행하고, 미설치 시 초대 페이지로 돌아온다.
    return `intent://${invite.host}${invite.pathname}${invite.search}#Intent;scheme=https;package=com.ssssok.app;S.browser_fallback_url=${encodeURIComponent(invite.href)};end`;
  }
  return `sssok://${invite.host}${invite.pathname}${invite.search}${invite.hash}`;
}

export const openInviteApp = (url: string): void => window.location.assign(url);
const ALTERNATIVE_ANDROID_BROWSER =
  /SamsungBrowser|EdgA|OPR|Firefox|FxiOS|DuckDuckGo|Whale|NAVER|KAKAOTALK|; wv\)/i;

export const shouldOpenInChrome = (userAgent: string): boolean => {
  const isAndroid = /Android/i.test(userAgent);
  const isChrome = /Chrome\/\d/i.test(userAgent) && !ALTERNATIVE_ANDROID_BROWSER.test(userAgent);

  return isAndroid && !isChrome;
};

export const buildChromeIntentUrl = (targetUrl: URL, fallbackUrl: URL): string => {
  const scheme = targetUrl.protocol.replace(":", "");
  const target = `${targetUrl.host}${targetUrl.pathname}${targetUrl.search}${targetUrl.hash}`;

  return (
    `intent://${target}#Intent;scheme=${scheme};package=com.android.chrome;` +
    `S.browser_fallback_url=${encodeURIComponent(fallbackUrl.href)};end`
  );
};

export const openChrome = (intentUrl: string): void => window.location.assign(intentUrl);
