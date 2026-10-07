import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useMutation } from "@tanstack/react-query";
import styled from "@emotion/styled";
import {
  HiArrowDownTray,
  HiArrowLeft,
  HiCheck,
  HiChevronLeft,
  HiChevronRight,
  HiHeart,
  HiOutlineHeart,
  HiOutlineTrash,
} from "react-icons/hi2";

import type { GalleryItem } from "@/entities/media";
import { DeleteMediaModal } from "@/features/delete-media";
import { downloadMedia, prefersShareSheet } from "@/features/download-media";
import { useMediaLike } from "@/features/toggle-media-like";
import { colors } from "@/shared/styles/tokens";

interface MediaViewerModalProps {
  items: GalleryItem[];
  activeMediaId: number;
  roomId: number;
  userId: number;
  hostId: number;
  token: string;
  selectedPhotoIds: number[];
  onChange: (mediaId: number) => void;
  onClose: () => void;
  onToggle: (mediaId: number) => void;
  onDeleted: (mediaId: number) => void;
}

const formatDate = (value: string) => {
  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? ""
    : new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(date);
};

export const MediaViewerModal = ({
  items,
  activeMediaId,
  roomId,
  userId,
  hostId,
  token,
  selectedPhotoIds,
  onChange,
  onClose,
  onToggle,
  onDeleted,
}: MediaViewerModalProps) => {
  // 삭제 확인 모달을 이 dialog 안에 붙여야 해서 ref 대신 state 로 들고 있는다.
  const [dialog, setDialog] = useState<HTMLDialogElement | null>(null);
  const [deletingMediaId, setDeletingMediaId] = useState<number | null>(null);
  const touchStart = useRef<{ x: number; y: number } | null>(null);
  const index = items.findIndex((item) => item.mediaId === activeMediaId);
  const item = items[index];
  const previous = items[index - 1];
  const next = items[index + 1];

  const movePrevious = () => {
    if (previous) onChange(previous.mediaId);
  };
  const moveNext = () => {
    if (next) onChange(next.mediaId);
  };

  useEffect(() => {
    if (!dialog) return;

    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;

    dialog.showModal();
    dialog.focus();
    document.body.style.overflow = "hidden";

    return () => {
      dialog.close();
      document.body.style.overflow = overflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, [dialog]);

  return createPortal(
    <Dialog
      ref={setDialog}
      tabIndex={-1}
      aria-label="사진 크게 보기"
      onCancel={(event) => {
        event.preventDefault();
        if (deletingMediaId === null) onClose();
      }}
      onKeyDown={(event) => {
        if (deletingMediaId !== null) {
          if (event.key === "Escape") event.preventDefault();
          return;
        }
        if (event.key === "ArrowLeft" && previous) {
          event.preventDefault();
          movePrevious();
        }
        if (event.key === "ArrowRight" && next) {
          event.preventDefault();
          moveNext();
        }
      }}
    >
      <Header>
        <BackButton type="button" aria-label="갤러리로 돌아가기" onClick={onClose}>
          <HiArrowLeft size={20} />
        </BackButton>
        <Counter aria-live="polite">
          {index < 0 ? "사진 보기" : `${index + 1} / ${items.length}`}
        </Counter>
        <SelectionButton
          type="button"
          aria-label="사진 선택"
          aria-pressed={selectedPhotoIds.includes(activeMediaId)}
          disabled={!item}
          onClick={() => onToggle(activeMediaId)}
        >
          <HiCheck size={19} />
        </SelectionButton>
      </Header>

      {item ? (
        <>
          <Stage
            onTouchStart={(event) => {
              const touch = event.touches[0];
              touchStart.current =
                event.touches.length === 1 ? { x: touch.clientX, y: touch.clientY } : null;
            }}
            onTouchMove={(event) => {
              if (event.touches.length > 1) touchStart.current = null;
            }}
            onTouchCancel={() => {
              touchStart.current = null;
            }}
            onTouchEnd={(event) => {
              const start = touchStart.current;
              touchStart.current = null;
              if (!start) return;

              const touch = event.changedTouches[0];
              const dx = touch.clientX - start.x;
              const dy = touch.clientY - start.y;
              if (Math.abs(dx) < 60 || Math.abs(dx) <= Math.abs(dy)) return;
              if (dx < 0 && next) moveNext();
              if (dx > 0 && previous) movePrevious();
            }}
          >
            <ViewerMedia key={`${item.mediaId}:${item.type}`} item={item} />
            {!isVideoItem(item) && (
              <>
                <TapZone
                  data-testid="viewer-tap-previous"
                  aria-hidden="true"
                  $side="left"
                  onClick={movePrevious}
                />
                <TapZone
                  data-testid="viewer-tap-next"
                  aria-hidden="true"
                  $side="right"
                  onClick={moveNext}
                />
              </>
            )}
            <PreviousButton
              type="button"
              aria-label="이전 사진"
              disabled={!previous}
              onClick={movePrevious}
            >
              <HiChevronLeft />
            </PreviousButton>
            <NextButton type="button" aria-label="다음 사진" disabled={!next} onClick={moveNext}>
              <HiChevronRight />
            </NextButton>
          </Stage>
          <ViewerFooter
            key={item.mediaId}
            item={item}
            roomId={roomId}
            userId={userId}
            token={token}
            canDelete={
              item.type === "local" || item.media.uploaderId === userId || hostId === userId
            }
            onDelete={() => setDeletingMediaId(item.mediaId)}
          />
        </>
      ) : (
        <StateMessage>사진이 목록에 없어요.</StateMessage>
      )}
      {deletingMediaId !== null && (
        <DeleteMediaModal
          roomId={roomId}
          mediaId={deletingMediaId}
          token={token}
          container={dialog}
          onClose={() => setDeletingMediaId(null)}
          onSuccess={() => onDeleted(deletingMediaId)}
        />
      )}
    </Dialog>,
    document.body,
  );
};

const isVideoItem = (item: GalleryItem) =>
  item.type === "local" ? item.file.type.startsWith("video/") : item.media.type === "VIDEO";

const ViewerMedia = ({ item }: { item: GalleryItem }) =>
  isVideoItem(item) ? <ViewerVideo item={item} /> : <ViewerImage item={item} />;

const ViewerImage = ({ item }: { item: GalleryItem }) => {
  const [localUrl] = useState(() =>
    item.type === "local" ? URL.createObjectURL(item.file) : null,
  );
  const [serverUrl, setServerUrl] = useState(() =>
    item.type === "local" ? "" : (item.media.thumbnailUrl ?? item.media.displayUrl ?? ""),
  );
  const [isVisible, setIsVisible] = useState(true);
  const fileName = item.type === "local" ? item.file.name : item.media.fileName;

  useEffect(() => {
    return () => {
      if (localUrl !== null) URL.revokeObjectURL(localUrl);
    };
  }, [localUrl]);

  useEffect(() => {
    if (item.type !== "server") return;

    const displayUrl = item.media.displayUrl;
    if (displayUrl === null) return;

    const display = new window.Image();
    display.onload = () => {
      setServerUrl(displayUrl);
      setIsVisible(true);
    };
    display.src = displayUrl;

    return () => {
      display.onload = null;
    };
  }, [item]);

  return (
    <ViewerPhoto
      src={(localUrl ?? serverUrl) || undefined}
      alt={fileName}
      draggable={false}
      $visible={isVisible}
      onLoad={() => setIsVisible(true)}
      onError={() => setIsVisible(false)}
    />
  );
};

const ViewerVideo = ({ item }: { item: GalleryItem }) => {
  const [localUrl] = useState(() =>
    item.type === "local" ? URL.createObjectURL(item.file) : null,
  );
  const source = item.type === "local" ? localUrl : item.media.displayUrl;
  const poster = item.type === "server" ? (item.media.thumbnailUrl ?? undefined) : undefined;

  useEffect(() => {
    return () => {
      if (localUrl !== null) URL.revokeObjectURL(localUrl);
    };
  }, [localUrl]);

  return (
    <ViewerVideoPlayer
      src={source ?? undefined}
      poster={poster}
      controls
      controlsList="nodownload noremoteplayback nofullscreen noplaybackrate"
      disablePictureInPicture
      disableRemotePlayback
      playsInline
    />
  );
};

const ViewerFooter = ({
  item,
  roomId,
  userId,
  token,
  canDelete,
  onDelete,
}: {
  item: GalleryItem;
  roomId: number;
  userId: number;
  token: string;
  canDelete: boolean;
  onDelete: () => void;
}) => {
  const media = item.type === "local" ? undefined : item.media;
  const fileName = item.type === "local" ? item.file.name : item.media.fileName;
  const downloadTarget =
    item.type === "local"
      ? {
          mediaId: item.mediaId,
          fileName: item.file.name,
          size: item.file.size,
          mimeType: item.file.type,
        }
      : {
          mediaId: item.mediaId,
          fileName: item.media.fileName,
          size: item.media.size,
          mimeType: item.media.mimeType,
        };
  const download = useMutation({
    mutationFn: async () => {
      const outcome = await downloadMedia({
        roomId,
        targets: [downloadTarget],
        // 폰에서 개별 다운을 할 경우, 여러 장 받기와 같은 기준으로 공유 시트를 쓸 기기를 가른다.
        mode: prefersShareSheet() ? "share" : "individual",
        token,
      });

      if (outcome.type === "failed") throw new Error(outcome.reason);
      if (outcome.type === "empty") throw new Error("사진을 다운로드하지 못했어요.");
    },
  });

  return (
    <Footer>
      <Metadata>
        <Uploader>{media?.uploaderName ?? "나"}</Uploader>
        <Subtitle>
          {[media ? formatDate(media.uploadedAt) : "", fileName].filter(Boolean).join(" · ")}
        </Subtitle>
        {download.isError && <ErrorMessage role="alert">{download.error.message}</ErrorMessage>}
      </Metadata>
      {item.type === "server" && (
        <ViewerLikeButton item={item} roomId={roomId} userId={userId} token={token} />
      )}
      <ActionButton
        type="button"
        $danger
        aria-label="사진 삭제"
        title={canDelete ? "사진 삭제" : "삭제 권한이 없어요"}
        disabled={!canDelete}
        onClick={onDelete}
      >
        <HiOutlineTrash size={21} />
      </ActionButton>
      <ActionButton
        type="button"
        aria-label="사진 다운로드"
        title="사진 다운로드"
        disabled={download.isPending}
        aria-busy={download.isPending}
        onClick={() => download.mutate()}
      >
        <HiArrowDownTray size={21} />
      </ActionButton>
    </Footer>
  );
};

const ViewerLikeButton = ({
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
  const mutation = useMediaLike({ roomId, mediaId: item.mediaId, userId, token });
  const likedByMe = item.media.likedByMe ?? false;

  return (
    <LikeActionButton
      type="button"
      $liked={likedByMe}
      aria-label={`좋아요 ${likedByMe ? "취소" : "추가"}`}
      aria-pressed={likedByMe}
      aria-busy={mutation.isPending}
      disabled={mutation.isPending}
      onClick={() => mutation.mutate(!likedByMe)}
    >
      {likedByMe ? <HiHeart size={21} /> : <HiOutlineHeart size={21} />}
      <span>{item.media.likeCount ?? 0}</span>
    </LikeActionButton>
  );
};

const Dialog = styled.dialog`
  position: fixed;
  inset: 0;
  width: 100%;
  max-width: none;
  height: 100dvh;
  max-height: none;
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 0;
  background: #000;
  color: #fff;

  &[open] {
    display: flex;
    flex-direction: column;
  }

  &:focus {
    outline: none;
  }

  &::backdrop {
    background: #000;
  }
`;

const Header = styled.header`
  display: flex;
  flex: none;
  align-items: center;
  gap: 12px;
  padding: calc(14px + env(safe-area-inset-top, 0px)) 16px 14px;
`;

const BackButton = styled.button`
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: #ffffff29;
  color: #fff;

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 3px;
  }
`;

const SelectionButton = styled(BackButton)`
  border-radius: 50%;

  &:disabled {
    cursor: default;
    opacity: 0.4;
  }

  &[aria-pressed="true"] {
    background: ${colors.primary};
  }
`;

const Counter = styled.span`
  flex: 1;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
`;

const Stage = styled.div`
  position: relative;
  display: grid;
  flex: 1;
  min-height: 0;
  margin: 0 16px;
  overflow: hidden;
  border-radius: 14px;
  place-items: center;
  touch-action: pan-y pinch-zoom;

  @media (min-width: 768px) {
    margin-right: 48px;
    margin-left: 48px;
  }
`;

const ViewerPhoto = styled.img<{ $visible: boolean }>`
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
  visibility: ${({ $visible }) => ($visible ? "visible" : "hidden")};
  user-select: none;
`;

const ViewerVideoPlayer = styled.video`
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
`;

const TapZone = styled.div<{ $side: "left" | "right" }>`
  position: absolute;
  top: 0;
  bottom: 0;
  ${({ $side }) => $side}: 0;
  width: 50%;
  -webkit-tap-highlight-color: transparent;

  @media (min-width: 768px) {
    display: none;
  }
`;

const SlideButton = styled(BackButton)`
  position: absolute;
  top: calc(50% - 22px);
  z-index: 1;
  width: 44px;
  height: 44px;
  border-radius: 50%;

  &:disabled {
    visibility: hidden;
  }

  svg {
    width: 26px;
    height: 26px;
  }
`;

const PreviousButton = styled(SlideButton)`
  left: 12px;
`;

const NextButton = styled(SlideButton)`
  right: 12px;
`;

const Footer = styled.footer`
  display: flex;
  flex: none;
  align-items: flex-end;
  gap: 12px;
  padding: 18px 20px calc(20px + env(safe-area-inset-bottom, 0px));
`;

const Metadata = styled.div`
  flex: 1;
  min-width: 0;
`;

const Uploader = styled.p`
  font-size: 15px;
  font-weight: 800;
`;

const Subtitle = styled.p`
  margin-top: 4px;
  overflow: hidden;
  color: #a9a6a1;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const ErrorMessage = styled.p`
  margin-top: 4px;
  color: #ff7a7e;
  font-size: 12px;
`;

const ActionButton = styled.button<{ $danger?: boolean }>`
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  color: ${({ $danger }) => ($danger ? "#ff5a5e" : "#fff")};

  &:hover:not(:disabled) {
    background: #ffffff1f;
  }

  &:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 3px;
  }

  &:disabled {
    cursor: default;
    opacity: 0.4;
  }
`;

const LikeActionButton = styled(ActionButton)<{ $liked: boolean }>`
  display: flex;
  gap: 4px;
  width: auto;
  min-width: 40px;
  padding: 0 8px;
  color: ${({ $liked }) => ($liked ? colors.danger : "#fff")};
  font-size: 12px;
  font-variant-numeric: tabular-nums;
`;

const StateMessage = styled.p`
  padding: 20px;
  color: #a9a6a1;
  font-size: 14px;
  text-align: center;
`;
