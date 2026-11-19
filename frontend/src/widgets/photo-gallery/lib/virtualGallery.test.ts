import { galleryColumns, galleryHitIds, galleryRange } from "./virtualGallery";

describe("가상 갤러리", () => {
  it("기존 반응형 그리드와 같은 열 수를 사용한다", () => {
    expect([390, 600, 860, 1080].map(galleryColumns)).toEqual([2, 3, 4, 5]);
  });
  it("화면 밖 포커스 행을 유지하되 전체 행을 마운트하지 않는다", () => {
    const rows = galleryRange({ startIndex: 100, endIndex: 103, overscan: 12, count: 400 }, 2);
    expect(rows).toContain(2);
    expect(rows).toContain(100);
    expect(rows).toContain(115);
    expect(rows.length).toBeLessThan(40);
    expect(new Set(rows).size).toBe(rows.length);
  });
  it("여유 범위를 정확히 12행으로 제한하고 배열 경계를 넘지 않는다", () => {
    const rows = galleryRange({ startIndex: 100, endIndex: 102, overscan: 12, count: 400 }, null);
    expect(rows[0]).toBe(88);
    expect(rows[rows.length - 1]).toBe(114);
    expect(galleryRange({ startIndex: 0, endIndex: 2, overscan: 12, count: 5 }, null)).toEqual([
      0, 1, 2, 3, 4,
    ]);
  });
  it("드래그가 화면 밖 행까지 이어져도 중간 사진을 빠뜨리지 않는다", () => {
    const ids = Array.from({ length: 2000 }, (_, index) => index + 1);
    const hits = galleryHitIds(ids, 2, 212, { x: 0, y: 0, width: 212, height: 124.5 * 100 });
    expect(hits).toHaveLength(200);
    expect(hits[0]).toBe(1);
    expect(hits[199]).toBe(200);
  });
  it("카드 사이 여백과 마지막 행의 빈 칸은 선택하지 않는다", () => {
    expect(galleryHitIds([1, 2, 3], 2, 212, { x: 101, y: 0, width: 10, height: 100 })).toEqual([]);
    expect(galleryHitIds([1, 2, 3], 2, 212, { x: 112, y: 125, width: 100, height: 100 })).toEqual(
      [],
    );
  });
});
