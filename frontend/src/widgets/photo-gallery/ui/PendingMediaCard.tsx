import { useEffect, useState } from "react";
import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";
import { HiCheck, HiPlay } from "react-icons/hi2";

import type { GalleryItem } from "@/entities/media";
import {
  Card,
  CardButton,
  Duration,
  PlayMark,
  SelectionMark,
  Thumbnail,
  UploaderBadge,
} from "@/entities/media/ui/MediaCard.styles";
import { colors, radius } from "@/shared/styles/tokens";

interface PendingMediaCardProps {
  slot: GalleryItem;
  isSelected: boolean;
  onToggle: () => void;
  onOpen?: () => void;
}

const formatDuration = (duration: number) => {
  const minutes = Math.floor(duration / 60);
  const seconds = duration % 60;

  return `${minutes}:${String(seconds).padStart(2, "0")}`;
};

const createInitialPreviewUrl = (slot: GalleryItem) => {
  if (slot.type === "server") return slot.media.thumbnailUrl;
  if (slot.file.type.startsWith("video/")) return "";

  return URL.createObjectURL(slot.file);
};

export const PendingMediaCard = ({ slot, isSelected, onToggle, onOpen }: PendingMediaCardProps) => {
  const isVideo =
    slot.type === "local" ? slot.file.type.startsWith("video/") : slot.media.type === "VIDEO";
  const [previewUrl] = useState(() => createInitialPreviewUrl(slot));
  const [imageUrl, setImageUrl] = useState(previewUrl);

  useEffect(() => {
    if (!previewUrl.startsWith("blob:")) return;

    return () => URL.revokeObjectURL(previewUrl);
  }, [previewUrl]);

  useEffect(() => {
    if (slot.type !== "server") return;

    const image = new Image();
    image.src = slot.media.thumbnailUrl;
    image.onload = () => {
      setImageUrl(slot.media.thumbnailUrl);
      if (previewUrl.startsWith("blob:")) URL.revokeObjectURL(previewUrl);
    };

    return () => {
      image.onload = null;
    };
  }, [previewUrl, slot]);

  const isWaitingForVideoThumbnail = isVideo && !imageUrl;
  const fileName = slot.type === "local" ? slot.file.name : slot.media.fileName;

  return (
    <Card $selected={isSelected}>
      <CardButton
        type="button"
        onClick={onOpen ?? onToggle}
        aria-label={`${fileName} ${onOpen ? "크게 보기" : "선택하기"}`}
      >
        {isWaitingForVideoThumbnail ? (
          <VideoThumbnailPlaceholder aria-label="동영상 썸네일 생성 중">
            <LoadingSpinner />
          </VideoThumbnailPlaceholder>
        ) : (
          <>
            <Thumbnail src={imageUrl} alt={fileName} draggable={false} />
            {isVideo && (
              <>
                <PlayMark>
                  <HiPlay />
                </PlayMark>
                {slot.type === "server" && slot.media.duration !== null && (
                  <Duration>{formatDuration(slot.media.duration)}</Duration>
                )}
              </>
            )}
          </>
        )}

        <UploaderBadge $mine>나</UploaderBadge>
      </CardButton>

      <SelectionMark
        type="button"
        $selected={isSelected}
        onClick={onToggle}
        aria-label={`${fileName} 선택`}
        aria-pressed={isSelected}
      >
        <HiCheck />
      </SelectionMark>
    </Card>
  );
};

const spin = keyframes`
  to {
    transform: rotate(360deg);
  }
`;

const VideoThumbnailPlaceholder = styled.span`
  display: grid;
  width: 100%;
  height: 100%;
  background-color: ${colors.backgroundSubtle};
  place-items: center;
`;

const LoadingSpinner = styled.span`
  width: 28px;
  height: 28px;
  border: 3px solid ${colors.primarySubtle};
  border-top-color: ${colors.primary};
  border-radius: ${radius.full};
  animation: ${spin} 0.8s linear infinite;
`;
