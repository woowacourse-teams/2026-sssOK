const base = require("./jest.config");

/** @type {import("jest").Config} */
module.exports = {
  ...base,
  displayName: "integration",
  testMatch: ["**/*.integration.test.[jt]s?(x)"],
  testPathIgnorePatterns: ["/node_modules/"],
};
