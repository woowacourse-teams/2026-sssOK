import { File, Paths } from "expo-file-system";
import { Asset, requestPermissionsAsync } from "expo-media-library";
import * as Sharing from "expo-sharing";

/**
 * 웹이 넘긴 서명 URL 을 받아 사진첩에 넣거나 공유 시트로 넘긴다 (#615).
 *
 * 메시지 형식의 짝은 웹의 `frontend/src/features/download-media/lib/nativeApp.ts` 에 있다.
 * 한쪽을 바꾸면 다른 쪽도 고친다.
 */

/** 웹이 이 값을 보고 앱에 저장을 맡긴다. 형식이 호환되지 않게 바뀌면 올린다. */
export const DOWNLOAD_BRIDGE_VERSION = 1;

/** 웹 `DOWNLOAD_CONCURRENCY` 보다 적게 둔다. 사진첩 쓰기가 받기보다 느려 몰아도 빨라지지 않는다. */
const CONCURRENCY = 3;

/** 진행률을 웹에 알리는 최소 간격(ms). 매 청크마다 웹뷰에 스크립트를 넣으면 화면이 버벅인다. */
const PROGRESS_INTERVAL_MS = 200;

interface BridgeFile {
  id: number;
  url: string;
  fileName: string;
  mimeType: string;
}

interface Failure {
  id: number;
  status: number;
}

export type BridgeEvent =
  | { type: "download/progress"; requestId: string; id: number; loaded: number; total: number }
  | { type: "download/saved"; requestId: string; id: number }
  | {
      type: "download/done";
      requestId: string;
      result: "completed" | "permissionDenied" | "cancelled";
      failed: Failure[];
    };

type BridgeRequest =
  | { type: "download/save"; requestId: string; files: BridgeFile[] }
  | { type: "download/share"; requestId: string; file: BridgeFile }
  | { type: "download/cancel"; requestId: string };

const isString = (value: unknown): value is string => typeof value === "string";

/** 서명 URL 만 받는다. 로컬 HTTP 는 웹 주소(`config/web.ts`)와 같이 개발 모드에서만 연다. */
const isAllowedUrl = (url: string) => {
  try {
    const { protocol } = new URL(url);

    return protocol === "https:" || (__DEV__ && protocol === "http:");
  } catch {
    return false;
  }
};

const isBridgeFile = (value: unknown): value is BridgeFile => {
  const file = value as BridgeFile | null;

  return (
    typeof file?.id === "number" &&
    isString(file.url) &&
    isAllowedUrl(file.url) &&
    isString(file.fileName) &&
    isString(file.mimeType)
  );
};

/** 웹에서 온 값은 믿지 않는다. 모양이 어긋나면 통째로 버린다. */
const parseRequest = (data: string): BridgeRequest | null => {
  try {
    const message = JSON.parse(data) as Partial<BridgeRequest> & Record<string, unknown>;

    if (!isString(message.requestId)) return null;

    if (message.type === "download/cancel") return message as BridgeRequest;

    if (
      message.type === "download/save" &&
      Array.isArray(message.files) &&
      message.files.every(isBridgeFile)
    ) {
      return message as BridgeRequest;
    }

    if (message.type === "download/share" && isBridgeFile(message.file)) {
      return message as BridgeRequest;
    }

    return null;
  } catch {
    return null;
  }
};

/** 네이티브 다운로드는 상태 코드를 메시지로만 알려준다 ("response has status 403"). 없으면 회선 실패로 본다. */
const statusOf = (error: unknown) => {
  const matched = error instanceof Error ? /status:? (\d{3})/.exec(error.message) : null;

  return matched === null ? 0 : Number(matched[1]);
};

/** 경로를 벗어나는 글자만 막는다. 이름은 사진첩·공유 시트에 그대로 보인다. */
const safeNameOf = (fileName: string) => fileName.replace(/[/\\]/g, "_") || "file";

const runWithLimit = async <T>(items: T[], limit: number, task: (item: T) => Promise<void>) => {
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const item = items[next];

      next += 1;
      await task(item);
    }
  };

  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
};

