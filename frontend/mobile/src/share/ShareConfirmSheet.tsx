import { useEffect, useMemo, useState } from "react";
import { FlatList, Image, Pressable, StyleSheet, useWindowDimensions, View } from "react-native";
import { close, openHostApp, Text, type InitialProps } from "expo-share-extension";
import { getThumbnailAsync } from "expo-video-thumbnails";

import { isVideo, keepOnlyInInbox, removeFromInbox } from "./inbox";

const COLUMNS = 3;
const GAP = 3;
const SIDE = 20;

const colors = {
  background: "#FFFFFF",
  textStrong: "#1A1A1A",
  textPrimary: "#4A4843",
  border: "#E4E2DD",
  tile: "#F4F2EE",
  primary: "#F66D3B",
  onPrimary: "#FFFFFF",
};

interface Picked {
  uri: string;
  video: boolean;
}

const objectParticleOf = (word: string): string => {
  const code = word.charCodeAt(word.length - 1) - 0xac00;

  return code >= 0 && code <= 11171 && code % 28 !== 0 ? "을" : "를";
};

export const pickedTitleOf = (photos: number, videos: number): string => {
  const parts = [photos > 0 ? `사진 ${photos}장` : null, videos > 0 ? `영상 ${videos}개` : null];
  const subject = parts.filter(Boolean).join(" · ");

  return `${subject}${objectParticleOf(subject)} 골랐어요`;
};

const pickedOf = ({ images = [], videos = [], files = [] }: InitialProps): Picked[] => [
  ...images.map((uri) => ({ uri, video: false })),
  ...videos.map((uri) => ({ uri, video: true })),
  ...files.map((uri) => ({ uri, video: isVideo(uri) })),
];

function VideoTile({ uri, size }: { uri: string; size: number }) {
  const [thumbnail, setThumbnail] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;

    getThumbnailAsync(uri, { time: 0, quality: 0.5 })
      .then((result) => alive && setThumbnail(result.uri))
      .catch(() => {});

    return () => {
      alive = false;
    };
  }, [uri]);

  return (
    <View style={[styles.tile, { width: size, height: size }]}>
      {thumbnail ? <Image source={{ uri: thumbnail }} style={StyleSheet.absoluteFill} /> : null}
      <View style={styles.videoBadge}>
        <Text allowFontScaling={false} style={styles.videoBadgeText}>
          ▶
        </Text>
      </View>
    </View>
  );
}

export function ShareConfirmSheet(props: InitialProps) {
  const picked = useMemo(() => pickedOf(props), [props]);
  const { width } = useWindowDimensions();
  const tileSize = Math.floor((width - SIDE * 2 - GAP * (COLUMNS - 1)) / COLUMNS);
  const videos = picked.filter((item) => item.video).length;

  useEffect(() => {
    keepOnlyInInbox(picked.map((item) => item.uri));
  }, [picked]);

  const handleCancel = (): void => {
    removeFromInbox(picked.map((item) => item.uri));
    close();
  };

  const handleContinue = (): void => openHostApp("share");

  return (
    <View style={styles.sheet}>
      <View style={styles.bar}>
        <Pressable accessibilityRole="button" hitSlop={12} onPress={handleCancel}>
          <Text allowFontScaling={false} style={styles.cancel}>
            취소
          </Text>
        </Pressable>
        <View style={styles.brand}>
          <Image source={require("../../assets/icon.png")} style={styles.brandIcon} />
          <Text allowFontScaling={false} style={styles.brandName}>
            쏙
          </Text>
        </View>
        <Text allowFontScaling={false} style={[styles.cancel, styles.hidden]}>
          취소
        </Text>
      </View>

      <Text allowFontScaling={false} style={styles.title}>
        {pickedTitleOf(picked.length - videos, videos)}
      </Text>

      <FlatList
        data={picked}
        keyExtractor={(item) => item.uri}
        numColumns={COLUMNS}
        style={styles.grid}
        columnWrapperStyle={styles.row}
        contentContainerStyle={styles.gridContent}
        renderItem={({ item }) =>
          item.video ? (
            <VideoTile uri={item.uri} size={tileSize} />
          ) : (
            <Image
              source={{ uri: item.uri, width: tileSize * 2, height: tileSize * 2 }}
              style={[styles.tile, { width: tileSize, height: tileSize }]}
              resizeMode="cover"
            />
          )
        }
      />

      <View style={styles.footer}>
        <Pressable
          accessibilityRole="button"
          disabled={picked.length === 0}
          onPress={handleContinue}
          style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}
        >
          <Text allowFontScaling={false} style={styles.buttonText}>
            쏙에서 이어서 올리기
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  sheet: { flex: 1, backgroundColor: colors.background },
  bar: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: SIDE,
    paddingTop: 16,
    paddingBottom: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  cancel: { fontSize: 17, lineHeight: 22, color: colors.textPrimary },
  hidden: { opacity: 0 },
  brand: { flexDirection: "row", alignItems: "center", gap: 6 },
  brandIcon: { width: 26, height: 26, borderRadius: 6 },
  brandName: { fontSize: 17, lineHeight: 22, fontWeight: "700", color: colors.textStrong },
  title: {
    paddingHorizontal: SIDE,
    paddingTop: 20,
    paddingBottom: 16,
    fontSize: 22,
    lineHeight: 30,
    fontWeight: "700",
    color: colors.textStrong,
  },
  grid: { flex: 1 },
  gridContent: { paddingHorizontal: SIDE, paddingBottom: 16, gap: GAP },
  row: { gap: GAP },
  tile: { borderRadius: 6, overflow: "hidden", backgroundColor: colors.tile },
  videoBadge: {
    position: "absolute",
    right: 6,
    bottom: 6,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
    backgroundColor: "rgba(0, 0, 0, 0.5)",
  },
  videoBadgeText: { fontSize: 11, color: colors.onPrimary },
  footer: { paddingHorizontal: SIDE, paddingTop: 8, paddingBottom: 34 },
  button: {
    height: 56,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.primary,
  },
  buttonPressed: { opacity: 0.85 },
  buttonText: { fontSize: 16, fontWeight: "700", color: colors.onPrimary },
});
