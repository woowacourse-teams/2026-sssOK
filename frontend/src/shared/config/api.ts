/** backend WebConfig가 붙이는 /api/v1까지 포함한, 빌드 시 주입된 API 주소다. */
export const API_BASE_URL = process.env.API_BASE_URL;

/**
 * 업로드 파일을 R2 로 바로 보내지 않고 개발 서버를 거쳐 보낼 때 쓰는 경로.
 * `pnpm start:app` 으로 띄울 때만 값이 있다. 평소 실행과 배포에서는 비어 있어 R2 로 바로 보낸다.
 */
export const STORAGE_PROXY_PATH = process.env.STORAGE_PROXY_PATH;
