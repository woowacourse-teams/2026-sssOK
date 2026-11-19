import type { Room } from "@/entities/room";
import { SearchRequestField } from "@/features/search-media";
import { FolderFilter } from "@/widgets/folder-filter";
import { GalleryOptions } from "@/widgets/gallery-options";
import { PhotoGallerySkeleton } from "@/widgets/photo-gallery";
import { RoomSummary } from "@/widgets/room-summary";
import { Preview } from "./RoomEntryPreview.styles";

const noop = (): void => {};

interface RoomEntryPreviewProps {
  room: Room;
}

/**
 * 이름 입력 시트 뒤에 까는 갤러리. "이름만 정하면 이 갤러리로 들어간다" 를 보여 주려고
 * 실제 갤러리와 같은 헤더·폴더 줄·정렬 줄을 그대로 그린다.
 *
 * 보여 주기만 하는 화면이라 통째로 `inert` 로 막는다. 누를 수도, 키보드로 갈 수도,
 * 스크린 리더가 읽을 수도 없다 — 시트가 닫히기 전에 할 수 있는 일은 이름 입력뿐이다.
 *
 * 입장 전에는 사진을 볼 권한이 없어서 사진 자리에는 로딩 타일만 깐다. 닉네임 배지는
 * 아직 이름이 없어서, 아래 올리기·피드백 버튼은 시트에 가려지는 자리라 그리지 않는다.
 */
export const RoomEntryPreview = ({ room }: RoomEntryPreviewProps) => (
  <Preview inert aria-hidden data-testid="room-entry-preview">
    <RoomSummary
      roomCode={room.code}
      hostId={room.hostId}
      roomName={room.name}
      onExit={noop}
      onOrganize={noop}
      searchField={
        <SearchRequestField
          appliedText={null}
          uploaders={[]}
          onSearchKeyword={noop}
          onSearchUploader={noop}
          onClear={noop}
        />
      }
    />
    <FolderFilter
      totalCount={room.photoCount}
      folders={room.folders}
      selectedFolderId={null}
      onSelectFolder={noop}
      onAddFolder={noop}
    />
    <GalleryOptions
      selectedOption="all"
      onSelectOption={noop}
      selectedSort="upload"
      onSelectSort={noop}
      isAllSelected={false}
      canSelectAll={room.photoCount > 0}
      onToggleAll={noop}
    />
    {room.photoCount > 0 && <PhotoGallerySkeleton count={room.photoCount} />}
  </Preview>
);
