import { z } from "zod";
export const ReportReasonSchema = z.union([
  z.literal("INAPPROPRIATE_CONTENT"),
  z.literal("PRIVACY_VIOLATION"),
  z.literal("COPYRIGHT_VIOLATION"),
  z.literal("HARASSMENT"),
]);
export const ReportMediaResponseSchema = z.object({
  mediaId: z.number(),
  reason: ReportReasonSchema,
  reportedAt: z.string(),
});
import { apiClient } from "@/shared/api";

export type ReportReason = z.infer<typeof ReportReasonSchema>;

type ReportMediaResponse = z.infer<typeof ReportMediaResponseSchema>;

export const reportMedia = ({
  roomId,
  mediaId,
  token,
  reason,
}: {
  roomId: number;
  mediaId: number;
  token: string;
  reason: ReportReason;
}): Promise<ReportMediaResponse> =>
  apiClient<ReportMediaResponse>(`/rooms/${roomId}/media/${mediaId}/reports`, {
    schema: ReportMediaResponseSchema,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    token,
    body: JSON.stringify({ reason }),
  });
