import type { AdminFeedback } from "../model/types";
import { collectFrontendVersions } from "./frontendVersions";

const feedbackWith = (frontendVersion: string | null): AdminFeedback => ({
  feedbackId: 1,
  content: "의견",
  roomId: 1,
  roomName: "방",
  memberId: 1,
  nickname: "사람",
  userAgent: null,
  frontendVersion,
  createdAt: "2026-09-16T08:41:00Z",
});

const versionsOf = (versions: (string | null)[]) =>
  collectFrontendVersions(versions.map(feedbackWith));

describe("collectFrontendVersions", () => {
  it("중복을 없애고 최신순으로 모은다", () => {
    expect(versionsOf(["fe-v0.0.9", "fe-v0.1.0", "fe-v0.0.9"])).toEqual(["fe-v0.1.0", "fe-v0.0.9"]);
  });

  it("자리수가 늘어난 버전을 뒤로 밀지 않는다", () => {
    expect(versionsOf(["fe-v0.9.0", "fe-v0.10.0"])).toEqual(["fe-v0.10.0", "fe-v0.9.0"]);
  });

  it("버전이 없는 의견은 빼고 센다", () => {
    expect(versionsOf([null, "fe-v0.1.0", null])).toEqual(["fe-v0.1.0"]);
  });

  it("버전이 하나도 없으면 빈 배열이다", () => {
    expect(versionsOf([null, null])).toEqual([]);
  });

  it("형식이 다른 값은 뒤로 몬다", () => {
    expect(versionsOf(["알 수 없음", "fe-v0.1.0"])).toEqual(["fe-v0.1.0", "알 수 없음"]);
  });
});
