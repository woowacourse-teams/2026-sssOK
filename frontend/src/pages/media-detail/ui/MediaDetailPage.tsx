import { useMutation, useQueryClient } from "@tanstack/react-query";
import { HiArrowDownTray, HiArrowLeft, HiCheck, HiOutlineTrash } from "react-icons/hi2";
import { Navigate, useNavigate, useParams } from "react-router-dom";

import { mediaQueryKey, photosQueryKey, useMediaQuery, type MediaList } from "@/entities/media";
import { roomQueryKey, useRoomQuery, type Room } from "@/entities/room";
import { readValidRoomSession, type AnonymousSession } from "@/entities/session";
import { deleteMedia } from "@/features/delete-media";
import { usePhotoSelection } from "@/features/select-media";
import { isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config";
import { Button } from "@/shared/ui/button";
import { Toast } from "@/shared/ui/toast";
import {
  ActionButton,
  BackButton,
  Counter,
  Footer,
  Header,
  Image,
  Metadata,
  Page,
  SelectionButton,
  Stage,
  StateMessage,
  Subtitle,
  Uploader,
} from "./MediaDetailPage.styles";

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

const DetailState = ({
  message,
  onBack,
  onRetry,
}: {
  message: string;
  onBack: () => void;
  onRetry?: () => void;
}) => (
  <Page aria-label="사진 크게 보기">
    <Header>
      <BackButton type="button" aria-label="갤러리로 돌아가기" onClick={onBack}>
        <HiArrowLeft size={20} />
      </BackButton>
      <Counter>사진 보기</Counter>
    </Header>
    <Stage>
      <div>
        <StateMessage role="status">{message}</StateMessage>
        {onRetry && <Button onClick={onRetry}>다시 시도</Button>}
      </div>
    </Stage>
  </Page>
);

const MediaDetailContent = ({
  room,
  session,
  mediaId,
}: {
  room: Room;
  session: AnonymousSession;
  mediaId: number;
}) => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { selectedPhotoIds, togglePhoto, removePhoto } = usePhotoSelection([mediaId]);
  const query = useMediaQuery({
    roomId: room.roomId,
    mediaId,
    userId: session.userId,
    token: session.accessToken,
  });
  const onBack = () => navigate(ROUTES.gallery(room.code), { replace: true });

  const deleteMutation = useMutation({
    mutationFn: () => deleteMedia({ roomId: room.roomId, mediaId, token: session.accessToken }),
    onSuccess: async () => {
      const listKey = photosQueryKey(room.roomId, session.userId);
      await queryClient.cancelQueries({ queryKey: listKey });
      queryClient.setQueriesData<MediaList>({ queryKey: listKey }, (current) =>
        current
          ? {
              ...current,
              items: current.items.filter((item) => item.mediaId !== mediaId),
            }
          : current,
      );
      removePhoto(mediaId);
      navigate(ROUTES.gallery(room.code), { replace: true });
      queryClient.removeQueries({
        queryKey: mediaQueryKey(room.roomId, mediaId, session.userId),
        exact: true,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: listKey, refetchType: "all" }),
        queryClient.invalidateQueries({
          queryKey: roomQueryKey(room.code, session.userId),
          exact: true,
          refetchType: "all",
        }),
      ]);
    },
  });

  if (query.isPending) return <DetailState message="사진을 불러오는 중이에요." onBack={onBack} />;
  if (query.isError)
    return (
      <DetailState
        message={isApiError(query.error) ? query.error.message : "사진을 불러오지 못했어요."}
        onBack={onBack}
        onRetry={() => void query.refetch()}
      />
    );
  const media = query.data;
  if (media.type !== "IMAGE")
    return <DetailState message="이미지만 크게 볼 수 있어요." onBack={onBack} />;

  return (
    <Page aria-label="사진 크게 보기">
      <Header>
        <BackButton type="button" aria-label="갤러리로 돌아가기" onClick={onBack}>
          <HiArrowLeft size={20} />
        </BackButton>
        <Counter>1 / 1</Counter>
        <SelectionButton
          type="button"
          aria-label="사진 선택"
          aria-pressed={selectedPhotoIds.includes(mediaId)}
          onClick={() => togglePhoto(mediaId)}
          disabled={media.status !== "READY"}
        >
          <HiCheck size={19} />
        </SelectionButton>
      </Header>
      <Stage>
        <Image src={media.originalUrl} alt={media.fileName} draggable={false} />
      </Stage>
      <Footer>
        <Metadata>
          <Uploader>{media.uploaderName}</Uploader>
          <Subtitle>
            {[formatDate(media.takenAt ?? media.uploadedAt), media.fileName, media.location?.name]
              .filter(Boolean)
              .join(" · ")}
          </Subtitle>
        </Metadata>
        <ActionButton
          type="button"
          $danger
          aria-label="사진 삭제"
          title={media.canDelete === true ? "사진 삭제" : "삭제 권한이 없어요"}
          disabled={media.canDelete !== true || deleteMutation.isPending}
          aria-busy={deleteMutation.isPending}
          onClick={() => {
            if (!deleteMutation.isPending) deleteMutation.mutate();
          }}
        >
          <HiOutlineTrash size={21} />
        </ActionButton>
        <ActionButton type="button" aria-label="사진 다운로드 (준비 중)" title="준비 중" disabled>
          <HiArrowDownTray size={21} />
        </ActionButton>
      </Footer>
      {deleteMutation.isError && (
        <Toast
          tone="error"
          message={
            isApiError(deleteMutation.error)
              ? deleteMutation.error.message
              : "사진을 삭제하지 못했어요. 다시 시도해 주세요."
          }
          onClose={() => deleteMutation.reset()}
        />
      )}
    </Page>
  );
};

export const MediaDetailPage = () => {
  const { code = "", mediaId: rawMediaId = "" } = useParams();
  const session = readValidRoomSession(code);
  const navigate = useNavigate();
  const mediaId = Number(rawMediaId);
  const isValidId = /^[1-9]\d*$/.test(rawMediaId) && Number.isSafeInteger(mediaId);
  const roomQuery = useRoomQuery({
    code,
    token: session?.accessToken,
    userId: session?.userId,
    enabled: session !== null && isValidId,
  });
  const onBack = () => navigate(ROUTES.gallery(code), { replace: true });

  if (!session) return <Navigate to={ROUTES.roomEntry(code)} replace />;
  if (!isValidId) return <DetailState message="올바르지 않은 사진 주소예요." onBack={onBack} />;
  if (roomQuery.isPending)
    return <DetailState message="방 정보를 불러오는 중이에요." onBack={onBack} />;
  if (roomQuery.isError)
    return (
      <DetailState
        message={
          isApiError(roomQuery.error) ? roomQuery.error.message : "방 정보를 불러오지 못했어요."
        }
        onBack={onBack}
        onRetry={() => void roomQuery.refetch()}
      />
    );
  if (roomQuery.data.status !== "ACTIVE") return <Navigate to={ROUTES.roomEntry(code)} replace />;

  return (
    <MediaDetailContent
      key={code + ":" + mediaId}
      room={roomQuery.data}
      session={session}
      mediaId={mediaId}
    />
  );
};
