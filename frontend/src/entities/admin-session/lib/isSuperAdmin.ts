/**
 * 슈퍼관리자인지. 화면에서 무엇을 보여줄지 가리는 데만 쓴다 — 실제 권한 판정은 서버가 한다.
 *
 * `SUPER_ADMIN` 과 정확히 같을 때만 참이다. 모르는 역할에 슈퍼관리자 버튼을 열어 주면
 * 누를 때마다 403 을 받는 화면이 된다.
 */
export const isSuperAdmin = (role: string) => role === "SUPER_ADMIN";
