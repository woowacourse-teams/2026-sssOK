import { ROUTES } from "@/shared/config";
import { isAppWebView } from "@/shared/lib";
import { useMemberSessionQuery } from "./useMemberSessionQuery";

export type StartDestination =
  | { kind: "rooms"; to: typeof ROUTES.rooms }
  | { kind: "landing"; to: typeof ROUTES.landing };

/**
 * 홈 화면 대신 돌아갈 곳. 앱은 로그인이 필수라 늘 방 목록이고,
 * 웹은 로그인했으면 방 목록, 게스트면 랜딩이다.
 * 로그인 확인 중에는 게스트로 본다 — 확인이 끝나면 다시 그려진다.
 */
export const useStartDestination = (): StartDestination => {
  const session = useMemberSessionQuery();
  return isAppWebView() || session.data
    ? { kind: "rooms", to: ROUTES.rooms }
    : { kind: "landing", to: ROUTES.landing };
};
