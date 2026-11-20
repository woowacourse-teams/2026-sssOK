import { type ReactNode, useEffect, useRef } from "react";
import { HiArrowLeft } from "react-icons/hi2";

import { RoomSessionBadge } from "@/entities/session";
import { RoomShareButton } from "@/features/share-room";
import { IconButton } from "@/shared/ui/icon-button";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";
import sparkleIcon from "../assets/sparkle.svg";
import { RoomMenuButton } from "./RoomMenuButton";
import { Header, RoomTitle, TitleArea } from "./RoomSummary.styles";

interface RoomSummaryProps {
  notificationMenu?: (close: () => void) => ReactNode;
  roomCode: string;
  hostId: number;
  roomName: string;
  session?: { userId: number; nickname: string; accessToken: string };
  exitLabel?: string;
  onExit?: () => void;
  isHost?: boolean;
  hasSelectedFolder?: boolean;
  searchField?: ReactNode;
  onOpenSettings?: () => void;
  onDeleteRoom?: () => void;
  onAddFolder?: () => void;
  onEditFolder?: () => void;
  onDeleteFolder?: () => void;
  /** 제목 옆 ✨ 버튼. 사람별 정리 확인을 연다. 없으면 버튼을 그리지 않는다. */
  onOrganize?: () => void;
}

export const RoomSummary = ({
  roomCode,
  hostId,
  roomName,
  session,
  exitLabel,
  onExit,
  isHost = false,
  hasSelectedFolder = false,
  searchField,
  onOpenSettings,
  onDeleteRoom,
  onAddFolder,
  onEditFolder,
  onDeleteFolder,
  onOrganize,
  notificationMenu,
}: RoomSummaryProps) => {
  const titleRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    titleRef.current?.focus({ preventScroll: true });
  }, [roomCode]);

  return (
    <Header>
      <Stack gap={8}>
        <Row align="center" justify="space-between">
          {onExit && (
            <IconButton aria-label={exitLabel ?? "방에서 나가기"} onClick={onExit}>
              <HiArrowLeft aria-hidden="true" />
            </IconButton>
          )}
          <RoomSessionBadge roomCode={roomCode} hostId={hostId} session={session} />
        </Row>

        <Row align="center" justify="space-between" gap={4}>
          <TitleArea>
            <RoomTitle ref={titleRef} tabIndex={-1} aria-label={`${roomName} 갤러리에 입장했어요.`}>
              {roomName}
            </RoomTitle>
          </TitleArea>
          <Row align="center" gap={4}>
            {/* 피그마 796:2622 · 공유 버튼 앞의 주황 ✨ 아이콘 버튼 */}
            {onOrganize && (
              <IconButton size="sm" aria-label="사진 사람별로 정리하기" onClick={onOrganize}>
                <img src={sparkleIcon} alt="" width={22} height={22} />
              </IconButton>
            )}
            <RoomShareButton roomCode={roomCode} />
            <RoomMenuButton
              notificationMenu={notificationMenu}
              isHost={isHost}
              hasSelectedFolder={hasSelectedFolder}
              onOpenSettings={onOpenSettings}
              onDeleteRoom={onDeleteRoom}
              onAddFolder={onAddFolder}
              onEditFolder={onEditFolder}
              onDeleteFolder={onDeleteFolder}
            />
          </Row>
        </Row>
        {searchField}
      </Stack>
    </Header>
  );
};
