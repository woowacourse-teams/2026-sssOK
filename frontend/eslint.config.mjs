// For more info, see https://github.com/storybookjs/eslint-plugin-storybook#configuration-flat-config-format
import storybook from "eslint-plugin-storybook";

import eslint from "@eslint/js";
import tseslint from "typescript-eslint";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import eslintConfigPrettier from "eslint-config-prettier";

import tanstackQuery from "@tanstack/eslint-plugin-query";

export default tseslint.config(
  // ignore
  { ignores: ["dist/**", "coverage/**", "storybook-static/**"] },

  // src 기본 규칙
  {
    files: ["src/**/*.{ts,tsx}"],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      react.configs.flat.recommended,
      react.configs.flat["jsx-runtime"],
      reactHooks.configs.flat.recommended,
    ],
    languageOptions: { globals: globals.browser },
    settings: { react: { version: "detect" } },
  },

  // TanStack Query 규칙
  ...tanstackQuery.configs["flat/recommended"],

  // shared 아키텍처 규칙
  {
    files: ["src/shared/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/app/**", "@/pages/**", "@/widgets/**", "@/features/**", "@/entities/**"],
              message: "shared는 상위 레이어를 import할 수 없습니다.",
            },
            {
              regex: "^(?:\\.\\./)+(?:app|pages|widgets|features|entities)(?:/|$)",
              message: "shared는 상대경로를 통해 상위 레이어를 import할 수 없습니다.",
            },
          ],
        },
      ],
    },
  },

  // Storybook 설정
  {
    files: [".storybook/**/*.{ts,tsx}"],
    extends: [eslint.configs.recommended, tseslint.configs.recommended],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
  },
  storybook.configs["flat/recommended"],

  // 설정 파일 설정
  {
    files: ["*.js"],
    extends: [eslint.configs.recommended],
    languageOptions: { globals: globals.node, sourceType: "commonjs" },
  },

  eslintConfigPrettier,
);
