import posthog from "posthog-js";

import { initAnalytics, parseInternalParam, track } from "./analytics";

jest.mock("posthog-js", () => ({
  __esModule: true,
  default: { init: jest.fn(), register: jest.fn(), unregister: jest.fn(), capture: jest.fn() },
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

describe("배포 빌드가 아닐 때", () => {
  it("PostHog 를 띄우지 않는다", async () => {
    await initAnalytics();

    expect(posthog.init).not.toHaveBeenCalled();
  });

  it("이벤트를 보내지 않는다", () => {
    track("Room Create Started", {});

    expect(posthog.capture).not.toHaveBeenCalled();
  });
});

describe("배포 빌드일 때", () => {
  const originalEnv = process.env.NODE_ENV;

  beforeEach(() => {
    process.env.NODE_ENV = "production";
  });

  afterEach(() => {
    process.env.NODE_ENV = originalEnv;
  });

  // 모듈이 들고 있는 SDK·대기열·방 정보를 테스트마다 새로 시작한다
  const loadFresh = () => {
    let analytics!: typeof import("./analytics");
    jest.isolateModules(() => {
      analytics = jest.requireActual("./analytics");
    });
    return analytics;
  };

  it("SDK 를 받기 전에 난 이벤트는 초기화가 끝난 뒤 보낸다", async () => {
    const analytics = loadFresh();

    analytics.track("Room Create Started", {});
    expect(posthog.capture).not.toHaveBeenCalled();

    await analytics.initAnalytics();

    expect(posthog.capture).toHaveBeenCalledWith("Room Create Started", {});
  });

  it("방 안에서는 room_code 와 role 을 함께 보낸다", async () => {
    const analytics = loadFresh();
    await analytics.initAnalytics();

    analytics.setAnalyticsRoom({ room_code: "ABC123", role: "guest" });
    analytics.track("Folder Created", { source: "menu" });

    expect(posthog.capture).toHaveBeenCalledWith("Folder Created", {
      room_code: "ABC123",
      role: "guest",
      source: "menu",
    });
  });

  it("방을 따로 넘기면 지금 방 대신 그 방으로 남긴다", async () => {
    const analytics = loadFresh();
    await analytics.initAnalytics();

    // 업로드를 시작한 방을 잡아 둔 뒤 갤러리를 떠난 상황
    const startedRoom = { room_code: "ABC123", role: "guest" } as const;
    analytics.setAnalyticsRoom(null);
    analytics.track(
      "Photo Uploaded",
      { photo_count: 1, failed_count: 0, duration_ms: 10 },
      startedRoom,
    );

    expect(posthog.capture).toHaveBeenCalledWith("Photo Uploaded", {
      room_code: "ABC123",
      role: "guest",
      photo_count: 1,
      failed_count: 0,
      duration_ms: 10,
    });
  });

  it("방을 나가면 방 속성을 붙이지 않는다", async () => {
    const analytics = loadFresh();
    await analytics.initAnalytics();

    analytics.setAnalyticsRoom({ room_code: "ABC123", role: "host" });
    analytics.setAnalyticsRoom(null);
    analytics.track("Room Create Started", {});

    expect(posthog.capture).toHaveBeenCalledWith("Room Create Started", {});
  });
});
