import { File } from "expo-file-system";
import { ImageManipulator, SaveFormat } from "expo-image-manipulator";
import { getThumbnailAsync } from "expo-video-thumbnails";
import { z } from "zod";

import { clearInbox, findInInbox, isVideo, listInbox, type SharedItem } from "./inbox";

export const INCOMING_SHARE_BRIDGE_VERSION = 1;

const PROGRESS_INTERVAL_MS = 200;
const THUMBNAIL_WIDTH = 240;
const THUMBNAIL_CONCURRENCY = 2;

const requestIdSchema = z.string().regex(/^[A-Za-z0-9-]{1,64}$/);

const IncomingShareRequestSchema = z.discriminatedUnion("type", [
  z.object({ type: z.literal("share/ready") }),
  z.object({
    type: z.literal("share/put"),
    requestId: requestIdSchema,
    localId: z.string().min(1).max(255),
    url: z.string().max(4096),
    headers: z.record(z.string(), z.string()),
  }),
  z.object({ type: z.literal("share/cancel"), requestId: requestIdSchema }),
  z.object({ type: z.literal("share/clear") }),
]);

type IncomingShareRequest = z.infer<typeof IncomingShareRequestSchema>;

export interface SharedItemInfo {
  localId: string;
  fileName: string;
  mimeType: string;
  size: number;
}

export type IncomingShareEvent =
  | { type: "share/received"; items: SharedItemInfo[] }
  | { type: "share/thumbnail"; localId: string; dataUrl: string }
  | { type: "share/progress"; requestId: string; loaded: number; total: number }
  | {
      type: "share/put-done";
      requestId: string;
      result: "success" | "failure" | "aborted";
      status: number;
    };

const SHARE_TYPES = new Set(["share/ready", "share/put", "share/cancel", "share/clear"]);

const isAllowedUrl = (url: string): boolean => {
  try {
    const { protocol } = new URL(url);

    return protocol === "https:" || (__DEV__ && protocol === "http:");
  } catch {
    return false;
  }
};

const parseRequest = (raw: string): IncomingShareRequest | null | undefined => {
  let message: unknown;

  try {
    message = JSON.parse(raw);
  } catch {
    return undefined;
  }

  const type = (message as { type?: unknown } | null)?.type;

  if (typeof type !== "string" || !SHARE_TYPES.has(type)) return undefined;

  const parsed = IncomingShareRequestSchema.safeParse(message);

  return parsed.success ? parsed.data : null;
};

const infoOf = ({ localId, fileName, mimeType, size }: SharedItem): SharedItemInfo => ({
  localId,
  fileName,
  mimeType,
  size,
});

const signatureOf = (items: SharedItem[]): string =>
  items.map((item) => `${item.localId}:${item.size}`).join("|");

const thumbnailOf = async (item: SharedItem): Promise<string> => {
  const source = isVideo(item.fileName)
    ? (await getThumbnailAsync(item.uri, { time: 0, quality: 0.6 })).uri
    : item.uri;
  const rendered = await ImageManipulator.manipulate(source)
    .resize({ width: THUMBNAIL_WIDTH })
    .renderAsync();
  const saved = await rendered.saveAsync({ compress: 0.6, format: SaveFormat.JPEG, base64: true });

  return `data:image/jpeg;base64,${saved.base64 ?? ""}`;
};

export interface IncomingShareBridge {
  handle: (raw: string) => boolean;
  refresh: () => void;
  resetReady: () => void;
  cancelAll: () => void;
}

export function createIncomingShareBridge(
  send: (event: IncomingShareEvent) => void,
): IncomingShareBridge {
  const running = new Map<string, AbortController>();
  const thumbnails = new Map<string, string>();
  let webReady = false;
  let sentSignature: string | null = null;
  let thumbnailRun = 0;

  const sendThumbnails = async (items: SharedItem[]): Promise<void> => {
    const run = ++thumbnailRun;
    let next = 0;

    const worker = async (): Promise<void> => {
      while (next < items.length && run === thumbnailRun && webReady) {
        const item = items[next];

        next += 1;

        try {
          const dataUrl = thumbnails.get(item.localId) ?? (await thumbnailOf(item));

          thumbnails.set(item.localId, dataUrl);
          if (run === thumbnailRun && webReady) {
            send({ type: "share/thumbnail", localId: item.localId, dataUrl });
          }
        } catch {}
      }
    };

    await Promise.all(Array.from({ length: THUMBNAIL_CONCURRENCY }, worker));
  };

  const announce = (force: boolean): void => {
    if (!webReady) return;

    const items = listInbox();
    const signature = signatureOf(items);

    if (!force && signature === sentSignature) return;
    if (items.length === 0 && sentSignature === null) return;

    sentSignature = signature;
    send({ type: "share/received", items: items.map(infoOf) });

    const alive = new Set(items.map((item) => item.localId));

    [...thumbnails.keys()].forEach((key) => !alive.has(key) && thumbnails.delete(key));
    void sendThumbnails(items);
  };

  const put = async (
    request: Extract<IncomingShareRequest, { type: "share/put" }>,
    signal: AbortSignal,
  ): Promise<void> => {
    const { requestId, localId, url, headers } = request;
    const done = (result: "success" | "failure" | "aborted", status: number): void =>
      send({ type: "share/put-done", requestId, result, status });

    const item = findInInbox(localId);

    if (item === null || !isAllowedUrl(url)) {
      done("failure", 0);
      return;
    }

    let lastSentAt = 0;

    try {
      const response = await new File(item.uri).upload(url, {
        httpMethod: "PUT",
        headers,
        signal,
        onProgress: ({ bytesSent, totalBytes }) => {
          const now = Date.now();

          if (now - lastSentAt < PROGRESS_INTERVAL_MS && bytesSent !== totalBytes) return;
          lastSentAt = now;
          send({ type: "share/progress", requestId, loaded: bytesSent, total: totalBytes });
        },
      });

      done(
        response.status >= 200 && response.status < 300 ? "success" : "failure",
        response.status,
      );
    } catch {
      done(signal.aborted ? "aborted" : "failure", 0);
    }
  };

  const cancelAll = (): void => {
    running.forEach((controller) => controller.abort());
    running.clear();
  };

  const handle = (raw: string): boolean => {
    const request = parseRequest(raw);

    if (request === undefined) return false;
    if (request === null) return true;

    switch (request.type) {
      case "share/ready":
        webReady = true;
        announce(true);
        return true;
      case "share/cancel":
        running.get(request.requestId)?.abort();
        return true;
      case "share/clear":
        cancelAll();
        clearInbox();
        thumbnails.clear();
        announce(true);
        return true;
      case "share/put": {
        if (running.has(request.requestId)) return true;

        const controller = new AbortController();

        running.set(request.requestId, controller);
        void put(request, controller.signal).finally(() => running.delete(request.requestId));
        return true;
      }
    }
  };

  return {
    handle,
    refresh: () => announce(false),
    resetReady: () => {
      webReady = false;
      sentSignature = null;
      thumbnailRun += 1;
    },
    cancelAll,
  };
}
