import { useEffect, useRef, useState } from "react";
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

interface GalleryMediaCardProps {
  item: GalleryItem;
  userId: number;
  isSelected: boolean;
  onToggle: () => void;
  onOpen?: () => void;
}

const formatDuration = (duration: number) => {
  const minutes = Math.floor(duration / 60);
  const seconds = duration % 60;

  return `${minutes}:${String(seconds).padStart(2, "0")}`;
};

const createImageUrl = (item: GalleryItem) => {
  if (item.type === "server") {
    return (
      item.media.thumbnailUrl ?? (item.media.type === "IMAGE" ? item.media.displayUrl : null) ?? ""
    );
  }
  if (item.file.type.startsWith("video/")) return "";

  return URL.createObjectURL(item.file);
};

export const GalleryMediaCard = ({
  item,
  userId,
  isSelected,
  onToggle,
  onOpen,
}: GalleryMediaCardProps) => {
  const [initialImageUrl] = useState(() => createImageUrl(item));
  const localPreviewUrl = useRef(item.type === "local" ? initialImageUrl : null);
  const [imageUrl, setImageUrl] = useState(initialImageUrl);

  useEffect(() => {
    if (item.type !== "server") return;

    const thumbnailUrl = item.media.thumbnailUrl;
    if (thumbnailUrl === null || imageUrl === thumbnailUrl) return;

    const image = new Image();
    image.src = thumbnailUrl;
    image.onload = () => {
      setImageUrl(thumbnailUrl);
      if (localPreviewUrl.current) {
        URL.revokeObjectURL(localPreviewUrl.current);
        localPreviewUrl.current = null;
      }
    };

    return () => {
      image.onload = null;
    };
  }, [imageUrl, item]);

  useEffect(
    () => () => {
      if (localPreviewUrl.current) URL.revokeObjectURL(localPreviewUrl.current);
    },
    [],
  );

  const fileName = item.type === "local" ? item.file.name : item.media.fileName;
  const isMine = item.type === "local" || item.media.uploaderId === userId;
  const isVideo =
    item.type === "local" ? item.file.type.startsWith("video/") : item.media.type === "VIDEO";
  const isWaitingForPreview = !imageUrl;
  const uploaderName = item.type === "local" ? "나" : item.media.uploaderName;

  return (
    <Card $selected={isSelected}>
      <CardButton
        type="button"
        onClick={onOpen ?? onToggle}
        aria-label={`${fileName} ${onOpen ? "크게 보기" : "선택하기"}`}
      >
        {isWaitingForPreview ? (
          <MediaPreviewPlaceholder aria-label="미디어 미리보기 생성 중">
            <LoadingSpinner />
          </MediaPreviewPlaceholder>
        ) : (
          <>
            <Thumbnail
              src={imageUrl}
              alt={fileName}
              loading={item.type === "local" ? undefined : "lazy"}
              draggable={false}
            />
            {isVideo && (
              <>
                <PlayMark>
                  <HiPlay />
                </PlayMark>
                {item.type !== "local" && item.media.duration !== null && (
                  <Duration>{formatDuration(item.media.duration)}</Duration>
                )}
              </>
            )}
          </>
        )}

        <UploaderBadge $mine={isMine}>{isMine ? "나" : uploaderName}</UploaderBadge>
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

const MediaPreviewPlaceholder = styled.span`
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
