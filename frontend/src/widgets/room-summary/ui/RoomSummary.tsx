import { useEffect, useRef } from "react";
import { HiArrowLeft, HiMagnifyingGlass } from "react-icons/hi2";

import { RoomSessionBadge } from "@/entities/session";
import { MediaSearchField, type SearchType } from "@/features/search-media";
import { RoomShareButton } from "@/features/share-room";
import { IconButton } from "@/shared/ui/icon-button";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";
import { RoomMenuButton } from "./RoomMenuButton";
import { Header, RoomTitle, SearchOverlay, TitleArea } from "./RoomSummary.styles";

interface RoomSummaryProps {
  roomCode: string;
  hostId: number;
  roomName: string;
  session?: { userId: number; nickname: string; accessToken: string };
  exitLabel?: string;
  onExit?: () => void;
  isHost?: boolean;
  hasSelectedFolder?: boolean;
  isSearchOpen?: boolean;
  searchType?: SearchType;
  onSearchTypeChange?: (searchType: SearchType) => void;
  onOpenSearch?: () => void;
  onSearch?: (query: string) => void;
  onCloseSearch?: () => void;
  onOpenSettings?: () => void;
  onDeleteRoom?: () => void;
  onAddFolder?: () => void;
  onEditFolder?: () => void;
  onDeleteFolder?: () => void;
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
  isSearchOpen = false,
  searchType = "keyword",
  onSearchTypeChange,
  onOpenSearch,
  onSearch,
  onCloseSearch,
  onOpenSettings,
  onDeleteRoom,
  onAddFolder,
  onEditFolder,
  onDeleteFolder,
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
            {onOpenSearch && (
              <IconButton
                size="sm"
                aria-label="사진 검색 열기"
                inert={isSearchOpen}
                onClick={onOpenSearch}
              >
                <HiMagnifyingGlass />
              </IconButton>
            )}
            {isSearchOpen && onSearchTypeChange && onSearch && onCloseSearch && (
              <SearchOverlay>
                <MediaSearchField
                  searchType={searchType}
                  onSearchTypeChange={onSearchTypeChange}
                  onSearch={onSearch}
                  onClose={onCloseSearch}
                />
              </SearchOverlay>
            )}
          </TitleArea>
          <Row align="center" gap={4}>
            <RoomShareButton roomCode={roomCode} accessToken={session?.accessToken} />
            <RoomMenuButton
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
      </Stack>
    </Header>
  );
};
