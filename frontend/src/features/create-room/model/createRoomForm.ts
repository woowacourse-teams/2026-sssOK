export interface CreateRoomFormValues {
  nickname: string;
  name: string;
  uploadPolicy: "everyone" | "host";
  expiryHours: "24" | "72";
}

export const INITIAL_CREATE_ROOM_FORM: CreateRoomFormValues = {
  nickname: "",
  name: "",
  uploadPolicy: "everyone",
  expiryHours: "24",
};
