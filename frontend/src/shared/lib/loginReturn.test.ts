import { rememberLoginReturn, takeLoginReturn } from "./loginReturn";

afterEach(() => {
  sessionStorage.clear();
  jest.useRealTimers();
});

it("기억한 화면을 한 번만 돌려준다", () => {
  rememberLoginReturn("/rooms/ABC/gallery");

  expect(takeLoginReturn()).toBe("/rooms/ABC/gallery");
  expect(takeLoginReturn()).toBeNull();
});

it.each(["https://evil.example", "//evil.example", "/\\evil.example", "/app/login", "rooms"])(
  "서비스 밖이나 로그인 화면 주소 %s 는 기억하지 않는다",
  (path) => {
    rememberLoginReturn(path);

    expect(takeLoginReturn()).toBeNull();
  },
);

it("저장소 값이 바뀌어 외부 주소가 되면 버린다", () => {
  sessionStorage.setItem(
    "sssok.login-return",
    JSON.stringify({ path: "//evil.example", savedAt: Date.now() }),
  );

  expect(takeLoginReturn()).toBeNull();
});

it("30분이 지나면 버린다", () => {
  jest.useFakeTimers({ now: new Date("2026-11-05T00:00:00Z") });
  rememberLoginReturn("/rooms/ABC/gallery");
  jest.setSystemTime(new Date("2026-11-05T00:31:00Z"));

  expect(takeLoginReturn()).toBeNull();
});
