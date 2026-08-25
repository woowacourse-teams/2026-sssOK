export interface CreateRoomFormValues {
  nickname: string;
  name: string;
  uploadPolicy: string;
  expiryHours: string;
}

export const INITIAL_CREATE_ROOM_FORM: CreateRoomFormValues = {
  nickname: "",
  name: "",
  uploadPolicy: "everyone",
  expiryHours: "24",
};
