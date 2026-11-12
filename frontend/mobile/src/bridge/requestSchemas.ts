import { z } from "zod";
const identity = {
  requestId: z.string().regex(/^[A-Za-z0-9-]{1,64}$/),
  type: z.literal("auth/login"),
};
export const AuthRequestSchema = z.discriminatedUnion("provider", [
  z.object({ ...identity, provider: z.literal("kakao") }),
  z.object({
    ...identity,
    provider: z.literal("apple"),
    nonce: z.string().regex(/^[A-Za-z0-9_-]{43}$/),
  }),
]);
export type AuthRequest = z.infer<typeof AuthRequestSchema>;

export const BridgeFileSchema = z.object({
  id: z.number(),
  url: z.string(),
  fileName: z.string(),
  mimeType: z.string(),
});
export const BridgeRequestSchema = z.discriminatedUnion("type", [
  z.object({
    type: z.literal("download/save"),
    requestId: z.string(),
    files: z.array(BridgeFileSchema),
  }),
  z.object({ type: z.literal("download/share"), requestId: z.string(), file: BridgeFileSchema }),
  z.object({ type: z.literal("download/cancel"), requestId: z.string() }),
]);
export type BridgeRequest = z.infer<typeof BridgeRequestSchema>;
export type BridgeFile = z.infer<typeof BridgeFileSchema>;

/** URL 정책은 앱의 개발/운영 환경에서 전달한다. */
export function parseDownloadRequest(
  raw: string,
  isAllowedUrl: (url: string) => boolean,
): BridgeRequest | null {
  try {
    const result = BridgeRequestSchema.safeParse(JSON.parse(raw));
    if (!result.success) return null;
    const request = result.data;
    const files =
      request.type === "download/save"
        ? request.files
        : request.type === "download/share"
          ? [request.file]
          : [];
    return files.every((file) => isAllowedUrl(file.url)) ? request : null;
  } catch {
    return null;
  }
}
