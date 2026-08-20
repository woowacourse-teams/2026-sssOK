import { Global, css } from "@emotion/react";
import "./fonts/fonts.css";
import { colors } from "./tokens";

export const GlobalStyles = () => (
  <Global
    styles={css`
      /* Box sizing */
      *,
      *::before,
      *::after {
        box-sizing: border-box;
      }

      /* Remove default spacing */
      * {
        margin: 0;
        padding: 0;
      }

      /* Prevent mobile browser font inflation */
      html {
        -webkit-text-size-adjust: 100%;
        text-size-adjust: 100%;
      }

      html,
      body,
      #root {
        min-height: 100%;
      }

      /* Global typography */
      body {
        font-family:
          "Pretendard",
          -apple-system,
          BlinkMacSystemFont,
          system-ui,
          sans-serif;

        color: ${colors.textPrimary};
        background-color: ${colors.backgroundDefault};

        -webkit-font-smoothing: antialiased;
      }

      /* Form elements */
      button,
      input,
      textarea,
      select {
        font: inherit;
        color: inherit;
      }

      button {
        border: 0;
        background: none;
        cursor: pointer;
      }

      input,
      textarea {
        border: 0;
      }

      /* Lists */
      ul,
      ol {
        list-style: none;
      }

      /* Links */
      a {
        color: inherit;
        text-decoration: none;
      }

      /* Media */
      img,
      picture,
      video,
      canvas,
      svg {
        display: block;
        max-width: 100%;
      }

      /* Tables */
      table {
        border-collapse: collapse;
        border-spacing: 0;
      }

      /* Prevent long text from breaking layouts */
      p,
      h1,
      h2,
      h3,
      h4,
      h5,
      h6 {
        overflow-wrap: break-word;
      }

      /* React root stacking context */
      #root {
        isolation: isolate;
      }
    `}
  />
);
