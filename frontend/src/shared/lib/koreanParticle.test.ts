import { withDirectionParticle } from "./koreanParticle";

describe("withDirectionParticle", () => {
  it("받침이 있으면 '으로' 를 붙인다", () => {
    expect(withDirectionParticle("반영")).toBe("반영으로");
    expect(withDirectionParticle("해니님")).toBe("해니님으로");
  });

  it("받침이 없으면 '로' 를 붙인다", () => {
    expect(withDirectionParticle("해니")).toBe("해니로");
  });

  it("ㄹ 받침이면 '로' 를 붙인다", () => {
    expect(withDirectionParticle("보류")).toBe("보류로");
    expect(withDirectionParticle("서울")).toBe("서울로");
  });

  it("한글로 끝나지 않으면 '(으)로' 를 붙인다", () => {
    expect(withDirectionParticle("ON_HOLD")).toBe("ON_HOLD(으)로");
    expect(withDirectionParticle("")).toBe("(으)로");
  });
});
