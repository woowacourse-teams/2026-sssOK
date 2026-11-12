import { expect, type Page } from "@playwright/test";

export interface TestRoom {
  name: string;
  gallery: string;
  code: string;
}

export async function createRoom(page: Page): Promise<TestRoom> {
  // 방 제목 제한 12자 안에서 실행별 이름을 만든다.
  const name = `여행 사진 ${crypto.randomUUID().slice(0, 5)}`;
  await page.getByRole("button", { name: "방 만들기", exact: true }).click();
  await page.getByRole("textbox", { name: "방 제목" }).fill(name);
  await page.getByRole("textbox", { name: "내 이름" }).fill("방장A");
  await page
    .getByRole("radiogroup", { name: "업로드 권한", exact: true })
    .getByText("누구나", { exact: true })
    .click();
  await expect(page.getByRole("radio", { name: "누구나", exact: true })).toBeChecked();
  await page.getByRole("button", { name: "방 만들기", exact: true }).click();
  await expect(page).toHaveURL(/\/rooms\/[^/]+\/gallery$/);
  await expect(page.getByRole("heading", { name: `${name} 갤러리에 입장했어요.` })).toBeVisible();
  const gallery = page.url();
  const code = new URL(gallery).pathname.split("/")[2];
  return { name, gallery, code };
}

export async function copyInvite(page: Page, room: TestRoom): Promise<string> {
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"], {
    origin: new URL(page.url()).origin,
  });
  await page.getByRole("button", { name: "내 방 목록", exact: true }).click();
  await page.getByRole("button", { name: `${room.name} 메뉴`, exact: true }).click();
  await page.getByRole("button", { name: "초대 링크 복사", exact: true }).click();
  await expect(page.getByText("초대 링크를 복사했어요.", { exact: true })).toBeVisible();
  const invite = await page.evaluate(() => navigator.clipboard.readText());
  expect(new URL(invite).pathname).toBe(`/join/${room.code}`);
  return invite;
}

export async function joinRoom(page: Page, invite: string, nickname = "게스트B"): Promise<void> {
  await page.goto(invite);
  await expect(page.getByRole("textbox", { name: "표시할 이름", exact: true })).toBeVisible();
  await page.getByRole("textbox", { name: "표시할 이름", exact: true }).fill(nickname);
  await page.getByRole("button", { name: "입장하기", exact: true }).click();
  await expect(page).toHaveURL(/\/gallery$/);
  await expect(
    page.getByRole("button", { name: "전체 사진 및 동영상, 0개", exact: true }),
  ).toBeVisible();
}

export async function prepareRoom(
  a: Page,
  b: Page,
): Promise<{ invite: string; name: string; gallery: string; code: string }> {
  const room = await createRoom(a);
  const invite = await copyInvite(a, room);
  await joinRoom(b, invite);
  await a.getByRole("link").filter({ hasText: room.name }).click();
  await expect(a).toHaveURL(room.gallery);
  return { ...room, invite };
}

export async function roomMenu(page: Page, item: string): Promise<void> {
  await page.getByRole("button", { name: "방 메뉴 열기", exact: true }).click();
  await page.getByRole("button", { name: item, exact: true }).click();
}