export const createDownloadBridge = (send: (event: BridgeEvent) => void) => {
  const running = new Map<string, AbortController>();

  /**
   * 캐시에 받아 둔 뒤 넘기고, 넘기고 나면 지운다. 이름 앞에 판 번호를 붙여
   * 같은 이름의 사진이 동시에 받아져도 서로 덮지 않게 한다.
   */
  const download = async (
    requestId: string,
    index: number,
    file: BridgeFile,
    signal: AbortSignal,
  ) => {
    let lastSentAt = 0;
    const destination = new File(
      Paths.cache,
      `sssok-${requestId}-${index}-${safeNameOf(file.fileName)}`,
    );

    return File.downloadFileAsync(file.url, destination, {
      idempotent: true,
      signal,
      onProgress: ({ bytesWritten, totalBytes }) => {
        const now = Date.now();

        if (now - lastSentAt < PROGRESS_INTERVAL_MS && bytesWritten !== totalBytes) return;

        lastSentAt = now;
        send({
          type: "download/progress",
          requestId,
          id: file.id,
          loaded: bytesWritten,
          total: totalBytes,
        });
      },
    });
  };

  const removeQuietly = (file: File | undefined) => {
    try {
      if (file?.exists) file.delete();
    } catch {
      // 캐시는 OS 가 비운다. 지우지 못했다고 저장 결과를 바꾸지 않는다.
    }
  };

  const save = async (requestId: string, files: BridgeFile[], signal: AbortSignal) => {
    // 읽기 권한은 필요 없다. 쓰기만 요청해야 "모든 사진 접근" 을 묻지 않는다.
    // 종류(photo·video)는 넘기지 않는다. 쓰기 전용이면 쓰이지 않고, 안드로이드 Expo Go 는 넘기는 순간 예외를 던진다.
    const permission = await requestPermissionsAsync(true);

    if (!permission.granted) {
      send({ type: "download/done", requestId, result: "permissionDenied", failed: [] });
      return;
    }

    const failed: Failure[] = [];

    await runWithLimit(
      files.map((file, index) => ({ file, index })),
      CONCURRENCY,
      async ({ file, index }) => {
        if (signal.aborted) return;

        let downloaded: File | undefined;

        try {
          downloaded = await download(requestId, index, file, signal);
          await Asset.create(downloaded.uri);
          send({ type: "download/saved", requestId, id: file.id });
        } catch (error) {
          if (!signal.aborted) failed.push({ id: file.id, status: statusOf(error) });
        } finally {
          removeQuietly(downloaded);
        }
      },
    );

    send({
      type: "download/done",
      requestId,
      result: signal.aborted ? "cancelled" : "completed",
      failed,
    });
  };

  /** 사용자가 시트를 닫아도 실패가 아니다. 무엇을 골랐는지는 OS 가 알려주지 않는다. */
  const share = async (requestId: string, file: BridgeFile, signal: AbortSignal) => {
    let downloaded: File | undefined;

    try {
      downloaded = await download(requestId, 0, file, signal);
      await Sharing.shareAsync(downloaded.uri, {
        mimeType: file.mimeType,
        dialogTitle: file.fileName,
      });
      send({ type: "download/done", requestId, result: "completed", failed: [] });
    } catch (error) {
      send({
        type: "download/done",
        requestId,
        result: signal.aborted ? "cancelled" : "completed",
        failed: signal.aborted ? [] : [{ id: file.id, status: statusOf(error) }],
      });
    } finally {
      removeQuietly(downloaded);
    }
  };

  const handle = (data: string) => {
    const request = parseRequest(data);

    if (request === null) return;

    if (request.type === "download/cancel") {
      running.get(request.requestId)?.abort();
      return;
    }

    if (running.has(request.requestId)) return;

    const { requestId } = request;
    const controller = new AbortController();
    const files = request.type === "download/save" ? request.files : [request.file];
    const work =
      request.type === "download/save"
        ? save(requestId, request.files, controller.signal)
        : share(requestId, request.file, controller.signal);

    running.set(requestId, controller);
    void work
      // 권한 요청처럼 예상 밖에서 무너져도 답은 보낸다. 안 보내면 웹의 진행 바가 영영 남는다.
      .catch(() =>
        send({
          type: "download/done",
          requestId,
          result: "completed",
          failed: files.map(({ id }) => ({ id, status: 0 })),
        }),
      )
      .finally(() => running.delete(requestId));
  };

  /** 웹뷰가 죽거나 화면이 내려가면 받던 것을 모두 그만둔다. 답을 받을 페이지가 없다. */
  const cancelAll = () => {
    running.forEach((controller) => controller.abort());
    running.clear();
  };

  return { handle, cancelAll };
};
