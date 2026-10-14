import type { MediaList } from "@/entities/media";
import type { MediaLikeResponse } from "../api/types";

export const setMyMediaLike = (
  current: MediaList | undefined,
  mediaId: number,
  likedByMe: boolean,
) => {
  if (!current) return current;

  return {
    ...current,
    items: current.items.map((media) => {
      if (media.mediaId !== mediaId || media.likedByMe === likedByMe) return media;

      return {
        ...media,
        likedByMe,
        likeCount: Math.max(0, media.likeCount + (likedByMe ? 1 : -1)),
      };
    }),
  };
};

export const setMediaLikeResponse = (
  current: MediaList | undefined,
  response: MediaLikeResponse,
) => {
  if (!current) return current;

  return {
    ...current,
    items: current.items.map((media) =>
      media.mediaId === response.mediaId
        ? { ...media, likedByMe: response.liked, likeCount: response.likeCount }
        : media,
    ),
  };
};
