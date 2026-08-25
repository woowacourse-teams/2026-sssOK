/**
 * 실제 API 베이스 URL. backend WebConfig 가 붙이는 /api/v1 까지 포함한다.
 * 개발 중에는 MSW 가 이 주소로 나가는 요청을 가로채므로 서버가 없어도 된다.
 */
export const API_BASE_URL = process.env.API_BASE_URL ?? "https://api.ssssok.com/api/v1";
