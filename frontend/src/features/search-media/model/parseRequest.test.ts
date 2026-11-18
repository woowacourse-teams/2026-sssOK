import { findName, parseRequest } from "./parseRequest";

const NAMES = ["하루", "소이", "지우", "나"];

describe("findName", () => {
  it("이름이 문장 어디에 있어도 찾는다", () => {
    expect(findName("하루 나온 사진", NAMES)).toBe("하루");
    expect(findName("사진 중에 하루 나온 거", NAMES)).toBe("하루");
  });

  it("조사가 붙어도 이름으로 본다", () => {
    expect(findName("하루랑 찍은 사진", NAMES)).toBe("하루");
    expect(findName("소이가 올린 사진", NAMES)).toBe("소이");
  });

  it("다른 낱말 속 글자는 이름으로 보지 않는다", () => {
    expect(findName("바다에서 나온 사진", NAMES)).toBeUndefined();
    expect(findName("지우고 싶은 사진", NAMES)).toBeUndefined();
  });

  it("'내' 는 '나' 로 본다", () => {
    expect(findName("내가 올린 사진", NAMES)).toBe("나");
  });

  it("긴 이름을 먼저 맞춘다", () => {
    expect(findName("하루나 사진", [...NAMES, "하루나"])).toBe("하루나");
  });
});

describe("parseRequest", () => {
  it("빈 글은 요청으로 보지 않는다", () => {
    expect(parseRequest("   ", NAMES)).toBeNull();
  });

  it("아는 이름이 있으면 검색어와 함께 이름을 돌려준다", () => {
    expect(parseRequest("소이가 올린 사진", NAMES)).toEqual({
      query: "소이가 올린 사진",
      name: "소이",
    });
    expect(parseRequest("내가 올린 사진", NAMES)).toEqual({ query: "내가 올린 사진", name: "나" });
  });

  it("아는 이름이 없으면 검색어만 돌려준다", () => {
    expect(parseRequest(" 바다에서 찍은 사진 ", NAMES)).toEqual({ query: "바다에서 찍은 사진" });
  });

  it("정리나 아직 못 하는 요청처럼 보이는 글도 검색어로 본다", () => {
    expect(parseRequest("사람별로 나눠줘", NAMES)).toEqual({ query: "사람별로 나눠줘" });
    expect(parseRequest("잘 나온 사진 골라줘", NAMES)).toEqual({ query: "잘 나온 사진 골라줘" });
  });
});
