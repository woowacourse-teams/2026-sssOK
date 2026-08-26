import type { IssuedUpload, RejectedFile } from "../api/types";
import { pairWithFiles } from "./pairWithFiles";

const file = (fileName: string) => new File(["사진"], fileName, { type: "image/jpeg" });

const issuedFor = (mediaId: number, fileName: string): IssuedUpload => ({
  mediaId,
  fileName,
  uploadUrl: `https://storage.example/${mediaId}`,
  method: "PUT",
  headers: { "Content-Type": "image/jpeg" },
  expiresIn: 600,
});

const rejectedFor = (fileName: string): RejectedFile => ({
  fileName,
  code: "UNSUPPORTED_FILE_TYPE",
  message: "지원하지 않는 파일 형식입니다",
});

/** 어떤 파일이 어떤 mediaId 로 갔는지만 본다. */
const pairsOf = (...args: Parameters<typeof pairWithFiles>) =>
  pairWithFiles(...args).map((target) => [target.file.name, target.issued.mediaId]);

describe("pairWithFiles", () => {
  it("거절이 없으면 순서대로 짝지어진다", () => {
    expect(
      pairsOf(
        [file("첫째.jpg"), file("둘째.jpg")],
        [issuedFor(1, "첫째.jpg"), issuedFor(2, "둘째.jpg")],
        [],
      ),
    ).toEqual([
      ["첫째.jpg", 1],
      ["둘째.jpg", 2],
    ]);
  });

  it("거절로 빠진 자리를 건너뛴다 — 뒤 파일이 한 칸 밀리지 않는다", () => {
    expect(
      pairsOf(
        [file("첫째.jpg"), file("메모.txt"), file("셋째.jpg")],
        [issuedFor(1, "첫째.jpg"), issuedFor(2, "셋째.jpg")],
        [rejectedFor("메모.txt")],
      ),
    ).toEqual([
      ["첫째.jpg", 1],
      ["셋째.jpg", 2],
    ]);
  });

  it("맨 앞이 거절돼도 어긋나지 않는다", () => {
    expect(
      pairsOf(
        [file("메모.txt"), file("둘째.jpg")],
        [issuedFor(2, "둘째.jpg")],
        [rejectedFor("메모.txt")],
      ),
    ).toEqual([["둘째.jpg", 2]]);
  });

  it("파일명이 겹쳐도 이름이 아니라 순서로 맞춘다", () => {
    // 이름으로 찾았다면 둘 다 첫 번째 발급에 붙었을 것이다.
    expect(
      pairsOf(
        [file("사진.jpg"), file("사진.jpg")],
        [issuedFor(1, "사진.jpg"), issuedFor(2, "사진.jpg")],
        [],
      ),
    ).toEqual([
      ["사진.jpg", 1],
      ["사진.jpg", 2],
    ]);
  });

  it("전부 거절되면 올릴 것이 없다", () => {
    expect(pairsOf([file("메모.txt")], [], [rejectedFor("메모.txt")])).toEqual([]);
  });

  it("발급이 요청보다 적게 오면 없는 자리를 만들지 않는다", () => {
    expect(pairsOf([file("첫째.jpg"), file("둘째.jpg")], [issuedFor(1, "첫째.jpg")], [])).toEqual([
      ["첫째.jpg", 1],
    ]);
  });
});
