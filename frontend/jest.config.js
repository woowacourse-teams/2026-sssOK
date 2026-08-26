/** @type {import("jest").Config} */
module.exports = {
  testEnvironment: "jest-fixed-jsdom",
  roots: ["<rootDir>/src"],
  moduleFileExtensions: ["ts", "tsx", "js", "jsx", "mjs"],
  testMatch: ["**/?(*.)+(spec|test).[jt]s?(x)"],
  transform: {
    "^.+\\.[cm]?[jt]sx?$": "babel-jest",
  },
  transformIgnorePatterns: [
    "<rootDir>/node_modules/.pnpm/(?!(rettime|until-async|@open-draft\\+deferred-promise)@)",
  ],
  moduleNameMapper: {
    // 별칭 규칙보다 먼저 걸러야 @/shared/assets/*.png 이 그대로 파싱되지 않는다
    "\\.(png|jpe?g|gif|svg)$": "<rootDir>/test/fileMock.js",
    "^@/(.*)$": "<rootDir>/src/$1",
  },
  setupFilesAfterEnv: ["<rootDir>/src/setupTests.ts"],
  clearMocks: true,
};
