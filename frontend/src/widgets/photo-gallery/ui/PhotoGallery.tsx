import { useCallback, useLayoutEffect, useMemo, useRef, type ReactNode } from "react";

import type { GalleryItem } from "@/entities/media";
import { GalleryMediaCard } from "./GalleryMediaCard";
import { GalleryGrid, GallerySection, StateMessage } from "./PhotoGallery.styles";
import { PhotoGallerySkeleton } from "./PhotoGallerySkeleton";

interface PhotoGalleryProps {
  items: GalleryItem[];
  roomId: number;
  userId: number;
  token: string;
  selectedPhotoIds: number[];
  isPending: boolean;
  isError: boolean;
  expectedPhotoCount?: number;
  emptyMessage?: ReactNode;
  errorMessage?: string;
  onTogglePhoto: (photoId: number) => void;
  onOpenPhoto?: (mediaId: number) => void;
}

export const PhotoGallery = ({
  items,
  roomId,
  userId,
  token,
  selectedPhotoIds,
  isPending,
  isError,
  expectedPhotoCount,
  emptyMessage = "조건에 맞는 사진이 없어요.",
  errorMessage = "사진을 불러오지 못했어요.",
  onTogglePhoto,
  onOpenPhoto,
}: PhotoGalleryProps) => {
  const selectedIdSet = useMemo(() => new Set(selectedPhotoIds), [selectedPhotoIds]);

  /*
   * 부모가 렌더마다 새 콜백을 넘겨도 카드에는 늘 같은 함수를 준다.
   * 콜백이 바뀔 때마다 카드 전체가 다시 그려지면 한 장을 고르는 데도 방의 사진 수만큼 비용이 든다.
   */
  const handlers = useRef({ onTogglePhoto, onOpenPhoto });
  useLayoutEffect(() => {
    handlers.current = { onTogglePhoto, onOpenPhoto };
  });
  const togglePhoto = useCallback((mediaId: number) => handlers.current.onTogglePhoto(mediaId), []);
  const openPhoto = useCallback((mediaId: number) => handlers.current.onOpenPhoto?.(mediaId), []);
  const canOpen = onOpenPhoto !== undefined;

  if (isPending && items.length === 0) return <PhotoGallerySkeleton count={expectedPhotoCount} />;
  if (isError && items.length === 0) return <StateMessage>{errorMessage}</StateMessage>;
  if (items.length === 0) return <StateMessage>{emptyMessage}</StateMessage>;

  return (
    <GallerySection>
      <GalleryGrid>
        {items.map((item) => (
          <GalleryMediaCard
            key={item.mediaId}
            item={item}
            roomId={roomId}
            userId={userId}
            token={token}
            isSelected={selectedIdSet.has(item.mediaId)}
            onToggle={togglePhoto}
            onOpen={canOpen ? openPhoto : undefined}
          />
        ))}
      </GalleryGrid>
    </GallerySection>
  );
};
