import type { GalleryItem } from "@/entities/media";

export const isVideoItem = (item: GalleryItem): boolean =>
  item.type === "local" ? item.file.type.startsWith("video/") : item.media.type === "VIDEO";
