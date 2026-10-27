import { parseRoomCode } from "./parseRoomCode";

describe("parseRoomCode", () => {
  it("초대 링크에서 방 코드를 꺼낸다", () => {
    expect(parseRoomCode("https://ssok.app/join/7K93QX2S")).toBe("7K93QX2S");
    expect(parseRoomCode("https://ssok.app/rooms/7K93QX2S/gallery?folder=1")).toBe("7K93QX2S");
  });

  it("코드만 붙여넣어도 받고, 앞뒤 공백과 소문자는 정리한다", () => {
    expect(parseRoomCode("  7k93qx2s ")).toBe("7K93QX2S");
  });

  it("쏙 링크가 아니면 null 이다", () => {
    expect(parseRoomCode("https://example.com/hello")).toBeNull();
    expect(parseRoomCode("NOTFOUND")).toBeNull();
    expect(parseRoomCode("")).toBeNull();
  });
});
