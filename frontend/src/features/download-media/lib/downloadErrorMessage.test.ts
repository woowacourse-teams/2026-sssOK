import { ApiError } from "@/shared/api";
import {
  DOWNLOAD_FALLBACK_MESSAGE,
  downloadMessageOfError,
  downloadMessageOfStatus,
  isRetryableError,
  isRetryableStatus,
} from "./downloadErrorMessage";

describe("downloadMessageOfStatus", () => {
  // #120 · #121 완료 조건에 적힌 문구를 그대로 검증한다.
  it.each([
    [404, "찾을 수 없어요"],
    [409, "아직 처리 중이에요"],
    [410, "기한이 지났어요"],
    [429, "받는 중인 요청이 많아요"],
  ])("%i 는 %s 로 옮긴다", (status, message) => {
    expect(downloadMessageOfStatus(status)).toBe(message);
  });

  it("응답 자체를 못 받은 경우(0)는 회선을 확인하라고 한다", () => {
    expect(downloadMessageOfStatus(0)).toBe("네트워크 연결을 확인해주세요");
  });

  it("모르는 상태 코드에 그럴듯한 사유를 붙이지 않는다", () => {
    expect(downloadMessageOfStatus(503)).toBe(DOWNLOAD_FALLBACK_MESSAGE);
  });
});

describe("isRetryableStatus", () => {
  it("없는 사진은 다시 받아도 없으므로 재시도를 내주지 않는다", () => {
    expect(isRetryableStatus(404)).toBe(false);
  });

  // 셋 다 기다렸다 다시 누르면 되는 실패다. 410 은 새 잡을 만들면 된다.
  it.each([0, 409, 410, 429])("%i 는 다시 눌러볼 수 있다", (status) => {
    expect(isRetryableStatus(status)).toBe(true);
  });
});

describe("downloadMessageOfError", () => {
  it("ApiError 면 상태 코드로 사유를 가른다", () => {
    const error = new ApiError(429, "TOO_MANY_REQUESTS", "서버 문구");

    expect(downloadMessageOfError(error)).toBe("받는 중인 요청이 많아요");
  });

  it("상태 코드를 알 수 없는 예외는 기본 문구로 떨어진다", () => {
    expect(downloadMessageOfError(new Error("어디서 터졌는지 모름"))).toBe(
      DOWNLOAD_FALLBACK_MESSAGE,
    );
  });

  it("정체를 모르는 예외는 일단 다시 눌러볼 수 있게 둔다", () => {
    expect(isRetryableError(new Error("모름"))).toBe(true);
  });
});
