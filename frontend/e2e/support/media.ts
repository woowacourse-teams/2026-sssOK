import type { Locator } from "@playwright/test";
import AdmZip from "adm-zip";
import { readFile } from "node:fs/promises";
import { expect, type Page } from "@playwright/test";

export interface TestPhoto {
  name: string;
  mimeType: string;
  buffer: Buffer;
}

// 외부 이미지 URL에 의존하지 않는 고정 PNG 이미지. 바다·산·고양이를 구분해 그린다.
export async function makePhotos(page: Page): Promise<TestPhoto[]> {
  const images = await page.evaluate(() => {
    return ["sea", "mountain", "cat", "sun"].map((kind) => {
      const canvas = document.createElement("canvas");
      canvas.width = 320;
      canvas.height = 240;
      const ctx = canvas.getContext("2d")!;
      ctx.fillStyle = "#b9e5ff";
      ctx.fillRect(0, 0, 320, 240);
      ctx.fillStyle = "#ffd34d";
      ctx.beginPath();
      ctx.arc(260, 45, 24, 0, Math.PI * 2);
      ctx.fill();
      if (kind === "sea") {
        ctx.fillStyle = "#157bbe";
        ctx.fillRect(0, 100, 320, 140);
        ctx.strokeStyle = "white";
        for (let y = 120; y < 240; y += 30) {
          ctx.beginPath();
          ctx.moveTo(0, y);
          ctx.bezierCurveTo(80, y - 20, 160, y + 20, 320, y);
          ctx.stroke();
        }
      }
      if (kind === "mountain") {
        ctx.fillStyle = "#287b46";
        ctx.beginPath();
        ctx.moveTo(0, 240);
        ctx.lineTo(155, 45);
        ctx.lineTo(320, 240);
        ctx.fill();
      }
      if (kind === "cat") {
        ctx.fillStyle = "#d8853b";
        ctx.beginPath();
        ctx.moveTo(80, 150);
        ctx.lineTo(80, 45);
        ctx.lineTo(140, 90);
        ctx.lineTo(180, 90);
        ctx.lineTo(240, 45);
        ctx.lineTo(240, 150);
        ctx.arc(160, 150, 80, 0, Math.PI);
        ctx.fill();
        ctx.fillStyle = "#222222";
        ctx.fillRect(115, 125, 12, 20);
        ctx.fillRect(193, 125, 12, 20);
        ctx.fillRect(154, 160, 12, 10);
      }
      return canvas.toDataURL("image/png").split(",")[1];
    });
  });
  return ["바다.png", "산.png", "고양이.png", "새사진.png"].map((name, index) => ({
    name,
    mimeType: "image/png",
    buffer: Buffer.from(images[index], "base64"),
  }));
}

export const photoButton = (page: Page, name: string): Locator =>
  page.getByRole("button", { name: `${name} 크게 보기`, exact: true });

export async function expectPhotos(page: Page, names: string[]): Promise<void> {
  await expect(page.getByRole("button", { name: / 크게 보기$/ })).toHaveCount(names.length);
  for (const name of names) {
    await expect(photoButton(page, name)).toBeVisible();
    await expect
      .poll(() =>
        page
          .getByRole("img", { name, exact: true })
          .evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0),
      )
      .toBe(true);
  }
}

export async function upload(page: Page, photos: TestPhoto[]): Promise<void> {
  const chooser = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "사진 올리기", exact: true }).click();
  await (await chooser).setFiles(photos);
  await expect(page.getByText(`${photos.length}장을 올렸어요.`, { exact: true })).toBeVisible();
}

export async function filterPhotos(page: Page, name: string): Promise<void> {
  await page.getByRole("button", { name: / 필터 열기$/ }).click();
  await page.getByRole("button", { name, exact: true }).click();
}

export async function selectPhotos(page: Page, names: string[]): Promise<void> {
  for (const name of names) {
    await page.getByRole("button", { name: `${name} 선택, 선택 안 됨`, exact: true }).click();
  }
}

export async function expectDetail(page: Page, name: string): Promise<Locator> {
  await photoButton(page, name).click();
  const dialog = page.getByRole("dialog", { name: "사진 크게 보기", exact: true });
  await expect(dialog).toBeVisible();
  const image = dialog.getByRole("img", { name, exact: true });
  await expect(image).toBeVisible();
  await expect
    .poll(() =>
      image.evaluate((element: HTMLImageElement) => element.complete && element.naturalWidth > 0),
    )
    .toBe(true);
  return dialog;
}

export async function verifyZip(page: Page, path: string, selected: TestPhoto[]): Promise<void> {
  const zip = new AdmZip(await readFile(path));
  const entries = zip.getEntries();
  expect(entries).toHaveLength(selected.length);
  const remaining = new Set(selected.map((photo) => photo.buffer.toString("base64")));
  for (const entry of entries) {
    expect(entry.isDirectory).toBe(false);
    const bytes = entry.getData();
    const content = bytes.toString("base64");
    expect(remaining.has(content), `${entry.entryName}이 선택한 원본과 일치해야 합니다.`).toBe(
      true,
    );
    remaining.delete(content);
    // 실제 브라우저 이미지 디코더로 파일을 열어 손상 여부를 확인한다.
    const dimensions = await page.evaluate(async (base64) => {
      const response = await fetch(`data:image/png;base64,${base64}`);
      const bitmap = await createImageBitmap(await response.blob());
      const size = { width: bitmap.width, height: bitmap.height };
      bitmap.close();
      return size;
    }, content);
    expect(dimensions).toEqual({ width: 320, height: 240 });
  }
  expect(remaining.size).toBe(0);
}
