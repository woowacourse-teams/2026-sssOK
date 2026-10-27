import { useEffect, useRef, useState } from "react";
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
import { WEB_ORIGIN, WEB_URL } from "../config/web";

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

export function SssokWebView() {
  const webView = useRef<WebView<object>>(null);
  const canGoBack = useRef(false);
  const [failed, setFailed] = useState(false);
  const lastInternalUrl = useRef(WEB_URL);
  const [sourceUrl, setSourceUrl] = useState(WEB_URL);

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
    setFailed(true);
  };

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
