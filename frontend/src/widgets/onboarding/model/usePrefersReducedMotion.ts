import { useEffect, useState } from "react";

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

export const readReducedMotion = () =>
  typeof window.matchMedia === "function" && window.matchMedia(REDUCED_MOTION_QUERY).matches;

/** 움직임을 줄이라는 기기 설정. 설정을 바꾸면 바로 따라간다. */
export const usePrefersReducedMotion = () => {
  const [reduced, setReduced] = useState(readReducedMotion);

  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;

    const query = window.matchMedia(REDUCED_MOTION_QUERY);
    const handleChange = () => setReduced(query.matches);
    query.addEventListener("change", handleChange);
    return () => query.removeEventListener("change", handleChange);
  }, []);

  return reduced;
};
