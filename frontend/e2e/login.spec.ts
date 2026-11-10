import { expect, test } from "@playwright/test";

test("비로그인 사용자가 소셜 로그인 화면에 진입한다", async ({ page }) => {
  await test.step("로그인 화면으로 이동한다", async () => {
    await page.goto("/app/login");
  });

  await test.step("로그인 수단과 이용약관 링크를 확인한다", async () => {
    await expect(page.getByRole("button", { name: "카카오 로그인" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Apple로 로그인" })).toBeVisible();
    await expect(page.getByRole("link", { name: "이용약관" })).toHaveAttribute("href", "/terms");
  });
});
