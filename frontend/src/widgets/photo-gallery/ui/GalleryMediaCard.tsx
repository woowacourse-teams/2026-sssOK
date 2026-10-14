import { useEffect, useRef, useState } from "react";
import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";
import { HiCheck, HiHeart, HiOutlineHeart, HiPlay } from "react-icons/hi2";

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
import { useMediaLike } from "@/features/toggle-media-like";
import { track } from "@/shared/lib";
import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

interface GalleryMediaCardProps {
  item: GalleryItem;
  roomId: number;
  userId: number;
  token: string;
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

const MediaLikeButton = ({
  item,
  roomId,
  userId,
  token,
}: {
  item: Extract<GalleryItem, { type: "server" }>;
  roomId: number;
  userId: number;
  token: string;
}) => {
  const likeMutation = useMediaLike({
    roomId,
    mediaId: item.mediaId,
    userId,
    token,
  });
  const likeCount = item.media.likeCount ?? 0;
  const likedByMe = item.media.likedByMe ?? false;

  return (
    <LikeButton
      type="button"
      $liked={likedByMe}
      aria-label={`${item.media.fileName} 좋아요 ${likedByMe ? "취소" : "추가"}`}
      aria-pressed={likedByMe}
      aria-busy={likeMutation.isPending}
      disabled={likeMutation.isPending}
      onClick={() =>
        likeMutation.mutate(!likedByMe, {
          onSuccess: (response) =>
            track("Photo Like Changed", {
              action: response.liked ? "like" : "unlike",
              source: "gallery",
            }),
        })
      }
    >
      {likedByMe ? <HiHeart /> : <HiOutlineHeart />}
      <span>{likeCount}</span>
    </LikeButton>
  );
};

export const GalleryMediaCard = ({
  item,
  roomId,
  userId,
  token,
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
                  <LikeAwareDuration>{formatDuration(item.media.duration)}</LikeAwareDuration>
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

      {item.type === "server" && (
        <MediaLikeButton item={item} roomId={roomId} userId={userId} token={token} />
      )}
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

const LikeButton = styled.button<{ $liked: boolean }>`
  position: absolute;
  right: ${spacing[8]};
  bottom: ${spacing[8]};
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 42px;
  height: 28px;
  padding: 4px 8px;
  border-radius: ${radius.full};
  background-color: ${colors.overlay};
  color: ${({ $liked }) => ($liked ? colors.danger : colors.textInverse)};
  font-variant-numeric: tabular-nums;

  ${typography.caption4}

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 2px;
  }

  &:disabled {
    cursor: wait;
  }

  svg {
    flex: none;
    width: 17px;
    height: 17px;
  }
`;

/** 영상 길이와 좋아요가 카드 오른쪽 아래에서 겹치지 않게 한 줄 위에 둔다. */
const LikeAwareDuration = styled(Duration)`
  bottom: 42px;
`;
