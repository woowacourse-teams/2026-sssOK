import { useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { type InfiniteData, useQueryClient } from "@tanstack/react-query";

import { photosQueryKey, type GalleryItem, type MediaList } from "@/entities/media";
import { canUploadTo, roomQueryKey, type Room } from "@/entities/room";
import { removeRoomSession } from "@/entities/session";
import { FeedbackBottomSheet, FeedbackButton } from "@/features/create-feedback";
import { DeleteRoomModal } from "@/features/delete-room";
import { DeleteSelectedMediaModal } from "@/features/delete-media";
import { DeleteFolderModal } from "@/features/delete-folder";
import { EditFolderBottomSheet } from "@/features/edit-folder";
import { SelectionDownloadBar } from "@/features/download-media";
import { MoveMediaFolderBottomSheet } from "@/features/move-media-folder";
import { useRoomEvents } from "@/features/subscribe-room-events";
import { MediaUploader } from "@/features/upload-media";
import { ROUTES } from "@/shared/config";
import { Toast } from "@/shared/ui/toast";
import { FolderFilter } from "@/widgets/folder-filter";
import { GalleryOptions } from "@/widgets/gallery-options";
import { MediaViewerModal } from "@/widgets/media-viewer";
import { PhotoGallery } from "@/widgets/photo-gallery";
import { RoomSummary } from "@/widgets/room-summary";
import { useAnalyticsRoom } from "../model/useAnalyticsRoom";
import { useCreateFolderAction } from "../model/useCreateFolderAction";
import { useGalleryFilter } from "../model/useGalleryFilter";
import { useGalleryPhotos } from "../model/useGalleryPhotos";
import { usePendingMedia } from "../model/usePendingMedia";
import { usePhotoSelection } from "../model/usePhotoSelection";
import { GalleryModalHost } from "./GalleryModalHost";
import { Page } from "./GalleryPage.styles";

interface GalleryContentProps {
  room: Room;
  accessToken: string;
  userId: number;
}

export const GalleryContent = ({ room, accessToken, userId }: GalleryContentProps) => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  useAnalyticsRoom(room.code, userId === room.hostId);
  const { uploadSlots, addPendingMedia, removePendingMedia, replacePendingMedia } =
    usePendingMedia();
  useRoomEvents({
    roomId: room.roomId,
    userId,
    token: accessToken,
    onMediaReady: replacePendingMedia,
  });
  // 모달
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const rawMediaId = searchParams.get("media");
  const activeMediaId =
    rawMediaId && /^[1-9]\d*$/.test(rawMediaId) && Number.isSafeInteger(Number(rawMediaId))
      ? Number(rawMediaId)
      : null;
  const setActiveMediaId = (mediaId: number | null) => {
    if (mediaId === null && location.state?.galleryViewer) {
      navigate(-1);
      return;
    }
    const next = new URLSearchParams(searchParams);
    if (mediaId === null) next.delete("media");
    else next.set("media", String(mediaId));
    setSearchParams(next, {
      replace: activeMediaId !== null,
      state: activeMediaId === null ? { galleryViewer: true } : location.state,
    });
  };
  const closeMediaViewerAfterDelete = () => {
    const next = new URLSearchParams(searchParams);
    next.delete("media");
    setSearchParams(next, { replace: true, state: null });
  };
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isEditFolderOpen, setIsEditFolderOpen] = useState(false);
  const [isDeleteFolderOpen, setIsDeleteFolderOpen] = useState(false);
  const [isDeleteSelectionOpen, setIsDeleteSelectionOpen] = useState(false);
  const [isMoveSelectionOpen, setIsMoveSelectionOpen] = useState(false);
  const [isFeedbackOpen, setIsFeedbackOpen] = useState(false);
  const [isFeedbackSuccess, setIsFeedbackSuccess] = useState(false);
  const [deletedFolderName, setDeletedFolderName] = useState<string | null>(null);

  // 옵션 선택
  const { selectedFolderId, selectedOption, selectFolder, selectOption } = useGalleryFilter();
  const selectedFolder = room.folders.find((folder) => folder.id === selectedFolderId) ?? null;

  // 사진 조회
  const {
    photos,
    totalCount,
    isPending,
    isError,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
    loadMore,
  } = useGalleryPhotos({
    roomId: room.roomId,
    accessToken,
    userId,
    selectedFolderId,
    selectedOption,
  });

  const isUnfiltered = selectedFolderId === null && selectedOption === "all";
  const allPhotoCount = isUnfiltered ? (totalCount ?? room.photoCount) : room.photoCount;

  const visibleUploadSlots = uploadSlots.filter((slot) => {
    if (selectedOption === "others") return false;
    if (selectedFolderId === null) return true;

    return slot.folderIds.includes(selectedFolderId);
  });

  // 사진 선택
  const uploadSlotIds = new Set(visibleUploadSlots.map((slot) => slot.mediaId));
  const viewerItems: GalleryItem[] = [
    ...visibleUploadSlots,
    ...photos
      .filter((photo) => !uploadSlotIds.has(photo.mediaId))
      .map((media) => ({
        mediaId: media.mediaId,
        type: "server" as const,
        media,
        folderIds: media.folderIds,
      })),
  ].filter((item) =>
    item.type === "local" ? !item.file.type.startsWith("video/") : item.media.type === "IMAGE",
  );
  const photoIds = [
    ...visibleUploadSlots.map((slot) => slot.mediaId),
    ...photos.filter((photo) => !uploadSlotIds.has(photo.mediaId)).map((photo) => photo.mediaId),
  ];
  const { selectedPhotoIds, isAllSelected, togglePhoto, toggleAllPhotos, clearSelection } =
    usePhotoSelection(photoIds);

  // 폴더 생성 흐름
  const { handleCreateFolder } = useCreateFolderAction({
    roomCode: room.code,
    userId,
    selectFolder,
    clearSelection,
  });

  // 고른 순서가 아니라 **화면에 보이는 순서**로 넘긴다. 압축을 풀었을 때 파일이
  // 갤러리와 같은 차례로 놓여야, 고른 순서를 기억하지 못하는 사용자가 헤매지 않는다.
  const downloadTargets = [
    ...visibleUploadSlots.map((slot) => ({
      mediaId: slot.mediaId,
      fileName: slot.type === "local" ? slot.file.name : slot.media.fileName,
      size: slot.type === "local" ? slot.file.size : slot.media.size,
      mimeType: slot.type === "local" ? slot.file.type : slot.media.mimeType,
    })),
    ...photos
      .filter((photo) => !uploadSlotIds.has(photo.mediaId))
      .map((photo) => ({
        mediaId: photo.mediaId,
        fileName: photo.fileName,
        size: photo.size,
        mimeType: photo.mimeType,
      })),
  ].filter((media) => selectedPhotoIds.includes(media.mediaId));

  return (
    <Page>
      <RoomSummary
        roomCode={room.code}
        hostId={room.hostId}
        expiresAt={room.expiresAt}
        roomName={room.name}
        isHost={userId === room.hostId}
        hasSelectedFolder={selectedFolderId !== null}
        onOpenSettings={() => navigate(ROUTES.roomSettings(room.code))}
        onDeleteRoom={() => setIsDeleteModalOpen(true)}
        onAddFolder={() => void handleCreateFolder("menu")}
        onEditFolder={() => setIsEditFolderOpen(true)}
        onDeleteFolder={() => setIsDeleteFolderOpen(true)}
      />
      <FolderFilter
        totalCount={allPhotoCount}
        folders={room.folders}
        selectedFolderId={selectedFolderId}
        onSelectFolder={(folderId) => {
          selectFolder(folderId);
          clearSelection();
        }}
        onAddFolder={() => void handleCreateFolder("filter_plus")}
      />
      <GalleryOptions
        selectedOption={selectedOption}
        onSelectOption={(option) => {
          selectOption(option);
          clearSelection();
        }}
        isAllSelected={isAllSelected}
        canSelectAll={photoIds.length > 0}
        onToggleAll={toggleAllPhotos}
      />
      <FeedbackButton
        hidden={selectedPhotoIds.length > 0}
        onClick={() => setIsFeedbackOpen(true)}
      />
      <MediaUploader
        roomId={room.roomId}
        token={accessToken}
        // 방장만 올리는 방의 참여자에게는 버튼을 아예 내주지 않는다. 서버가 발급에서
        // 403 으로 막는 것을, 누르기 전에 화면이 먼저 말해주는 셈이다 (#148).
        canUpload={canUploadTo(room, userId)}
        hideButton={selectedPhotoIds.length > 0}
        folderIds={selectedFolderId === null ? undefined : [selectedFolderId]}
        onPreviewReady={addPendingMedia}
        onRegistered={() =>
          queryClient.invalidateQueries({
            queryKey: roomQueryKey(room.code, userId),
            exact: true,
          })
        }
        /*
         * 업로드가 "이 방은 이제 없다" 고 답했다 (만료·삭제·세션 만료). 갤러리에 남겨두면
         * 무엇을 눌러도 같은 실패만 돌려받는다.
         *
         * **캐시부터 버린다.** 들고 있는 방 정보는 아직 ACTIVE 라, 그대로 두고 보내면
         * 입장 화면이 멀쩡한 방으로 알고 갤러리로 되돌려보낸다. 결국은 갤러리가 다시
         * 조회해 사라진 걸 알아차리고 또 내보내지만, 그 사이 **갤러리가 한 번 번쩍인다.**
         * 지우면 입장 화면이 처음부터 새로 물어보고 "삭제된 방이에요" 를 말한다.
         */
        onLeaveRoom={() => {
          queryClient.removeQueries({ queryKey: roomQueryKey(room.code, userId) });
          navigate(ROUTES.roomEntry(room.code), { replace: true });
        }}
      />
      <PhotoGallery
        photos={photos}
        uploadSlots={visibleUploadSlots}
        userId={userId}
        selectedPhotoIds={selectedPhotoIds}
        isPending={isPending}
        isError={isError}
        onTogglePhoto={togglePhoto}
        onOpenPhoto={setActiveMediaId}
        hasMore={hasNextPage}
        isLoadingMore={isFetchingNextPage}
        onLoadMore={loadMore}
      />
      {activeMediaId !== null && (
        <MediaViewerModal
          items={viewerItems}
          totalCount={totalCount ?? viewerItems.length}
          activeMediaId={activeMediaId}
          roomId={room.roomId}
          userId={userId}
          hostId={room.hostId}
          token={accessToken}
          selectedPhotoIds={selectedPhotoIds}
          hasNextPage={hasNextPage}
          isFetchingNextPage={isFetchingNextPage}
          isNextPageError={isFetchNextPageError}
          onLoadNextPage={loadMore}
          onChange={setActiveMediaId}
          onClose={() => setActiveMediaId(null)}
          onToggle={togglePhoto}
          onDeleted={(mediaId) => {
            closeMediaViewerAfterDelete();
            removePendingMedia([mediaId]);
            if (selectedPhotoIds.includes(mediaId)) togglePhoto(mediaId);
            queryClient.setQueriesData<InfiniteData<MediaList>>(
              { queryKey: photosQueryKey(room.roomId, userId) },
              (current) => {
                if (!current) return current;
                const exists = current.pages.some((page) =>
                  page.items.some((item) => item.mediaId === mediaId),
                );

                return {
                  ...current,
                  pages: current.pages.map((page) => ({
                    ...page,
                    items: page.items.filter((item) => item.mediaId !== mediaId),
                    totalCount: exists ? Math.max(0, page.totalCount - 1) : page.totalCount,
                  })),
                };
              },
            );
            void queryClient.invalidateQueries({
              queryKey: roomQueryKey(room.code, userId),
              exact: true,
            });
          }}
        />
      )}
      <SelectionDownloadBar
        targets={downloadTargets}
        roomId={room.roomId}
        roomCode={room.code}
        roomPhotoCount={room.photoCount}
        isAllSelected={isAllSelected}
        token={accessToken}
        onClearSelection={clearSelection}
        onDeleteSelection={() => setIsDeleteSelectionOpen(true)}
        onMoveSelection={() => setIsMoveSelectionOpen(true)}
      />

      {isDeleteSelectionOpen && (
        <DeleteSelectedMediaModal
          roomId={room.roomId}
          mediaIds={selectedPhotoIds}
          token={accessToken}
          onClose={() => setIsDeleteSelectionOpen(false)}
          onSuccess={async () => {
            setIsDeleteSelectionOpen(false);
            removePendingMedia(selectedPhotoIds);
            clearSelection();
            await Promise.all([
              queryClient.invalidateQueries({
                // 필터별 목록이 이 키 뒤에 붙어 있다. exact 로 두면 하나도 갱신되지 않는다.
                queryKey: photosQueryKey(room.roomId, userId),
              }),
              queryClient.invalidateQueries({
                queryKey: roomQueryKey(room.code, userId),
                exact: true,
              }),
            ]);
          }}
        />
      )}

      {isMoveSelectionOpen && (
        <MoveMediaFolderBottomSheet
          roomId={room.roomId}
          mediaIds={selectedPhotoIds}
          folders={room.folders}
          currentFolderId={selectedFolderId}
          token={accessToken}
          onClose={() => setIsMoveSelectionOpen(false)}
          onSuccess={async (folderId) => {
            setIsMoveSelectionOpen(false);
            selectFolder(folderId);
            clearSelection();
            await Promise.all([
              queryClient.invalidateQueries({
                // 필터별 목록이 이 키 뒤에 붙어 있다. exact 로 두면 하나도 갱신되지 않는다.
                queryKey: photosQueryKey(room.roomId, userId),
              }),
              queryClient.invalidateQueries({
                queryKey: roomQueryKey(room.code, userId),
                exact: true,
              }),
            ]);
          }}
        />
      )}

      {isDeleteModalOpen && (
        <DeleteRoomModal
          roomId={room.roomId}
          accessToken={accessToken}
          onClose={() => setIsDeleteModalOpen(false)}
          onSuccess={() => {
            removeRoomSession(room.code);
            queryClient.removeQueries({ queryKey: ["room", room.code] });
            queryClient.removeQueries({ queryKey: ["photos", room.roomId] });
            navigate(ROUTES.home, { replace: true });
          }}
        />
      )}

      <GalleryModalHost roomId={room.roomId} accessToken={accessToken} />

      {isFeedbackOpen && (
        <FeedbackBottomSheet
          roomId={room.roomId}
          accessToken={accessToken}
          onClose={() => setIsFeedbackOpen(false)}
          onSuccess={() => setIsFeedbackSuccess(true)}
        />
      )}

      {isEditFolderOpen && selectedFolder && (
        <EditFolderBottomSheet
          roomId={room.roomId}
          folder={selectedFolder}
          accessToken={accessToken}
          onClose={() => setIsEditFolderOpen(false)}
          onSuccess={async () => {
            setIsEditFolderOpen(false);
            await queryClient.invalidateQueries({
              queryKey: roomQueryKey(room.code, userId),
              exact: true,
            });
          }}
        />
      )}

      {isDeleteFolderOpen && selectedFolder && (
        <DeleteFolderModal
          roomId={room.roomId}
          folderId={selectedFolder.id}
          folderName={selectedFolder.name}
          accessToken={accessToken}
          onClose={() => setIsDeleteFolderOpen(false)}
          onSuccess={async () => {
            const folderName = selectedFolder.name;
            setIsDeleteFolderOpen(false);
            selectFolder(null);
            clearSelection();
            await Promise.all([
              queryClient.invalidateQueries({
                queryKey: roomQueryKey(room.code, userId),
                exact: true,
              }),
              queryClient.invalidateQueries({
                // 필터별 목록이 이 키 뒤에 붙어 있다. exact 로 두면 하나도 갱신되지 않는다.
                queryKey: photosQueryKey(room.roomId, userId),
              }),
            ]);
            setDeletedFolderName(folderName);
          }}
        />
      )}

      {deletedFolderName && (
        <Toast
          message={`‘${deletedFolderName}’ 폴더를 삭제했어요.`}
          onClose={() => setDeletedFolderName(null)}
        />
      )}

      {isFeedbackSuccess && (
        <Toast message="문의를 보냈어요." onClose={() => setIsFeedbackSuccess(false)} />
      )}
    </Page>
  );
};
