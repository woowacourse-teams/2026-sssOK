import { overallPercentOf, PERSON_STAGES } from "./overallProgress";

const at = (stage: string, processed: number | null = null, total: number | null = null) => ({
  stage,
  processed,
  total,
});

describe("overallPercentOf", () => {
  it("단계 순서와 단계 안 처리 비율로 전체 진행률을 만든다", () => {
    // 11단계 중 6번째(detecting_faces)의 절반 → (5 + 0.5) / 11
    expect(overallPercentOf(at("detecting_faces", 5, 10), null)).toBe(50);
  });

  it("단계가 바뀌어 처리 수가 0으로 돌아가도 진행률은 줄지 않는다", () => {
    const faces = overallPercentOf(at("detecting_faces", 10, 10), null);
    const grouping = overallPercentOf(at("grouping_faces"), faces);
    const bodies = overallPercentOf(at("detecting_bodies", 0, 10), grouping);

    expect(faces).toBe(54);
    expect(grouping).toBe(54);
    expect(bodies).toBe(63);
  });

  it("사진을 내려받는 동안에도 진행률이 오른다", () => {
    expect(overallPercentOf(at("waiting"), null)).toBe(0);
    expect(overallPercentOf(at("downloading_photos", 20, 40), 0)).toBe(13);
  });

  it("수치 없는 모델 준비 단계를 지나도 진행률이 줄지 않는다", () => {
    const downloaded = overallPercentOf(at("downloading_photos", 40, 40), null);
    const loading = overallPercentOf(at("loading_models"), downloaded);

    expect(downloaded).toBe(18);
    expect(loading).toBe(27);
  });

  it("숫자가 없는 단계는 그 단계 시작 지점으로 본다", () => {
    expect(overallPercentOf(at("matching_bodies"), null)).toBe(81);
  });

  it("모르는 단계나 진행 정보가 없으면 이전 값을 그대로 둔다", () => {
    expect(overallPercentOf(at("clustering_v2", 3, 4), 40)).toBe(40);
    expect(overallPercentOf(undefined, null)).toBeNull();
  });

  it("끝나기 전에는 100% 를 보이지 않는다", () => {
    const last = PERSON_STAGES[PERSON_STAGES.length - 1];
    expect(overallPercentOf(at(last, 10, 10), null)).toBe(99);
  });
});
