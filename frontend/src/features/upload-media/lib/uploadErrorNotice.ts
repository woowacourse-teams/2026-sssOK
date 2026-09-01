import { isApiError } from "@/shared/api";

/**
 * 배치 전체가 못 올라갔을 때(#148) 무엇을 할지.
 *
 * 문구와 "이 방에 더 있을 수 있는가" 를 한 곳에서 함께 정한다. 같은 응답 하나를 보고
 * 갈리는 두 판단이라, 떨어뜨려 두면 어긋난다 (`downloadErrorMessage` 와 같은 이유다).
 */
export interface UploadErrorNotice {
  message: string;
  /**
   * 방이 사라졌거나 세션이 죽었다. 이 화면에 남아 있을 이유가 없어서, 알리는 대신
   * 입장 화면으로 되돌린다 — 거기서 "만료된 방이에요" 까지 말해준다.
   */
  leavesRoom: boolean;
}

/**
 * 사유를 특정할 수 없을 때. 모르는 코드가 여기로 온다 (#148 완료 조건의 "최소" 문구다).
 *
 * 서버 문장으로 대신하지 않는다 — 개발자용 서식이 그대로 실려 있어서
 * ("존재하지 않는 폴더입니다: 31") 사용자에게 내부 번호가 새어나간다.
 */
export const UPLOAD_FALLBACK_MESSAGE = "사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요.";

/**
 * 사유를 아는 것만 우리 말로 적는다 (`GalleryPage` · `RoomEntryPage` 와 같은 방식이다).
 *
 * **사용자가 할 수 있는 일이 다르면 문구도 달라야 한다.** 폴더가 사라진 건 다시 골라
 * 올리면 되고, 방장 전용 방은 몇 번을 눌러도 같다 — 한 문장으로 뭉치면 그 차이가 사라진다.
 *
 * 여기 없는 코드는 `UPLOAD_FALLBACK_MESSAGE` 로 떨어진다. 새 코드가 생겨도 서버 말투가
 * 화면에 새지 않는다.
 */
const MESSAGE_BY_CODE: Record<string, string> = {
  /** 회선이 끊겨 요청 자체가 못 나갔다. 모바일에서 제일 흔하다 */
  NETWORK_ERROR: "네트워크가 끊겼어요. 연결을 확인하고 다시 올려 주세요.",
  /** 방장만 올리는 방. 방을 불러온 뒤에 정책이 바뀌면 버튼을 숨겨도 여기로 온다 */
  UPLOAD_NOT_ALLOWED: "방장만 사진을 올릴 수 있어요.",
  NOT_ROOM_MEMBER: "입장하지 않은 방이에요.",
  /** 남이 예약한 mediaId 가 섞였다. 사용자가 만들 수 있는 상황이 아니다 */
  MEDIA_FORBIDDEN: "사진을 올리지 못했어요. 처음부터 다시 올려 주세요.",
  /** 열어둔 폴더가 그 사이에 지워졌다. 폴더만 다시 고르면 된다 */
  FOLDER_NOT_FOUND: "열어둔 폴더가 사라졌어요. 폴더를 다시 골라 주세요.",
};

export const uploadErrorNoticeOf = (error: unknown): UploadErrorNotice => {
  if (!isApiError(error)) {
    return { message: UPLOAD_FALLBACK_MESSAGE, leavesRoom: false };
  }

  return {
    message: MESSAGE_BY_CODE[error.code] ?? UPLOAD_FALLBACK_MESSAGE,
    leavesRoom: leavesRoom(error.status, error.code),
  };
};

/**
 * 404 만 코드까지 본다. 방이 없는 것(`ROOM_NOT_FOUND`)과 열어둔 폴더가 지워진 것
 * (`FOLDER_NOT_FOUND`)이 같은 404 인데, 앞은 나가야 하고 뒤는 폴더만 다시 고르면 된다.
 *
 * 나가는 쪽은 문구가 필요 없다 — 토스트를 띄워봐야 화면이 바뀌며 같이 사라지고,
 * 무슨 일인지는 입장 화면이 "만료된 방이에요" 로 말해준다.
 */
const leavesRoom = (status: number, code: string) =>
  status === 410 || status === 401 || (status === 404 && code === "ROOM_NOT_FOUND");
