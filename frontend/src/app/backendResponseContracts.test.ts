import { RoomSchema } from "@/entities/room/model/types";
import { MediaDetailSchema } from "@/entities/media/model/types";
import { CreateRoomResponseSchema } from "@/features/create-room/api/types";
import { DeleteRoomResponseSchema } from "@/features/delete-room/api/deleteRoom";
import { UpdateRoomResponseSchema } from "@/features/update-room/api/types";
import { RejectedFileSchema, FailedRegistrationSchema } from "@/features/upload-media/api/types";

// MSW 데이터가 아니라 backend RoomResponse/MediaDetailResponse DTO의 직렬화 형태를 기준으로 한다.
const roomResponse = {
  roomId: 5031,
  code: "7K93QX2S",
  name: "제주 여행",
  status: "ACTIVE",
  hostId: 10234,
  hostName: "민수",
  uploadPolicy: "everyone",
  joined: true,
  createdAt: "2026-11-12T00:00:00Z",
  photoCount: 0,
  folders: [],
  myNickname: "민수",
};

test.each([
  ["방 생성", CreateRoomResponseSchema],
  ["방 조회", RoomSchema],
  ["방 수정", UpdateRoomResponseSchema],
])("%s은 만료 필드 없는 실제 RoomResponse를 허용한다", (_, schema) => {
  expect(schema.safeParse(roomResponse).success).toBe(true);
});

test("방 삭제는 deletedAt만 반환하는 DeleteRoomResponse를 허용한다", () => {
  expect(DeleteRoomResponseSchema.parse({ deletedAt: "2026-11-12T00:00:00Z" })).toEqual({
    deletedAt: "2026-11-12T00:00:00Z",
  });
});

test("위치 좌표가 있어도 지명은 null인 실제 사진 상세 응답을 허용한다", () => {
  expect(
    MediaDetailSchema.safeParse({
      mediaId: 5012,
      type: "IMAGE",
      fileName: "photo.jpg",
      mimeType: "image/jpeg",
      size: 100,
      thumbnailUrl: null,
      thumbnailUrlExpiresAt: null,
      displayUrl: "https://example.com/photo.jpg",
      displayUrlExpiresAt: null,
      width: 1200,
      height: 800,
      duration: null,
      folderIds: [],
      uploaderId: 10234,
      uploaderName: "민수",
      status: "READY",
      uploadedAt: "2026-11-12T00:00:00Z",
      likeCount: 0,
      likedByMe: false,
      takenAt: null,
      location: { latitude: 35.1587, longitude: 129.1604, name: null },
      canDelete: true,
    }).success,
  ).toBe(true);
});

test.each(["FILE_TOO_LARGE", "UNSUPPORTED_MEDIA_TYPE"])(
  "업로드 발급·등록에서 서버 거절 코드 %s를 허용한다",
  (code) => {
    expect(
      RejectedFileSchema.safeParse({ fileName: "photo.jpg", code, message: "업로드 거절" }).success,
    ).toBe(true);
    expect(
      FailedRegistrationSchema.safeParse({ mediaId: 5012, code, message: "등록 거절" }).success,
    ).toBe(true);
  },
);

test("필수 식별자나 필드 타입이 잘못된 응답은 계속 거절한다", () => {
  expect(CreateRoomResponseSchema.safeParse({ ...roomResponse, roomId: "5031" }).success).toBe(
    false,
  );
  expect(DeleteRoomResponseSchema.safeParse({}).success).toBe(false);
  expect(
    FailedRegistrationSchema.safeParse({ mediaId: 5012, code: "UNKNOWN", message: "오류" }).success,
  ).toBe(false);
});
