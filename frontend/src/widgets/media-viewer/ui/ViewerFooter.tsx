import { useState } from "react";
import { useParams } from "react-router-dom";
import { HiArrowDownTray, HiOutlineTrash } from "react-icons/hi2";
import { LuSiren } from "react-icons/lu";

import type { GalleryItem } from "@/entities/media";
import { useRoomSession } from "@/entities/member-session";
import { ReportMediaModal } from "@/features/report-media";
import { useToast } from "@/shared/ui/toast";
import { isVideoItem } from "../lib/isVideoItem";
import { useViewerDownload } from "../model/useViewerDownload";
import { ViewerActionMenu, type ViewerAction } from "./ViewerActionMenu";
import { ViewerLikeButton } from "./ViewerLikeButton";
import { Footer, Metadata, Uploader, Subtitle, ErrorMessage } from "./ViewerFooter.styles";

const formatDate = (value: string): string => {
  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? ""
    : new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(date);
};

interface ViewerFooterProps {
  item: GalleryItem;
  roomId: number;
  canDelete: boolean;
  container: Element | null;
  onDelete: () => void;
}

export const ViewerFooter = ({
  item,
  roomId,
  canDelete,
  container,
  onDelete,
}: ViewerFooterProps) => {
  const { code = "" } = useParams();
  const { session } = useRoomSession(code);
  const { showToast } = useToast();
  const [reportOpen, setReportOpen] = useState(false);
  const download = useViewerDownload({ roomId, item, token: session?.accessToken });
  const media = item.type === "local" ? undefined : item.media;
  const fileName = item.type === "local" ? item.file.name : item.media.fileName;
  const actions: ViewerAction[] = [
    ...(item.type === "server" && !isVideoItem(item)
      ? [
          {
            label: "사진 신고",
            icon: <LuSiren size={21} />,
            onSelect: () => setReportOpen(true),
          },
        ]
      : []),
    {
      label: "사진 삭제",
      icon: <HiOutlineTrash size={21} />,
      onSelect: onDelete,
      danger: true,
      disabled: !canDelete,
      title: canDelete ? "사진 삭제" : "삭제 권한이 없어요",
    },
    {
      label: "사진 다운로드",
      icon: <HiArrowDownTray size={21} />,
      onSelect: () => download.mutate(),
      disabled: download.isPending,
      busy: download.isPending,
    },
  ];

  if (!session) return null;

  return (
    <Footer>
      <Metadata>
        <Uploader>{media?.uploaderName ?? "나"}</Uploader>
        <Subtitle>
          {[media ? formatDate(media.uploadedAt) : "", fileName].filter(Boolean).join(" · ")}
        </Subtitle>
        {download.isError && <ErrorMessage role="alert">{download.error.message}</ErrorMessage>}
      </Metadata>
      {item.type === "server" && <ViewerLikeButton item={item} roomId={roomId} session={session} />}
      <ViewerActionMenu actions={actions} />
      {reportOpen && (
        <ReportMediaModal
          roomId={roomId}
          mediaId={item.mediaId}
          container={container}
          onClose={() => setReportOpen(false)}
          onSubmit={() => {
            setReportOpen(false);
            showToast({ message: "신고가 완료됐어요." });
          }}
        />
      )}
    </Footer>
  );
};
