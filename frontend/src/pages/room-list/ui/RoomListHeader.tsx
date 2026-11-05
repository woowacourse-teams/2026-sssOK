import { HiOutlineCog6Tooth } from "react-icons/hi2";
import { useNavigate } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { IconButton } from "@/shared/ui/icon-button";
import { AccountSlots, Logo, Nav, NavInner } from "./RoomListPage.styles";

export const RoomListHeader = () => (
  <Nav>
    <NavInner>
      <Logo>
        <img src="/icons/icon-192.png" alt="" />쏙
      </Logo>
      <AccountActions />
    </NavInner>
  </Nav>
);

const AccountActions = () => {
  const navigate = useNavigate();
  return (
    <AccountSlots>
      <IconButton aria-label="설정" onClick={() => navigate(ROUTES.accountSettings)}>
        <HiOutlineCog6Tooth aria-hidden="true" />
      </IconButton>
    </AccountSlots>
  );
};
