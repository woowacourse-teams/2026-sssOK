import { HiMagnifyingGlass } from "react-icons/hi2";

import { RoomSessionBadge } from "@/entities/session";
import { MediaSearchField } from "@/features/search-media";
import { RoomShareButton } from "@/features/share-room";
import { IconButton } from "@/shared/ui/icon-button";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";
import { RoomMenuButton } from "./RoomMenuButton";
import { RoomRemainingTime } from "./RoomRemainingTime";
import { Header, RoomTitle, SearchOverlay, TitleArea } from "./RoomSummary.styles";

interface RoomSummaryProps {
  roomCode: string;
  hostId: number;
  expiresAt: string;
  roomName: string;
  isHost?: boolean;
  hasSelectedFolder?: boolean;
  isSearchOpen?: boolean;
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
  expiresAt,
  roomName,
  isHost = false,
  hasSelectedFolder = false,
  isSearchOpen = false,
  onOpenSearch,
  onSearch,
  onCloseSearch,
  onOpenSettings,
  onDeleteRoom,
  onAddFolder,
  onEditFolder,
  onDeleteFolder,
}: RoomSummaryProps) => {
  return (
    <Header>
      <Stack gap={8}>
        <Row align="center" justify="space-between">
          <RoomSessionBadge roomCode={roomCode} hostId={hostId} />
          <RoomRemainingTime expiresAt={expiresAt} />
        </Row>

        <Row align="center" justify="space-between" gap={4}>
          <TitleArea>
            <RoomTitle>{roomName}</RoomTitle>
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
            {isSearchOpen && onSearch && onCloseSearch && (
              <SearchOverlay>
                <MediaSearchField onSearch={onSearch} onClose={onCloseSearch} />
              </SearchOverlay>
            )}
          </TitleArea>
          <Row align="center" gap={4}>
            <RoomShareButton roomCode={roomCode} />
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
