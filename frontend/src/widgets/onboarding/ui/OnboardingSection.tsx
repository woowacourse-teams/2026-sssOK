import { CreateRoomNavButton } from "./CreateRoomNavButton";
import { JoinRoomNavButton } from "./JoinRoomNavButton";
import { OnboardingIntro } from "./OnboardingIntro";
import { ActionGroup, IntroArea, Section } from "./OnboardingSection.styles";

export const OnboardingSection = () => {
  return (
    <Section>
      <IntroArea>
        <OnboardingIntro />
      </IntroArea>

      <ActionGroup>
        <CreateRoomNavButton />
        <JoinRoomNavButton />
      </ActionGroup>
    </Section>
  );
};
