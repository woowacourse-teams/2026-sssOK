import { apiClient } from "@/shared/api";
import type { AdminAccountList } from "../model/types";

interface GetAdminAccountsParams {
  token: string;
}

/** 계정 수가 적어 페이지네이션이 없다. 한 번에 전부 받는다. */
export const getAdminAccounts = ({ token }: GetAdminAccountsParams) =>
  apiClient<AdminAccountList>("/admin/accounts", { token });
