import { createExpiryDayMarks } from "./expiryDayMarks";

const labelsOf = (min: number, max: number) =>
  createExpiryDayMarks(min, max).map((mark) => mark.label);

describe("createExpiryDayMarks", () => {
  it("양 끝과 주 단위 값을 눈금으로 만든다", () => {
    expect(labelsOf(1, 14)).toEqual(["1일", "1주", "2주"]);
  });

  it("최댓값이 늘어나면 눈금도 따라 늘어난다", () => {
    expect(labelsOf(1, 21)).toEqual(["1일", "1주", "2주", "3주"]);
  });

  it("끝값과 붙어 있는 눈금은 뺀다", () => {
    expect(labelsOf(1, 30)).toEqual(["1일", "1주", "2주", "3주", "30일"]);
  });

  it("범위가 넓으면 눈금 간격을 넓힌다", () => {
    expect(labelsOf(1, 60)).toEqual(["1일", "2주", "4주", "6주", "60일"]);
  });
});
