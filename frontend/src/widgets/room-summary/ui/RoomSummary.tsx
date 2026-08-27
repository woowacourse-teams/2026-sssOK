import { HiEllipsisHorizontal, HiLink } from "react-icons/hi2";

import { RoomSessionBadge } from "@/entities/session";
import { IconButton } from "@/shared/ui/icon-button";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";
import { RoomRemainingTime } from "./RoomRemainingTime";
import { Header, RoomTitle } from "./RoomSummary.styles";

interface RoomSummaryProps {
  roomCode: string;
  hostId: number;
  expiresAt: string;
  roomName: string;
}

export const RoomSummary = ({ roomCode, hostId, expiresAt, roomName }: RoomSummaryProps) => {
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
            <IconButton size="sm" aria-label="방 링크 복사">
              <HiLink />
            </IconButton>
            <IconButton size="sm" aria-label="방 메뉴 열기">
              <HiEllipsisHorizontal />
            </IconButton>
          </Row>
        </Row>
      </Stack>
    </Header>
  );
};
