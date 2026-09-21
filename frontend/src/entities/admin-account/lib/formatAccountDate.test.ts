import { formatAccountDate } from "./formatAccountDate";

describe("formatAccountDate", () => {
  it("UTC 시각을 한국 날짜로 바꾼다", () => {
    expect(formatAccountDate("2026-09-17T03:00:00Z")).toBe("2026-09-17");
  });

  it("한국에서 자정을 넘긴 시각은 다음 날로 보여준다", () => {
    expect(formatAccountDate("2026-09-16T15:30:00Z")).toBe("2026-09-17");
  });

  it("읽지 못한 시각은 null 을 돌려준다", () => {
    expect(formatAccountDate("not-a-date")).toBeNull();
  });
});
