import { useCallback, useState } from "react";
import { StatusBar } from "expo-status-bar";
import { View } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { SssokWebView } from "./src/components/SssokWebView";
import { AnimatedSplash } from "./src/splash/AnimatedSplash";

export default function App() {
  const [webReady, setWebReady] = useState(false);
  const handleReady = useCallback(() => setWebReady(true), []);

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <View style={{ flex: 1 }}>
        <SssokWebView onReady={handleReady} />
        <AnimatedSplash webReady={webReady} />
      </View>
    </SafeAreaProvider>
  );
}
