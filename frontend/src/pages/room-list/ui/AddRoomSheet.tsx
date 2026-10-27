import styled from "@emotion/styled";
import type { ReactNode } from "react";
import { HiLink, HiOutlinePhoto } from "react-icons/hi2";

import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { colors, spacing, typography } from "@/shared/styles/tokens";

interface AddRoomSheetProps {
  onCreateRoom: () => void;
  onJoinByLink: () => void;
  onClose: () => void;
}

interface OptionProps {
  icon: ReactNode;
  title: string;
  description: string;
  onClick: () => void;
}

const Option = ({ icon, title, description, onClick }: OptionProps) => (
  <OptionButton type="button" onClick={onClick}>
    <OptionIcon aria-hidden="true">{icon}</OptionIcon>
    <span>
      <OptionTitle>{title}</OptionTitle>
      <OptionDescription>{description}</OptionDescription>
    </span>
  </OptionButton>
);

export const AddRoomSheet = ({ onCreateRoom, onJoinByLink, onClose }: AddRoomSheetProps) => (
  <BottomSheet title="방 추가" onClose={onClose}>
    <Subtitle>새로 만들거나, 받은 링크로 들어가요.</Subtitle>
    <Option
      icon={<HiOutlinePhoto />}
      title="새 방 만들기"
      description="방 이름만 정하면 바로 링크가 나와요"
      onClick={onCreateRoom}
    />
    <Option
      icon={<HiLink />}
      title="초대 링크 붙여넣기"
      description="받은 링크로 방에 들어가요"
      onClick={onJoinByLink}
    />
  </BottomSheet>
);

const Subtitle = styled.p`
  /* 시트 제목 바로 밑에 붙인다. 시트 본문은 제목에서 16px 떨어져 시작한다. */
  margin: -12px 0 ${spacing[12]};
  color: ${colors.textSecondary};
  font-size: 14px;
  line-height: 20px;
`;

const OptionButton = styled.button`
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: ${spacing[16]} ${spacing[4]};
  text-align: left;

  & + & {
    border-top: 1px solid ${colors.backgroundSubtle};
  }
`;

const OptionIcon = styled.span`
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: ${colors.primarySubtle};
  color: ${colors.primary};

  svg {
    width: 22px;
    height: 22px;
  }
`;

const OptionTitle = styled.strong`
  display: block;
  color: ${colors.textStrong};

  ${typography.label2}
`;

const OptionDescription = styled.span`
  color: ${colors.textSecondary};

  ${typography.caption3}
  font-size: 13px;
`;
