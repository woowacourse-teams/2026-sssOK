/**
 * 여러 장 작업(삭제/폴더 담기/폴더 빼기/다운로드)에 실어 보낼 대상
 *
 * 갤러리가 목록 전체를 들고 있으므로 대상은 언제나 사용자가 고른 id 그대로이다.
 * 서버가 조건으로 대상을 다시 계산하는 exclude 방식은 쓰지 않는다.
 *
 * 요청 모양은 이 한 곳에서만 정한다. 서버 계약이 `mediaIds` 배열로 바뀌면 여기만 고친다.
 */
export const selectedMediaBody = (mediaIds: number[]) => ({
  selection: { mode: "include" as const, ids: mediaIds },
});
