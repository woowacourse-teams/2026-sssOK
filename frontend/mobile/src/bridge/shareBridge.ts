import { Platform, Share } from "react-native";
import { z } from "zod";

/**
 * 웹이 넘긴 초대 링크를 기기 공유 시트로 띄운다 (#855).
 *
 * `expo-sharing` 은 파일만 받아서, 링크는 React Native 기본 `Share` 로 넘긴다.
 * 메시지 형식의 짝은 웹의 `frontend/src/features/share-room/lib/nativeShare.ts` 에 있다.
 * 한쪽을 바꾸면 다른 쪽도 고친다.
 */

/** 웹이 이 값을 보고 앱에 공유를 맡긴다. 형식이 호환되지 않게 바뀌면 올린다. */
export const SHARE_BRIDGE_VERSION = 1;

const ShareRequestSchema = z.object({
  type: z.literal("share/link"),
  requestId: z.string().regex(/^[A-Za-z0-9-]{1,64}$/),
  url: z.string().max(2048),
});

/**
 * - `shared`    공유 대상을 골랐다. 안드로이드는 시트를 그냥 닫아도 이 값이다.
 * - `dismissed` 시트를 닫았다. iOS 만 알려 준다.
 * - `failed`    시트를 띄우지 못했다. 웹은 링크 복사로 넘어간다.
 */
export interface ShareEvent {
  type: "share/done";
  requestId: string;
  result: "shared" | "dismissed" | "failed";
}

/**
 * 공유 요청이면 처리하고 `true` 를 돌려준다. 다른 브리지의 메시지면 `false` 다.
 * 링크는 서비스 주소(`isServiceUrl`)일 때만 공유한다. 다른 주소를 앱 이름으로 퍼뜨리지 못하게 한다.
 */
export function handleShareMessage(
  raw: string,
  isServiceUrl: (url: string) => boolean,
  emit: (event: ShareEvent) => void,
): boolean {
  let message: unknown;
  try {
    message = JSON.parse(raw);
  } catch {
    return false;
  }
  if ((message as { type?: unknown } | null)?.type !== "share/link") return false;

  const parsed = ShareRequestSchema.safeParse(message);
  // 번호를 알 수 없으면 답할 곳이 없다. 형식이 틀린 요청은 무시한다.
  if (!parsed.success) return true;

  const { requestId, url } = parsed.data;
  if (!isServiceUrl(url)) {
    emit({ type: "share/done", requestId, result: "failed" });
    return true;
  }

  // 안드로이드는 `url` 을 무시하고 `message` 만 넘긴다.
  const content = Platform.OS === "ios" ? { url } : { message: url };

  Share.share(content)
    .then(({ action }) =>
      emit({
        type: "share/done",
        requestId,
        result: action === Share.dismissedAction ? "dismissed" : "shared",
      }),
    )
    .catch(() => emit({ type: "share/done", requestId, result: "failed" }));

  return true;
}
