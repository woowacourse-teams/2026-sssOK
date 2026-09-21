import { useEffect, useState } from "react";
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

export const PendingMediaCard = ({ slot, isSelected, onToggle, onOpen }: PendingMediaCardProps) => {
  const [previewUrl] = useState(() =>
    slot.type === "local" ? URL.createObjectURL(slot.file) : slot.media.thumbnailUrl,
  );
  const [imageUrl, setImageUrl] = useState(previewUrl);

  useEffect(() => () => URL.revokeObjectURL(previewUrl), [previewUrl]);

  useEffect(() => {
    if (slot.type !== "server") return;

    const image = new Image();
    image.src = slot.media.thumbnailUrl;
    image.onload = () => {
      setImageUrl(slot.media.thumbnailUrl);
      URL.revokeObjectURL(previewUrl);
    };

    return () => {
      image.onload = null;
    };
  }, [previewUrl, slot]);

  const isVideo = slot.type === "server" && slot.media.type === "VIDEO";
  const fileName = slot.type === "local" ? slot.file.name : slot.media.fileName;

  return (
    <Card $selected={isSelected}>
      <CardButton
        type="button"
        onClick={onOpen ?? onToggle}
        aria-label={`${fileName} ${onOpen ? "크게 보기" : "선택하기"}`}
      >
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

        <UploaderBadge $mine>나</UploaderBadge>
      </CardButton>

      <SelectionMark
        type="button"
        $selected={isSelected}
        onClick={onToggle}
        aria-label={`${fileName} 선택`}
        aria-pressed={isSelected}
      >
        {isSelected && <HiCheck />}
      </SelectionMark>
    </Card>
  );
};
