import { useCallback, useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Asset } from "expo-asset";
import { File } from "expo-file-system";
import * as SplashScreen from "expo-splash-screen";
import { WebView } from "react-native-webview";

/** 영상 준비·재생을 포함해 최대 3초만 가린다. */
const TIMEOUT_MS = 3_000;
const VIDEO_SOURCE = require("../../assets/splash.mp4");

const createVideoHtml = (base64: string): string => `<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<style>
  html, body { margin: 0; width: 100%; height: 100%; overflow: hidden; background: #fff; }
  video { display: block; width: 100%; height: 100%; object-fit: contain; pointer-events: none; }
</style>
</head>
<body>
<video id="splash" muted playsinline webkit-playsinline preload="auto" disablepictureinpicture></video>
<script>
  const video = document.getElementById('splash');
  const send = (event) => window.ReactNativeWebView.postMessage(event);
  video.muted = true;
  video.addEventListener('ended', () => send('ended'));
  video.addEventListener('error', () => send('error'));
  video.src = 'data:video/mp4;base64,${base64}';
  video.play().catch(() => send('error'));
</script>
</body>
</html>`;

interface AnimatedSplashProps {
  /** 서비스 WebView의 문서 로딩 완료 여부. API 완료를 의미하지 않는다. */
  webReady: boolean;
}

export function AnimatedSplash({ webReady }: AnimatedSplashProps) {
  const [source, setSource] = useState<{ html: string } | null>(null);
  const [ended, setEnded] = useState(false);
  const [done, setDone] = useState(false);

  const dismiss = useCallback(() => {
    SplashScreen.hide();
    setDone(true);
  }, []);

  useEffect(() => {
    const timer = setTimeout(dismiss, TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [dismiss]);

  useEffect(() => {
    if (done) return;
    let cancelled = false;
    const prepare = async (): Promise<void> => {
      // 앱에 번들된 영상을 읽어 HTML에 포함한다. 외부 URL이나 파일 접근 권한은 필요 없다.
      const asset = await Asset.fromModule(VIDEO_SOURCE).downloadAsync();
      if (!asset.localUri) throw new Error("Splash video is unavailable");
      const base64 = await new File(asset.localUri).base64();
      if (!cancelled) setSource({ html: createVideoHtml(base64) });
    };
    void prepare().catch(() => {
      if (!cancelled) dismiss();
    });
    return () => {
      cancelled = true;
    };
  }, [done, dismiss]);

  if (done || (ended && webReady)) return null;

  return (
    <View
      style={[StyleSheet.absoluteFill, styles.overlay]}
      accessible
      accessibilityLabel="쏙 불러오는 중"
      // 정적 스플래시가 WebView의 화면 그리기를 막지 않도록 먼저 닫는다.
      onLayout={() => SplashScreen.hide()}
    >
      {source && (
        <WebView
          source={source}
          originWhitelist={["*"]}
          style={styles.video}
          allowsInlineMediaPlayback
          mediaPlaybackRequiresUserAction={false}
          allowsFullscreenVideo={false}
          allowsPictureInPictureMediaPlayback={false}
          scrollEnabled={false}
          bounces={false}
          overScrollMode="never"
          contentInsetAdjustmentBehavior="never"
          onMessage={({ nativeEvent }) => {
            if (nativeEvent.data === "ended") setEnded(true);
            if (nativeEvent.data === "error") dismiss();
          }}
          onError={dismiss}
          onContentProcessDidTerminate={dismiss}
          onRenderProcessGone={dismiss}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { backgroundColor: "#FFFFFF" },
  video: { flex: 1, backgroundColor: "#FFFFFF" },
});
