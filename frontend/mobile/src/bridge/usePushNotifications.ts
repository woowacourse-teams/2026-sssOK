import { getApps } from "@react-native-firebase/app";
import {
  AuthorizationStatus,
  getInitialNotification,
  getMessaging,
  getToken,
  onMessage,
  onNotificationOpenedApp,
  onTokenRefresh,
  requestPermission,
  type RemoteMessage,
} from "@react-native-firebase/messaging";
import { useCallback, useEffect, useRef } from "react";
import { AppState, NativeModules, PermissionsAndroid, Platform } from "react-native";

// 이전 개발 APK에서도 오류 없이 열리게 한다. 시스템 알림 표시는 새 APK부터 지원된다.
const loadNotifee = () =>
  NativeModules.NotifeeApiModule ? import("@notifee/react-native") : Promise.resolve(null);

export type PushEvent = { type: "token"; token: string; platform: "ios" | "android" };

const getNativeMessaging = () => {
  try {
    return getApps().length > 0 ? getMessaging() : null;
  } catch {
    // Firebase 설정이 없는 빌드에서도 WebView는 사용할 수 있다.
    return null;
  }
};

const allowNotifications = async (messaging: ReturnType<typeof getMessaging>) => {
  if (Platform.OS === "ios") {
    const status = await requestPermission(messaging);
    return status === AuthorizationStatus.AUTHORIZED || status === AuthorizationStatus.PROVISIONAL;
  }
  if (Platform.OS === "android" && Number(Platform.Version) >= 33) {
    const permission = PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS;
    return (await PermissionsAndroid.request(permission)) === PermissionsAndroid.RESULTS.GRANTED;
  }
  return Platform.OS === "android";
};

const roomCodeOf = (message: RemoteMessage | null) => {
  const code = message?.data?.roomCode;
  return typeof code === "string" && /^[a-zA-Z0-9]{1,32}$/.test(code) ? code : null;
};

export const usePushNotifications = (
  emit: (event: PushEvent) => void,
  openRoom: (url: string) => void,
  readCurrentRoom: () => string | null,
) => {
  const tokenRequest = useRef<Promise<string | null> | null>(null);
  const tokenAllowed = useRef(false);
  const sendToken = useCallback(
    (token: string) => {
      const platform = Platform.OS;
      if (platform === "ios" || platform === "android") emit({ type: "token", token, platform });
    },
    [emit],
  );

  useEffect(() => {
    const messaging = getNativeMessaging();
    if (!messaging) return;

    let active = true;
    let openedMessageId: string | undefined;
    let pendingUrl: string | null = null;
    let removeLocalPress: (() => void) | undefined;
    const openNotification = (url: unknown) => {
      if (!active || typeof url !== "string") return;
      if (AppState.currentState === "active") openRoom(url);
      else pendingUrl = url;
    };
    const handleOpen = (message: RemoteMessage | null) => {
      const url = message?.data?.url;
      if (
        !active ||
        typeof url !== "string" ||
        (message?.messageId && message.messageId === openedMessageId)
      )
        return;
      openedMessageId = message?.messageId;
      openNotification(url);
    };

    const removeOpen = onNotificationOpenedApp(messaging, handleOpen);
    const appStateListener = AppState.addEventListener("change", (state) => {
      if (state === "active" && pendingUrl) {
        const url = pendingUrl;
        pendingUrl = null;
        openNotification(url);
      }
    });
    void loadNotifee()
      .then(async (module) => {
        if (!active || !module) return;
        const { default: notifee, EventType } = module;
        removeLocalPress = notifee.onForegroundEvent(({ type, detail }) => {
          if (type === EventType.PRESS) openNotification(detail.notification?.data?.url);
        });
        notifee.onBackgroundEvent(async ({ type, detail }) => {
          if (type === EventType.PRESS) openNotification(detail.notification?.data?.url);
        });
        if (Platform.OS === "android") {
          const initial = await notifee.getInitialNotification();
          openNotification(initial?.notification.data?.url);
        }
      })
      .catch(() => {});

    const removeMessage = onMessage(messaging, (message) => {
      const roomCode = roomCodeOf(message);
      if (AppState.currentState !== "active" || !roomCode) return;
      // 현재 방의 화면 알림은 SSE만 사용한다.
      if (readCurrentRoom() === roomCode) return;
      // 포그라운드 FCM은 OS가 자동 표시하지 않는다. 다른 방의 알림만 직접 표시한다.
      void loadNotifee()
        .then(async (module) => {
          if (!active) return;
          if (!module) {
            return;
          }
          const { default: notifee, AndroidImportance } = module;
          const channelId = await notifee.createChannel({
            id: "room-notifications",
            name: "방 알림",
            importance: AndroidImportance.HIGH,
          });
          await notifee.displayNotification({
            id: message.messageId,
            title: message.notification?.title ?? "쏙",
            body: message.notification?.body,
            data: message.data,
            android: {
              channelId,
              pressAction: { id: "default" },
            },
            ios: {
              foregroundPresentationOptions: {
                banner: true,
                list: true,
                sound: true,
                badge: false,
              },
            },
          });
        })
        .catch(() => {});
    });
    const removeRefresh = onTokenRefresh(messaging, (token) => {
      if (tokenAllowed.current) sendToken(token);
    });
    void getInitialNotification(messaging)
      .then(handleOpen)
      .catch(() => {});

    return () => {
      active = false;
      removeOpen();
      removeMessage();
      removeRefresh();
      removeLocalPress?.();
      appStateListener.remove();
    };
  }, [emit, openRoom, sendToken, readCurrentRoom]);

  return () => {
    const messaging = getNativeMessaging();
    if (!messaging || tokenRequest.current) return;

    tokenRequest.current = allowNotifications(messaging).then(async (allowed) => {
      tokenAllowed.current = allowed;
      return allowed ? getToken(messaging) : null;
    });
    void tokenRequest.current
      .then((token) => {
        if (token) sendToken(token);
      })
      .catch(() => {})
      .finally(() => {
        tokenRequest.current = null;
      });
  };
};
