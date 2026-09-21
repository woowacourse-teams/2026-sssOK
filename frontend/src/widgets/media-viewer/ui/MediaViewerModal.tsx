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
  HiOutlineTrash,
} from "react-icons/hi2";

import type { GalleryItem } from "@/entities/media";
import { deleteMedia } from "@/features/delete-media";
import { downloadMedia } from "@/features/download-media";
import { isApiError } from "@/shared/api";
import { colors } from "@/shared/styles/tokens";

interface MediaViewerModalProps {
  items: GalleryItem[];
  totalCount: number;
  activeMediaId: number;
  roomId: number;
  userId: number;
  hostId: number;
  token: string;
  selectedPhotoIds: number[];
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  isNextPageError: boolean;
  onLoadNextPage: () => void;
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
  totalCount,
  activeMediaId,
  roomId,
  userId,
  hostId,
  token,
  selectedPhotoIds,
  hasNextPage,
  isFetchingNextPage,
  isNextPageError,
  onLoadNextPage,
  onChange,
  onClose,
  onToggle,
  onDeleted,
}: MediaViewerModalProps) => {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const touchStart = useRef<{ x: number; y: number } | null>(null);
  const moveAfterLoading = useRef(false);
  const index = items.findIndex((item) => item.mediaId === activeMediaId);
  const item = items[index];
  const previous = items[index - 1];
  const next = items[index + 1];

  const moveNext = () => {
    if (next) {
      onChange(next.mediaId);
      if (!items[index + 2] && hasNextPage && !isFetchingNextPage) onLoadNextPage();
      return;
    }
    if (hasNextPage && !isFetchingNextPage) {
      moveAfterLoading.current = true;
      onLoadNextPage();
    }
  };

  useEffect(() => {
    if (!moveAfterLoading.current || isFetchingNextPage) return;
    if (isNextPageError) {
      moveAfterLoading.current = false;
      return;
    }
    if (next) {
      moveAfterLoading.current = false;
      onChange(next.mediaId);
      return;
    }
    if (hasNextPage) {
      onLoadNextPage();
      return;
    }
    moveAfterLoading.current = false;
  }, [hasNextPage, isFetchingNextPage, isNextPageError, next, onChange, onLoadNextPage]);

  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;

    dialog?.showModal();
    document.body.style.overflow = "hidden";

    return () => {
      dialog?.close();
      document.body.style.overflow = overflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, []);

  return createPortal(
    <Dialog
      ref={dialogRef}
      aria-label="사진 크게 보기"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onKeyDown={(event) => {
        if (event.key === "ArrowLeft" && previous) {
          event.preventDefault();
          onChange(previous.mediaId);
        }
        if (event.key === "ArrowRight" && (next || hasNextPage)) {
          event.preventDefault();
          moveNext();
        }
      }}
    >
      <Header>
        <BackButton autoFocus type="button" aria-label="갤러리로 돌아가기" onClick={onClose}>
          <HiArrowLeft size={20} />
        </BackButton>
        <Counter aria-live="polite">
          {index < 0 ? "사진 보기" : `${index + 1} / ${totalCount}`}
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
              if (dx < 0 && (next || hasNextPage)) moveNext();
              if (dx > 0 && previous) onChange(previous.mediaId);
            }}
          >
            <ViewerImage key={`${item.mediaId}:${item.type}`} item={item} />
            <PreviousButton
              type="button"
              aria-label="이전 사진"
              disabled={!previous}
              onClick={() => previous && onChange(previous.mediaId)}
            >
              <HiChevronLeft />
            </PreviousButton>
            <NextButton
              type="button"
              aria-label="다음 사진"
              disabled={(!next && !hasNextPage) || isFetchingNextPage}
              onClick={moveNext}
            >
              <HiChevronRight />
            </NextButton>
          </Stage>
          <ViewerFooter
            key={item.mediaId}
            item={item}
            roomId={roomId}
            token={token}
            canDelete={
              item.type === "server" && (item.media.uploaderId === userId || hostId === userId)
            }
            onDeleted={onDeleted}
          />
        </>
      ) : (
        <StateMessage>사진이 목록에 없어요.</StateMessage>
      )}
    </Dialog>,
    document.body,
  );
};

const ViewerImage = ({ item }: { item: GalleryItem }) => {
  const [localUrl] = useState(() =>
    item.type === "local" ? URL.createObjectURL(item.file) : null,
  );
  const [serverUrl, setServerUrl] = useState(() =>
    item.type === "server" ? item.media.thumbnailUrl : "",
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

    const original = new window.Image();
    original.onload = () => {
      setServerUrl(item.media.originalUrl);
      setIsVisible(true);
    };
    original.src = item.media.originalUrl;

    return () => {
      original.onload = null;
    };
  }, [item]);

  return (
    <ViewerPhoto
      src={localUrl ?? serverUrl}
      alt={fileName}
      draggable={false}
      $visible={isVisible}
      onLoad={() => setIsVisible(true)}
      onError={() => setIsVisible(false)}
    />
  );
};

const ViewerFooter = ({
  item,
  roomId,
  token,
  canDelete,
  onDeleted,
}: {
  item: GalleryItem;
  roomId: number;
  token: string;
  canDelete: boolean;
  onDeleted: (mediaId: number) => void;
}) => {
  const media = item.type === "server" ? item.media : undefined;
  const fileName = item.type === "local" ? item.file.name : item.media.fileName;
  const deletion = useMutation({
    mutationFn: () => deleteMedia({ roomId, mediaId: item.mediaId, token }),
    onSuccess: () => onDeleted(item.mediaId),
  });
  const download = useMutation({
    mutationFn: async () => {
      if (!media) return;

      const outcome = await downloadMedia({
        roomId,
        targets: [
          {
            mediaId: media.mediaId,
            fileName: media.fileName,
            size: media.size,
            mimeType: media.mimeType,
          },
        ],
        mode: "individual",
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
        {deletion.isError && (
          <ErrorMessage role="alert">
            {isApiError(deletion.error)
              ? deletion.error.message
              : "사진을 삭제하지 못했어요."}
          </ErrorMessage>
        )}
        {download.isError && <ErrorMessage role="alert">{download.error.message}</ErrorMessage>}
      </Metadata>
      <ActionButton
        type="button"
        $danger
        aria-label="사진 삭제"
        title={canDelete ? "사진 삭제" : "삭제 권한이 없어요"}
        disabled={!canDelete || deletion.isPending}
        aria-busy={deletion.isPending}
        onClick={() => deletion.mutate()}
      >
        <HiOutlineTrash size={21} />
      </ActionButton>
      <ActionButton
        type="button"
        aria-label="사진 다운로드"
        title={media ? "사진 다운로드" : "업로드가 끝난 뒤 다운로드할 수 있어요"}
        disabled={!media || download.isPending}
        aria-busy={download.isPending}
        onClick={() => download.mutate()}
      >
        <HiArrowDownTray size={21} />
      </ActionButton>
    </Footer>
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

const StateMessage = styled.p`
  padding: 20px;
  color: #a9a6a1;
  font-size: 14px;
  text-align: center;
`;
