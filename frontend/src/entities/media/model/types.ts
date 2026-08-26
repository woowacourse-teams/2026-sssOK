export type PhotoFilter = "all" | "mine" | "others";

export interface MediaItem {
  mediaId: number;
  type: "IMAGE" | "VIDEO";
  fileName: string;
  mimeType: string;
  size: number;
  thumbnailUrl: string;
  originalUrl: string;
  width: number;
  height: number;
  duration: number | null;
  folderIds: number[];
  uploaderId: number;
  uploaderName: string;
  status: "READY";
  uploadedAt: string;
}

export interface MediaList {
  items: MediaItem[];
}
