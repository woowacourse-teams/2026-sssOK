// /join은 외부 브라우저 안내를 거친다. 앱에서는 같은 방의 입장 화면을 바로 연다.
export function getInviteEntryUrl(rawUrl: string, webOrigin: string): string | null {
  try {
    const url = new URL(rawUrl);
    const match = /^\/join\/([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8})\/?$/.exec(url.pathname);
    if (
      url.origin !== webOrigin ||
      url.protocol !== "https:" ||
      url.username ||
      url.password ||
      !match
    ) {
      return null;
    }
    url.pathname = `/rooms/${match[1]}`;
    return url.href;
  } catch {
    return null;
  }
}
