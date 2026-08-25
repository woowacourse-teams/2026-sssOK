import styled from "@emotion/styled";

import { spacing } from "@/shared/styles/tokens";

export const Section = styled.section`
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
`;

export const IntroArea = styled.div`
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: ${spacing[32]} 0;
`;

export const ActionGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[12]};
  width: 100%;
`;
