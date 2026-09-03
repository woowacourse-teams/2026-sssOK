import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";

import { photosQueryKey } from "@/entities/media";
import { canUploadTo, roomQueryKey, type Room } from "@/entities/room";
import { removeRoomSession } from "@/entities/session";
import { DeleteRoomModal } from "@/features/delete-room";
import { DeleteSelectedMediaModal } from "@/features/delete-media";
import { CreateFolderBottomSheet } from "@/features/create-folder";
import { DeleteFolderModal } from "@/features/delete-folder";
import { EditFolderBottomSheet } from "@/features/edit-folder";
import { SelectionDownloadBar } from "@/features/download-media";
import { MoveMediaFolderBottomSheet } from "@/features/move-media-folder";
import { MediaUploader } from "@/features/upload-media";
import { ROUTES } from "@/shared/config";
import { Toast } from "@/shared/ui/toast";
import { FolderFilter } from "@/widgets/folder-filter";
import { GalleryOptions } from "@/widgets/gallery-options";
import { PhotoGallery } from "@/widgets/photo-gallery";
import { RoomSummary } from "@/widgets/room-summary";
import { useGalleryFilter } from "../model/useGalleryFilter";
import { useGalleryPhotos } from "../model/useGalleryPhotos";
import { usePhotoSelection } from "../model/usePhotoSelection";
import { Page } from "./GalleryPage.styles";

interface GalleryContentProps {
  room: Room;
  accessToken: string;
  userId: number;
}

export const GalleryContent = ({ room, accessToken, userId }: GalleryContentProps) => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // 모달
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isCreateFolderOpen, setIsCreateFolderOpen] = useState(false);
  const [isEditFolderOpen, setIsEditFolderOpen] = useState(false);
  const [isDeleteFolderOpen, setIsDeleteFolderOpen] = useState(false);
  const [isDeleteSelectionOpen, setIsDeleteSelectionOpen] = useState(false);
  const [isMoveSelectionOpen, setIsMoveSelectionOpen] = useState(false);
  const [deletedFolderName, setDeletedFolderName] = useState<string | null>(null);

  // 옵션 선택
  const { selectedFolderId, selectedOption, selectFolder, selectOption } = useGalleryFilter();
  const selectedFolder = room.folders.find((folder) => folder.id === selectedFolderId) ?? null;

  // 사진 조회
  const { photos, isPending, isError } = useGalleryPhotos({
    roomId: room.roomId,
    accessToken,
    userId,
    selectedFolderId,
    selectedOption,
  });

  // 사진 선택
  const photoIds = photos.map((photo) => photo.mediaId);
  const { selectedPhotoIds, isAllSelected, togglePhoto, toggleAllPhotos, clearSelection } =
    usePhotoSelection(photoIds);

  // 고른 순서가 아니라 **화면에 보이는 순서**로 넘긴다. 압축을 풀었을 때 파일이
  // 갤러리와 같은 차례로 놓여야, 고른 순서를 기억하지 못하는 사용자가 헤매지 않는다.
  const downloadTargets = photos
    .filter((photo) => selectedPhotoIds.includes(photo.mediaId))
    .map((photo) => ({
      mediaId: photo.mediaId,
      fileName: photo.fileName,
      size: photo.size,
      mimeType: photo.mimeType,
    }));

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
        onAddFolder={() => setIsCreateFolderOpen(true)}
        onEditFolder={() => setIsEditFolderOpen(true)}
        onDeleteFolder={() => setIsDeleteFolderOpen(true)}
      />
      <FolderFilter
        totalCount={room.photoCount}
        folders={room.folders}
        selectedFolderId={selectedFolderId}
        onSelectFolder={(folderId) => {
          selectFolder(folderId);
          clearSelection();
        }}
        onAddFolder={() => setIsCreateFolderOpen(true)}
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
      <MediaUploader
        roomId={room.roomId}
        token={accessToken}
        // 방장만 올리는 방의 참여자에게는 버튼을 아예 내주지 않는다. 서버가 발급에서
        // 403 으로 막는 것을, 누르기 전에 화면이 먼저 말해주는 셈이다 (#148).
        canUpload={canUploadTo(room, userId)}
        hideButton={selectedPhotoIds.length > 0}
        folderIds={selectedFolderId === null ? undefined : [selectedFolderId]}
        // 올린 사진은 서버에만 있다. 목록을 다시 불러오지 않으면 갤러리에 나타나지 않는다.
        onUploaded={() =>
          queryClient.invalidateQueries({ queryKey: photosQueryKey(room.roomId, userId) })
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
        userId={userId}
        selectedPhotoIds={selectedPhotoIds}
        isPending={isPending}
        isError={isError}
        onTogglePhoto={togglePhoto}
        onOpenPhoto={(photo) => navigate(ROUTES.mediaDetail(room.code, photo.mediaId))}
      />
      <SelectionDownloadBar
        targets={downloadTargets}
        roomId={room.roomId}
        roomCode={room.code}
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
            clearSelection();
            await Promise.all([
              queryClient.invalidateQueries({
                queryKey: photosQueryKey(room.roomId, userId),
                exact: true,
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
                queryKey: photosQueryKey(room.roomId, userId),
                exact: true,
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

      {isCreateFolderOpen && (
        <CreateFolderBottomSheet
          roomId={room.roomId}
          accessToken={accessToken}
          onClose={() => setIsCreateFolderOpen(false)}
          onSuccess={async (folder) => {
            setIsCreateFolderOpen(false);
            selectFolder(folder.id);
            clearSelection();
            await queryClient.invalidateQueries({
              queryKey: roomQueryKey(room.code, userId),
              exact: true,
            });
          }}
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
                queryKey: photosQueryKey(room.roomId, userId),
                exact: true,
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
    </Page>
  );
};
