const ALTERNATIVE_ANDROID_BROWSER =
  /SamsungBrowser|EdgA|OPR|Firefox|FxiOS|DuckDuckGo|Whale|NAVER|KAKAOTALK|; wv\)/i;

export const shouldOpenInChrome = (userAgent: string) => {
  const isAndroid = /Android/i.test(userAgent);
  const isChrome = /Chrome\/\d/i.test(userAgent) && !ALTERNATIVE_ANDROID_BROWSER.test(userAgent);

  return isAndroid && !isChrome;
};

export const buildChromeIntentUrl = (targetUrl: URL, fallbackUrl: URL) => {
  const scheme = targetUrl.protocol.replace(":", "");
  const target = `${targetUrl.host}${targetUrl.pathname}${targetUrl.search}${targetUrl.hash}`;

  return (
    `intent://${target}#Intent;scheme=${scheme};package=com.android.chrome;` +
    `S.browser_fallback_url=${encodeURIComponent(fallbackUrl.href)};end`
  );
};

export const openChrome = (intentUrl: string) => window.location.assign(intentUrl);
