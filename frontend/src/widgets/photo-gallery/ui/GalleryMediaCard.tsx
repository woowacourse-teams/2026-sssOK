import { useEffect, useRef, useState } from "react";
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

const createImageUrl = (item: GalleryItem) =>
  item.type === "local" ? URL.createObjectURL(item.file) : item.media.thumbnailUrl;

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
    if (item.type !== "server" || imageUrl === item.media.thumbnailUrl) return;

    const image = new Image();
    image.src = item.media.thumbnailUrl;
    image.onload = () => {
      setImageUrl(item.media.thumbnailUrl);
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
  const isVideo = item.type === "server" && item.media.type === "VIDEO";
  const uploaderName = item.type === "server" ? item.media.uploaderName : "나";

  return (
    <Card $selected={isSelected}>
      <CardButton
        type="button"
        onClick={onOpen ?? onToggle}
        aria-label={`${fileName} ${onOpen ? "크게 보기" : "선택하기"}`}
      >
        <Thumbnail
          src={imageUrl}
          alt={fileName}
          loading={item.type === "server" ? "lazy" : undefined}
          draggable={false}
        />

        {isVideo && (
          <>
            <PlayMark>
              <HiPlay />
            </PlayMark>
            {item.type === "server" && item.media.duration !== null && (
              <Duration>{formatDuration(item.media.duration)}</Duration>
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
