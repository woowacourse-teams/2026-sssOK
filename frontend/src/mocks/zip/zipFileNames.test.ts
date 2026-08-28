import { zipFileNames } from "./zipFileNames";

describe("zipFileNames", () => {
  it("겹치지 않는 이름은 올린 그대로 둔다", () => {
    expect(zipFileNames(["여행.jpg", "IMG_0001.HEIC", "밤바다 🌊.mp4"])).toEqual([
      "여행.jpg",
      "IMG_0001.HEIC",
      "밤바다 🌊.mp4",
    ]);
  });

  it("같은 이름이 또 오면 확장자 앞에 번호를 붙인다", () => {
    expect(zipFileNames(["IMG_0001.jpg", "IMG_0001.jpg", "IMG_0001.jpg"])).toEqual([
      "IMG_0001.jpg",
      "IMG_0001 (1).jpg",
      "IMG_0001 (2).jpg",
    ]);
  });

  it("대소문자만 다른 이름도 같은 것으로 본다 — 윈도우·맥에서 하나가 덮인다", () => {
    expect(zipFileNames(["photo.jpg", "PHOTO.JPG"])).toEqual(["photo.jpg", "PHOTO (1).JPG"]);
  });

  it("경로 문자를 지운다 — zip 안에서 폴더가 되면 안 된다", () => {
    expect(zipFileNames(["../../etc/passwd.jpg"])).toEqual([".._.._etc_passwd.jpg"]);
  });

  it("확장자가 없으면 이름 뒤에 번호를 붙인다", () => {
    expect(zipFileNames(["scan", "scan"])).toEqual(["scan", "scan (1)"]);
  });

  it("손볼 것이 없어 이름이 비면 대체 이름을 쓴다", () => {
    expect(zipFileNames(["///"])).toEqual(["___"]);
    expect(zipFileNames([""])).toEqual(["media"]);
  });
});
