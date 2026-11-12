import { test, expect } from "./support/fixtures";
import { prepareRoom, roomMenu } from "./support/room";
import {
  makePhotos,
  upload,
  expectPhotos,
  expectDetail,
  filterPhotos,
  selectPhotos,
  verifyZip,
} from "./support/media";

test("사진을 올리고 정리하고 내려받기 · E04–E09", async ({ users: { a, b } }, testInfo) => {
  test.setTimeout(180_000);
  const network = await b.context().newCDPSession(b);
  await network.send("Network.enable");
  const receivedFiles = new Set<string>();
  network.on("Network.eventSourceMessageReceived", ({ eventName, data }) => {
    if (eventName === "media.created" || eventName === "media.ready") {
      const media = JSON.parse(data);
      if (typeof media.fileName === "string") receivedFiles.add(media.fileName);
    }
  });
  const room = await prepareRoom(a, b);
  const [sea, mountain, cat] = await makePhotos(a);

  await test.step("E04 · A 업로드가 열린 B 갤러리에 자동 반영되고 상세 사진 일치", async () => {
    const urlBeforeUpload = b.url();
    let navigations = 0;
    const onNavigation = (): void => {
      navigations += 1;
    };
    b.on("domcontentloaded", onNavigation);
    try {
      await upload(a, [sea, mountain]);
      await expectPhotos(b, [sea.name, mountain.name]);
      expect(receivedFiles.has(sea.name), "B가 바다 사진 SSE 이벤트를 받아야 합니다.").toBe(true);
      expect(receivedFiles.has(mountain.name), "B가 산 사진 SSE 이벤트를 받아야 합니다.").toBe(
        true,
      );
      expect(b.url()).toBe(urlBeforeUpload);
      expect(navigations, "B 화면이 새로고침 없이 갱신되어야 합니다.").toBe(0);
    } finally {
      b.off("domcontentloaded", onNavigation);
    }
    await expectDetail(b, sea.name);
    await b.getByRole("button", { name: "갤러리로 돌아가기", exact: true }).click();
  });

  await test.step("E05 · B의 내 사진과 다른 사람 사진 필터", async () => {
    await upload(b, [cat]);
    await filterPhotos(b, "내 사진");
    await expectPhotos(b, [cat.name]);
    await filterPhotos(b, "다른 사람 사진");
    await expectPhotos(b, [sea.name, mountain.name]);
    await filterPhotos(b, "전체");
    await expectPhotos(b, [sea.name, mountain.name, cat.name]);
  });

  await test.step("E06 · 상세 좋아요와 좋아요 필터 및 취소", async () => {
    const dialog = await expectDetail(b, sea.name);
    await dialog.getByRole("button", { name: "좋아요 추가", exact: true }).click();
    await expect(dialog.getByRole("button", { name: "좋아요 취소", exact: true })).toBeVisible();
    await dialog.getByRole("button", { name: "갤러리로 돌아가기", exact: true }).click();
    await filterPhotos(b, "좋아요한 사진");
    await expectPhotos(b, [sea.name]);
    const liked = await expectDetail(b, sea.name);
    await liked.getByRole("button", { name: "좋아요 취소", exact: true }).click();
    await liked.getByRole("button", { name: "갤러리로 돌아가기", exact: true }).click();
    await expectPhotos(b, []);
    await filterPhotos(b, "전체");
    await expectPhotos(b, [sea.name, mountain.name, cat.name]);
  });

  await test.step("E07 · A의 새 폴더에 두 사진 담기와 B의 폴더 공유", async () => {
    await expectPhotos(a, [sea.name, mountain.name, cat.name]);
    await selectPhotos(a, [sea.name, mountain.name]);
    await a.getByRole("button", { name: "선택한 사진 폴더 이동", exact: true }).click();
    await a.getByRole("button", { name: "새 폴더 만들어 옮기기", exact: true }).click();
    await a.getByRole("textbox", { name: "폴더 이름", exact: true }).fill("첫날");
    await a.getByRole("button", { name: "만들기", exact: true }).click();
    await expectPhotos(a, [sea.name, mountain.name]);
    await b.getByRole("button", { name: /^첫날 폴더, 사진 및 동영상 2개$/ }).click();
    await expectPhotos(b, [sea.name, mountain.name]);
    await b.getByRole("button", { name: /^전체 사진 및 동영상, 3개$/ }).click();
    await expectPhotos(b, [sea.name, mountain.name, cat.name]);
  });

  await test.step("E08 · 선택한 바다·고양이만 ZIP에 포함되고 원본으로 열림", async () => {
    await selectPhotos(b, [sea.name, cat.name]);
    await b.getByRole("button", { name: "다운로드", exact: true }).click();
    await b.getByRole("radio", { name: /\.zip 일괄 다운로드/ }).check();
    const downloading = b.waitForEvent("download");
    await b.getByRole("button", { name: "다운로드", exact: true }).last().click();
    const download = await downloading;
    expect(download.suggestedFilename()).toMatch(/\.zip$/i);
    const path = testInfo.outputPath("selected-photos.zip");
    await download.saveAs(path);
    expect(await download.failure()).toBeNull();
    await verifyZip(b, path, [sea, cat]);
  });

  await test.step("E09 · 폴더 삭제 후 원본 사진 유지", async () => {
    await roomMenu(a, "폴더 삭제");
    await a.getByRole("button", { name: "삭제", exact: true }).click();
    await expect(a.getByRole("button", { name: /^첫날 폴더,/ })).toHaveCount(0);
    await b.goto(room.gallery);
    await expect(b.getByRole("button", { name: /^첫날 폴더,/ })).toHaveCount(0);
    await expectPhotos(b, [sea.name, mountain.name, cat.name]);
    for (const photo of [sea, mountain]) {
      await expectDetail(b, photo.name);
      await b.getByRole("button", { name: "갤러리로 돌아가기", exact: true }).click();
    }
  });
});
