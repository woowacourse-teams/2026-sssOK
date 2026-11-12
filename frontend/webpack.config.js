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
      "process.env.API_BASE_URL": JSON.stringify(process.env.API_BASE_URL),
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
    open: true,
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
