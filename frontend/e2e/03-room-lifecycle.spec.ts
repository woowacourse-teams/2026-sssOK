import { test, expect } from "./support/fixtures";
import { prepareRoom, roomMenu } from "./support/room";
import { makePhotos, upload, expectPhotos, expectDetail } from "./support/media";

test("권한을 바꾸고 방 이용을 끝내기 · E10–E12", async ({ users: { a, b }, browser, baseURL }) => {
  test.setTimeout(180_000);
  const room = await prepareRoom(a, b);
  const [sea, mountain, cat, extra] = await makePhotos(a);
  await upload(a, [sea, mountain]);
  await upload(b, [cat]);
  await expectPhotos(a, [sea.name, mountain.name, cat.name]);

  await test.step("E10 · 방장만 업로드로 변경하고 참여자 조회와 방장 업로드 유지", async () => {
    await roomMenu(a, "방 설정");
    await a
      .getByRole("radiogroup", { name: "업로드 권한", exact: true })
      .getByText("방장만", { exact: true })
      .click();
    await expect(a.getByRole("radio", { name: "방장만", exact: true })).toBeChecked();
    await a.getByRole("button", { name: "변경 사항 저장", exact: true }).click();
    await expect(a).toHaveURL(room.gallery);
    await b.goto(room.gallery);
    await expectPhotos(b, [sea.name, mountain.name, cat.name]);
    await expect(b.getByRole("button", { name: "사진 올리기", exact: true })).toHaveCount(0);
    await expect(b.locator('input[type="file"]')).toHaveCount(0);
    await upload(a, [extra]);
    await expectPhotos(b, [sea.name, mountain.name, cat.name, extra.name]);
  });

  await test.step("E11 · B가 홈으로 나간 뒤 링크로 재입장하고 A의 방과 사진 유지", async () => {
    const firstSession = await b.evaluate(
      (code) => JSON.parse(localStorage.getItem(`sssok.auth:${code}`)!),
      room.code,
    );
    await b.getByRole("button", { name: "홈으로", exact: true }).click();
    await expect(b).toHaveURL(/\/home$/);
    await b.goto(room.invite);
    await expect(b).toHaveURL(room.gallery);
    await expect(b.getByRole("textbox", { name: "표시할 이름", exact: true })).toHaveCount(0);
    await expectPhotos(b, [sea.name, mountain.name, cat.name, extra.name]);
    const session = await b.evaluate(
      (code) => JSON.parse(localStorage.getItem(`sssok.auth:${code}`)!),
      room.code,
    );
    expect(session.userId).toBe(firstSession.userId);
    await a.getByRole("button", { name: "내 방 목록", exact: true }).click();
    await a.getByRole("link").filter({ hasText: room.name }).click();
    await expect(a).toHaveURL(room.gallery);
    await expectPhotos(a, [sea.name, mountain.name, cat.name, extra.name]);
    await expectDetail(a, cat.name);
    await a.getByRole("button", { name: "갤러리로 돌아가기", exact: true }).click();
  });

  await test.step("E12 · 방 삭제 후 최초 초대 링크로 접근 차단", async () => {
    await roomMenu(a, "방 삭제");
    await a.getByRole("button", { name: "삭제하기", exact: true }).click();
    await expect(a).not.toHaveURL(room.gallery);
    const fresh = await browser.newContext({ baseURL, storageState: { cookies: [], origins: [] } });
    try {
      const page = await fresh.newPage();
      await page.goto(room.invite);
      await expect(
        page.getByRole("heading", { name: "이 방은 사라졌어요", exact: true }),
      ).toBeVisible();
      await expect(page.getByRole("textbox", { name: "표시할 이름", exact: true })).toHaveCount(0);
      await expect(page.getByRole("button", { name: / 크게 보기$/ })).toHaveCount(0);
    } finally {
      await fresh.close();
    }
  });
});
