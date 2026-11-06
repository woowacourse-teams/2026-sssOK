import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { myRoomsQueryKey } from "@/entities/room";
import { ROUTES } from "@/shared/config";
import { takeLoginReturn } from "@/shared/lib";
import { loginNative } from "./loginNative";
import { supportsNativeLogin, type LoginProvider } from "./nativeAuthBridge";

export const useSocialLogin = (provider: LoginProvider, startWebLogin: () => Promise<string>) => {
  const client = useQueryClient();
  const navigate = useNavigate();
  const pending = useRef<AbortController | null>(null);
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => () => pending.current?.abort(), []);

  const login = async () => {
    if (pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    setIsPending(true);
    setError("");
    try {
      if (!supportsNativeLogin(provider)) {
        const url = await startWebLogin();
        if (!controller.signal.aborted) window.location.assign(url);
        return;
      }
      const session = await loginNative(provider, controller.signal);
      if (!session) return;
      await client.cancelQueries({ queryKey: MEMBER_SESSION_QUERY_KEY });
      controller.signal.throwIfAborted();
      client.setQueryData(MEMBER_SESSION_QUERY_KEY, session);
      client.removeQueries({ queryKey: myRoomsQueryKey });
      navigate(takeLoginReturn() ?? ROUTES.rooms, { replace: true });
    } catch (error) {
      if (!controller.signal.aborted)
        setError(error instanceof Error ? error.message : "로그인을 완료하지 못했어요.");
    } finally {
      if (!controller.signal.aborted) setIsPending(false);
      pending.current = null;
    }
  };

  return { login, isPending, error };
};
