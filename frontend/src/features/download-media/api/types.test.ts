import { DownloadJobSchema } from "./types";

it.each([12, "dl_12"])("서버 숫자 ID와 기존 문자열 ID를 내부 문자열로 정규화한다: %s", (jobId) => {
  expect(
    DownloadJobSchema.parse({
      jobId,
      status: "QUEUED",
      mediaCount: 1,
      totalSize: 100,
      fileName: "photos.zip",
    }).jobId,
  ).toBe(String(jobId));
});
it.each([null, -1, 1.5, {}])("잘못된 ZIP 작업 ID를 거부한다: %j", (jobId) => {
  expect(
    DownloadJobSchema.safeParse({
      jobId,
      status: "QUEUED",
      mediaCount: 1,
      totalSize: 100,
      fileName: "photos.zip",
    }).success,
  ).toBe(false);
});
