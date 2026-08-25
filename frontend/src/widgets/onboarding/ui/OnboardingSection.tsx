import { CreateRoomButton } from "@/features/create-room";
import { JoinRoomButton } from "@/features/join-room";
import { OnboardingIntro } from "./OnboardingIntro";
import { ActionGroup, IntroArea, Section } from "./OnboardingSection.styles";

export const OnboardingSection = () => {
  return (
    <Section>
      <IntroArea>
        <OnboardingIntro />
      </IntroArea>

      <ActionGroup>
        <CreateRoomButton />
        <JoinRoomButton />
      </ActionGroup>
    </Section>
  );
};
