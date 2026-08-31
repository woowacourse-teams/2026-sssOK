import { RoomSessionBadge } from "@/entities/session";
import { RoomShareButton } from "@/features/share-room";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";
import { RoomMenuButton } from "./RoomMenuButton";
import { RoomRemainingTime } from "./RoomRemainingTime";
import { Header, RoomTitle } from "./RoomSummary.styles";

interface RoomSummaryProps {
  roomCode: string;
  hostId: number;
  expiresAt: string;
  roomName: string;
  isHost?: boolean;
  hasSelectedFolder?: boolean;
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

        <Row align="center" justify="space-between">
          <RoomTitle>{roomName}</RoomTitle>
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
