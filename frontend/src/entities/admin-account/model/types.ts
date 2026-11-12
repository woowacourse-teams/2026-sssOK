import { z } from "zod";
export const AdminAccountSchema = z.object({
  adminId: z.number(),
  loginId: z.string(),
  name: z.string(),
  /** `SUPER_ADMIN`·`ADMIN`. 유니온으로 좁히지 않는 이유는 `AdminSession.role` 과 같다. */
  role: z.string(),
  /** ISO 8601 (UTC). */
  createdAt: z.string(),
});
export const AdminAccountListSchema = z.object({
  /** 서버가 id 오름차순으로 준다 — 먼저 만든 계정이 위에 온다. */
  accounts: z.array(AdminAccountSchema),
});
/** 관리자 계정 한 건 (`GET /admin/accounts`). 비밀번호는 해시값도 내려오지 않는다. */
export type AdminAccount = z.infer<typeof AdminAccountSchema>;

export type AdminAccountList = z.infer<typeof AdminAccountListSchema>;
