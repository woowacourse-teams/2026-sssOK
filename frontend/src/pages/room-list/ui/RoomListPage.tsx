import { useEffect, useState } from "react";
import {
  HiArrowRightOnRectangle,
  HiLink,
  HiOutlineBell,
  HiOutlineCog6Tooth,
  HiPlus,
} from "react-icons/hi2";
import { useNavigate } from "react-router-dom";

import { useMemberSessionQuery } from "@/entities/member-session";
import { useMyRoomsQuery, type MyRoom } from "@/entities/room";
import { LeaveRoomModal } from "@/features/leave-room";
import mascotHello from "@/shared/assets/mascot-hello.svg";
import { ROUTES } from "@/shared/config";
import { BottomSheet } from "@/shared/ui/bottom-sheet";
import { Button } from "@/shared/ui/button";
import { DropdownMenu, DropdownMenuItem } from "@/shared/ui/dropdown-menu";
import { IconButton } from "@/shared/ui/icon-button";
import { PageSpinner } from "@/shared/ui/spinner";
import { Toast, type ToastProps } from "@/shared/ui/toast";
import { useIsDesktop } from "../model/useIsDesktop";
import { AddRoomSheet } from "./AddRoomSheet";
import { JoinByLinkModal } from "./JoinByLinkModal";
import { RoomCard } from "./RoomCard";
import {
  AccountSlots,
  BellSlot,
  Body,
  Chip,
  Chips,
  Description,
  Empty,
  EmptyActions,
  EmptyBody,
  ErrorBox,
  Fab,
  FilterEmpty,
  Header,
  HeaderActions,
  List,
  Logo,
  MobileOnly,
  Nav,
  NavInner,
  Page,
  PageButton,
  SheetMenuItem,
  Title,
} from "./RoomListPage.styles";

type RoomFilter = "all" | "host" | "member";

const FILTERS: { value: RoomFilter; label: string; emptyMessage: string }[] = [
  { value: "all", label: "전체", emptyMessage: "" },
  { value: "host", label: "내가 만든 방", emptyMessage: "내가 만든 방이 없어요." },
  { value: "member", label: "참여한 방", emptyMessage: "참여한 방이 없어요." },
];

const AccountActions = () => {
  const navigate = useNavigate();
  return (
    <AccountSlots>
      <BellSlot aria-hidden="true">
        <HiOutlineBell />
      </BellSlot>
      <IconButton aria-label="설정" onClick={() => navigate(ROUTES.accountSettings)}>
        <HiOutlineCog6Tooth aria-hidden="true" />
      </IconButton>
    </AccountSlots>
  );
};

const matchesFilter = (room: MyRoom, filter: RoomFilter) =>
  filter === "all" || (filter === "host" ? room.isHost : !room.isHost);

