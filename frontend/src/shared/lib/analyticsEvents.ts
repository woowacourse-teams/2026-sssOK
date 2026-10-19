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
  "Room Created": { room_code: string; upload_policy: "everyone" | "host" };
  "Invite Link Copied": { is_success: boolean };

  /** 이름 입력 화면이 떴을 때. 이미 세션이 있어 곧장 들어가는 재방문은 세지 않는다. */
  "Room Join Started": { room_code: string; entry_src: AnalyticsEntrySource };
  "Room Joined": { room_code: string; entry_src: AnalyticsEntrySource };
  "Room Join Failed": { room_code: string; reason: string };

  "Photo Uploaded": { photo_count: number; failed_count: number; duration_ms: number };
  /** 한 장도 못 올린 판. 일부라도 올라갔으면 `Photo Uploaded` 의 `failed_count` 로 남는다. */
  "Photo Upload Failed": { reason: string; failed_count: number };
  /** 아무것도 안 고른 상태에서 처음 한 장을 골랐을 때. 선택 후 다운로드 전환율을 계산하는 데 쓴다. */
  "Photo Selection Started": Record<string, never>;
  /**
   * 다운로드를 시작했을 때. 성공(`Photo Downloaded`)·실패(`Photo Download Failed`) 수와 비교해
   * 완료율과 중간에 그만둔 비율을 계산한다.
   */
  "Photo Download Started": {
    mode: "individual" | "zip" | "share";
    source: AnalyticsDownloadSource;
    photo_count: number;
    /** 목록이 알려준 크기의 합 */
    total_bytes: number;
  };
  "Photo Downloaded": {
    mode: "individual" | "zip" | "share";
    /** 갤러리에서 골라 받았는지, 사진 한 장을 크게 본 화면에서 받았는지 */
    source: AnalyticsDownloadSource;
    photo_count: number;
    failed_count: number;
    /** 방 전체 장수. 고른 장수와 비교해 골라 받기(H5)를 본다. */
    total_count: number;
    is_select_all: boolean;
    /** 받기를 시작해서 다 받기까지 걸린 시간 (`trackDownload` 참고) */
    duration_ms: number;
    /** 드래그로 골랐는지. 드래그 선택이 생기면 보낸다. */
    used_drag?: boolean;
  };
  "Photo Download Failed": {
    mode: "individual" | "zip" | "share";
    source: AnalyticsDownloadSource;
    reason: string;
    failed_count: number;
    duration_ms: number;
  };
  /**
   * 받아둔 묶음 하나를 공유 시트로 넘긴 결과. 한 번에 넘기는 장수 기준을 조정하는 근거다.
   * 묶음 기준을 넘은 판과, 사파리가 막아 한 번 더 눌러 연 판에서 남는다.
   */
  "Photo Share Batch Settled": {
    batch_index: number;
    batch_count: number;
    photo_count: number;
    total_bytes: number;
    result: "shared" | "dismissed" | "blocked" | "unsupported";
  };
  "Photo Like Changed": {
    action: "like" | "unlike";
    source: "gallery" | "viewer";
  };

  "Device Link Copied": { is_success: boolean };
  /** 다른 기기에서 연결 링크로 들어와 세션을 이어받았을 때 */
  "Device Linked": { room_code: string };
  "Device Link Failed": { room_code: string };

  /** 폴더 추가 진입점이 둘이라 어느 쪽을 누르는지 본다 */
  "Folder Create Started": { source: "menu" | "filter_plus" };
  "Folder Created": { source: "menu" | "filter_plus" };

  "Gallery Filter Changed": { filter: "all" | "mine" | "others" | "liked" };
  "Gallery Sort Changed": { sort: "upload" | "popular" };

  "Search Opened": Record<string, never>;
  "Photo Searched":
    | { search_type: "keyword"; query_length: number; result_count: number; duration_ms: number }
    | { search_type: "nickname"; query_length: number; result_count: number };
  "Photo Search Failed": { reason: string };
  "Search Result Opened": {
    search_type: "keyword" | "nickname";
    rank: number;
    result_count: number;
  };
}

export type AnalyticsEventName = keyof AnalyticsEvents;

/** 방에 들어온 경로. 공유 링크로 왔는지, 코드를 직접 입력했는지 */
export type AnalyticsEntrySource = "link" | "code";

/**
 * 다운로드를 누른 자리. 지금은 갤러리 선택 바뿐이고,
 * 사진 한 장 보기 화면의 다운로드 버튼은 준비 중이라 `viewer` 는 아직 오지 않는다.
 * `long_press` 는 사진을 꾹 눌러 저장한 경우로, 그 기능이 생기면 보낸다.
 */
export type AnalyticsDownloadSource = "gallery" | "viewer" | "long_press";

/** 방 안에서 일어난 이벤트에 공통으로 붙는 속성 */
export interface AnalyticsRoomContext {
  room_code: string;
  /** 방장을 빼고 게스트만 세야 하는 지표(게스트 활성 비율 등)가 있다 */
  role: "host" | "guest";
}
