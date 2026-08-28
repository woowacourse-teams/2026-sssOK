import styled from "@emotion/styled";

import { Button } from "@/shared/ui/button";
import { colors, spacing, typography } from "@/shared/styles/tokens";

/**
 * 업로드의 `UploadFailureModal.styles` 와 거의 같다. **지금은 일부러 복제해 둔다** —
 * 두 모달이 정말 한 컴포넌트인지는 사유 문구와 조건부 재시도가 붙은 이 모달이 자리를 잡은
 * 뒤에야 알 수 있다. `shared/ui` 승격은 #119 처럼 그때 별도 이슈로 다룬다.
 */

export const Content = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: ${spacing[12]};
  text-align: center;
`;

export const Mascot = styled.img`
  width: 106px;
  height: auto;
`;

export const Title = styled.h2`
  color: ${colors.textStrong};

  ${typography.heading3}
`;

export const Description = styled.p`
  color: ${colors.textSecondary};

  ${typography.caption1}
`;

const Action = styled(Button)`
  flex: 1;
`;

export const Actions = styled.div`
  display: flex;
  gap: ${spacing[12]};
  width: 100%;
  margin-top: ${spacing[4]};
`;

export const CloseAction = styled(Action)`
  background-color: ${colors.interactiveHover};
`;

export const RetryAction = Action;
