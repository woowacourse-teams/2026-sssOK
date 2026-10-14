import { useEffect, useState, type RefObject } from "react";

interface FitScaleOptions {
  /** 그림을 처음 그린 크기 */
  width: number;
  height: number;
  /** 칸 가장자리와 띄울 여백 */
  padding: number;
  /** 넓은 화면에서 그림이 지나치게 커지지 않게 막는 상한 */
  max: number;
}

/**
 * 고정 크기로 그린 그림이 담긴 칸을 꽉 채우도록 확대 배율을 구한다.
 * 칸은 화면 높이에 따라 늘어나는데 그림이 그대로면 가운데에 작게 떠 보인다.
 *
 * `fillWidth`·`fillHeight` 는 그 배율로 칸을 꽉 채우려면 그림을 얼마나 넓고 높게 그려야
 * 하는지다. 한쪽에 맞춰 줄어 다른 쪽이 남을 때, 남는 만큼을 그림 안에서 쓰게 한다.
 */
export const useFitScale = (
  ref: RefObject<HTMLElement | null>,
  { width, height, padding, max }: FitScaleOptions,
) => {
  const [fit, setFit] = useState({ scale: 1, fillWidth: width, fillHeight: height });

  useEffect(() => {
    const element = ref.current;
    if (!element || typeof ResizeObserver === "undefined") return;

    const observer = new ResizeObserver(([entry]) => {
      const { width: boxWidth, height: boxHeight } = entry.contentRect;
      const next = Math.min(
        (boxWidth - padding * 2) / width,
        (boxHeight - padding * 2) / height,
        max,
      );
      const scale = Math.max(next, 0);
      setFit({
        scale,
        fillWidth: scale > 0 ? Math.max(width, (boxWidth - padding * 2) / scale) : width,
        fillHeight: scale > 0 ? Math.max(height, (boxHeight - padding * 2) / scale) : height,
      });
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [ref, width, height, padding, max]);

  return fit;
};
