import { memo, useEffect, useRef, useState } from "react";
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
  /** 카드마다 새 함수를 만들지 않도록 id 를 받는다. 갤러리가 같은 함수를 계속 넘겨야 memo 가 통한다. */
  onToggle: (mediaId: number) => void;
  onOpen?: (mediaId: number) => void;
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

const GalleryMediaCardView = ({
  item,
  roomId,
  userId,
  token,
  isSelected,
  onToggle,
  onOpen,
}: GalleryMediaCardProps) => {
  const [previewUrl] = useState(() => createImageUrl(item));
  const localPreviewUrl = useRef(item.type === "local" ? previewUrl : null);
  const thumbnailUrl = item.type === "server" ? item.media.thumbnailUrl : null;
  const [isThumbnailShown, setIsThumbnailShown] = useState(thumbnailUrl !== null);
  // 썸네일을 한 번 보여 준 카드는 서명 URL이 갱신돼도 src만 바꿔 loading="lazy"에 맡긴다.
  const imageUrl = isThumbnailShown && thumbnailUrl !== null ? thumbnailUrl : previewUrl;

  useEffect(() => {
    if (isThumbnailShown || thumbnailUrl === null) return;

    // 업로드 미리보기에서 바뀔 때만 미리 받아 빈 화면 깜빡임 없이 교체한다.
    const image = new Image();
    image.src = thumbnailUrl;
    image.onload = () => {
      setIsThumbnailShown(true);
      if (localPreviewUrl.current) {
        URL.revokeObjectURL(localPreviewUrl.current);
        localPreviewUrl.current = null;
      }
    };

    return () => {
      image.onload = null;
    };
  }, [isThumbnailShown, thumbnailUrl]);

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
        onClick={() => (onOpen ?? onToggle)(item.mediaId)}
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
        onClick={() => onToggle(item.mediaId)}
        aria-label={`${fileName} 선택, ${isSelected ? "선택됨" : "선택 안 됨"}`}
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

/** 사진 한 장을 고를 때 나머지 카드는 props 가 그대로라 다시 그리지 않는다. */
export const GalleryMediaCard = memo(GalleryMediaCardView);

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
