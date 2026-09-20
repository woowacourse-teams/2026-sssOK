import { apiClient } from "@/shared/api";
import type { AdminLoginRequest, AdminSession } from "../model/types";

/**
 * 관리자 로그인. 익명 인증(`POST /auth/anonymous`)과 완전히 별개의 경로다.
 *
 * 토큰을 싣지 않고 부른다 — 로그인 자체는 누구나 호출할 수 있고, 토큰을 실으면 401 응답이
 * `apiClient` 의 방 세션 복구 흐름을 건드린다.
 */
export const loginAdmin = (request: AdminLoginRequest) =>
  apiClient<AdminSession>("/admin/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
