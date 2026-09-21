import { apiClient } from "@/shared/api";
import type { AdminFeedbackPage } from "../model/types";

interface GetAdminFeedbacksParams {
  token: string;
  /** 직전 응답의 `nextCursor`. 없으면 최신부터. */
  cursor?: number;
  /** 한 번에 가져올 개수. 서버 기본 20, 최대 100. */
  size?: number;
}

export const getAdminFeedbacks = ({ token, cursor, size }: GetAdminFeedbacksParams) => {
  const query = new URLSearchParams();

  // 안 보낸 것과 "빈 값을 보낸 것" 은 다르다 — 빈 값은 서버가 400 으로 돌려준다.
  if (cursor !== undefined) query.set("cursor", String(cursor));
  if (size !== undefined) query.set("size", String(size));

  const search = query.toString();

  return apiClient<AdminFeedbackPage>(`/admin/feedbacks${search ? `?${search}` : ""}`, { token });
};
