const SHARE_APP_GROUP = "group.com.ssssok.app";

const SHARE_MAX_IMAGES = 100;
const SHARE_MAX_VIDEOS = 20;

module.exports = ({ config }) => {
  const environment =
    process.env.APP_ENV ||
    (process.env.EAS_BUILD_PROFILE === "production" ? "production" : "development");
  if (!["development", "production"].includes(environment)) {
    throw new Error("APP_ENV는 development 또는 production이어야 합니다.");
  }
  const suffix = environment === "production" ? "prod" : "dev";
  const inviteHost = environment === "production" ? "www.ssssok.com" : "dev.ssssok.com";
  const iosFile =
    process.env.GOOGLE_SERVICES_INFO_PLIST || `./firebase/GoogleService-Info.${suffix}.plist`;
  const androidFile =
    process.env.GOOGLE_SERVICES_JSON || `./firebase/google-services.${suffix}.json`;

  return {
    ...config,
    ios: iosFile
      ? {
          ...config.ios,
          associatedDomains: [`applinks:${inviteHost}`],
          usesAppleSignIn: true,
          googleServicesFile: iosFile,
          entitlements: {
            ...config.ios?.entitlements,
            "aps-environment": environment === "production" ? "production" : "development",
          },
          infoPlist: {
            ...config.ios?.infoPlist,
            AppGroup: SHARE_APP_GROUP,
          },
          privacyManifests: {
            ...config.ios?.privacyManifests,
            NSPrivacyAccessedAPITypes: [
              ...(config.ios?.privacyManifests?.NSPrivacyAccessedAPITypes ?? []),
              {
                NSPrivacyAccessedAPIType: "NSPrivacyAccessedAPICategoryFileTimestamp",
                NSPrivacyAccessedAPITypeReasons: ["C617.1"],
              },
            ],
          },
        }
      : config.ios,
    android: androidFile
      ? {
          ...config.android,
          googleServicesFile: androidFile,
          intentFilters: [
            {
              action: "VIEW",
              autoVerify: true,
              category: ["BROWSABLE", "DEFAULT"],
              data: [{ scheme: "https", host: inviteHost, pathPrefix: "/join/" }],
            },
          ],
          permissions: [
            ...(config.android?.permissions ?? []),
            "android.permission.POST_NOTIFICATIONS",
          ],
        }
      : config.android,
    plugins: [
      ...(config.plugins ?? []),
      "@react-native-firebase/app",
      "@react-native-firebase/messaging",
      "expo-apple-authentication",
      "expo-asset",
      ["@react-native-seoul/kakao-login", { kakaoAppKey: "49e12318151676c430bd253609755519" }],
      "./plugins/withKakaoSceneDelegate",
      [
        "expo-share-extension",
        {
          activationRules: [
            { type: "image", max: SHARE_MAX_IMAGES },
            { type: "video", max: SHARE_MAX_VIDEOS },
          ],
          backgroundColor: { red: 255, green: 255, blue: 255, alpha: 255 },
          excludedPackages: [
            "expo-dev-client",
            "expo-dev-launcher",
            "expo-dev-menu",
            "expo-splash-screen",
            "expo-apple-authentication",
            "expo-media-library",
            "expo-sharing",
            "expo-share-intent",
          ],
        },
      ],
      [
        "expo-share-intent",
        {
          disableIOS: true,
          androidIntentFilters: ["image/*", "video/*"],
          androidMultiIntentFilters: ["image/*", "video/*"],
        },
      ],
      [
        "expo-build-properties",
        {
          ios: { useFrameworks: "dynamic" },
          android: {
            extraProguardRules:
              "-keep class androidx.work.impl.WorkDatabase_Impl { public <init>(); }",
            usesCleartextTraffic: environment === "development",
            extraMavenRepos: ["https://devrepo.kakao.com/nexus/content/groups/public/"],
          },
        },
      ],
    ],
  };
};
