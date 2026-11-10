import { useCallback, useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  BackHandler,
  Linking,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { WebView } from "react-native-webview";
import {
  createDownloadBridge,
  DOWNLOAD_BRIDGE_VERSION,
  type BridgeEvent,
} from "../bridge/downloadBridge";
import { usePushNotifications, type PushEvent } from "../bridge/usePushNotifications";
import { createAuthBridge } from "../bridge/authBridge";
import { NATIVE_AUTH_PROVIDERS } from "../bridge/sdkAuth";
import { WEB_ORIGIN, WEB_URL } from "../config/web";
import {
  canPersistCookies,
  COOKIE_PERSISTENCE_SCRIPT,
  handleCookiePersistence,
} from "../bridge/cookiePersistence";

/** 웹이 페이지 스크립트보다 먼저 볼 수 있게 심는다. 웹은 이 값이 있어야 저장을 앱에 맡긴다. */
const BRIDGE_FLAG_SCRIPT = `window.sssokApp = { download: ${DOWNLOAD_BRIDGE_VERSION}, auth: ${JSON.stringify(NATIVE_AUTH_PROVIDERS)} }; true;`;

/** 브릿지 메시지는 쏙 웹에서 온 것만 받는다. 로그인 페이지(`AUTH_ORIGINS`)도 저장을 시킬 수 없다. */
const isWebOrigin = (url: string) => {
  try {
    return new URL(url).origin === WEB_ORIGIN;
  } catch {
    return false;
  }
};

const AUTH_ORIGINS = new Set([
  "https://kauth.kakao.com",
  "https://accounts.kakao.com",
  "https://appleid.apple.com",
  "https://account.apple.com",
  "https://idmsa.apple.com",
]);

const isAllowedWebUrl = (url: string) => {
  try {
    const { origin } = new URL(url);
    return origin === WEB_ORIGIN || AUTH_ORIGINS.has(origin);
  } catch {
    return false;
  }
};

type SssokWebViewProps = {
  /** 첫 페이지가 다 뜨거나 오류 화면으로 넘어가면 부른다. 스플래시를 닫는 신호다. */
  onReady?: () => void;
};

export function SssokWebView({ onReady }: SssokWebViewProps) {
  const webView = useRef<WebView<object>>(null);
  const downloadBridge = useRef<ReturnType<typeof createDownloadBridge>>(null);
  const authBridge = useRef<ReturnType<typeof createAuthBridge>>(null);
  const canGoBack = useRef(false);
  const [failed, setFailed] = useState(false);
  const lastInternalUrl = useRef(WEB_URL);
  const [sourceUrl, setSourceUrl] = useState(WEB_URL);
  const loadedUrl = useRef<string | null>(null);
  const currentRoomCode = useRef<string | null>(null);
  const readCurrentRoom = useCallback(() => currentRoomCode.current, []);

  const emitPush = useCallback((event: PushEvent) => {
    webView.current?.injectJavaScript(
      `if (location.origin === ${JSON.stringify(WEB_ORIGIN)}) {
        window.dispatchEvent(new CustomEvent("sssok:push", { detail: ${JSON.stringify(event)} }));
      } true;`,
    );
  }, []);

  const openPushRoom = useCallback((notificationUrl: string) => {
    let target: URL;
    try {
      target = new URL(notificationUrl, WEB_ORIGIN);
    } catch {
      return;
    }
    if (
      target.origin !== WEB_ORIGIN ||
      target.username ||
      target.password ||
      !/^\/rooms\/[A-Z2-9]{8}\/gallery$/.test(target.pathname)
    )
      return;
    const url = target.href;
    if (webView.current && loadedUrl.current && isWebOrigin(loadedUrl.current)) {
      webView.current.injectJavaScript(
        `if (location.origin === ${JSON.stringify(WEB_ORIGIN)} && location.href !== ${JSON.stringify(url)}) {
          location.assign(${JSON.stringify(url)});
        } true;`,
      );
    } else {
      setFailed(false);
      setSourceUrl(url);
    }
  }, []);
  const requestPushToken = usePushNotifications(emitPush, openPushRoom, readCurrentRoom);

  const rememberInternalUrl = (url: string) => {
    try {
      if (new URL(url).origin === WEB_ORIGIN) lastInternalUrl.current = url;
    } catch {
      // about:blank 등 서비스 주소가 아닌 값은 재시도 대상으로 저장하지 않는다.
    }
  };

  const showError = (url?: string) => {
    if (url) rememberInternalUrl(url);
    canGoBack.current = false;
    loadedUrl.current = null;
    // 답을 받을 페이지가 사라졌다. 받던 것을 이어 가봐야 결과를 알릴 곳이 없다.
    downloadBridge.current?.cancelAll();
    authBridge.current?.cancelAll();
    setFailed(true);
    onReady?.();
  };

  useEffect(() => {
    const bridge = createAuthBridge((event) => {
      webView.current?.injectJavaScript(
        `if (location.origin === ${JSON.stringify(WEB_ORIGIN)}) {
          window.dispatchEvent(new CustomEvent("sssok:auth", { detail: ${JSON.stringify(event)} }));
        } true;`,
      );
    });
    authBridge.current = bridge;
    return () => bridge.cancelAll();
  }, []);

  useEffect(() => {
    const bridge = createDownloadBridge((event: BridgeEvent) => {
      webView.current?.injectJavaScript(
        `window.dispatchEvent(new CustomEvent("sssok:native", { detail: ${JSON.stringify(event)} })); true;`,
      );
    });

    downloadBridge.current = bridge;

    return () => bridge.cancelAll();
  }, []);

  useEffect(() => {
    const subscription = BackHandler.addEventListener("hardwareBackPress", () => {
      if (!canGoBack.current || !webView.current) return false;
      webView.current.goBack();
      return true;
    });
    return () => subscription.remove();
  }, []);

  const openExternal = (url: string) => {
    try {
      const { protocol } = new URL(url);
      if (!["https:", "http:", "mailto:", "tel:"].includes(protocol)) return;
      void Linking.openURL(url).catch(() =>
        Alert.alert("링크를 열 수 없어요", "잠시 후 다시 시도해주세요."),
      );
    } catch {
      // 지원하지 않는 주소는 실행하지 않는다.
    }
  };

  return (
    <SafeAreaView style={styles.page}>
      {failed ? (
        <View style={styles.message}>
          <Text style={styles.title}>쏙에 연결하지 못했어요</Text>
          <Text style={styles.description}>인터넷 연결을 확인하고 다시 시도해주세요.</Text>
          <Pressable
            accessibilityRole="button"
            style={styles.button}
            onPress={() => {
              canGoBack.current = false;
              setFailed(false);
              setSourceUrl(lastInternalUrl.current);
            }}
          >
            <Text style={styles.buttonText}>다시 시도</Text>
          </Pressable>
        </View>
      ) : (
        <WebView<object>
          ref={webView}
          source={{ uri: sourceUrl }}
          style={styles.webView}
          originWhitelist={["*"]}
          onShouldStartLoadWithRequest={(request) => {
            if (request.isTopFrame === false) return true;
            if (request.url === "about:blank") return true;
            if (isAllowedWebUrl(request.url)) return true;
            openExternal(request.url);
            return false;
          }}
          onOpenWindow={({ nativeEvent }) => {
            if (isAllowedWebUrl(nativeEvent.targetUrl)) {
              setSourceUrl(nativeEvent.targetUrl);
              return;
            }
            openExternal(nativeEvent.targetUrl);
          }}
          injectedJavaScriptBeforeContentLoaded={
            BRIDGE_FLAG_SCRIPT + (canPersistCookies ? COOKIE_PERSISTENCE_SCRIPT : "")
          }
          // Android에서는 로딩 전 주입이 누락될 수 있어 로딩 후에도 설치한다.
          injectedJavaScript={
            BRIDGE_FLAG_SCRIPT + (canPersistCookies ? COOKIE_PERSISTENCE_SCRIPT : "")
          }
          onMessage={({ nativeEvent }) => {
            // 서비스 페이지가 보낸 것만 받는다. 다른 주소의 페이지가 저장을 시키지 못하게 한다.
            if (!isWebOrigin(nativeEvent.url)) return;
            if (authBridge.current?.handle(nativeEvent.data)) return;
            try {
              const message = JSON.parse(nativeEvent.data);
              if (message?.type === "push/current-room") {
                currentRoomCode.current =
                  typeof message.roomCode === "string" && /^[A-Z2-9]{8}$/.test(message.roomCode)
                    ? message.roomCode
                    : null;
                return;
              }
            } catch {
              // 다른 브릿지의 형식 검증은 각 핸들러에서 수행한다.
            }
            if (nativeEvent.data === '{"type":"push/request-token"}') {
              requestPushToken();
              return;
            }
            if (handleCookiePersistence(nativeEvent.data)) return;
            downloadBridge.current?.handle(nativeEvent.data);
          }}
          onLoadStart={() => {
            authBridge.current?.cancelAll();
            loadedUrl.current = null;
            currentRoomCode.current = null;
          }}
          onLoad={({ nativeEvent }) => {
            loadedUrl.current = nativeEvent.url;
          }}
          onLoadEnd={() => onReady?.()}
          onNavigationStateChange={(state) => {
            rememberInternalUrl(state.url);
            canGoBack.current = state.canGoBack;
          }}
          onError={({ nativeEvent }) => showError(nativeEvent.url)}
          onHttpError={({ nativeEvent }) => {
            showError(nativeEvent.url);
          }}
          onContentProcessDidTerminate={() => showError()}
          onRenderProcessGone={() => showError()}
          // 첫 로딩은 스플래시가 가리고, 이후 다시 불러올 때만 스피너가 보인다.
          startInLoadingState
          renderLoading={() => (
            <View style={[StyleSheet.absoluteFill, styles.message]}>
              <ActivityIndicator size="large" color="#F66D3B" accessibilityLabel="쏙 불러오는 중" />
            </View>
          )}
          domStorageEnabled
          javaScriptEnabled
          allowsBackForwardNavigationGestures
          allowsInlineMediaPlayback
          contentInsetAdjustmentBehavior="never"
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: "#FFFFFF" },
  webView: { flex: 1, backgroundColor: "#FFFFFF" },
  message: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 16,
    padding: 24,
    backgroundColor: "#FFFFFF",
  },
  title: { fontSize: 20, fontWeight: "700", color: "#1A1A1A", textAlign: "center" },
  description: { fontSize: 15, color: "#8B8781", textAlign: "center" },
  button: {
    backgroundColor: "#F66D3B",
    borderRadius: 12,
    paddingHorizontal: 24,
    paddingVertical: 14,
  },
  buttonText: { fontSize: 16, fontWeight: "600", color: "#FFFFFF" },
});
