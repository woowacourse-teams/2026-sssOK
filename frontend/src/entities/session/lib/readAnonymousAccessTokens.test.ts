import { readAnonymousAccessTokens } from "./readAnonymousAccessTokens";

const session = (accessToken: string, expiresAt: string) =>
  JSON.stringify({ accessToken, userId: 1, nickname: "익명", expiresAt });

afterEach(() => localStorage.clear());

it("만료되지 않은 방 세션의 토큰만 중복 없이 모은다", () => {
  localStorage.setItem("sssok.auth:A", session("same", "2099-01-01T00:00:00Z"));
  localStorage.setItem("sssok.auth:B", session("same", "2099-01-01T00:00:00Z"));
  localStorage.setItem("sssok.auth:C", session("other", "2099-01-01T00:00:00Z"));
  localStorage.setItem("sssok.auth:OLD", session("expired", "2000-01-01T00:00:00Z"));
  localStorage.setItem("setting", "keep");

  expect(readAnonymousAccessTokens().sort()).toEqual(["other", "same"]);
  expect(localStorage.getItem("sssok.auth:OLD")).toBeNull();
});

it("서버가 받는 100개까지만 보낸다", () => {
  for (let i = 0; i < 120; i++) {
    localStorage.setItem(`sssok.auth:R${i}`, session(`t${i}`, "2099-01-01T00:00:00Z"));
  }

  expect(readAnonymousAccessTokens()).toHaveLength(100);
});

it("세션이 없으면 빈 목록이다", () => {
  expect(readAnonymousAccessTokens()).toEqual([]);
});
