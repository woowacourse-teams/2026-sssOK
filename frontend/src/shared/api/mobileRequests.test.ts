import { AuthRequestSchema, parseDownloadRequest } from "../../../mobile/src/bridge/requestSchemas";

const allowedUrl = (url: string): boolean => url.startsWith("https://");
const file = {
  id: 1,
  url: "https://cdn.example.com/photo.jpg",
  fileName: "photo.jpg",
  mimeType: "image/jpeg",
};

it("모바일 인증 명령은 제공자·요청 ID·Apple nonce를 확인한다", () => {
  expect(
    AuthRequestSchema.safeParse({ type: "auth/login", requestId: "id-1", provider: "kakao" })
      .success,
  ).toBe(true);
  expect(
    AuthRequestSchema.safeParse({ type: "auth/login", requestId: "id-1", provider: "apple" })
      .success,
  ).toBe(false);
  expect(
    AuthRequestSchema.safeParse({
      type: "auth/login",
      requestId: "id-1",
      provider: "apple",
      nonce: "a".repeat(43),
    }).success,
  ).toBe(true);
  expect(
    AuthRequestSchema.safeParse({ type: "auth/login", requestId: {}, provider: "other" }).success,
  ).toBe(false);
});
it.each([
  "null",
  "{",
  '{"type":"unknown","requestId":"id"}',
  JSON.stringify({ type: "download/save", requestId: "id", files: [null] }),
])("모바일은 잘못된 다운로드 명령을 실행하지 않는다: %s", (raw) => {
  expect(parseDownloadRequest(raw, allowedUrl)).toBeNull();
});
it("다운로드 구조가 맞아도 허용하지 않은 URL이면 거부한다", () => {
  expect(
    parseDownloadRequest(
      JSON.stringify({
        type: "download/share",
        requestId: "id",
        file: { ...file, url: "file:///private/data" },
      }),
      allowedUrl,
    ),
  ).toBeNull();
  expect(
    parseDownloadRequest(
      JSON.stringify({ type: "download/save", requestId: "id", files: [file] }),
      allowedUrl,
    ),
  ).toMatchObject({ type: "download/save", files: [file] });
});
