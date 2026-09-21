import { useRef } from "react";

import { MediaCard } from "@/entities/media";
import type { MediaItem } from "@/entities/media";
import { useInfiniteScroll } from "@/shared/hooks";
import {
  GalleryGrid,
  GallerySection,
  LoadingMore,
  Sentinel,
  StateMessage,
} from "./PhotoGallery.styles";

interface PhotoGalleryProps {
  photos: MediaItem[];
  userId: number;
  selectedPhotoIds: number[];
  isPending: boolean;
  isError: boolean;
  onTogglePhoto: (photoId: number) => void;
  onOpenPhoto?: (photo: MediaItem) => void;
  /** 서버에 아직 받지 않은 사진이 남았는지. */
  hasMore?: boolean;
  isLoadingMore?: boolean;
  onLoadMore?: () => void;
}

/**
 * 목록 끝에 두는 감시 대상.
 *
 * 컴포넌트로 따로 뺀다. 갤러리는 불러오는 동안 목록 대신 안내 문구를 그려서, 한 컴포넌트에서
 * 감시하면 ref 가 비어 있을 때 effect 가 돌고 나서 다시 돌 계기가 없다. 목록과 함께
 * 마운트되면 ref 가 붙은 뒤에 effect 가 돈다.
 */
const LoadMoreSentinel = ({ enabled, onReach }: { enabled: boolean; onReach: () => void }) => {
  const sentinel = useRef<HTMLDivElement>(null);

  useInfiniteScroll({ target: sentinel, enabled, onReach });

  return <Sentinel ref={sentinel} />;
};

export const PhotoGallery = ({
  photos,
  userId,
  selectedPhotoIds,
  isPending,
  isError,
  onTogglePhoto,
  onOpenPhoto,
  hasMore = false,
  isLoadingMore = false,
  onLoadMore,
}: PhotoGalleryProps) => {
  if (isPending) return <StateMessage>사진을 불러오는 중이에요.</StateMessage>;
  if (isError) return <StateMessage>사진을 불러오지 못했어요.</StateMessage>;
  if (photos.length === 0) return <StateMessage>조건에 맞는 사진이 없어요.</StateMessage>;

  return (
    <GallerySection>
      <GalleryGrid>
        {photos.map((photo) => {
          const isSelected = selectedPhotoIds.includes(photo.mediaId);

          return (
            <MediaCard
              key={photo.mediaId}
              media={photo}
              isMine={photo.uploaderId === userId}
              isSelected={isSelected}
              onToggle={() => onTogglePhoto(photo.mediaId)}
              onOpen={photo.type === "IMAGE" && onOpenPhoto ? () => onOpenPhoto(photo) : undefined}
            />
          );
        })}
      </GalleryGrid>
      {onLoadMore && (
        // 불러오는 중에는 끈다 — 켜 두면 같은 커서로 여러 번 부른다.
        <LoadMoreSentinel enabled={hasMore && !isLoadingMore} onReach={onLoadMore} />
      )}
      {isLoadingMore && <LoadingMore>사진을 더 불러오는 중이에요.</LoadingMore>}
    </GallerySection>
  );
};
