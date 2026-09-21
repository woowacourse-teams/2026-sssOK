import styled from "@emotion/styled";

import { colors, spacing, typography } from "@/shared/styles/tokens";

export const Page = styled.main`
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: ${spacing[24]};
`;

export const Form = styled.form`
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 360px;
`;

export const Heading = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: ${spacing[12]};
  margin-bottom: ${spacing[20]};
`;

export const Title = styled.h1`
  color: ${colors.textStrong};

  ${typography.heading1}
`;

export const Description = styled.p`
  color: ${colors.textSecondary};

  ${typography.body}
`;

export const Fields = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[16]};
  margin-bottom: ${spacing[20]};
`;

/** 잠금 안내는 버튼 아래에 둔다 — 왜 못 누르는지가 버튼 다음에 읽혀야 한다. */
export const LockNote = styled.p`
  margin-top: ${spacing[20]};
  color: ${colors.textSecondary};

  ${typography.caption3}
`;
