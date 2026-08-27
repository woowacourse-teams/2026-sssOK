import { MAX_IMAGE_BYTES, MAX_VIDEO_BYTES } from "../lib/mediaFileRules";
import { selectMediaFiles } from "./selectMediaFiles";

const fileOf = (name: string, size: number, type = "") => {
  const file = new File(["x"], name, { type });
  Object.defineProperty(file, "size", { value: size });

  return file;
};

describe("selectMediaFiles", () => {
  it("올릴 수 있는 파일만 accepted 에 순서대로 담는다", () => {
    const first = fileOf("a.jpg", 1024);
    const second = fileOf("b.mp4", 2048);

    const { accepted, rejected } = selectMediaFiles([first, second]);

    expect(accepted).toEqual([first, second]);
    expect(rejected).toEqual([]);
  });

  it("지원하지 않는 확장자는 사유와 함께 걸러낸다", () => {
    const { accepted, rejected } = selectMediaFiles([fileOf("a.jpg", 1024), fileOf("b.txt", 10)]);

    expect(accepted.map((file) => file.name)).toEqual(["a.jpg"]);
    expect(rejected).toEqual([
      {
        fileName: "b.txt",
        size: 10,
        code: "UNSUPPORTED_FILE_TYPE",
        message: "이미지와 영상만 올릴 수 있어요",
      },
    ]);
  });

  it("확장자로 판별한다 — 아이폰이 넘겨준 mimeType 은 보지 않는다", () => {
    // 사파리는 파일명이 .HEIC 인데 image/jpeg 를 실어 보내기도 한다
    const { accepted, rejected } = selectMediaFiles([fileOf("IMG_0001.HEIC", 1024, "image/jpeg")]);

    expect(accepted).toEqual([]);
    expect(rejected[0].code).toBe("UNSUPPORTED_FILE_TYPE");
  });

  it("mimeType 이 비어 있어도 확장자가 맞으면 통과시킨다", () => {
    const { accepted } = selectMediaFiles([fileOf("a.JPG", 1024, "")]);

    expect(accepted.map((file) => file.name)).toEqual(["a.JPG"]);
  });

  it("한도를 넘는 사진은 서버에 요청하기 전에 걸러낸다", () => {
    const { accepted, rejected } = selectMediaFiles([fileOf("big.png", MAX_IMAGE_BYTES + 1)]);

    expect(accepted).toEqual([]);
    expect(rejected).toEqual([
      {
        fileName: "big.png",
        size: MAX_IMAGE_BYTES + 1,
        code: "FILE_SIZE_EXCEEDED",
        message: "이미지 최대 10MB 초과",
      },
    ]);
  });

  it("한도를 넘는 영상도 걸러내고, 사진과 다른 한도를 쓴다", () => {
    const { accepted, rejected } = selectMediaFiles([
      fileOf("ok.mp4", MAX_IMAGE_BYTES + 1),
      fileOf("big.mp4", MAX_VIDEO_BYTES + 1),
    ]);

    expect(accepted.map((file) => file.name)).toEqual(["ok.mp4"]);
    expect(rejected).toEqual([
      {
        fileName: "big.mp4",
        size: MAX_VIDEO_BYTES + 1,
        code: "FILE_SIZE_EXCEEDED",
        message: "영상 최대 1GB 초과",
      },
    ]);
  });

  it("한도와 정확히 같은 크기는 통과시킨다", () => {
    const { accepted } = selectMediaFiles([fileOf("edge.png", MAX_IMAGE_BYTES)]);

    expect(accepted.map((file) => file.name)).toEqual(["edge.png"]);
  });

  it("빈 목록은 빈 결과가 된다", () => {
    expect(selectMediaFiles([])).toEqual({ accepted: [], rejected: [] });
  });
});
