import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { myRoomsQueryKey } from "@/entities/room";
import { removeRoomSession } from "@/entities/session";
import { captureLoginCallback, completeLogin } from "../api/completeLogin";

export const useCompleteLogin = () => {
  const client = useQueryClient();
  const [status, setStatus] = useState<"pending" | "success" | "error">("pending");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    captureLoginCallback();
    void completeLogin()
      .then(async (session) => {
        if (!active) return;
        await client.cancelQueries({ queryKey: MEMBER_SESSION_QUERY_KEY });
        if (!active) return;
        client.setQueryData(MEMBER_SESSION_QUERY_KEY, session);
        client.removeQueries({ queryKey: myRoomsQueryKey });
        try {
          Object.keys(localStorage)
            .filter((key) => key.startsWith("sssok.auth:"))
            .forEach((key) => removeRoomSession(key.slice("sssok.auth:".length)));
        } catch {
          // 저장소가 차단돼도 서버에서 완료된 회원 로그인을 실패로 되돌리지 않는다.
        }
        setStatus("success");
      })
      .catch((error: unknown) => {
        if (!active) return;
        setError(error instanceof Error ? error.message : "로그인을 완료하지 못했어요.");
        setStatus("error");
      });
    return () => {
      active = false;
    };
  }, [client]);

  return { status, error };
};
