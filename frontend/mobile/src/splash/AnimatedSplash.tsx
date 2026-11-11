import { useCallback, useEffect, useRef, useState } from "react";
import { StyleSheet, View } from "react-native";
import * as SplashScreen from "expo-splash-screen";
import { useVideoPlayer, VideoView } from "expo-video";
import { useEventListener } from "expo";

/** 첨부 영상(3초)이 중간에 잘리지 않도록 끝까지 표시한다. */
const MIN_VISIBLE_MS = 3_000;
/** 웹이나 영상이 준비되지 않더라도 스플래시에 갇히지 않도록 한다. */
const TIMEOUT_MS = 3_000;
const BACKGROUND = "#FFFFFF";
const VIDEO_SOURCE = require("../../assets/splash.mp4");

type AnimatedSplashProps = {
  /** 뒤에 깔린 웹이 첫 화면을 다 그렸는지. */
  webReady: boolean;
};

/** 정적 스플래시를 먼저 닫아 Android에서도 영상 프레임을 그릴 수 있도록 한다. */
export function AnimatedSplash({ webReady }: AnimatedSplashProps) {
  const player = useVideoPlayer(VIDEO_SOURCE, (player) => {
    player.muted = true;
    player.loop = false;
  });
  const [shownAt, setShownAt] = useState<number | null>(null);
  const [done, setDone] = useState(false);
  const started = useRef(false);

  const dismiss = useCallback(() => setDone(true), []);

  useEventListener(player, "statusChange", ({ status }) => {
    if (status === "error") {
      SplashScreen.hide();
      dismiss();
    }
  });

  useEffect(() => {
    if (done) player.pause();
  }, [done, player]);

  useEffect(() => {
    const timer = setTimeout(() => {
      SplashScreen.hide();
      dismiss();
    }, TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [dismiss]);

  useEffect(() => {
    if (!webReady || shownAt === null) return;
    const timer = setTimeout(dismiss, Math.max(0, MIN_VISIBLE_MS - (Date.now() - shownAt)));
    return () => clearTimeout(timer);
  }, [webReady, shownAt, dismiss]);

  if (done) return null;

  return (
    <View
      style={[StyleSheet.absoluteFill, styles.overlay]}
      accessible
      accessibilityLabel="쏙 불러오는 중"
      onLayout={() => {
        if (started.current) return;
        started.current = true;
        SplashScreen.hide();
        player.play();
      }}
    >
      <VideoView
        player={player}
        style={styles.video}
        contentFit="contain"
        nativeControls={false}
        surfaceType="textureView"
        onFirstFrameRender={() => {
          setShownAt((prev) => prev ?? Date.now());
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { backgroundColor: BACKGROUND },
  video: { flex: 1, backgroundColor: BACKGROUND },
});
