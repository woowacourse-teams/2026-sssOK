import type { Preview } from "@storybook/react-webpack5";
import { mswLoader } from "msw-storybook-addon/csf3";

import { handlers } from "../src/mocks/handlers";
import { GlobalStyles } from "../src/shared/styles/GlobalStyles";

const preview: Preview = {
  loaders: [mswLoader()],

  decorators: [
    (Story) => (
      <>
        <GlobalStyles />
        <Story />
      </>
    ),
  ],

  parameters: {
    msw: handlers,

    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
};

export default preview;
