import { shareBatchCountOf, toShareBatches } from "./shareBatches";

describe("toShareBatches", () => {
  it("기준 장수씩 앞에서부터 나누고, 마지막 묶음만 작을 수 있다", () => {
    const items = Array.from({ length: 165 }, (_, index) => index);

    expect(toShareBatches(items, 100).map((batch) => batch.length)).toEqual([100, 65]);
    expect(toShareBatches(items, 100)[1][0]).toBe(100);
  });

  it("기준 이하면 묶음 하나다", () => {
    expect(toShareBatches([1, 2, 3], 100)).toEqual([[1, 2, 3]]);
  });

  it("기준과 정확히 같으면 나누지 않는다", () => {
    expect(toShareBatches([1, 2], 2)).toEqual([[1, 2]]);
  });
});

describe("shareBatchCountOf", () => {
  it("몇 번 나눠 넘기는지 센다", () => {
    expect(shareBatchCountOf(165, 100)).toBe(2);
    expect(shareBatchCountOf(100, 100)).toBe(1);
    expect(shareBatchCountOf(201, 100)).toBe(3);
  });
});
