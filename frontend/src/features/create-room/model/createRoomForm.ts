export interface CreateRoomFormValues {
  nickname: string;
  name: string;
  uploadPolicy: "everyone" | "host";
}

export const INITIAL_CREATE_ROOM_FORM: CreateRoomFormValues = {
  nickname: "",
  name: "",
  uploadPolicy: "everyone",
};
