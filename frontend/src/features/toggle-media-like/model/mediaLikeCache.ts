import type { MediaList } from "@/entities/media";

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
