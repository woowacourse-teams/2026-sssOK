import { ApiError } from "@/shared/api";
import { UPLOAD_FALLBACK_MESSAGE, uploadErrorNoticeOf } from "./uploadErrorNotice";

describe("uploadErrorNoticeOf", () => {
  /** 사용자가 할 수 있는 일이 다르면 문구도 달라야 한다. */
  it("아는 사유는 사유마다 다른 말로 알린다", () => {
    const messageOf = (status: number, code: string) =>
      uploadErrorNoticeOf(new ApiError(status, code, "서버 문장")).message;

    expect(messageOf(403, "UPLOAD_NOT_ALLOWED")).toBe("방장만 사진을 올릴 수 있어요.");
    expect(messageOf(403, "NOT_ROOM_MEMBER")).toBe("입장하지 않은 방이에요.");
    expect(messageOf(404, "FOLDER_NOT_FOUND")).toBe(
      "열어둔 폴더가 사라졌어요. 폴더를 다시 골라 주세요.",
    );
    expect(messageOf(0, "NETWORK_ERROR")).toBe(
      "네트워크가 끊겼어요. 연결을 확인하고 다시 올려 주세요.",
    );
  });

  /**
   * 서버 문장에는 개발자용 서식이 그대로 실려 있다. 흘려보내면 내부 번호가 화면에 새어나간다.
   */
  it("서버 문장을 그대로 흘리지 않는다", () => {
    const notice = uploadErrorNoticeOf(
      new ApiError(404, "FOLDER_NOT_FOUND", "존재하지 않는 폴더입니다: 31"),
    );

    expect(notice.message).not.toContain("31");
    expect(notice.message).not.toContain("습니다");
  });

  /** 새 코드가 생겨도 서버 말투가 화면에 새지 않는다. */
  it("모르는 코드는 최소 문구로 떨어뜨린다", () => {
    const unknown = [
      new ApiError(400, "INVALID_PARAM", "업로드할 파일이 없습니다"),
      new ApiError(500, "INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다"),
      new ApiError(409, "SOME_NEW_CODE", "%s 를 처리할 수 없습니다"),
    ];

    expect(unknown.map((error) => uploadErrorNoticeOf(error).message)).toEqual([
      UPLOAD_FALLBACK_MESSAGE,
      UPLOAD_FALLBACK_MESSAGE,
      UPLOAD_FALLBACK_MESSAGE,
    ]);
  });

  it("상태 코드를 못 읽는 실패도 같은 문구로 알린다", () => {
    expect(uploadErrorNoticeOf(new TypeError("undefined is not a function"))).toEqual({
      message: UPLOAD_FALLBACK_MESSAGE,
      leavesRoom: false,
    });
  });

  it("방이 사라졌거나 세션이 죽으면 방을 떠난다", () => {
    const gone = [
      new ApiError(410, "ROOM_EXPIRED", "이미 사라진 방입니다"),
      new ApiError(410, "ROOM_ALREADY_DELETED", "이미 삭제되었거나 만료된 방입니다"),
      new ApiError(401, "UNAUTHORIZED", "다시 접속해주세요"),
      new ApiError(404, "ROOM_NOT_FOUND", "존재하지 않는 방입니다"),
    ];

    expect(gone.map((error) => uploadErrorNoticeOf(error).leavesRoom)).toEqual([
      true,
      true,
      true,
      true,
    ]);
  });

  /** 열어둔 폴더가 지워진 것뿐이다. 폴더만 다시 고르면 되므로 방에서 내보내지 않는다. */
  it("같은 404 라도 폴더가 없는 것이면 방에 남는다", () => {
    const notice = uploadErrorNoticeOf(
      new ApiError(404, "FOLDER_NOT_FOUND", "존재하지 않는 폴더입니다: 31"),
    );

    expect(notice.leavesRoom).toBe(false);
  });

  it("회선이 끊긴 것도 방에 남아 다시 시도할 수 있다", () => {
    expect(
      uploadErrorNoticeOf(new ApiError(0, "NETWORK_ERROR", "네트워크 연결을 확인해주세요."))
        .leavesRoom,
    ).toBe(false);
  });
});
