import { z } from "zod";
/**
 * 쏙 앱(`frontend/mobile`)의 웹뷰에 저장을 맡긴다.
 *
 * 웹뷰는 `<a download>` 도 `navigator.share` 도 보장하지 않는다. 그래서 앱 안에서는
 * 바이트를 받지 않고 **서명 URL·파일명·MIME 만** 넘긴다 — 앱이 직접 받아 사진첩에 넣거나
 * 공유 시트로 넘긴다. 바이트가 웹뷰 경계를 넘지 않으니 장수가 많아도 메모리에 쌓이지 않는다.
 *
 * 메시지 형식의 짝은 `frontend/mobile/src/bridge/downloadBridge.ts` 에 있다. 한쪽을 바꾸면 다른 쪽도 고친다.
 */

import { captureException } from "@/shared/lib";
import { prefersShareSheet } from "./prefersShareSheet";

/** 앱이 진행·결과를 알릴 때 `window` 에 쏘는 이벤트 이름. */
const NATIVE_EVENT = "sssok:native";

declare global {
  interface Window {
    /** react-native-webview 가 페이지에 심는 객체. 일반 브라우저에는 없다. */
    ReactNativeWebView?: { postMessage: (message: string) => void };
    /**
     * 앱이 페이지보다 먼저 심어 두는 표시. 브릿지를 모르는 예전 앱 빌드에는 없다 —
     * 그런 앱에 메시지를 보내면 답이 영영 오지 않으므로, 이 값이 있을 때만 앱에 맡긴다.
     */
    sssokApp?: {
      download?: number;
      auth?: ("kakao" | "apple")[];
      /** 링크 공유 브리지 버전. `features/share-room/lib/nativeShare.ts` 가 본다. */
      share?: number;
    };
  }
}

export const isNativeApp = (): boolean =>
  typeof window !== "undefined" &&
  window.ReactNativeWebView !== undefined &&
  (window.sssokApp?.download ?? 0) >= 1;

/** 낱장이 사진첩으로 가는지. 앱은 기기와 상관없이, 브라우저는 iOS 공유 시트를 쓸 때만이다. */
export const savesToPhotoLibrary = (): boolean => isNativeApp() || prefersShareSheet();

export interface NativeFile {
  id: number;
  url: string;
  fileName: string;
  mimeType: string;
}

/**
 * 앱 안에서 어느 단계가 실패했는지. 앱은 상태 코드가 없는 실패를 모두 0 으로 넘겨서,
 * 이 값이 없으면 받기가 끊긴 것과 사진첩이 거절한 것을 가를 수 없다.
 *
 * - `download` 스토리지에서 받다가 실패했다.
 * - `save`     받은 파일을 사진첩에 넣다가 실패했다.
 * - `share`    받은 파일을 공유 시트로 넘기다가 실패했다.
 */
export type NativeFailureStage = "download" | "save" | "share";

export interface NativeFailure {
  id: number;
  /** 0 이면 응답 자체를 못 받은 것이다. `FailedDownload.status` 와 같은 규칙이다. */
  status: number;
  /** 단계를 넘기기 전의 앱 빌드에서는 비어 있다. */
  stage?: NativeFailureStage;
  /** 앱이 받은 원래 에러 메시지. */
  message?: string;
}

type NativeRequest =
  { type: "download/save"; files: NativeFile[] } | { type: "download/share"; file: NativeFile };

const identity = { requestId: z.string() };
const NativeEventSchema = z.discriminatedUnion("type", [
  z.object({
    ...identity,
    type: z.literal("download/progress"),
    id: z.number(),
    loaded: z.number(),
    total: z.number(),
  }),
  z.object({ ...identity, type: z.literal("download/saved"), id: z.number() }),
  z.object({
    ...identity,
    type: z.literal("download/done"),
    result: z.enum(["completed", "permissionDenied", "cancelled"]),
    failed: z.array(
      z.object({
        id: z.number(),
        status: z.number(),
        // 모르는 단계가 와도 답 전체를 버리지 않는다. 버리면 진행 바가 영영 남는다.
        stage: z.enum(["download", "save", "share"]).optional().catch(undefined),
        message: z.string().optional().catch(undefined),
      }),
    ),
  }),
]);

