import { useParams } from "react-router-dom";

export const RoomEntryPage = () => {
  const { code } = useParams<{ code: string }>();

  return <main>{code} 방으로 들어가는 중이에요.</main>;
};
