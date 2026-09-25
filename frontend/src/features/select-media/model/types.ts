/** 다건 작업 API 가 받는 선택 범위. 화면은 언제나 고른 id 를 `include` 로 보낸다. */
export interface MediaSelectionRequest {
  mode: "include" | "exclude";
  ids: number[];
}
