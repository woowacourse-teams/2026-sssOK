import { existsSync } from "node:fs";
import { expect, type Page } from "@playwright/test";

export async function loginAsA(page: Page) {
  if (existsSync(".env.local")) process.loadEnvFile(".env.local");
  const email = process.env.E2E_KAKAO_EMAIL;
  const password = process.env.E2E_KAKAO_PASSWORD;
  if (!email || !password) {
    throw new Error(".env.local에 E2E_KAKAO_EMAIL과 E2E_KAKAO_PASSWORD를 설정하세요.");
  }

  console.log("[로그인 1/4] 쏙 로그인 화면 접속");
  await page.goto("/app/login");
  console.log("[로그인 2/4] 카카오 로그인 화면으로 이동");
  await page.getByRole("button", { name: "카카오 로그인", exact: true }).click();
  await page.waitForURL((url) => url.hostname === "accounts.kakao.com", { timeout: 30_000 });
  console.log("[로그인 3/4] 계정 정보 입력");
  await page.locator('input[name="loginId"]').fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole("button", { name: /^(로그인|Log In)$/i }).click();
  console.log(
    "[로그인 4/4] 쏙 방 목록으로 복귀 대기. 추가 인증이 있으면 열린 브라우저에서 완료하세요.",
  );
  await page.waitForURL((url) => url.pathname === "/rooms", { timeout: 60_000 });
  await expect(page.getByRole("heading", { name: "내 방", exact: true })).toBeVisible();
  console.log("[로그인 완료] 현재 브라우저에서 테스트를 진행합니다.");
}
