module.exports = ({ config }) => {
  const environment =
    process.env.APP_ENV ||
    (process.env.EAS_BUILD_PROFILE === "production" ? "production" : "development");
  if (!["development", "production"].includes(environment)) {
    throw new Error("APP_ENV는 development 또는 production이어야 합니다.");
  }
  const suffix = environment === "production" ? "prod" : "dev";
  const iosFile =
    process.env.GOOGLE_SERVICES_INFO_PLIST || `./firebase/GoogleService-Info.${suffix}.plist`;
  const androidFile =
    process.env.GOOGLE_SERVICES_JSON || `./firebase/google-services.${suffix}.json`;

  return {
    ...config,
    ios: iosFile
      ? {
          ...config.ios,
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
      [
        "expo-build-properties",
        {
          ios: { useFrameworks: "dynamic" },
          android: { usesCleartextTraffic: environment === "development" },
        },
      ],
    ],
  };
};
