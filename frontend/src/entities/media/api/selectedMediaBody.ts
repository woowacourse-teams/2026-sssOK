/**
 * 여러 장 작업(삭제/폴더 담기/폴더 빼기/다운로드)에 실어 보낼 대상
 *
 *
 * 요청 모양은 이 한 곳에서만 정한다.
 */
export const selectedMediaBody = (mediaIds: number[]) => ({ mediaIds });
