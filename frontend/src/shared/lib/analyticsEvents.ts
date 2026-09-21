/**
 * 수집하는 이벤트와 그 속성. 이름은 프로토타입(Amplitude)과 똑같이 맞춰
 * 두 기간의 데이터를 이어서 볼 수 있게 한다.
 *
 * 레벨4 유저 데이터 리포트가 이 이벤트로 계산된다.
 * - 활성 유저: `Photo Uploaded` 또는 `Photo Downloaded` 를 한 사람
 * - 핵심 지표(공유 성사 방): 같은 `room_code` 에서 활성 유저가 2명 이상인 방
 */
export interface AnalyticsEvents {
  "Room Create Started": Record<string, never>;
  "Room Created": { room_code: string; expiry_hours: number; upload_policy: "everyone" | "host" };
  "Invite Link Copied": { is_success: boolean };

  /** 이름 입력 화면이 떴을 때. 이미 세션이 있어 곧장 들어가는 재방문은 세지 않는다. */
  "Room Join Started": { room_code: string };
  "Room Joined": { room_code: string };
  "Room Join Failed": { room_code: string; reason: string };

  "Photo Uploaded": { photo_count: number; failed_count: number; duration_ms: number };
  /** 한 장도 못 올린 판. 일부라도 올라갔으면 `Photo Uploaded` 의 `failed_count` 로 남는다. */
  "Photo Upload Failed": { reason: string; failed_count: number };
  "Photo Downloaded": {
    mode: "individual" | "zip" | "share";
    photo_count: number;
    failed_count: number;
    /** 방 전체 장수. 고른 장수와 비교해 골라 받기(H5)를 본다. */
    total_count: number;
    is_select_all: boolean;
  };
  "Photo Download Failed": {
    mode: "individual" | "zip" | "share";
    reason: string;
    failed_count: number;
  };

  "Device Link Copied": { is_success: boolean };
  /** 다른 기기에서 연결 링크로 들어와 세션을 이어받았을 때 */
  "Device Linked": { room_code: string };
  "Device Link Failed": { room_code: string };

  /** 폴더 추가 진입점이 둘이라 어느 쪽을 누르는지 본다 */
  "Folder Create Started": { source: "menu" | "filter_plus" };
  "Folder Created": { source: "menu" | "filter_plus" };
}

export type AnalyticsEventName = keyof AnalyticsEvents;

/** 방 안에서 일어난 이벤트에 공통으로 붙는 속성 */
export interface AnalyticsRoomContext {
  room_code: string;
  /** 방장을 빼고 게스트만 세야 하는 지표(게스트 활성 비율 등)가 있다 */
  role: "host" | "guest";
}
