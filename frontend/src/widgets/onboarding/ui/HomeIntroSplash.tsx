import { type RefObject, useLayoutEffect, useRef, useState } from "react";

import mascotIntro from "@/shared/assets/mascot-hello-intro.svg";
import type { HomeIntroPhase } from "../model/useHomeIntro";
import { Backdrop, Mascot, Overlay } from "./HomeIntroSplash.styles";

/**
 * 두 그림은 같은 캐릭터를 같은 자세로 그린 것이라, 구멍 하나만 맞추면 전부 겹친다.
 * 구멍의 가운데와 폭을 각 그림 크기에 대한 비율로 적는다.
 * - 인트로 SVG: 둘레에 사진이 날아갈 여백이 있어 구멍이 그림 폭의 88% 다
 * - 로고 SVG: 인트로의 마지막 장면을 캐릭터만 남게 잘라, 구멍이 그림 양 끝까지 닿는다
 */
const INTRO_HOLE = { x: 0.501, y: 0.761, width: 0.88 };
const LOGO_HOLE = { x: 0.5, y: 0.776, width: 1 };

/**
 * 등장 움직임이 끝난 뒤의 화면 위 자리. 로고가 든 헤드라인은 아래에서 8px 올라오며 나타나는데,
 * 캐릭터가 출발할 때는 아직 올라오는 중이라 보이는 자리로 재면 다 올라온 로고보다 아래에 내려앉는다.
 * 그래서 지금 자리에서 조상들이 아직 옮겨 둔 만큼(translate)을 빼 준다.
 * offsetTop 은 정수로 반올림돼 1px 가까이 어긋날 수 있어 쓰지 않는다.
 */
const settledRect = (element: HTMLElement) => {
  const rect = element.getBoundingClientRect();
  let shiftX = 0;
  let shiftY = 0;
  for (let node: HTMLElement | null = element; node; node = node.parentElement) {
    const transform = getComputedStyle(node).transform;
    if (!transform || transform === "none") continue;

    const matrix = new DOMMatrixReadOnly(transform);
    shiftX += matrix.m41;
    shiftY += matrix.m42;
  }
  return {
    left: rect.left - shiftX,
    top: rect.top - shiftY,
    width: rect.width,
    height: rect.height,
  };
};

interface HomeIntroSplashProps {
  phase: Exclude<HomeIntroPhase, "done">;
  /** 캐릭터가 줄어들며 들어갈 홈의 로고 그림 */
  logoRef: RefObject<HTMLImageElement | null>;
  onSkip: () => void;
}

/**
 * 처음 온 사람에게 캐릭터가 구멍에서 튀어나와 인사하고, 홈의 로고 자리로 쏙 들어가는 인트로.
 * 꾸밈이라 스크린 리더에서는 빠지고, 홈은 아래에 그대로 그려져 있다.
 */
export const HomeIntroSplash = ({ phase, logoRef, onSkip }: HomeIntroSplashProps) => {
  const mascotRef = useRef<HTMLImageElement>(null);
  const [flyTo, setFlyTo] = useState<string>();
  const leaving = phase === "leave";

  // 물러나는 순간의 두 그림 자리를 재서, 캐릭터가 로고와 같은 자리·크기로 줄어들게 한다
  useLayoutEffect(() => {
    if (!leaving) return;

    const mascot = mascotRef.current?.getBoundingClientRect();
    const logo = logoRef.current && settledRect(logoRef.current);
    if (!mascot || !logo || mascot.width === 0) return;

    const fromX = mascot.left + mascot.width * INTRO_HOLE.x;
    const fromY = mascot.top + mascot.height * INTRO_HOLE.y;
    const toX = logo.left + logo.width * LOGO_HOLE.x;
    const toY = logo.top + logo.height * LOGO_HOLE.y;
    const scale = (logo.width * LOGO_HOLE.width) / (mascot.width * INTRO_HOLE.width);

    setFlyTo(`translate(${toX - fromX}px, ${toY - fromY}px) scale(${scale})`);
  }, [leaving, logoRef]);

  return (
    <Overlay leaving={leaving} aria-hidden="true" data-intro-phase={phase} onClick={onSkip}>
      <Backdrop leaving={leaving} />
      <Mascot
        ref={mascotRef}
        src={mascotIntro}
        alt=""
        style={{
          transform: flyTo,
          transformOrigin: `${INTRO_HOLE.x * 100}% ${INTRO_HOLE.y * 100}%`,
        }}
      />
    </Overlay>
  );
};
