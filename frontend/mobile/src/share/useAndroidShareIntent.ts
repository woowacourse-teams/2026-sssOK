import { useEffect, useRef } from "react";
import { Platform } from "react-native";
import { useShareIntent } from "expo-share-intent";

import { replaceInbox } from "./inbox";

const isMedia = (mimeType: string): boolean =>
  mimeType.startsWith("image/") || mimeType.startsWith("video/");

export function useAndroidShareIntent(onImported: () => void): void {
  const { hasShareIntent, shareIntent, resetShareIntent } = useShareIntent({
    disabled: Platform.OS !== "android",
    resetOnBackground: false,
  });
  const latest = useRef({ onImported, resetShareIntent });

  useEffect(() => {
    latest.current = { onImported, resetShareIntent };
  });

  useEffect(() => {
    if (!hasShareIntent) return;

    const files = (shareIntent.files ?? [])
      .filter((file) => isMedia(file.mimeType))
      .map((file) => ({ uri: file.path, fileName: file.fileName, mimeType: file.mimeType }));

    if (files.length === 0) {
      latest.current.resetShareIntent();
      return;
    }

    void replaceInbox(files)
      .catch(() => {})
      .finally(() => {
        latest.current.resetShareIntent();
        latest.current.onImported();
      });
  }, [hasShareIntent, shareIntent]);
}
