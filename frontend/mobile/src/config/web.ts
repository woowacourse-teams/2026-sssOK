const configuredUrl = process.env.EXPO_PUBLIC_WEB_URL || "https://www.ssssok.com/home";
const parsedUrl = new URL(configuredUrl);

if (parsedUrl.protocol !== "https:" && !(__DEV__ && parsedUrl.protocol === "http:")) {
  throw new Error("EXPO_PUBLIC_WEB_URL은 HTTPS 주소여야 합니다.");
}

export const WEB_URL = parsedUrl.href;
export const WEB_ORIGIN = parsedUrl.origin;
