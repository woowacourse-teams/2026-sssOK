import { formatFeedbackDateTime, formatFeedbackTime } from "./formatFeedbackTime";

describe("formatFeedbackTime", () => {
  it("UTC 를 한국 시간으로 바꿔 보여준다", () => {
    expect(formatFeedbackTime("2026-09-16T23:41:00Z")).toBe("09-17 08:41");
  });

  it("날짜를 넘기는 시각도 하루를 더해 적는다", () => {
    expect(formatFeedbackTime("2026-09-16T15:00:00Z")).toBe("09-17 00:00");
  });

  it("자정을 24시로 적지 않는다", () => {
    expect(formatFeedbackTime("2026-01-01T15:00:00Z")).toBe("01-02 00:00");
  });

  it("이미 KST 로 표기된 값도 그대로 읽는다", () => {
    expect(formatFeedbackTime("2026-09-16T08:41:00+09:00")).toBe("09-16 08:41");
  });

  it("읽지 못한 시각은 비운다", () => {
    expect(formatFeedbackTime("언제인지 모를 값")).toBeNull();
  });
});

describe("formatFeedbackDateTime", () => {
  it("연도까지 붙여 한국 시간으로 보여준다", () => {
    expect(formatFeedbackDateTime("2026-09-17T11:42:10Z")).toBe("2026-09-17 20:42");
  });

  it("한국 시간으로 해가 바뀌면 연도도 바뀐다", () => {
    expect(formatFeedbackDateTime("2025-12-31T15:00:00Z")).toBe("2026-01-01 00:00");
  });

  it("읽지 못한 시각은 비운다", () => {
    expect(formatFeedbackDateTime("언제인지 모를 값")).toBeNull();
  });
});
