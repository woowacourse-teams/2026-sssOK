import { FolderEmptyDescription, FolderEmptyState, FolderEmptyTitle } from "./GalleryPage.styles";

export const EMPTY_ROOM_TITLE = "아직 올라온 사진이 없어요";

interface EmptyRoomGuideProps {
  isHost: boolean;
  /** 방장만 올리는 방의 게스트면 거짓이다 (`canUploadTo`). */
  canUpload: boolean;
}

const descriptionOf = (isHost: boolean, canUpload: boolean): string => {
  if (isHost)
    return "오른쪽 위 링크 버튼으로 링크나 QR을 보내면\n다 같이 각자 찍은 사진이 여기에 모여요";
  if (canUpload)
    return "아래 올리기 버튼으로 내 사진을 올려 주세요.\n다른 사람이 올린 사진도 여기에 모여요";
  return "방장이 사진을 올리면 여기에 보여요";
};

/**
 * 사진이 한 장도 없는 방의 첫 화면. 방을 막 만든 방장과, 아직 아무도 올리지 않은 방에
 * 들어온 게스트가 다음에 할 일을 알 수 있게 한다.
 *
 * 버튼은 따로 두지 않는다. 링크·QR 공유는 헤더의 공유 메뉴가, 올리기는 아래에 떠 있는
 * 버튼이 이미 맡고 있어서 같은 일을 하는 버튼이 두 개가 되지 않게 그 자리를 가리키기만 한다.
 */
export const EmptyRoomGuide = ({ isHost, canUpload }: EmptyRoomGuideProps) => (
  // 빈 폴더 안내와 같은 모양으로 둔다. 둘 다 "아직 비어 있다" 는 같은 상태다.
  <FolderEmptyState>
    <FolderEmptyTitle>{EMPTY_ROOM_TITLE}</FolderEmptyTitle>
    <FolderEmptyDescription>{descriptionOf(isHost, canUpload)}</FolderEmptyDescription>
  </FolderEmptyState>
);
