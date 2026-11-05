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

  it("사람별로 나눠 달라는 말은 정리 요청으로 본다", () => {
    expect(parseRequest("사람별로 나눠줘", NAMES)).toEqual({ type: "organize" });
  });

  it("아직 못 하는 요청을 가려낸다", () => {
    expect(parseRequest("잘 나온 사진 골라줘", NAMES)).toEqual({ type: "unsupported" });
  });

  it("이름과 '올린' 이 있으면 올린 사람으로 찾는다", () => {
    expect(parseRequest("소이가 올린 사진", NAMES)).toEqual({ type: "uploader", name: "소이" });
  });

  it("이름만 있으면 그 사람 사진을 찾는다", () => {
    expect(parseRequest("하루 나온 사진", NAMES)).toEqual({ type: "person", name: "하루" });
  });

  it("아는 이름이 없으면 사진 내용 검색어로 본다", () => {
    expect(parseRequest(" 바다에서 찍은 사진 ", NAMES)).toEqual({
      type: "keyword",
      query: "바다에서 찍은 사진",
    });
  });
});
