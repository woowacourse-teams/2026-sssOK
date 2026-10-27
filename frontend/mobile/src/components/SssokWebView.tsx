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

export function SssokWebView() {
  const webView = useRef<WebView<object>>(null);
  const canGoBack = useRef(false);
  const [failed, setFailed] = useState(false);
  const [instance, setInstance] = useState(0);

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
              setInstance((value) => value + 1);
            }}
          >
            <Text style={styles.buttonText}>다시 시도</Text>
          </Pressable>
        </View>
      ) : (
        <WebView<object>
          key={instance}
          ref={webView}
          source={{ uri: WEB_URL }}
          style={styles.webView}
          originWhitelist={["*"]}
          onShouldStartLoadWithRequest={(request) => {
            if (request.isTopFrame === false) return true;
            if (request.url === "about:blank") return true;
            try {
              if (new URL(request.url).origin === WEB_ORIGIN) return true;
            } catch {
              return false;
            }
            openExternal(request.url);
            return false;
          }}
          onOpenWindow={({ nativeEvent }) => openExternal(nativeEvent.targetUrl)}
          onNavigationStateChange={(state) => {
            canGoBack.current = state.canGoBack;
          }}
          onError={() => setFailed(true)}
          onHttpError={({ nativeEvent }) => {
            if (nativeEvent.url === WEB_URL) setFailed(true);
          }}
          onContentProcessDidTerminate={() => setFailed(true)}
          onRenderProcessGone={() => setFailed(true)}
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
