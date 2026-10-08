import { CreateRoomNavButton } from "./CreateRoomNavButton";
import { JoinRoomNavButton } from "./JoinRoomNavButton";
import { OnboardingIntro } from "./OnboardingIntro";
import { OnboardingPreview } from "./OnboardingPreview";
import { ActionGroup, IntroArea, PreviewArea, Section } from "./OnboardingSection.styles";

/**
 * 처음 온 사람이 3초 안에 "무엇을 하는지 · 어떻게 쓰는지 · 무엇을 누르면 되는지" 를
 * 알 수 있도록 헤드라인 → 사용 흐름 미리보기 → 버튼 순으로 한 화면에 놓는다.
 * 넓은 화면에서는 왼쪽에 헤드라인·버튼, 오른쪽에 미리보기를 둔다.
 */
export const OnboardingSection = () => {
  return (
    <Section>
      <IntroArea>
        <OnboardingIntro />
      </IntroArea>

      <PreviewArea>
        <OnboardingPreview />
      </PreviewArea>

      <ActionGroup>
        <CreateRoomNavButton />
        <JoinRoomNavButton />
      </ActionGroup>
    </Section>
  );
};
