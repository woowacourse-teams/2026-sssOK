import { registerRootComponent } from "expo";
import * as SplashScreen from "expo-splash-screen";

import App from "./App";

// 앱 안 스플래시(AnimatedSplash)가 같은 장면을 그릴 때까지 네이티브 스플래시를 띄워 둔다.
void SplashScreen.preventAutoHideAsync();

registerRootComponent(App);
