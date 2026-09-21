import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";

import { loginWithLinkCode, saveRoomSession } from "@/entities/session";
import { track } from "@/shared/lib";

export const useLinkCodeSession = (roomCode: string) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const linkCode = searchParams.get("linkCode");

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
