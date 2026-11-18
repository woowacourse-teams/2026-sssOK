import { canShareFromApp, shareFromApp } from "./nativeShare";
import { roomInviteUrl } from "./roomInviteUrl";

/**
 * 링크가 어디로 나갔는지.
 * - `app`       쏙 앱이 띄운 기기 공유 시트
 * - `web`       브라우저가 띄운 기기 공유 시트 (`navigator.share`)
 * - `clipboard` 클립보드에 복사
 */
export type InviteShareChannel = "app" | "web" | "clipboard";

/**
 * PC 에도 `navigator.share` 가 있지만(맥·윈도우), 그 공유 창에는 카카오톡이 거의 없다.
 * PC 사용자는 링크를 복사해 붙여 넣는 편이 빨라서, 터치 기기에서만 공유 시트를 쓴다.
 */
const prefersWebShare = (): boolean =>
  typeof navigator.share === "function" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(pointer: coarse)").matches;

const isDismissed = (error: unknown): boolean =>
  error instanceof DOMException && error.name === "AbortError";

/**
 * 방 초대 링크를 공유한다. 앱이면 앱의 공유 시트, 모바일 브라우저면 브라우저의 공유 시트를 띄우고,
 * 둘 다 쓸 수 없거나 띄우지 못하면 링크를 복사한다. 복사까지 실패하면 예외를 던진다.
 */
export const shareRoomInviteLink = async (roomCode: string): Promise<InviteShareChannel> => {
  const url = roomInviteUrl(roomCode);

  if (canShareFromApp()) {
    if ((await shareFromApp(url)) !== "failed") return "app";
  } else if (prefersWebShare()) {
    try {
      await navigator.share({ url });
      return "web";
    } catch (error) {
      // 시트를 닫은 것은 실패가 아니다. 그 밖의 거절(권한, 인앱 브라우저 제한 등)은 복사로 넘어간다.
      if (isDismissed(error)) return "web";
    }
  }

  await navigator.clipboard.writeText(url);
  return "clipboard";
};
