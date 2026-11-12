const { defineConfig } = require("eslint/config");
const expoConfig = require("eslint-config-expo/flat");

const teamTypescript = require("../eslint-rules/typescript.cjs");
module.exports = defineConfig([
  expoConfig,
  { ignores: ["dist/**", ".expo/**"] },
  {
    files: ["**/*.{ts,tsx}"],
    plugins: { team: teamTypescript },
    linterOptions: { reportUnusedDisableDirectives: "error" },
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/consistent-type-definitions": ["error", "interface"],
      "@typescript-eslint/ban-ts-comment": [
        "error",
        {
          "ts-expect-error": "allow-with-description",
          "ts-ignore": true,
          "ts-nocheck": true,
          minimumDescriptionLength: 10,
        },
      ],
      "team/explicit-return-type": "error",
      "team/exception-description": "error",
    },
  },
]);
