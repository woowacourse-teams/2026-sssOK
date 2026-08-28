/**
 * #120 · #121 에서 정한 값들. 근거는 docs/frontend/DOWNLOAD_FLOW.md 에 있다.
 * 실기기·실제 회선에서 재면 다시 볼 값이다.
 */

/**
 * 동시에 받는 파일 개수.
 *
 * 업로드(`UPLOAD_CONCURRENCY`)와 같은 이유다 — 한꺼번에 30개를 열면 회선을 나눠 갖느라
 * 전부 느려지고, 폰에서는 연결 수 자체가 막힌다.
 */
export const DOWNLOAD_CONCURRENCY = 4;

/**
 * 개별 저장 사이의 간격(ms).
 *
 * 브라우저는 짧은 시간에 몰아친 저장을 "원치 않는 다운로드"로 보고 두 번째부터 막는다.
 * 틈을 두면 크롬은 "여러 파일 다운로드를 허용하시겠습니까"를 한 번 묻고 나머지를 통과시킨다.
 */
export const INDIVIDUAL_SAVE_GAP_MS = 400;

/** 압축 잡 상태를 되묻는 간격(ms). 명세의 "프론트는 1~2초 간격으로 조회한다" 를 따른다. */
export const POLL_INTERVAL_MS = 1500;
