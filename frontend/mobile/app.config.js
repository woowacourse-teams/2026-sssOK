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
      ["@react-native-seoul/kakao-login", { kakaoAppKey: "49e12318151676c430bd253609755519" }],
      "./plugins/withKakaoSceneDelegate",
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
