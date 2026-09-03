/**
 * 401 을 만났을 때 부를 자리 하나 (#149).
 *
 * 세션을 지우고 입장 화면으로 되돌리는 일은 여기서 할 수 없다 — 세션(entities)도
 * 라우터(app)도 shared 보다 위 레이어라 import 할 수 없다. 그래서 자리만 비워두고
 * 앱이 뜰 때 위에서 채운다 (`useUnauthorizedRecovery`).
 *
 * 방 코드가 아니라 **토큰**을 넘긴다. 401 은 "요청에 실린 토큰이 죽었다" 는 뜻인데
 * `apiClient` 는 그 요청이 어느 방 것인지 모른다. 토큰으로 방을 되짚는 일은 세션을
 * 들고 있는 쪽(`findRoomCodeByToken`)의 몫이다.
 */
type UnauthorizedHandler = (token: string) => void;

let handler: UnauthorizedHandler | null = null;

/** 등록하고 정리 함수를 돌려준다. effect 의 cleanup 으로 그대로 쓴다. */
export const setUnauthorizedHandler = (next: UnauthorizedHandler) => {
  handler = next;

  return () => {
    // 그 사이 다른 핸들러가 자리를 넘겨받았다면 그것까지 지우지는 않는다.
    if (handler === next) {
      handler = null;
    }
  };
};

/** 등록된 핸들러가 없으면 아무 일도 하지 않는다 — 인증을 쓰지 않는 화면도 있다. */
export const notifyUnauthorized = (token: string) => handler?.(token);
