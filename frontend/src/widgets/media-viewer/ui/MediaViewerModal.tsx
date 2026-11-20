import { useState } from "react";
import { createPortal } from "react-dom";
import { useParams } from "react-router-dom";

import type { GalleryItem } from "@/entities/media";
import { useRoomSession } from "@/entities/member-session";
import { DeleteMediaModal } from "@/features/delete-media";
import { useViewerDialog } from "../model/useViewerDialog";
import { ViewerFooter } from "./ViewerFooter";
import { ViewerHeader } from "./ViewerHeader";
import { ViewerStage } from "./ViewerStage";
import { Dialog, StateMessage } from "./MediaViewerModal.styles";

interface MediaViewerModalProps {
  items: GalleryItem[];
  activeMediaId: number;
  roomId: number;
  hostId: number;
  selectedPhotoIds: number[];
  onChange: (mediaId: number) => void;
  onClose: () => void;
  onToggle: (mediaId: number) => void;
  onDeleted: (mediaId: number) => void;
}

export const MediaViewerModal = ({
  items,
  activeMediaId,
  roomId,
  hostId,
  selectedPhotoIds,
  onChange,
  onClose,
  onToggle,
  onDeleted,
}: MediaViewerModalProps) => {
  const { code = "" } = useParams();
  const { session } = useRoomSession(code);
  const [deletingMediaId, setDeletingMediaId] = useState<number | null>(null);
  const index = items.findIndex((item) => item.mediaId === activeMediaId);
  const item = items[index];
  const previous = items[index - 1];
  const next = items[index + 1];
  const { dialog, setDialog, events } = useViewerDialog({
    blocked: deletingMediaId !== null,
    onClose,
    onPrevious: previous ? () => onChange(previous.mediaId) : undefined,
    onNext: next ? () => onChange(next.mediaId) : undefined,
  });

  if (!session) return null;
  const canDelete =
    item?.type === "local" ||
    item?.media.uploaderId === session.userId ||
    hostId === session.userId;

  return createPortal(
    <Dialog ref={setDialog} tabIndex={-1} aria-label="사진 크게 보기" {...events}>
      <ViewerHeader
        index={index}
        totalCount={items.length}
        selected={selectedPhotoIds.includes(activeMediaId)}
        disabled={!item}
        onClose={onClose}
        onToggle={() => onToggle(activeMediaId)}
      />
      {item ? (
        <>
          <ViewerStage current={item} previous={previous} next={next} onChange={onChange} />
          <ViewerFooter
            key={item.mediaId}
            item={item}
            roomId={roomId}
            canDelete={canDelete}
            container={dialog}
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
          token={session.accessToken}
          container={dialog}
          onClose={() => setDeletingMediaId(null)}
          onSuccess={() => onDeleted(deletingMediaId)}
        />
      )}
    </Dialog>,
    document.body,
  );
};
