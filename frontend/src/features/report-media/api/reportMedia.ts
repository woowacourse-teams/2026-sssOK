import { apiClient } from "@/shared/api";

export type ReportReason =
  "INAPPROPRIATE_CONTENT" | "PRIVACY_VIOLATION" | "COPYRIGHT_VIOLATION" | "HARASSMENT";

interface ReportMediaResponse {
  mediaId: number;
  reason: ReportReason;
  reportedAt: string;
}

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
}) =>
  apiClient<ReportMediaResponse>(`/rooms/${roomId}/media/${mediaId}/reports`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    token,
    body: JSON.stringify({ reason }),
  });
