import posthog, { type CaptureResult } from "posthog-js";

import { parseMemberIds } from "@/shared/config";
import { attachCommonProperties, initAnalytics, parseInternalParam, track } from "./analytics";

jest.mock("posthog-js", () => ({
  __esModule: true,
  default: {
    init: jest.fn(),
    register: jest.fn(),
    unregister: jest.fn(),
    capture: jest.fn(),
    captureException: jest.fn(),
  },
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

describe("attachCommonProperties", () => {
  const pageview = (): CaptureResult => ({
    uuid: "event-1",
    event: "$pageview",
    properties: { $current_url: "/" },
  });

  afterEach(() => {
    delete window.ReactNativeWebView;
  });

  it("브라우저에서는 번들 버전과 web 을 붙인다", () => {
    expect(attachCommonProperties(pageview())?.properties).toEqual({
      $current_url: "/",
      app_version: "unknown",
      platform: "web",
    });
  });

  it("쏙 앱 웹뷰에서는 app 을 붙인다", () => {
    window.ReactNativeWebView = { postMessage: jest.fn() };

    expect(attachCommonProperties(pageview())?.properties).toMatchObject({ platform: "app" });
  });

  it("앞선 단계가 버린 이벤트는 그대로 버린다", () => {
    expect(attachCommonProperties(null)).toBeNull();
  });
});

describe("parseMemberIds", () => {
  it("쉼표로 이은 회원 ID 를 읽고, 빈칸과 숫자가 아닌 값은 버린다", () => {
    expect(parseMemberIds(" 12, 34 ,,abc,5x")).toEqual([12, 34]);
  });

  it("값이 없으면 빈 목록이다", () => {
    expect(parseMemberIds(undefined)).toEqual([]);
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
  const loadFresh = (): typeof import("./analytics") => {
    let analytics!: typeof import("./analytics");
    jest.isolateModules(() => {
      analytics = jest.requireActual("./analytics");
    });
    return analytics;
  };

  it("모든 이벤트에 번들 버전과 실행 환경이 붙도록 초기화한다", async () => {
    const analytics = loadFresh();
    await analytics.initAnalytics();

    expect(posthog.init).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ before_send: analytics.attachCommonProperties }),
    );
  });

  describe("팀원 계정 로그인", () => {
    const TEAM = [12, 34];

    it("팀원 계정이면 이 기기를 팀원 기기로 표시한다", async () => {
      const analytics = loadFresh();
      await analytics.initAnalytics();

      analytics.markInternalMember(34, TEAM);

      expect(posthog.register).toHaveBeenCalledWith({ is_internal: true });
    });

    it("SDK 를 받기 전에 확인했으면 초기화할 때 표시한다", async () => {
      const analytics = loadFresh();

      analytics.markInternalMember(12, TEAM);
      expect(posthog.register).not.toHaveBeenCalled();
      await analytics.initAnalytics();

      expect(posthog.register).toHaveBeenCalledWith({ is_internal: true });
    });

    it("팀원이 아니면 표시를 켜지도 지우지도 않는다", async () => {
      const analytics = loadFresh();
      await analytics.initAnalytics();

      analytics.markInternalMember(99, TEAM);

      expect(posthog.register).not.toHaveBeenCalled();
      expect(posthog.unregister).not.toHaveBeenCalled();
    });
  });

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
      { photo_count: 1, failed_count: 0, duration_ms: 10, uploaded_format_counts: { jpeg: 1 } },
      startedRoom,
    );

    expect(posthog.capture).toHaveBeenCalledWith("Photo Uploaded", {
      room_code: "ABC123",
      role: "guest",
      photo_count: 1,
      failed_count: 0,
      duration_ms: 10,
      uploaded_format_counts: { jpeg: 1 },
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

  it("예외와 디버깅 문맥을 함께 보낸다", async () => {
    const analytics = loadFresh();
    await analytics.initAnalytics();
    const error = new Error("요청 실패");

    analytics.captureException(error, {
      level: "error",
      operation: "gallery.get_photos",
      method: "GET",
      route: "/rooms/:roomId/media/all",
      status: 500,
      code: "INTERNAL_SERVER_ERROR",
    });

    expect(posthog.captureException).toHaveBeenCalledWith(error, {
      $exception_level: "error",
      operation: "gallery.get_photos",
      api_method: "GET",
      api_route: "/rooms/:roomId/media/all",
      api_status: 500,
      api_error_code: "INTERNAL_SERVER_ERROR",
      environment: "production",
    });
  });
});
