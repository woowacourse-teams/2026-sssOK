const fs = require("node:fs/promises");
const path = require("node:path");
const { IOSConfig, withDangerousMod } = require("expo/config-plugins");

// kakao-login의 기본 플러그인은 AppDelegate만 처리한다. Expo 58의 scene 링크 경로도 연결한다.
module.exports = (config) =>
  withDangerousMod(config, [
    "ios",
    async (config) => {
      const sourceRoot = IOSConfig.Paths.getSourceRoot(config.modRequest.projectRoot);
      const file = path.join(sourceRoot, "SceneDelegate.swift");
      let source = await fs.readFile(file, "utf8");
      const marker = "// sssok-kakao-scene";
      if (source.includes(marker)) return config;
      const anchor = "class SceneDelegate: ExpoAppSceneDelegate {";
      if (!source.includes(anchor))
        throw new Error("카카오 로그인: Expo 58 SceneDelegate 형식을 확인해주세요.");
      if (!source.includes("import KakaoSDKAuth")) source = `import KakaoSDKAuth\n${source}`;
      source = source.replace(
        anchor,
        `${anchor}
  ${marker}
  override func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
    let remaining = URLContexts.filter { context in
      if AuthApi.isKakaoTalkLoginUrl(context.url) {
        _ = AuthController.handleOpenUrl(url: context.url)
        return false
      }
      return true
    }
    if !remaining.isEmpty {
      super.scene(scene, openURLContexts: Set(remaining))
    }
  }
`,
      );
      await fs.writeFile(file, source);
      return config;
    },
  ]);
