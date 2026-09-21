import type { PostHog } from "posthog-js";

import { POSTHOG_HOST, POSTHOG_KEY } from "@/shared/config";
import type { AnalyticsEventName, AnalyticsEvents, AnalyticsRoomContext } from "./analyticsEvents";

const INTERNAL_PARAM = "internal";
const INTERNAL_PROPERTY = "is_internal";

/** SDK 를 받는 동안 쌓아둘 이벤트 상한. 초기화가 끝내 실패해도 메모리가 새지 않게 한다. */
const MAX_PENDING_EVENTS = 100;

type Properties = Record<string, unknown>;

let client: PostHog | null = null;
let pendingEvents: [AnalyticsEventName, Properties][] = [];
let roomContext: AnalyticsRoomContext | null = null;

/**
 * 지금 보고 있는 방. 방 안에서 보내는 이벤트마다 `room_code`·`role` 을 붙인다.
 *
 * PostHog `register` 로 두지 않는 이유 — 그건 기기에 저장돼 탭끼리 공유된다.
 * 두 탭에서 서로 다른 방을 열면 한쪽 이벤트에 다른 방 코드가 붙는다.
 */
export const setAnalyticsRoom = (context: AnalyticsRoomContext | null) => {
  roomContext = context;
};

/**
 * 이벤트를 보낸다. 배포 빌드가 아니면 아무 일도 하지 않는다.
 * SDK 는 늦게 받으므로, 그 전에 난 이벤트는 모아 뒀다가 초기화가 끝나면 보낸다.
 */
export const track = <E extends AnalyticsEventName>(event: E, properties: AnalyticsEvents[E]) => {
  if (process.env.NODE_ENV !== "production") {
    return;
  }

  const merged: Properties = { ...roomContext, ...properties };

  if (client) {
    client.capture(event, merged);
    return;
  }
  if (pendingEvents.length < MAX_PENDING_EVENTS) {
    pendingEvents.push([event, merged]);
  }
};

/**
 * `?internal=1` 이면 팀원 기기로 표시하고, `?internal=0` 이면 표시를 푼다.
 * 그 밖에는 기존 표시를 건드리지 않는다는 뜻으로 null 을 돌려준다.
 */
export const parseInternalParam = (search: string): boolean | null => {
  const value = new URLSearchParams(search).get(INTERNAL_PARAM);

  if (value === "1") return true;
  if (value === "0") return false;
  return null;
};

/** 표시용 쿼리가 공유 링크에 딸려가지 않도록 주소창에서 지운다. */
const removeInternalParam = () => {
  const url = new URL(window.location.href);
  url.searchParams.delete(INTERNAL_PARAM);
  window.history.replaceState(window.history.state, "", url);
};

/**
 * 팀원 기기 표시. register 한 속성은 PostHog 가 기기에 저장해 두고 이후 모든 이벤트에 붙인다.
 * PostHog 의 내부 인원 필터에서 `is_internal` 이 true 인 이벤트를 빼면 지표에서 제외된다.
 */
const applyInternalFlag = (posthog: PostHog) => {
  const internal = parseInternalParam(window.location.search);
  if (internal === null) return;

  if (internal) {
    posthog.register({ [INTERNAL_PROPERTY]: true });
  } else {
    posthog.unregister(INTERNAL_PROPERTY);
  }
  removeInternalParam();
};

export const initAnalytics = async () => {
  // 배포 빌드에서만 수집한다. 이 비교는 빌드 시점에 상수로 접혀서
  // 아래 동적 import 가 죽은 코드가 되고, 개발 번들에는 SDK 가 딸려가지 않는다.
  if (process.env.NODE_ENV !== "production") {
    return;
  }
  // SDK 가 300KB 가까이 돼서 첫 번들에 넣으면 우리가 재려는 LCP 부터 늦어진다. 따로 받는다.
  const { default: posthog } = await import("posthog-js");

  posthog.init(POSTHOG_KEY, {
    api_host: POSTHOG_HOST,
    // SPA 라우팅의 페이지 이동(history 변경)도 $pageview 로 잡는 기본값 묶음
    defaults: "2026-08-30",
    session_recording: {
      // 게스트가 올린 사진·영상이 녹화에 남지 않도록 자리만 남기고 가린다
      blockSelector: "img, video",
    },
  });
  applyInternalFlag(posthog);

  client = posthog;
  pendingEvents.forEach(([event, properties]) => posthog.capture(event, properties));
  pendingEvents = [];
};
