import { useRef, useState } from "react";

import { startKakaoLogin } from "../api/startKakaoLogin";

export const useKakaoLogin = () => {
  const inFlight = useRef(false);
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState("");

  const login = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setIsPending(true);
    setError("");
    try {
      window.location.assign(await startKakaoLogin());
    } catch (error) {
      setError(error instanceof Error ? error.message : "카카오 로그인을 시작하지 못했어요.");
    } finally {
      inFlight.current = false;
      setIsPending(false);
    }
  };

  return { login, isPending, error };
};
