import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { SssokWebView } from "./src/components/SssokWebView";

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <SssokWebView />
    </SafeAreaProvider>
  );
}
