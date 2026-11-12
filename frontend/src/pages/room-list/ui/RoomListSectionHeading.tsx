import { useState } from "react";
import { HiLink, HiPlus } from "react-icons/hi2";
import { useNavigate } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { Button } from "@/shared/ui/button";

import { JoinByLinkModal } from "./JoinByLinkModal";
import {
  Header,
  HeaderActions,
  HeaderIntro,
  SectionDescription,
  Title,
} from "./RoomListPage.styles";

export const RoomListSectionHeading = () => {
  const navigate = useNavigate();
  const [isJoinModalOpen, setIsJoinModalOpen] = useState(false);

  return (
    <Header>
      <HeaderIntro>
        <Title>내 방</Title>
        <SectionDescription>내 방을 확인하고 관리할 수 있어요.</SectionDescription>
      </HeaderIntro>
      <HeaderActions>
        <Button variant="default" size="lg" onClick={() => setIsJoinModalOpen(true)}>
          <HiLink aria-hidden="true" />
          링크·코드로 참여
        </Button>
        <Button variant="primary" size="lg" onClick={() => navigate(ROUTES.createRoom)}>
          <HiPlus aria-hidden="true" />방 만들기
        </Button>
      </HeaderActions>
      {isJoinModalOpen && <JoinByLinkModal onClose={() => setIsJoinModalOpen(false)} />}
    </Header>
  );
};
