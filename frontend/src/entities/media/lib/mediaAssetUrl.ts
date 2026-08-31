import { API_BASE_URL } from "@/shared/config";

/**
 * 목록이 알려준 파생 URL 을 실제로 부를 수 있는 주소로 만든다.
 *
 * 서버는 `thumbnailUrl`·`originalUrl` 을 **상대 경로**로 내려준다
 * (`/api/v1/rooms/20/media/128/thumbnail`). 그대로 `<img src>` 에 넣으면 API 가 아니라
 * **지금 페이지의 오리진** 기준으로 풀린다. 개발 서버에서는 `historyApiFallback` 이
 * `index.html` 을 200 으로 돌려줘서, 깨진 그림인데도 요청은 성공한 것처럼 보인다.
 *
 * 이미 절대 주소면 그대로 둔다 — 서버가 서명 URL 을 직접 싣기 시작해도 여기는 안 고쳐도 된다.
 */
export const mediaAssetUrl = (url: string) => new URL(url, API_BASE_URL).href;
