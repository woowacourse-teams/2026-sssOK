import { formatBytes } from "./formatBytes";
import { MAX_IMAGE_BYTES, MAX_VIDEO_BYTES } from "./mediaFileRules";

const MB = 1024 * 1024;
const GB = MB * 1024;

describe("formatBytes", () => {
  // 시안(07d)에 적힌 표기와 같아야 한다.
  it.each([
    [14 * MB, "14MB"],
    [Math.round(2.3 * GB), "2.3GB"],
  ])("%i 는 %s 로 보여준다", (bytes, expected) => {
    expect(formatBytes(bytes)).toBe(expected);
  });

  // 2.0GB 는 사용자에게 그냥 2GB 다.
  it("소수 자리가 0 이면 떼어낸다", () => {
    expect(formatBytes(2 * GB)).toBe("2GB");
  });

  it("한도 자체도 칩에 쓰는 그대로 나온다", () => {
    expect(formatBytes(MAX_IMAGE_BYTES)).toBe("10MB");
    expect(formatBytes(MAX_VIDEO_BYTES)).toBe("1GB");
  });

  /** 1MB 미만을 MB 로 반올림하면 전부 0MB 가 되어 크기를 말하지 못한다. */
  it("아주 작은 파일도 0 으로 뭉개지 않는다", () => {
    expect(formatBytes(300)).toBe("1KB");
    expect(formatBytes(20 * 1024)).toBe("20KB");
  });
});