export const RoomListPage = () => {
  const navigate = useNavigate();
  const isDesktop = useIsDesktop();
  const { data: session } = useMemberSessionQuery();
  const accessToken = session?.accessToken;
  const roomsQuery = useMyRoomsQuery({ token: accessToken });

  const [filter, setFilter] = useState<RoomFilter>("all");
  const [menuRoom, setMenuRoom] = useState<MyRoom | null>(null);
  const [leavingRoom, setLeavingRoom] = useState<MyRoom | null>(null);
  const [isAddSheetOpen, setIsAddSheetOpen] = useState(false);
  const [isJoinModalOpen, setIsJoinModalOpen] = useState(false);
  const [notice, setNotice] = useState<Pick<ToastProps, "message" | "tone"> | null>(null);

  useEffect(() => {
    if (!notice) return;

    const timer = window.setTimeout(() => setNotice(null), 4000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const goToCreateRoom = () => navigate(ROUTES.createRoom);
  const openJoinModal = () => {
    setIsAddSheetOpen(false);
    setIsJoinModalOpen(true);
  };

  const copyInviteLink = async (room: MyRoom) => {
    setMenuRoom(null);
    try {
      const url = new URL(ROUTES.roomInvite(room.code), window.location.origin).href;
      await navigator.clipboard.writeText(url);
      setNotice({ message: "초대 링크를 복사했어요.", tone: "success" });
    } catch {
      setNotice({
        message: "링크를 복사하지 못했어요. 잠시 후 다시 시도해 주세요.",
        tone: "error",
      });
    }
  };

  const menuItemsOf = (room: MyRoom) => (
    <>
      <DropdownMenuItem icon={<HiLink />} onClick={() => void copyInviteLink(room)}>
        초대 링크 복사
      </DropdownMenuItem>
      {room.isHost ? (
        <DropdownMenuItem
          icon={<HiOutlineCog6Tooth />}
          onClick={() => navigate(ROUTES.roomSettings(room.code))}
        >
          방 설정
        </DropdownMenuItem>
      ) : (
        <DropdownMenuItem
          icon={<HiArrowRightOnRectangle />}
          tone="danger"
          onClick={() => {
            setMenuRoom(null);
            setLeavingRoom(room);
          }}
        >
          방 나가기
        </DropdownMenuItem>
      )}
    </>
  );

  const sheetItemsOf = (room: MyRoom) => (
    <>
      <SheetMenuItem type="button" onClick={() => void copyInviteLink(room)}>
        <HiLink aria-hidden="true" />
        초대 링크 복사
      </SheetMenuItem>
      {room.isHost ? (
        <SheetMenuItem type="button" onClick={() => navigate(ROUTES.roomSettings(room.code))}>
          <HiOutlineCog6Tooth aria-hidden="true" />방 설정
        </SheetMenuItem>
      ) : (
        <SheetMenuItem
          type="button"
          $tone="danger"
          onClick={() => {
            setMenuRoom(null);
            setLeavingRoom(room);
          }}
        >
          <HiArrowRightOnRectangle aria-hidden="true" />방 나가기
        </SheetMenuItem>
      )}
    </>
  );

  const renderContent = () => {
    if (roomsQuery.isPending) return <PageSpinner label="방 목록을 불러오는 중이에요" />;

    if (roomsQuery.isError) {
      return (
        <ErrorBox>
          <p>방 목록을 불러오지 못했어요.</p>
          <Button variant="default" onClick={() => void roomsQuery.refetch()}>
            다시 불러오기
          </Button>
        </ErrorBox>
      );
    }

    const rooms = roomsQuery.data;

    if (rooms.length === 0) {
      return (
        <Empty>
          <EmptyBody>
            <img src={mascotHello} alt="" />
            <h2>아직 들어간 방이 없어요</h2>
            <p>
              방을 만들거나, 받은 초대 링크로
              <br /> 들어가 보세요.
            </p>
          </EmptyBody>
          <EmptyActions>
            <PageButton type="button" $variant="primary" $size="lg" onClick={goToCreateRoom}>
              방 만들기
            </PageButton>
            <PageButton type="button" $variant="line" $size="lg" onClick={openJoinModal}>
              초대 링크로 들어가기
            </PageButton>
          </EmptyActions>
        </Empty>
      );
    }

    const visibleRooms = rooms.filter((room) => matchesFilter(room, filter));
    const emptyMessage = FILTERS.find(({ value }) => value === filter)?.emptyMessage;

    return (
      <>
        <Chips role="group" aria-label="방 종류">
          {FILTERS.map(({ value, label }) => (
            <Chip
              key={value}
              type="button"
              $selected={filter === value}
              aria-pressed={filter === value}
              onClick={() => setFilter(value)}
            >
              {label} <b>{rooms.filter((room) => matchesFilter(room, value)).length}</b>
            </Chip>
          ))}
        </Chips>

        {visibleRooms.length === 0 ? (
          <FilterEmpty>{emptyMessage}</FilterEmpty>
        ) : (
          <List>
            {visibleRooms.map((room) => (
              <RoomCard
                key={room.roomId}
                room={room}
                onOpenMenu={() => setMenuRoom(room)}
                menu={
                  isDesktop &&
                  menuRoom?.roomId === room.roomId && (
                    <DropdownMenu onClose={() => setMenuRoom(null)}>
                      {menuItemsOf(room)}
                    </DropdownMenu>
                  )
                }
              />
            ))}
          </List>
        )}

        <Fab type="button" onClick={() => setIsAddSheetOpen(true)}>
          <HiPlus aria-hidden="true" />방 추가
        </Fab>
      </>
    );
  };

  const hasRooms = roomsQuery.isSuccess && roomsQuery.data.length > 0;

  return (
    <Page>
      <Nav>
        <NavInner>
          <Logo>
            <img src="/icons/icon-192.png" alt="" />쏙
          </Logo>
          <AccountActions />
        </NavInner>
      </Nav>

      <Body>
        <Header>
          <div>
            <Title>내 방</Title>
            {hasRooms && (
              <Description>{roomsQuery.data.length}개의 방에 참여하고 있어요</Description>
            )}
          </div>
          {hasRooms && (
            <HeaderActions>
              <PageButton type="button" $variant="line" $size="md" onClick={openJoinModal}>
                <HiLink aria-hidden="true" />
                초대 링크로 들어가기
              </PageButton>
              <PageButton type="button" $variant="primary" $size="md" onClick={goToCreateRoom}>
                <HiPlus aria-hidden="true" />방 만들기
              </PageButton>
            </HeaderActions>
          )}
          <MobileOnly>
            <AccountActions />
          </MobileOnly>
        </Header>

        {renderContent()}
      </Body>

      {!isDesktop && menuRoom && (
        <BottomSheet title={menuRoom.name} onClose={() => setMenuRoom(null)}>
          {sheetItemsOf(menuRoom)}
        </BottomSheet>
      )}

      {isAddSheetOpen && (
        <AddRoomSheet
          onCreateRoom={goToCreateRoom}
          onJoinByLink={openJoinModal}
          onClose={() => setIsAddSheetOpen(false)}
        />
      )}

      {isJoinModalOpen && <JoinByLinkModal onClose={() => setIsJoinModalOpen(false)} />}

      {leavingRoom && (
        <LeaveRoomModal
          roomId={leavingRoom.roomId}
          roomName={leavingRoom.name}
          accessToken={accessToken}
          onClose={() => setLeavingRoom(null)}
          onSuccess={() => {
            setLeavingRoom(null);
            setNotice({ message: `‘${leavingRoom.name}’에서 나갔어요.`, tone: "success" });
          }}
        />
      )}

      {notice && (
        <Toast
          message={notice.message}
          tone={notice.tone}
          showClose={false}
          onClose={() => setNotice(null)}
        />
      )}
    </Page>
  );
};
