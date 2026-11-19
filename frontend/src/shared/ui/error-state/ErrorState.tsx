import { useId, type ReactNode } from "react";

import mascot from "@/shared/assets/mascot-close.png";
import { Actions, Content, Description, Image, Page, Title } from "./ErrorState.styles";

interface ErrorStateProps {
  title: string;
  description: ReactNode;
  children: ReactNode;
}

export const ErrorState = ({ title, description, children }: ErrorStateProps) => {
  const titleId = useId();

  return (
    <Page aria-labelledby={titleId}>
      <Content role="alert">
        <Image src={mascot} alt="" />
        <div>
          <Title id={titleId}>{title}</Title>
          <Description>{description}</Description>
        </div>
      </Content>
      <Actions>{children}</Actions>
    </Page>
  );
};
