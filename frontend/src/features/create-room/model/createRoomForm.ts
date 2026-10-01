export const MIN_EXPIRY_DAYS = 1;
// 백엔드 RoomExpiration.MAX_DAYS 와 같이 바꾼다. 눈금은 이 값에서 자동으로 만들어진다.
export const MAX_EXPIRY_DAYS = 14;

export interface CreateRoomFormValues {
  nickname: string;
  name: string;
  uploadPolicy: "everyone" | "host";
  expiryDays: number;
}

export const INITIAL_CREATE_ROOM_FORM: CreateRoomFormValues = {
  nickname: "",
  name: "",
  uploadPolicy: "everyone",
  expiryDays: MIN_EXPIRY_DAYS,
};
