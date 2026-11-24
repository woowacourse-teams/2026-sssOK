import { ApiError } from "@/shared/api";
import { track } from "@/shared/lib/analytics";
import {
  trackOrganizeCompleted,
  trackOrganizeOpened,
  trackOrganizeRejected,
  trackOrganizeStartFailed,
} from "./trackOrganize";

jest.mock("@/shared/lib/analytics", () => ({
  ...jest.requireActual("@/shared/lib/analytics"),
  track: jest.fn(),
}));

const ROOM = { room_code: "44MCJRUR", role: "host" } as const;

beforeEach(() => jest.mocked(track).mockClear());

describe("trackOrganize", () => {
  it("정리 버튼을 누르면 센 장수를 남기고, 막히지 않았으면 막힘은 남기지 않는다", () => {
    trackOrganizeOpened(40, null);

    expect(track).toHaveBeenCalledTimes(1);
    expect(track).toHaveBeenCalledWith("Photo Organize Opened", { photo_count: 40 });
  });

  it("200장이 넘어 버튼에서 막히면 사유와 장수를 함께 남긴다", () => {
    trackOrganizeOpened(500, { reason: "tooMany", count: 500 });

    expect(track).toHaveBeenLastCalledWith("Photo Organize Blocked", {
      reason: "tooMany",
      photo_count: 500,
      stage: "precheck",
    });
  });

  it("서버가 체험 소진으로 거절하면 장수 없이 server 단계로 남긴다", () => {
    trackOrganizeRejected({ reason: "trialExhausted" });

    expect(track).toHaveBeenCalledWith("Photo Organize Blocked", {
      reason: "trialExhausted",
      photo_count: null,
      stage: "server",
    });
  });

  it("접수 실패는 서버 오류 코드를, 모르는 오류는 unknown 을 남긴다", () => {
    trackOrganizeStartFailed(new ApiError(429, "ORGANIZE_RATE_LIMITED", "잠시 뒤"));
    trackOrganizeStartFailed(new Error("network"));

    expect(track).toHaveBeenNthCalledWith(1, "Photo Organize Failed", {
      reason: "ORGANIZE_RATE_LIMITED",
      stage: "start",
    });
    expect(track).toHaveBeenNthCalledWith(2, "Photo Organize Failed", {
      reason: "unknown",
      stage: "start",
    });
  });

  it("완료는 시작할 때 잡아 둔 방으로, 접수부터 걸린 시간과 함께 남긴다", () => {
    trackOrganizeCompleted(
      { jobId: "job", kind: "PERSON", photoCount: 40, startedAt: 1_000, room: ROOM },
      6,
      61_000,
    );

    expect(track).toHaveBeenCalledWith(
      "Photo Organize Completed",
      { kind: "PERSON", photo_count: 40, group_count: 6, duration_ms: 60_000 },
      ROOM,
    );
  });
});