export type NativeDownloadResult =
  | { type: "completed"; failed: NativeFailure[] }
  /** 사진첩 쓰기 권한을 받지 못했다. 한 장도 받지 않았다. */
  | { type: "permissionDenied" }
  | { type: "aborted" };

interface NativeDownloadParams {
  signal?: AbortSignal;
  /** `total` 은 응답에 길이가 없으면 -1 이다. 분모는 부르는 쪽이 정한다. */
  onProgress?: (id: number, loaded: number, total: number) => void;
  onSaved?: (id: number) => void;
}

let sequence = 0;

const post = (message: object): void | undefined =>
  window.ReactNativeWebView?.postMessage(JSON.stringify(message));

const requestNative = (
  request: NativeRequest,
  { signal, onProgress, onSaved }: NativeDownloadParams,
): Promise<NativeDownloadResult> =>
  new Promise<NativeDownloadResult>((resolve) => {
    if (signal?.aborted) {
      resolve({ type: "aborted" });
      return;
    }

    sequence += 1;

    // 판이 겹치거나 취소한 판의 답이 늦게 와도 섞이지 않게, 판마다 번호를 붙인다.
    const requestId = `${Date.now()}-${sequence}`;

    const settle = (result: NativeDownloadResult): void => {
      window.removeEventListener(NATIVE_EVENT, listen);
      signal?.removeEventListener("abort", abort);
      resolve(result);
    };

    // 이미 사진첩에 들어간 것은 되돌리지 않는다. 앱에는 남은 것만 그만두라고 알린다.
    const abort = (): void => {
      post({ type: "download/cancel", requestId });
      settle({ type: "aborted" });
    };

    const listen = (event: Event): void => {
      const parsed = NativeEventSchema.safeParse((event as CustomEvent<unknown>).detail);
      if (!parsed.success) return;
      const detail = parsed.data;

      if (detail?.requestId !== requestId) return;

      if (detail.type === "download/progress") {
        onProgress?.(detail.id, detail.loaded, detail.total);
        return;
      }

      if (detail.type === "download/saved") {
        onSaved?.(detail.id);
        return;
      }

      if (detail.result === "permissionDenied") {
        settle({ type: "permissionDenied" });
      } else if (detail.result === "cancelled") {
        settle({ type: "aborted" });
      } else {
        settle({ type: "completed", failed: detail.failed });
      }
    };

    window.addEventListener(NATIVE_EVENT, listen);
    signal?.addEventListener("abort", abort, { once: true });
    post({ ...request, requestId });
  });

/** 사진·영상을 사진첩에 넣는다. 실패한 것만 `failed` 로 돌아오고 나머지는 저장된 것이다. */
export const saveToPhotoLibrary = (
  files: NativeFile[],
  params: NativeDownloadParams = {},
): Promise<NativeDownloadResult> => requestNative({ type: "download/save", files }, params);

/** 파일 하나를 받아 공유 시트로 넘긴다. zip 처럼 사진첩에 들어갈 수 없는 파일에 쓴다. */
export const shareFromNativeApp = (
  file: NativeFile,
  params: NativeDownloadParams = {},
): Promise<NativeDownloadResult> => requestNative({ type: "download/share", file }, params);

/**
 * 앱에서 난 실패를 PostHog 예외로 남긴다. 웹이 직접 받는 경로는 `fetchMediaBlob` 이 남기지만,
 * 앱 경로는 웹에 결과만 오므로 여기서 남기지 않으면 원인이 어디에도 없다.
 */
export const reportNativeFailure = (
  { status, stage, message }: NativeFailure,
  operation: "download.native_save" | "download.native_share",
): void =>
  captureException(new Error(message ?? "앱에서 파일을 저장하지 못했습니다."), {
    level: "error",
    operation,
    status,
    code: `NATIVE_${(stage ?? "unknown").toUpperCase()}_FAILED`,
  });
