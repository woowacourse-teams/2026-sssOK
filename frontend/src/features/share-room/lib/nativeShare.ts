import { z } from "zod";

import { isAppWebView } from "@/shared/lib/isAppWebView";

/**
 * 쏙 앱(`frontend/mobile`)에 링크 공유 시트를 맡긴다.
 *
 * 웹뷰는 `navigator.share` 를 보장하지 않아서, 앱 안에서는 앱이 기기 공유 시트를 띄운다.
 * 메시지 형식의 짝은 `frontend/mobile/src/bridge/shareBridge.ts` 에 있다. 한쪽을 바꾸면 다른 쪽도 고친다.
 */

/** 앱이 결과를 알릴 때 `window` 에 쏘는 이벤트 이름. 다운로드 브리지와 같은 통로를 쓴다. */
const NATIVE_EVENT = "sssok:native";

/** 값마다 뜻은 앱의 `shareBridge.ts` 의 `ShareEvent` 에 적혀 있다. */
export type NativeShareResult = "shared" | "dismissed" | "failed";

const ShareDoneSchema = z.object({
  type: z.literal("share/done"),
  requestId: z.string(),
  // 모르는 결과가 와도 답을 버리지 않는다. 버리면 공유 버튼이 영영 풀리지 않는다.
  result: z.enum(["shared", "dismissed", "failed"]).catch("failed"),
});

/**
 * 앱이 공유 브리지를 아는지. 이 표시가 없는 예전 앱 빌드에 메시지를 보내면 답이 영영 오지 않는다.
 */
export const canShareFromApp = (): boolean => isAppWebView() && (window.sssokApp?.share ?? 0) >= 1;

let sequence = 0;

export const shareFromApp = (url: string): Promise<NativeShareResult> =>
  new Promise<NativeShareResult>((resolve) => {
    sequence += 1;
    // 공유가 겹쳐도 다른 판의 답과 섞이지 않게 판마다 번호를 붙인다.
    const requestId = `${Date.now()}-${sequence}`;

    const listen = (event: Event): void => {
      const parsed = ShareDoneSchema.safeParse((event as CustomEvent<unknown>).detail);
      if (!parsed.success || parsed.data.requestId !== requestId) return;

      window.removeEventListener(NATIVE_EVENT, listen);
      resolve(parsed.data.result);
    };

    window.addEventListener(NATIVE_EVENT, listen);
    window.ReactNativeWebView?.postMessage(JSON.stringify({ type: "share/link", requestId, url }));
  });
