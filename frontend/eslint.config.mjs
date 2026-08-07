import eslint from "@eslint/js";
import tseslint from "typescript-eslint";
import prettier from "eslint-config-prettier";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import eslintConfigPrettier from "eslint-config-prettier";

export default tseslint.config(
  { ignores: ["dist", "node_modules"] },

  // src: 브라우저 + React + TS
  {
    files: ["src/**/*.{ts,tsx}"],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      react.configs.flat.recommended,
      react.configs.flat["jsx-runtime"], // ← react-in-jsx-scope 끔
      reactHooks.configs.flat.recommended,
    ],
    languageOptions: { globals: globals.browser },
    settings: { react: { version: "detect" } },
  },

  // 루트 설정 파일: Node CJS
  {
    files: ["*.js", "*.cjs"],
    extends: [eslint.configs.recommended],
    languageOptions: { globals: globals.node, sourceType: "commonjs" },
  },

  prettier,
  eslintConfigPrettier,
);
