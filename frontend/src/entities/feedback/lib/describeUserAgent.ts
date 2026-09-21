export interface DescribedUserAgent {
  /** 기기. 읽어내지 못하면 null. */
  device: string | null;
  /** 브라우저(알아내면 버전까지). 읽어내지 못하면 null. */
  browser: string | null;
}

const UNKNOWN: DescribedUserAgent = { device: null, browser: null };

/**
 * UA 가 실제로 담고 있는 것만 꺼낸다.
 *
 * 디자인은 `iPhone 14 · Safari 17.4` 처럼 기기 모델명까지 보여주지만, iOS UA 에는 모델이
 * 아예 없다 — 애플이 `iPhone` 하나로만 보낸다. 안드로이드는 `SM-S911N` 같은 빌드 코드로
 * 들어와서, `Galaxy S23` 으로 바꾸려면 기종 대조표를 들고 있어야 한다. 그 표는 기기가 나올
 * 때마다 낡으므로, 없는 정보를 지어내느니 UA 에 적힌 대로 보여준다.
 */

/** 순서가 곧 우선순위다. 카카오톡·엣지·삼성인터넷은 UA 에 Chrome·Safari 도 함께 싣는다. */
const BROWSER_RULES: { name: string; match: RegExp; version?: RegExp }[] = [
  { name: "KakaoTalk", match: /KAKAOTALK/i },
  { name: "Instagram", match: /Instagram/i },
  { name: "NAVER", match: /NAVER\(inapp/i },
  { name: "Edge", match: /Edg[A-Z]?\//, version: /Edg[A-Z]?\/(\d+)/ },
  {
    name: "Samsung Internet",
    match: /SamsungBrowser\//,
    version: /SamsungBrowser\/(\d+(?:\.\d+)?)/,
  },
  { name: "Firefox", match: /Firefox\//, version: /Firefox\/(\d+(?:\.\d+)?)/ },
  { name: "Chrome", match: /(?:Chrome|CriOS)\//, version: /(?:Chrome|CriOS)\/(\d+)/ },
  { name: "Safari", match: /Safari\//, version: /Version\/(\d+(?:\.\d+)?)/ },
];

const readBrowser = (userAgent: string): string | null => {
  const rule = BROWSER_RULES.find(({ match }) => match.test(userAgent));

  if (!rule) return null;

  const version = rule.version?.exec(userAgent)?.[1];

  return version ? `${rule.name} ${version}` : rule.name;
};

/** 안드로이드는 `Android 14; SM-S911N Build/...` 처럼 모델을 세미콜론 뒤에 싣는다. */
const readAndroidDevice = (userAgent: string): string => {
  const model = /Android [\d.]+;\s*([^;)]+?)(?:\s+Build\/[^;)]*)?\s*[;)]/.exec(userAgent)?.[1];

  // `wv`(웹뷰)나 `K`(최근 크롬이 모델을 가린 값)는 기기를 가리키지 않는다.
  if (!model || model === "wv" || model === "K") return "Android";

  return model;
};

export const describeUserAgent = (userAgent: string | null): DescribedUserAgent => {
  if (userAgent === null || userAgent.trim() === "") return UNKNOWN;

  const browser = readBrowser(userAgent);

  if (/iPhone/.test(userAgent)) return { device: "iPhone", browser };
  if (/iPad/.test(userAgent)) return { device: "iPad", browser };
  if (/Android/.test(userAgent)) return { device: readAndroidDevice(userAgent), browser };
  if (/Macintosh|Mac OS X/.test(userAgent)) return { device: "Mac", browser };
  if (/Windows NT/.test(userAgent)) return { device: "Windows", browser };
  if (/Linux/.test(userAgent)) return { device: "Linux", browser };

  // `curl/8.4.0` 처럼 어느 규칙에도 걸리지 않는 값이 있다. 빈 값으로 두고 나머지 메타만 보여준다.
  return { device: null, browser };
};
