import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { myRoomsQueryKey } from "@/entities/room";
import { ROUTES } from "@/shared/config";
import { takeLoginReturn } from "@/shared/lib";
import { loginNative } from "./loginNative";
import { supportsNativeLogin, type LoginProvider } from "./nativeAuthBridge";
import { startKakaoLogin } from "../api/startKakaoLogin";
import { startAppleLogin } from "../api/startAppleLogin";

const startWebLogin = { kakao: startKakaoLogin, apple: startAppleLogin };

export const useSocialLogin = () => {
  const client = useQueryClient();
  const navigate = useNavigate();
  const pending = useRef<AbortController | null>(null);
  const redirecting = useRef(false);
  const [pendingProvider, setPendingProvider] = useState<LoginProvider | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const restore = (event: PageTransitionEvent) => {
      if (!event.persisted || !redirecting.current) return;

      pending.current?.abort();
      pending.current = null;
      redirecting.current = false;
      setPendingProvider(null);
      setError("");
    };

    window.addEventListener("pageshow", restore);
    return () => {
      window.removeEventListener("pageshow", restore);
      pending.current?.abort();
      pending.current = null;
    };
  }, []);

  const login = async (provider: LoginProvider) => {
    if (pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    setPendingProvider(provider);
    setError("");
    try {
      if (!supportsNativeLogin(provider)) {
        const url = await startWebLogin[provider]();
        if (controller.signal.aborted) return;
        window.location.assign(url);
        // assign은 이동 완료를 기다리지 않으므로 페이지를 떠날 때까지 로딩을 유지한다.
        redirecting.current = true;
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
      if (!redirecting.current && pending.current === controller) {
        if (!controller.signal.aborted) setPendingProvider(null);
        pending.current = null;
      }
    }
  };

  return { login, pendingProvider, isPending: pendingProvider !== null, error };
};
