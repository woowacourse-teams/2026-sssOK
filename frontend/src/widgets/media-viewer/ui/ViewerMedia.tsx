import { useEffect, useState } from "react";
import type { GalleryItem } from "@/entities/media";
import { isVideoItem } from "../lib/isVideoItem";
import { ViewerPhoto, ViewerVideoPlayer } from "./ViewerMedia.styles";

export const ViewerMedia = ({ item, active = true }: { item: GalleryItem; active?: boolean }) => {
  if (!isVideoItem(item)) return <ViewerImage item={item} />;
  if (active) return <ViewerVideo item={item} />;
  return (
    <ViewerPhoto
      src={item.type === "server" ? (item.media.thumbnailUrl ?? undefined) : undefined}
      alt=""
      $visible
    />
  );
};

const ViewerImage = ({ item }: { item: GalleryItem }) => {
  const [localUrl] = useState(() =>
    item.type === "local" ? URL.createObjectURL(item.file) : null,
  );
  const [serverUrl, setServerUrl] = useState(() =>
    item.type === "local" ? "" : (item.media.thumbnailUrl ?? item.media.displayUrl ?? ""),
  );
  const [isVisible, setIsVisible] = useState(true);
  const fileName = item.type === "local" ? item.file.name : item.media.fileName;

  useEffect(() => {
    return () => {
      if (localUrl !== null) URL.revokeObjectURL(localUrl);
    };
  }, [localUrl]);

  useEffect(() => {
    if (item.type !== "server") return;

    const displayUrl = item.media.displayUrl;
    if (displayUrl === null) return;

    const display = new window.Image();
    display.onload = () => {
      setServerUrl(displayUrl);
      setIsVisible(true);
    };
    display.src = displayUrl;

    return () => {
      display.onload = null;
    };
  }, [item]);

  return (
    <ViewerPhoto
      src={(localUrl ?? serverUrl) || undefined}
      alt={fileName}
      draggable={false}
      $visible={isVisible}
      onLoad={() => setIsVisible(true)}
      onError={() => setIsVisible(false)}
    />
  );
};

const ViewerVideo = ({ item }: { item: GalleryItem }) => {
  const [localUrl] = useState(() =>
    item.type === "local" ? URL.createObjectURL(item.file) : null,
  );
  const source = item.type === "local" ? localUrl : item.media.displayUrl;
  const poster = item.type === "server" ? (item.media.thumbnailUrl ?? undefined) : undefined;

  useEffect(() => {
    return () => {
      if (localUrl !== null) URL.revokeObjectURL(localUrl);
    };
  }, [localUrl]);

  return (
    <ViewerVideoPlayer
      src={source ?? undefined}
      poster={poster}
      controls
      controlsList="nodownload noremoteplayback nofullscreen noplaybackrate"
      disablePictureInPicture
      disableRemotePlayback
      playsInline
    />
  );
};
