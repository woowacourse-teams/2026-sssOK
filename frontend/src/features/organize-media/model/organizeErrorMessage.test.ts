import { ApiError } from "@/shared/api";
import { organizeErrorMessage } from "./organizeErrorMessage";

describe("organizeErrorMessage", () => {
  it("회원의 시간 제한은 남은 시간을 알려 준다", () => {
    expect(organizeErrorMessage(new ApiError(429, "ORGANIZE_RATE_LIMITED", "", 90))).toBe(
      "2분 뒤에 다시 정리할 수 있어요.",
    );
  });

  it("남은 시간을 모르면 막연하게 알린다", () => {
    expect(organizeErrorMessage(new ApiError(429, "ORGANIZE_RATE_LIMITED", ""))).toBe(
      "조금 뒤에 다시 정리할 수 있어요.",
    );
  });

  it("사진 한도를 넘으면 200장을 알려 준다", () => {
    expect(organizeErrorMessage(new ApiError(400, "ORGANIZE_TOO_MANY_PHOTOS", ""))).toContain(
      "200장",
    );
  });

  it("체험을 다 쓰면 웹·앱 모두 앱 설치 없이 로그인을 권한다", () => {
    expect(organizeErrorMessage(new ApiError(403, "ORGANIZE_TRIAL_EXHAUSTED", ""))).toBe(
      "로그인하면 AI 정리를 계속 쓸 수 있어요.",
    );
  });

  it("모르는 실패는 다시 시도를 권한다", () => {
    expect(organizeErrorMessage(new Error("boom"))).toContain("다시 시도");
  });
});
