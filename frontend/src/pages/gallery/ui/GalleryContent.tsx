import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { photosQueryKey, type MediaList } from "@/entities/media";
import { useMemberSessionQuery } from "@/entities/member-session";
import { canUploadTo, myRoomsQueryKey, roomQueryKey, type Room } from "@/entities/room";
import { removeRoomSession } from "@/entities/session";
import { FeedbackBottomSheet, FeedbackButton } from "@/features/create-feedback";
import { DeleteRoomModal } from "@/features/delete-room";
import { PhotoRequestBanner } from "@/features/app-push/PhotoRequestBanner";
import { getNotificationSetting } from "@/features/room-notifications/api";
import { RoomNotificationMenu } from "@/features/room-notifications";
import { DeleteSelectedMediaModal } from "@/features/delete-media";
import { DeleteFolderModal } from "@/features/delete-folder";
import { EditFolderBottomSheet } from "@/features/edit-folder";
import { SelectionDownloadBar } from "@/features/download-media";
import { MoveMediaFolderBottomSheet } from "@/features/move-media-folder";
import {
  countAnalyzablePhotos,
  OrganizeCancelSheet,
  OrganizeKindSheet,
  type OrganizeLimit,
  OrganizeLimitModal,
  organizeLimitOf,
  OrganizeStatusBar,
  PersonReviewScreen,
  useOrganizeJob,
} from "@/features/organize-media";
import { SearchRequestField } from "@/features/search-media";
import { useRoomEvents } from "@/features/subscribe-room-events";
import { MediaUploader } from "@/features/upload-media";
import { ROUTES } from "@/shared/config";
import { isAppWebView, rememberLoginReturn, track } from "@/shared/lib";
import { useToast } from "@/shared/ui/toast";
import { FolderFilter } from "@/widgets/folder-filter";
import { GalleryOptions } from "@/widgets/gallery-options";
import { MediaViewerModal } from "@/widgets/media-viewer";
import { PhotoGallery } from "@/widgets/photo-gallery";
import { RoomSummary } from "@/widgets/room-summary";
import { useAnalyticsRoom } from "../model/useAnalyticsRoom";
import { useCreateFolderAction } from "../model/useCreateFolderAction";
import { useGalleryFilter } from "../model/useGalleryFilter";
import { useGalleryPhotos } from "../model/useGalleryPhotos";
import { useGalleryRefresh } from "../model/useGalleryRefresh";
import { useGallerySearch } from "../model/useGallerySearch";
import { usePendingMedia } from "../model/usePendingMedia";
import { usePhotoSelection } from "../model/usePhotoSelection";
import { GalleryModalHost } from "./GalleryModalHost";
import {
  FolderEmptyDescription,
  FolderEmptyState,
  FolderEmptyTitle,
  Page,
} from "./GalleryPage.styles";

interface GalleryContentProps {
  room: Room;
  accessToken: string;
  userId: number;
  nickname: string;
}

