import posthog from "posthog-js";

import { initAnalytics, parseInternalParam } from "./analytics";

jest.mock("posthog-js", () => ({
  __esModule: true,
  default: { init: jest.fn(), register: jest.fn(), unregister: jest.fn() },
}));

describe("parseInternalParam", () => {
  it("internal=1 이면 팀원 기기로 표시한다", () => {
    expect(parseInternalParam("?internal=1")).toBe(true);
  });

  it("internal=0 이면 표시를 푼다", () => {
    expect(parseInternalParam("?internal=0")).toBe(false);
  });

  it("쿼리가 없거나 다른 값이면 기존 표시를 건드리지 않는다", () => {
    expect(parseInternalParam("")).toBeNull();
    expect(parseInternalParam("?internal=yes")).toBeNull();
    expect(parseInternalParam("?code=abc")).toBeNull();
  });
});

describe("initAnalytics", () => {
  it("배포 빌드가 아니면 PostHog 를 띄우지 않는다", async () => {
    await initAnalytics();

    expect(posthog.init).not.toHaveBeenCalled();
  });
});
