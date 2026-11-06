import styled from "@emotion/styled";

import { colors, spacing } from "@/shared/styles/tokens";
import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { Button } from "@/shared/ui/button";
import { Stack } from "@/shared/ui/stack";

interface SimilarMergeSheetProps {
  /** 두 모음이 함께 쓰려는 폴더 이름. */
  name: string;
  onMerge: () => void;
  onCancel: () => void;
}

/**
 * 다른 모음과 같은 이름을 붙였을 때 한 폴더로 합칠지 묻는다.
 * 피그마 "Ssok/BottomSheet · 확인"(660:2719): 공용 BottomSheet 에 Button(primary·default)을 넣는다.
 * 바깥을 누르거나 손잡이를 내리면 합치지 않는 쪽이다.
 */
export const SimilarMergeSheet = ({ name, onMerge, onCancel }: SimilarMergeSheetProps) => (
  <BottomSheet title="한 폴더로 합칠까요?" onClose={onCancel}>
    <Body>
      ‘{name}’ 모음이 이미 있어요.
      <br />
      합치면 한 폴더로 만들어져요.
    </Body>
    <Stack gap={8}>
      <Button onClick={onMerge}>네, 합칠게요</Button>
      <Button variant="default" onClick={onCancel}>
        아니요
      </Button>
    </Stack>
  </BottomSheet>
);

/** 본문 15/22 Medium, 아래 버튼과 16px 띄운다. 토큰에 같은 줄 높이가 없어 시안 값을 쓴다. */
const Body = styled.p`
  margin-bottom: ${spacing[16]};
  font-size: 15px;
  font-weight: 500;
  line-height: 22px;
  color: ${colors.textPrimary};
`;
