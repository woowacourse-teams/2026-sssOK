import { test as base, expect, type Page } from "@playwright/test";

import { loginAsA } from "./login";

export const test = base.extend<{ users: { a: Page; b: Page } }>({
  users: [
    async ({ browser, baseURL }, use) => {
      const aContext = await browser.newContext({
        baseURL,
        storageState: { cookies: [], origins: [] },
      });
      const bContext = await browser.newContext({
        baseURL,
        storageState: { cookies: [], origins: [] },
      });
      const a = await aContext.newPage();
      const b = await bContext.newPage();
      const rooms: { url: string; token: string; deleted: boolean }[] = [];
      const registrations: Promise<void>[] = [];

      // 생성 요청을 먼저 기록하므로 화면 이동에 실패해도 해당 실행의 방을 정리한다.
      a.on("response", (response) => {
        if (response.request().method() === "DELETE" && response.ok()) {
          const room = rooms.find((room) => room.url === response.url());
          if (room) room.deleted = true;
        }
        if (
          response.request().method() !== "POST" ||
          !/\/rooms$/.test(new URL(response.url()).pathname)
        )
          return;
        if (!response.ok()) return;
        registrations.push(
          response.json().then(({ data }) => {
            rooms.push({
              url: `${response.url()}/${data.roomId}`,
              token: response.request().headers().authorization,
              deleted: false,
            });
          }),
        );
      });

      try {
        await loginAsA(a);
        await use({ a, b });
      } finally {
        try {
          await Promise.all(registrations);
          for (const room of rooms.filter((room) => !room.deleted)) {
            const response = await aContext.request.delete(room.url, {
              headers: { Authorization: room.token },
            });
            expect([200, 204, 404, 410], "테스트 방 정리 실패").toContain(response.status());
          }
        } finally {
          await Promise.all([aContext.close(), bContext.close()]);
        }
      }
    },
    { timeout: 120_000 },
  ],
});

export { expect };
