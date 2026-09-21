import { apiClient } from "@/shared/api";
import type { MediaList, MediaUploaderFilter } from "../model/types";

interface GetPhotosParams {
  roomId: number;
  token: string;
  /** 직전 응답의 `nextCursor`. 없으면 최신부터. */
  cursor?: string;
  /** 한 번에 가져올 개수. 서버 기본 30, 최대 100. */
  size?: number;
  /** 이 폴더에 담긴 것만 받는다. 없으면 방 전체. */
  folderId?: number;
  /** 없으면 서버가 ALL 로 본다. */
  uploader?: MediaUploaderFilter;
}

export const getPhotos = ({ roomId, token, cursor, size, folderId, uploader }: GetPhotosParams) => {
  const query = new URLSearchParams();

  // 안 보낸 것과 "빈 값을 보낸 것" 은 다르다 — 빈 값은 서버가 400 으로 돌려준다.
  if (cursor !== undefined) query.set("cursor", cursor);
  if (size !== undefined) query.set("size", String(size));
  if (folderId !== undefined) query.set("folderId", String(folderId));
  if (uploader !== undefined) query.set("uploader", uploader);

  const search = query.toString();

  return apiClient<MediaList>(`/rooms/${roomId}/media${search ? `?${search}` : ""}`, { token });
};
