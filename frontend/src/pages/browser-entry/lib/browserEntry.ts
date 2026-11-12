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
