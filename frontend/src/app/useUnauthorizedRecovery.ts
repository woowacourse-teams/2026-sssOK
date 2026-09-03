import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

import { findRoomCodeByToken, removeRoomSession } from "@/entities/session";
import { setUnauthorizedHandler } from "@/shared/api";
import { ROUTES } from "@/shared/config";

/**
 * 401 을 만나면 그 방 세션을 버리고 입장 화면으로 되돌린다 (#149).
 *
 * **한 곳에서만 한다.** 화면마다 401 을 알아보게 두면 하나씩 빠뜨리고, 빠뜨린 화면은
 * 무효한 토큰을 들고 그 자리에 갇힌다 — 다음 요청도 같은 토큰으로 나가 401 만 되풀이한다.
 * 그래서 판단은 `apiClient` 가 하고(모든 요청이 지나는 길목이다) 뒷정리만 여기서 받는다.
 *
 * 세션을 지우는 것까지가 반이다. 지우기만 하면 사용자는 "코드로 입장" 을 스스로 다시
 * 찾아야 해서, 이름만 다시 받으면 이어갈 수 있는 입장 화면까지 데려다준다.
 */
export const useUnauthorizedRecovery = () => {
  const navigate = useNavigate();

  useEffect(
    () =>
      setUnauthorizedHandler((token) => {
        const roomCode = findRoomCodeByToken(token);

        // 이미 지운 세션의 토큰이다. 같이 나갔던 다른 요청이 뒤늦게 401 로 돌아온 것이라
        // 다시 옮길 화면이 없다 — 여기서 또 이동하면 사용자가 방금 연 화면을 밀어낸다.
        if (roomCode === null) return;

        removeRoomSession(roomCode);
        // 되돌아갈 자리가 아니다. 뒤로 가기로 다시 들어와도 세션이 없어 그대로 튕긴다.
        navigate(ROUTES.roomEntry(roomCode), { replace: true });
      }),
    [navigate],
  );
};
