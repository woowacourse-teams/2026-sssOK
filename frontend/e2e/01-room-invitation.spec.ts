import { test, expect } from "./support/fixtures";
import { createRoom, copyInvite, joinRoom } from "./support/room";
import { expectPhotos } from "./support/media";

test("방을 만들고 사람을 초대하기 · E01–E03", async ({ users: { a, b } }) => {
  const room = await test.step("E01 · 누구나 업로드 방 생성 후 목록에서 다시 열기", async () => {
    const created = await createRoom(a);
    await expectPhotos(a, []);
    await a.getByRole("button", { name: "내 방 목록", exact: true }).click();
    const link = a.getByRole("link").filter({ hasText: created.name });
    await expect(link).toHaveCount(1);
    await expect(link.getByText("방장", { exact: true })).toBeVisible();
    await link.click();
    await expect(a).toHaveURL(created.gallery);
    await expectPhotos(a, []);
    return created;
  });

  const invite = await test.step("E02 · A의 초대 링크로 비로그인 B 입장", async () => {
    const link = await copyInvite(a, room);
    await joinRoom(b, link, "게스트B");
    await expect(
      b.getByRole("heading", { name: `${room.name} 갤러리에 입장했어요.` }),
    ).toBeVisible();
    await expect(b.getByRole("button", { name: "홈으로", exact: true })).toBeVisible();
    return link;
  });

  await test.step("E03 · B가 같은 초대 링크로 재방문하면 이름과 익명 세션 유지", async () => {
    const firstSession = await b.evaluate(
      (code) => localStorage.getItem(`sssok.auth:${code}`),
      room.code,
    );
    expect(firstSession).not.toBeNull();
    await b.goto(invite);
    await expect(b).toHaveURL(room.gallery);
    await expect(b.getByRole("textbox", { name: "표시할 이름", exact: true })).toHaveCount(0);
    const session = await b.evaluate(
      (code) => JSON.parse(localStorage.getItem(`sssok.auth:${code}`)!),
      room.code,
    );
    expect(session.userId).toBe(JSON.parse(firstSession!).userId);
    expect(session.nickname).toBe("게스트B");
  });
});
