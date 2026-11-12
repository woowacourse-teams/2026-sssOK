import { z } from "zod";
import { captureException } from "@/shared/lib";
import { readRoomEvent } from "./readRoomEvent";
import { OrganizeJobUpdatedEventSchema } from "./roomEventTypes";

jest.mock("@/shared/lib", () => ({ captureException: jest.fn() }));
const schema = z.object({ mediaId: z.number() });
it.each(["{", "null", "[]", '{"mediaId":"secret-token"}'])(
  "잘못된 이벤트를 버리고 본문은 기록하지 않는다: %s",
  (raw) => {
    expect(readRoomEvent(schema, raw)).toBeNull();
    expect(JSON.stringify(jest.mocked(captureException).mock.calls)).not.toContain("secret-token");
  },
);
it("오류 이후에도 정상 이벤트를 읽을 수 있다", () => {
  readRoomEvent(schema, "null");
  expect(readRoomEvent(schema, '{"mediaId":1}')).toEqual({ mediaId: 1 });
});
it("정리 이벤트의 기능별 확장 필드는 보존한다", () => {
  const payload = { jobId: "job", jobVersion: 1, status: "RUNNING", progress: { processed: 2 } };
  expect(readRoomEvent(OrganizeJobUpdatedEventSchema, JSON.stringify(payload))).toEqual(payload);
});
