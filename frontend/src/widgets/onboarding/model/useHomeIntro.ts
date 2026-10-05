import { useEffect, useState } from "react";

import { readReducedMotion } from "./usePrefersReducedMotion";

/** 캐릭터가 튀어나와 인사하는 시간. 인트로 SVG 한 번 재생 길이(1.15초)보다 조금 짧게 끊는다. */
export const HOME_INTRO_PLAY_MS = 1000;
/** 캐릭터가 로고 자리로 줄어들며 화면이 드러나는 시간 */
export const HOME_INTRO_LEAVE_MS = 350;

export type HomeIntroPhase = "play" | "leave" | "done";

/**
 * 홈에 들어올 때마다 캐릭터 인트로를 보여준다.
 * 움직임을 줄이는 설정에서는 처음부터 `done` 이라 아무것도 기다리지 않는다.
 */
export const useHomeIntro = () => {
  const [phase, setPhase] = useState<HomeIntroPhase>(() => (readReducedMotion() ? "done" : "play"));
  const [hasIntro] = useState(phase !== "done");

  useEffect(() => {
    if (phase === "done") return;

    const timer = window.setTimeout(
      () => setPhase(phase === "play" ? "leave" : "done"),
      phase === "play" ? HOME_INTRO_PLAY_MS : HOME_INTRO_LEAVE_MS,
    );
    return () => window.clearTimeout(timer);
  }, [phase]);

  /** 화면을 누르면 인사를 끊고 바로 물러난다 */
  const skip = () => setPhase((current) => (current === "play" ? "leave" : current));

  return { phase, hasIntro, skip };
};
