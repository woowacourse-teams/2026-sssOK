import { apiClient } from "@/shared/api";

export interface AnonymousAuth {
  accessToken: string;
  userId: number;
  nickname: string;
  expiresAt: string;
}

/** 회원가입 없이 이름만으로 토큰을 발급받는다. 부를 때마다 서버에 새 회원이 생긴다. */
export const postAnonymousAuth = (nickname: string) =>
  apiClient<AnonymousAuth>("/auth/anonymous", {
    method: "POST",
    body: JSON.stringify({ nickname }),
  });
