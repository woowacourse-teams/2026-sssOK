const fs = require("fs");
const path = require("path");
const webpack = require("webpack");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const CopyWebpackPlugin = require("copy-webpack-plugin");
const { version } = require("./package.json");

const envPath = path.resolve(__dirname, ".env");

if (fs.existsSync(envPath)) {
  process.loadEnvFile(envPath);
}

if (!process.env.API_BASE_URL?.trim()) {
  throw new Error("API_BASE_URL 환경변수가 필요합니다.");
}

/**
 * 폰(앱 웹뷰)에서 `http://<맥 IP>:3000` 으로 열 때 쓴다. `pnpm start:app` 참고.
 *
 * dev API 는 허용한 출처(localhost 등)만 받아서 IP 로 연 페이지의 요청은 CORS 에서 막힌다.
 * 켜면 API 요청을 같은 출처(`/api/v1`)로 보내고, 개발 서버가 dev API 로 대신 넘긴다.
 */
const useApiProxy = process.env.API_PROXY === "1";
const apiUrl = new URL(process.env.API_BASE_URL);
const apiBaseUrl = useApiProxy ? apiUrl.pathname.replace(/\/$/, "") : process.env.API_BASE_URL;

const apiProxy = {
  context: [apiUrl.pathname],
  target: apiUrl.origin,
  changeOrigin: true,
  on: {
    // 쿠키 인증은 Origin 을 허용 목록과 대조한다. 서버끼리의 요청이라 Origin 을 떼어 그 검사를 피한다.
    proxyReq: (proxyReq) => proxyReq.removeHeader("origin"),
    // IP 로 연 http 페이지는 Secure 쿠키를 받지 못한다. 로컬에서만 떼어 리프레시 쿠키가 남게 한다.
    proxyRes: (proxyRes) => {
      const cookies = proxyRes.headers["set-cookie"];

      if (cookies) {
        proxyRes.headers["set-cookie"] = cookies.map((cookie) =>
          cookie.replace(/;\s*Secure/gi, ""),
        );
      }
    },
  },
};

/**
 * 업로드는 웹이 R2 서명 URL 로 직접 PUT 해서 위 프록시를 거치지 않고, R2 버킷 CORS 에 막힌다.
 * 켜면 `/__storage/<R2 호스트>/<경로>` 로 보내고(`putToStorage` 참고) 개발 서버가 R2 로 넘긴다.
 * 서명은 호스트·경로·content-type 만 덮으므로 `changeOrigin` 으로 호스트를 맞추면 그대로 유효하다.
 */
const STORAGE_PROXY_PATH = "/__storage";
// 열린 프록시가 되지 않게 R2 주소로만 넘긴다.
const storagePathPattern = new RegExp(
  `^${STORAGE_PROXY_PATH}/([a-z0-9-]+\\.r2\\.cloudflarestorage\\.com)(/.*)$`,
);

const storageProxy = {
  pathFilter: (pathname) => storagePathPattern.test(pathname),
  // `target` 은 필수라 자리만 채운다. 실제 주소는 요청마다 `router` 가 정한다.
  target: "https://r2.cloudflarestorage.com",
  router: (req) => `https://${storagePathPattern.exec(req.url)[1]}`,
  pathRewrite: (pathname) => storagePathPattern.exec(pathname)[2],
  changeOrigin: true,
  on: {
    // 서버끼리의 요청이라 CORS 를 볼 일이 없다. Origin 을 떼어 R2 가 CORS 규칙을 대조하지 않게 한다.
    proxyReq: (proxyReq) => proxyReq.removeHeader("origin"),
  },
};

