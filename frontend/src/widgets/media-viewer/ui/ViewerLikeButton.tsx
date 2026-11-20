import { HiHeart, HiOutlineHeart } from "react-icons/hi2";
import type { GalleryItem } from "@/entities/media";
import type { AnonymousSession } from "@/entities/session";
import { useMediaLike } from "@/features/toggle-media-like";
import { track } from "@/shared/lib";
import { LikeActionButton } from "./ViewerControls.styles";

export const ViewerLikeButton = ({
  item,
  roomId,
  session,
}: {
  item: Extract<GalleryItem, { type: "server" }>;
  roomId: number;
  session: AnonymousSession;
}) => {
  const mutation = useMediaLike({
    roomId,
    mediaId: item.mediaId,
    userId: session.userId,
    token: session.accessToken,
  });
  const likedByMe = item.media.likedByMe ?? false;

  return (
    <LikeActionButton
      type="button"
      $liked={likedByMe}
      aria-label={`좋아요 ${likedByMe ? "취소" : "추가"}`}
      aria-pressed={likedByMe}
      aria-busy={mutation.isPending}
      disabled={mutation.isPending}
      onClick={() =>
        mutation.mutate(!likedByMe, {
          onSuccess: (response) =>
            track("Photo Like Changed", {
              action: response.liked ? "like" : "unlike",
              source: "viewer",
            }),
        })
      }
    >
      {likedByMe ? <HiHeart size={21} /> : <HiOutlineHeart size={21} />}
      <span>{item.media.likeCount ?? 0}</span>
    </LikeActionButton>
  );
};
