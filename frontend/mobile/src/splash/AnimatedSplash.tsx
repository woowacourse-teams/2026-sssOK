import { useCallback, useEffect, useRef, useState } from "react";
import { Animated, StyleSheet } from "react-native";
import * as SplashScreen from "expo-splash-screen";
import { WebView } from "react-native-webview";

import { MASCOT_HELLO_SVG } from "./mascotHelloSvg";

/** 캐릭터가 구멍에서 나와 손을 흔들기 시작할 때까지는 보여 준다. */
const MIN_VISIBLE_MS = 1_200;
const FADE_OUT_MS = 250;
/** 웹이 끝내 뜨지 않아도 스플래시에 갇히지 않게 이 시간이 지나면 닫는다. */
const TIMEOUT_MS = 10_000;

/** 네이티브 스플래시(app.json의 imageWidth)와 같은 크기·위치에 그려야 넘어갈 때 튀지 않는다. */
const MASCOT_SIZE = 200;
const BACKGROUND = "#FFFFFF";

const HTML = `<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<style>
  html, body { margin: 0; height: 100%; overflow: hidden; background: ${BACKGROUND}; }
  body { display: flex; align-items: center; justify-content: center; }
  svg { width: ${MASCOT_SIZE}px; height: ${MASCOT_SIZE}px; }
</style>
</head>
<body>${MASCOT_HELLO_SVG}</body>
</html>`;

type AnimatedSplashProps = {
  /** 뒤에 깔린 웹이 첫 화면을 다 그렸는지. */
  webReady: boolean;
};

/**
 * 네이티브 스플래시는 정지 이미지만 띄울 수 있어서, 그 위에 같은 장면을 이어 받아 애니메이션을 재생한다.
 * 첫 장면(빈 구멍)이 그려지면 네이티브 스플래시를 닫고, 웹이 뜨면 페이드로 사라진다.
 */
export function AnimatedSplash({ webReady }: AnimatedSplashProps) {
  const [opacity] = useState(() => new Animated.Value(1));
  const fadeStarted = useRef(false);
  const [shownAt, setShownAt] = useState<number | null>(null);
  const [fading, setFading] = useState(false);
  const [done, setDone] = useState(false);

  const fadeOut = useCallback(() => {
    if (fadeStarted.current) return;
    fadeStarted.current = true;
    setFading(true);
    Animated.timing(opacity, {
      toValue: 0,
      duration: FADE_OUT_MS,
      useNativeDriver: true,
    }).start(() => setDone(true));
  }, [opacity]);

  useEffect(() => {
    const timer = setTimeout(() => {
      SplashScreen.hide();
      fadeOut();
    }, TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [fadeOut]);

  useEffect(() => {
    if (!webReady || shownAt === null) return;
    const timer = setTimeout(fadeOut, Math.max(0, MIN_VISIBLE_MS - (Date.now() - shownAt)));
    return () => clearTimeout(timer);
  }, [webReady, shownAt, fadeOut]);

  if (done) return null;

  return (
    <Animated.View
      style={[StyleSheet.absoluteFill, styles.overlay, { opacity }]}
      pointerEvents={fading ? "none" : "auto"}
      accessible
      accessibilityLabel="쏙 불러오는 중"
    >
      <WebView
        source={{ html: HTML }}
        // 인라인 HTML의 주소는 플랫폼마다 달라(about:blank, data: 등) 막히지 않게 모두 허용한다. 외부로 이동하는 링크는 없다.
        originWhitelist={["*"]}
        style={styles.webView}
        scrollEnabled={false}
        bounces={false}
        overScrollMode="never"
        contentInsetAdjustmentBehavior="never"
        showsHorizontalScrollIndicator={false}
        showsVerticalScrollIndicator={false}
        onLoadEnd={() => {
          SplashScreen.hide();
          setShownAt((prev) => prev ?? Date.now());
        }}
      />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  overlay: { backgroundColor: BACKGROUND },
  webView: { flex: 1, backgroundColor: BACKGROUND },
});
