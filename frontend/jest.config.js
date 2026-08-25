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
    "^@/(.*)$": "<rootDir>/src/$1",
    "\\.(png|jpe?g|gif|svg)$": "<rootDir>/test/fileMock.js",
  },
  setupFilesAfterEnv: ["<rootDir>/src/setupTests.ts"],
  clearMocks: true,
};
