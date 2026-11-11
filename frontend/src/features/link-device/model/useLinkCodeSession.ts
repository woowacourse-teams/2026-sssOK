import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";

import { loginWithLinkCode, saveRoomSession } from "@/entities/session";
import { isAppWebView, track } from "@/shared/lib";

export const useLinkCodeSession = (roomCode: string) => {
  const [searchParams, setSearchParams] = useSearchParams();
  // 앱에서는 현재 로그인한 회원으로 참여한다. 기기 연결로 익명 인증을 교환하지 않는다.
  const linkCode = isAppWebView() ? null : searchParams.get("linkCode");

  useEffect(() => {
    if (!linkCode) return;

    void loginWithLinkCode(linkCode)
      .then((session) => {
        saveRoomSession(roomCode, session);
        track("Device Linked", { room_code: roomCode });
      })
      .catch(() => track("Device Link Failed", { room_code: roomCode }))
      .finally(() => {
        setSearchParams(
          (current) => {
            const next = new URLSearchParams(current);
            next.delete("linkCode");
            return next;
          },
          { replace: true },
        );
      });
  }, [linkCode, roomCode, setSearchParams]);

  return { isLinking: linkCode !== null };
};
