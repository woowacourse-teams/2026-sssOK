import { useRef } from "react";

import { HOME_INTRO_PLAY_MS, useHomeIntro } from "../model/useHomeIntro";
import { CreateRoomNavButton } from "./CreateRoomNavButton";
import { HomeIntroSplash } from "./HomeIntroSplash";
import { JoinRoomNavButton } from "./JoinRoomNavButton";
import { OnboardingIntro } from "./OnboardingIntro";
import { OnboardingPreview } from "./OnboardingPreview";
import { ActionGroup, IntroArea, PreviewArea, Section } from "./OnboardingSection.styles";

/**
 * 처음 온 사람이 3초 안에 "무엇을 하는지 · 어떻게 쓰는지 · 무엇을 누르면 되는지" 를
 * 알 수 있도록 헤드라인 → 사용 흐름 미리보기 → 버튼 순으로 한 화면에 놓는다.
 * 넓은 화면에서는 왼쪽에 헤드라인·버튼, 오른쪽에 미리보기를 둔다.
 *
 * 처음 온 사람에게는 캐릭터 인트로가 먼저 1초 인사하고 로고 자리로 들어간다.
 * 헤드라인·미리보기·버튼은 인트로가 걷히는 때에 맞춰 나타나고,
 * 미리보기의 첫 단계는 캐릭터가 로고 자리에 닿고 화면이 다 나타난 뒤 잠깐 쉬었다가 시작한다.
 */
export const OnboardingSection = () => {
  const logoRef = useRef<HTMLImageElement>(null);
  const intro = useHomeIntro();

  return (
    <Section enterOffsetMs={intro.hasIntro ? HOME_INTRO_PLAY_MS : 0}>
      <IntroArea>
        <OnboardingIntro logoRef={logoRef} hideLogo={intro.phase !== "done"} />
      </IntroArea>

      <PreviewArea>
        <OnboardingPreview paused={!intro.settled} />
      </PreviewArea>

      <ActionGroup>
        <CreateRoomNavButton />
        <JoinRoomNavButton />
      </ActionGroup>

      {intro.phase !== "done" && (
        <HomeIntroSplash phase={intro.phase} logoRef={logoRef} onSkip={intro.skip} />
      )}
    </Section>
  );
};
