/**
 * 이 번들의 프론트엔드 버전
 * 테스트처럼 webpack 을 거치지 않는 환경에서는 `unknown` 이다.
 */
export const APP_VERSION = process.env.APP_VERSION ?? "unknown";