export const GalleryContent = ({ room, accessToken, userId, nickname }: GalleryContentProps) => {
  const [receivedRequest, setReceivedRequest] = useState<{ title: string; body: string } | null>(
    null,
  );
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  const notificationSetting = useQuery({
    queryKey: ["room-notification-setting", room.roomId, userId],
    queryFn: () => getNotificationSetting(room.roomId, accessToken),
  });
  useEffect(() => {
    if (!receivedRequest) return;
    const timer = window.setTimeout(() => setReceivedRequest(null), 3000);
    return () => window.clearTimeout(timer);
  }, [receivedRequest]);
  const navigate = useNavigate();
  const memberSession = useMemberSessionQuery();
  const queryClient = useQueryClient();
  const { refreshRoom, refreshPhotos } = useGalleryRefresh({
    roomCode: room.code,
    roomId: room.roomId,
    userId,
  });
  useAnalyticsRoom(room.code, userId === room.hostId);
  const {
    uploadSlots,
    addPendingMedia,
    addProcessingMedia,
    removePendingMedia,
    addPendingMediaToFolder,
    removePendingMediaFromFolder,
  } = usePendingMedia();
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
  const { showToast } = useToast();
  const [isOrganizeSheetOpen, setIsOrganizeSheetOpen] = useState(false);
  const [organizeLimit, setOrganizeLimit] = useState<OrganizeLimit | null>(null);
  const [isOrganizeCancelOpen, setIsOrganizeCancelOpen] = useState(false);
  const [isOrganizeResultCloseOpen, setIsOrganizeResultCloseOpen] = useState(false);
  const [isPersonReviewOpen, setIsPersonReviewOpen] = useState(false);

  // 옵션 선택
  const { selectedFolderId, selectedOption, selectedSort, selectFolder, selectOption, selectSort } =
    useGalleryFilter();
  const selectedFolder = room.folders.find((folder) => folder.id === selectedFolderId) ?? null;

  // 사진 조회
  const {
    galleryItems: filteredItems,
    allGalleryItems,
    completedUploadIds,
    isProcessingPhotos,
    totalCount,
    folderCounts,
    isPending,
    isError,
  } = useGalleryPhotos({
    roomId: room.roomId,
    accessToken,
    userId,
    selectedFolderId,
    selectedOption,
    selectedSort,
    initialTotalCount: room.photoCount,
    initialFolders: room.folders,
    uploadSlots,
  });

  const search = useGallerySearch({
    roomId: room.roomId,
    accessToken,
    userId,
    items: filteredItems,
    allItems: allGalleryItems,
  });
  const galleryItems = search.items;

  const emptyGalleryViewedRoom = useRef<string | null>(null);
  const isEmptyGalleryVisible =
    !isPending &&
    !isError &&
    !search.isPending &&
    !search.isError &&
    !search.isSearching &&
    selectedFolderId === null &&
    selectedOption === "all" &&
    totalCount === 0 &&
    galleryItems.length === 0 &&
    activeMediaId === null;

  useEffect(() => {
    if (!isEmptyGalleryVisible || emptyGalleryViewedRoom.current === room.code) return;

    emptyGalleryViewedRoom.current = room.code;
    track(
      "Empty Gallery Viewed",
      {},
      { room_code: room.code, role: userId === room.hostId ? "host" : "guest" },
    );
  }, [isEmptyGalleryVisible, room.code, room.hostId, userId]);

  useEffect(() => {
    if (completedUploadIds.length === 0) return;
    removePendingMedia(completedUploadIds);
  }, [completedUploadIds, removePendingMedia]);

  // 사진 정리
  const organize = useOrganizeJob({ roomId: room.roomId, token: accessToken });
  // 시트를 열어 둔 채 작업이 끝나면 닫는다. 다음 작업에서 누르지 않은 시트가 저절로 뜨지 않게 한다.
  if (isOrganizeCancelOpen && !organize.isRunning) setIsOrganizeCancelOpen(false);
  if (isOrganizeResultCloseOpen && organize.notice?.status !== "READY") {
    setIsOrganizeResultCloseOpen(false);
  }
  /** 사진 처리가 다 끝나야 정리할 수 있다. 진행 중인 정리 같은 다른 사유가 있으면 그쪽을 먼저 보인다. */
  const organizeBlockedReason =
    organize.blockedReason ?? (isProcessingPhotos ? "사진을 처리하고 있어요" : null);
  /**
   * 정리를 시작하기 전에 장수 한도를 먼저 본다. 시트에서 방식까지 고른 뒤에 막히면 헛걸음이라서다.
   * 사진 목록을 아직 못 읽었으면 세지 않고 서버 판단에 맡긴다(거절되면 같은 모달이 뜬다).
   */
  const beginOrganize = (open: () => void) => {
    const limit =
      isPending || isError ? null : organizeLimitOf(countAnalyzablePhotos(allGalleryItems));
    if (limit) setOrganizeLimit(limit);
    else open();
  };

  /**
   * 검토 화면의 이름 칩. 방 멤버 목록 API 가 없어서 사진을 올린 사람 이름으로 대신한다.
   * 내 이름은 "나" 칩으로 따로 보인다.
   */
  const myName = room.myNickname ?? nickname;
  const reviewMemberNames = [
    ...new Set(
      allGalleryItems.flatMap((item) => (item.type === "server" ? [item.media.uploaderName] : [])),
    ),
  ].filter((name) => name.trim() !== "" && name !== myName);

  // 결과가 취소·만료돼 검토할 것이 없어지면 검토 화면도 닫는다. 다음 결과에서 저절로 열리지 않게 한다.
  const hasReview = organize.review !== null;
  if (isPersonReviewOpen && !hasReview) setIsPersonReviewOpen(false);

  const folders = room.folders.map((folder) => ({
    ...folder,
    photoCount: folderCounts.get(folder.id) ?? 0,
  }));

  // 사진 선택
  const viewerItems = galleryItems;
  const photoIds = galleryItems.map((item) => item.mediaId);
  const {
    selectedPhotoIds,
    isAllSelected,
    togglePhoto,
    removePhotos,
    toggleAllPhotos,
    clearSelection,
  } = usePhotoSelection(photoIds);

  const isOrganizeBarVisible = organize.notice !== null && selectedPhotoIds.length === 0;

  useRoomEvents({
    roomId: room.roomId,
    roomCode: room.code,
    userId,
    token: accessToken,
    onUploadRequested: (event) => {
      if (document.visibilityState === "visible" && notificationSetting.data?.enabled === true) {
        setReceivedRequest({ title: event.title, body: event.body });
      }
    },
    onMediaCreated: addProcessingMedia,
    onMediaDeleted: (mediaIds) => {
      removePendingMedia(mediaIds);
      removePhotos(mediaIds);
      if (activeMediaId !== null && mediaIds.includes(activeMediaId)) {
        setActiveMediaId(null);
      }
      void refreshRoom();
    },
    onFoldersChanged: () => {
      void refreshRoom();
    },
    onFolderDeleted: (folderId) => {
      if (selectedFolderId === folderId) selectFolder(null);
    },
    onMediaFoldersUpdated: () => {
      void Promise.all([refreshRoom(), refreshPhotos()]);
    },
    onOrganizeRoomChanged: organize.applyRoomChange,
    onOrganizeJobUpdated: organize.applyJobUpdate,
    onReconnected: () => void organize.resync(),
  });

  // 폴더 생성 흐름
  const { handleCreateFolder, requestCreateFolder } = useCreateFolderAction({
    roomCode: room.code,
    userId,
    selectFolder,
    clearSelection,
  });

  // 고른 순서가 아니라 **화면에 보이는 순서**로 넘긴다. 압축을 풀었을 때 파일이
  // 갤러리와 같은 차례로 놓여야, 고른 순서를 기억하지 못하는 사용자가 헤매지 않는다.
  const downloadTargets = galleryItems
    .filter((item) => selectedPhotoIds.includes(item.mediaId))
    .map((slot) => ({
      mediaId: slot.mediaId,
      fileName: slot.type === "local" ? slot.file.name : slot.media.fileName,
      size: slot.type === "local" ? slot.file.size : slot.media.size,
      mimeType: slot.type === "local" ? slot.file.type : slot.media.mimeType,
    }));

  const { error: organizeError, clearError: clearOrganizeError } = organize;
  useEffect(() => {
    if (!organizeError) return;
    showToast({
      message: organizeError,
      tone: "error",
    });
    clearOrganizeError();
  }, [organizeError, clearOrganizeError, showToast]);

  return (
    <Page>
      {receivedRequest && notificationSetting.data?.enabled && (
        <PhotoRequestBanner
          title={receivedRequest.title}
          message={receivedRequest.body}
          roomPath={ROUTES.gallery(room.code)}
          onClose={() => setReceivedRequest(null)}
        />
      )}
      <RoomSummary
        notificationMenu={(close) => (
          <RoomNotificationMenu
            roomId={room.roomId}
            userId={userId}
            accessToken={accessToken}
            onRequested={() => {
              close();
              showToast({ message: "사진 요청을 보냈어요.", tone: "success" });
            }}
            onRequestLimited={() => {
              close();
              showToast({
                message: "사진 요청은 1분에 한 번 보낼 수 있어요.",
                tone: "error",
              });
            }}
          />
        )}
        roomCode={room.code}
        hostId={room.hostId}
        roomName={room.name}
        session={{ userId, accessToken, nickname: room.myNickname ?? nickname }}
        exitLabel={memberSession.data ? "내 방 목록" : "홈으로"}
        onExit={() => navigate(memberSession.data ? ROUTES.rooms : ROUTES.home, { replace: true })}
        isHost={userId === room.hostId}
        hasSelectedFolder={selectedFolderId !== null}
        onOpenSettings={() => navigate(ROUTES.roomSettings(room.code))}
        onDeleteRoom={() => setIsDeleteModalOpen(true)}
        onAddFolder={() => void handleCreateFolder("menu")}
        onEditFolder={() => setIsEditFolderOpen(true)}
        onDeleteFolder={() => setIsDeleteFolderOpen(true)}
        searchField={
          <SearchRequestField
            appliedText={search.appliedText}
            folders={room.folders}
            uploaders={search.uploaders}
            myNickname={room.myNickname ?? nickname}
            onOpen={search.openSearch}
            onSearchKeyword={(query) => {
              selectFolder(null);
              search.searchBy("keyword", query);
              clearSelection();
            }}
            onSearchUploader={(uploader) => {
              selectFolder(null);
              search.searchByUploader(uploader);
              clearSelection();
            }}
            organizeBlockedReason={organizeBlockedReason}
            onOrganize={() => beginOrganize(() => setIsOrganizeSheetOpen(true))}
            onOrganizeText={(text) => beginOrganize(() => void organize.start({ text }))}
            onOpenFolder={(folderId) => {
              search.closeSearch();
              selectFolder(folderId);
              clearSelection();
            }}
            onClear={() => {
              search.closeSearch();
              clearSelection();
            }}
          />
        }
      />
      <FolderFilter
        totalCount={totalCount}
        folders={folders}
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
          if (option === selectedOption) return;

          track("Gallery Filter Changed", { filter: option });
          selectOption(option);
          clearSelection();
        }}
        selectedSort={selectedSort}
        onSelectSort={(sort) => {
          if (sort === selectedSort) return;

          track("Gallery Sort Changed", { sort });
          selectSort(sort);
          clearSelection();
        }}
        hideViewControls={search.isSearching}
        isAllSelected={isAllSelected}
        canSelectAll={photoIds.length > 0}
        onToggleAll={toggleAllPhotos}
      />
      <FeedbackButton
        hidden={selectedPhotoIds.length > 0 || isOrganizeBarVisible}
        onClick={() => setIsFeedbackOpen(true)}
      />
      <MediaUploader
        roomId={room.roomId}
        token={accessToken}
        // 방장만 올리는 방의 참여자에게는 버튼을 아예 내주지 않는다. 서버가 발급에서
        // 403 으로 막는 것을, 누르기 전에 화면이 먼저 말해주는 셈이다.
        canUpload={canUploadTo(room, userId)}
        hideButton={selectedPhotoIds.length > 0 || isOrganizeBarVisible}
        folderIds={selectedFolderId === null ? undefined : [selectedFolderId]}
        onPreviewReady={addPendingMedia}
        onRegistered={() => refreshRoom()}
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
        items={galleryItems}
        roomId={room.roomId}
        userId={userId}
        token={accessToken}
        selectedPhotoIds={selectedPhotoIds}
        isPending={isPending || search.isPending}
        isError={isError || search.isError}
        expectedPhotoCount={
          search.isSearching ? undefined : (selectedFolder?.photoCount ?? room.photoCount)
        }
        emptyMessage={
          search.isSearching ? (
            search.searchType === "nickname" ? (
              "이 닉네임으로 올린 사진이 없어요."
            ) : (
              "검색어에 맞는 사진이 없어요."
            )
          ) : selectedFolder?.photoCount === 0 ? (
            <FolderEmptyState>
              <FolderEmptyTitle>‘{selectedFolder.name}’ 폴더가 비어 있어요</FolderEmptyTitle>
              <FolderEmptyDescription>
                {canUploadTo(room, userId)
                  ? "전체에서 사진을 선택해 이 폴더로 옮기거나,\n여기서 올리면 이 폴더에 바로 담겨요."
                  : "전체에서 사진을 선택해 이 폴더로 옮겨 보세요."}
              </FolderEmptyDescription>
            </FolderEmptyState>
          ) : undefined
        }
        errorMessage={search.isError ? search.errorMessage : undefined}
        onTogglePhoto={togglePhoto}
        onOpenPhoto={(mediaId) => {
          search.trackResultOpened(mediaId);
          setActiveMediaId(mediaId);
        }}
      />
      {activeMediaId !== null && (
        <MediaViewerModal
          items={viewerItems}
          activeMediaId={activeMediaId}
          roomId={room.roomId}
          userId={userId}
          hostId={room.hostId}
          token={accessToken}
          selectedPhotoIds={selectedPhotoIds}
          onChange={setActiveMediaId}
          onClose={() => setActiveMediaId(null)}
          onToggle={togglePhoto}
          onDeleted={(mediaId) => {
            closeMediaViewerAfterDelete();
            removePendingMedia([mediaId]);
            removePhotos([mediaId]);
            queryClient.setQueryData<MediaList>(photosQueryKey(room.roomId, userId), (current) =>
              current
                ? { ...current, items: current.items.filter((item) => item.mediaId !== mediaId) }
                : current,
            );
            void refreshRoom();
          }}
        />
      )}
      <SelectionDownloadBar
        targets={downloadTargets}
        roomId={room.roomId}
        roomCode={room.code}
        roomPhotoCount={totalCount}
        isAllSelected={isAllSelected}
        token={accessToken}
        onClearSelection={clearSelection}
        onDeleteSelection={() => setIsDeleteSelectionOpen(true)}
        onMoveSelection={() => setIsMoveSelectionOpen(true)}
      />

      {isOrganizeBarVisible && organize.notice && (
        <OrganizeStatusBar
          job={organize.notice}
          onCancel={() => setIsOrganizeCancelOpen(true)}
          onRetry={organize.retry}
          // 찾은 사람이 있는 결과는 닫으면 버려지므로 한 번 더 묻는다. 실패·0명 알림은 버릴 것이 없어 바로 닫는다.
          onDismiss={() => {
            const notice = organize.notice;
            if (notice?.status === "READY" && notice.peopleCount !== 0) {
              setIsOrganizeResultCloseOpen(true);
              return;
            }
            organize.dismiss();
          }}
          // 알림은 그대로 둔다. 검토 화면을 닫으면 알림의 '확인하기' 로 다시 들어올 수 있다.
          // 검토 화면은 결과를 새로 받아 연다. 받는 동안은 화면 안에서 불러오는 중으로 보인다.
          onReview={() => {
            organize.reloadResult();
            setIsPersonReviewOpen(true);
          }}
        />
      )}

      {isPersonReviewOpen && organize.review && (
        <PersonReviewScreen
          source={organize.review}
          myName={myName}
          memberNames={reviewMemberNames}
          folderNames={room.folders.map((folder) => folder.name)}
          onClose={() => setIsPersonReviewOpen(false)}
          onRetry={organize.reloadResult}
          isSubmitting={organize.isApplying}
          submitError={organize.applyError}
          onDismissError={organize.clearApplyError}
          // 반영이 끝나면 폴더 목록을 다시 받아 처음 만든 폴더 탭을 연다. 실패하면 화면에 남는다.
          onSubmit={async (groups) => {
            const applied = await organize.apply(groups);
            if (!applied) return;
            setIsPersonReviewOpen(false);
            search.closeSearch();
            clearSelection();
            await Promise.all([refreshRoom(), refreshPhotos()]);
            const [first] = applied.folders;
            if (first) selectFolder(first.folderId);
            showToast({
              message: `폴더 ${applied.folders.length}개를 만들었어요`,
              showClose: false,
            });
          }}
        />
      )}

      {isOrganizeSheetOpen && (
        <OrganizeKindSheet
          isGuest={!memberSession.data}
          isStarting={organize.isStarting}
          onClose={() => setIsOrganizeSheetOpen(false)}
          onStart={(kind) => {
            setIsOrganizeSheetOpen(false);
            void organize.start({ kind });
          }}
        />
      )}

      {isOrganizeCancelOpen && organize.isRunning && (
        <OrganizeCancelSheet
          onKeepGoing={() => setIsOrganizeCancelOpen(false)}
          onStop={() => {
            setIsOrganizeCancelOpen(false);
            void organize.cancel();
          }}
        />
      )}

      {isOrganizeResultCloseOpen && organize.notice?.status === "READY" && (
        <OrganizeCancelSheet
          kind="result"
          onKeepGoing={() => setIsOrganizeResultCloseOpen(false)}
          onStop={() => {
            setIsOrganizeResultCloseOpen(false);
            organize.dismiss();
          }}
        />
      )}

      {(organizeLimit ?? organize.limit) && (
        <OrganizeLimitModal
          limit={(organizeLimit ?? organize.limit)!}
          onClose={() => {
            setOrganizeLimit(null);
            organize.clearLimit();
          }}
          onLogin={() => {
            organize.clearLimit();
            rememberLoginReturn(location.pathname);
            navigate(ROUTES.login);
          }}
        />
      )}

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
            await Promise.all([refreshPhotos(), refreshRoom()]);
          }}
        />
      )}

      {isMoveSelectionOpen && (
        <MoveMediaFolderBottomSheet
          roomId={room.roomId}
          mediaIds={selectedPhotoIds}
          folders={folders}
          currentFolderId={selectedFolderId}
          token={accessToken}
          onCreateFolder={requestCreateFolder}
          onClose={() => setIsMoveSelectionOpen(false)}
          onSuccess={async ({ action, folderId }) => {
            setIsMoveSelectionOpen(false);
            if (action === "add") addPendingMediaToFolder(selectedPhotoIds, folderId);
            else removePendingMediaFromFolder(selectedPhotoIds, folderId);
            selectFolder(folderId);
            clearSelection();
            await Promise.all([refreshPhotos(), refreshRoom()]);
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
            void queryClient.invalidateQueries({ queryKey: myRoomsQueryKey });
            navigate(isAppWebView() ? ROUTES.rooms : ROUTES.home, { replace: true });
          }}
        />
      )}

      <GalleryModalHost roomId={room.roomId} accessToken={accessToken} />

      {isFeedbackOpen && (
        <FeedbackBottomSheet
          roomId={room.roomId}
          accessToken={accessToken}
          onClose={() => setIsFeedbackOpen(false)}
          onSuccess={() => showToast({ message: "문의를 보냈어요." })}
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
            await refreshRoom();
          }}
        />
      )}

      {isDeleteFolderOpen && selectedFolder && (
        <DeleteFolderModal
          roomId={room.roomId}
          roomCode={room.code}
          userId={userId}
          folderId={selectedFolder.id}
          folderName={selectedFolder.name}
          accessToken={accessToken}
          onClose={() => setIsDeleteFolderOpen(false)}
          onDelete={() => {
            setIsDeleteFolderOpen(false);
            selectFolder(null);
            clearSelection();
            showToast({ message: `‘${selectedFolder.name}’ 폴더를 삭제했어요.` });
          }}
          onError={() => {
            showToast({
              message: `‘${selectedFolder.name}’ 폴더를 삭제하지 못했어요.`,
              tone: "error",
            });
          }}
        />
      )}
    </Page>
  );
};
