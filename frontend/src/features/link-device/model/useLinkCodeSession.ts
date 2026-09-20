import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";

import { loginWithLinkCode, saveRoomSession } from "@/entities/session";

export const useLinkCodeSession = (roomCode: string) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const linkCode = searchParams.get("linkCode");

  useEffect(() => {
    if (!linkCode) return;

    void loginWithLinkCode(linkCode)
      .then((session) => saveRoomSession(roomCode, session))
      .catch(() => undefined)
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