module.exports = {
  mode: "development",
  entry: "./src/index.tsx",

  output: {
    filename: "[name].[contenthash].js",
    chunkFilename: "[name].[contenthash].js",
    path: path.resolve(__dirname, "dist"),
    // 중첩 경로(/rooms/:code)에서도 번들을 루트 기준으로 찾게 한다
    publicPath: "/",
    // contenthash가 바뀐 이전 빌드 파일을 dist에 남기지 않는다.
    clean: true,
  },

  resolve: {
    extensions: [".tsx", ".ts", ".jsx", ".js"],
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },

  module: {
    rules: [
      {
        test: /\.[jt]sx?$/,
        exclude: /node_modules/,
        use: {
          loader: "babel-loader",
        },
      },

      {
        test: /\.css$/i,
        use: ["style-loader", "css-loader"],
      },

      {
        test: /\.(png|svg|jpg|jpeg|gif|webp)$/i,
        type: "asset",
      },

      {
        test: /\.(woff|woff2|eot|ttf|otf)$/i,
        type: "asset/resource",
      },
    ],
  },
  plugins: [
    // 브라우저 번들에는 process 가 없다. mode 가 자동으로 넣어주는 건 NODE_ENV 뿐이라
    // 그 밖의 환경변수는 여기서 직접 값으로 치환해야 런타임에 터지지 않는다.
    new webpack.DefinePlugin({
      "process.env.API_BASE_URL": JSON.stringify(apiBaseUrl),
      "process.env.STORAGE_PROXY_PATH": JSON.stringify(
        useApiProxy ? STORAGE_PROXY_PATH : undefined,
      ),
      // 목을 어디까지 씌울지 (full | hybrid | off). src/shared/config/mock.ts 참고
      "process.env.MOCK": JSON.stringify(process.env.MOCK),
      // PostHog 프로젝트를 바꿔 보낼 때만 넘긴다. src/shared/config/analytics.ts 참고
      "process.env.POSTHOG_KEY": JSON.stringify(process.env.POSTHOG_KEY),
      "process.env.POSTHOG_HOST": JSON.stringify(process.env.POSTHOG_HOST),
      // 이 번들이 몇 버전인지. src/shared/config/version.ts 참고
      "process.env.APP_VERSION": JSON.stringify(version),
    }),

    new HtmlWebpackPlugin({
      template: "./public/index.html",
      filename: "index.html",
      inject: true,
      meta: { "app-version": version },
      og: {
        title: "사진 공유, 쏙으로 더 가볍게!",
        description: "링크 하나로 모으고, 고르고, 쏙 나누세요",
        url: "https://www.ssssok.com/",
        image: "https://www.ssssok.com/og/default.png",
        imageAlt: "링크 하나로 사진 모으고 쏙! — 사진을 든 쏙이",
      },
    }),

    // 초대 링크(/join/:code)와 방 주소(/rooms/:code/...) 전용이다. 앱과 같은 번들을 싣고 미리보기만 초대용으로 바꾼다.
    // CloudFront Function(cloudfront/invite-preview.js)이 이 요청들을 이 파일로 보낸다
    new HtmlWebpackPlugin({
      template: "./public/index.html",
      filename: "join.html",
      inject: true,
      meta: { "app-version": version },
      og: {
        title: "사진방에 초대받았어요 📸",
        description: "설치 없이 바로 들어와서 사진을 쏙 올려 주세요!",
        // 방마다 주소가 달라 og:url 은 두지 않는다. 루트를 적으면 수집기가 랜딩 미리보기를 가져갈 수 있다
        image: "https://www.ssssok.com/og/invite.png",
        imageAlt: "사진방에 초대받았어요 — 손 흔드는 쏙이",
      },
    }),

    new CopyWebpackPlugin({
      patterns: [
        {
          from: "public",
          globOptions: {
            ignore: ["**/index.html", "**/mockServiceWorker.js"],
          },
        },
        // 루트(/)의 랜딩. 검색, 미리보기용 정적 HTML 이라 번들을 거치지 않고 그대로 내보낸다.
        // CloudFront 의 기본 루트 객체가 landing.html 을 가리킨다
        { from: "landing/index.html", to: "landing.html" },
        {
          from: "landing",
          to: "landing",
          globOptions: { ignore: ["**/index.html"] },
        },
      ],
    }),
  ],
  devServer: {
    static: {
      directory: path.join(__dirname, "public"),
    },
    port: 3000,
    // 폰에서 맥 IP 로 열어야 하니 프록시를 쓸 때는 모든 네트워크에서 받는다.
    host: useApiProxy ? "0.0.0.0" : undefined,
    open: !useApiProxy,
    proxy: useApiProxy ? [apiProxy, storageProxy] : undefined,
    hot: true,
    // 운영에서 / 를 랜딩으로 보내는 CloudFront 기본 루트 객체를 로컬에서도 흉내 낸다.
    devMiddleware: {
      index: "landing.html",
    },
    // 초대 링크·방 주소를 join.html 로 보내는 CloudFront Function 도 함께 흉내 낸다.
    // 판단 규칙은 cloudfront/invite-preview.js 와 같게 둔다 (/rooms/create, /rooms/closed 는 제외)
    historyApiFallback: {
      rewrites: [
        { from: /^\/join\//, to: "/join.html" },
        { from: /^\/rooms\/(?!(create|closed)(\/|$))[^/]+/, to: "/join.html" },
      ],
    },
    client: {
      overlay: true,
    },
  },
};
