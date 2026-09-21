import { useQuery } from "@tanstack/react-query";

import { getAdminAccounts } from "../api/getAdminAccounts";

/** `["admin"]` 아래에 둔다 — 로그아웃할 때 다른 관리자 캐시와 함께 버려진다. */
export const adminAccountsQueryKey = ["admin", "accounts"] as const;

/** token 을 queryKey 에 넣지 않는 이유는 `useAdminFeedbacksQuery` 와 같다. */
export const useAdminAccountsQuery = (token: string) =>
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  useQuery({
    queryKey: adminAccountsQueryKey,
    queryFn: () => getAdminAccounts({ token }),
  });
