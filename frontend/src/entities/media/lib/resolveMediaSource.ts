import { API_BASE_URL } from "@/shared/config";

/** 완성된 URL은 유지하고, API 상대 경로에만 베이스 URL을 붙인다. */
export const resolveMediaSource = (src: string | null | undefined, baseUrl = API_BASE_URL) => {
  const value = src?.trim();
  if (!value) return undefined;
  if (/^(https?:\/\/|\/\/|blob:|data:)/i.test(value)) return value;

  const base = new URL(baseUrl, window.location.origin);
  const prefix = base.pathname.replace(/\/$/, "");
  const path = value.startsWith("/") ? value : `/${value}`;
  const withPrefix = path === prefix || path.startsWith(`${prefix}/`) ? path : `${prefix}${path}`;
  return new URL(withPrefix, base.origin).href;
};
