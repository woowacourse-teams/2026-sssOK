import type { Meta, StoryObj } from "@storybook/react-webpack5";

import type { MediaItem } from "@/entities/media";
import type { OrganizeResult } from "../api/types";
import { SimilarReviewScreen } from "./SimilarReviewScreen";

const swatch = (hue: number, index: number): string =>
  `data:image/svg+xml;utf8,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="hsl(${hue},45%,${62 + (index % 3) * 8}%)"/></svg>`,
  )}`;

const resultOf = (sizes: number[]): OrganizeResult => {
  let next = 1;
  return {
    jobId: "job",
    kind: "SIMILAR",
    resultVersion: 1,
    photoCount: sizes.reduce((sum, size) => sum + size, 0),
    removedCount: 0,
    groups: sizes.map((size, groupIndex) => ({
      groupId: `s${groupIndex + 1}`,
      photoCount: size,
      photos: Array.from({ length: size }, (_, index) => {
        const mediaId = next++;
        return {
          mediaId,
          fileName: `IMG_${mediaId}.jpg`,
          thumbnailUrl: swatch(groupIndex * 110 + 20, index),
        } as MediaItem;
      }),
    })),
    unassigned: { groupId: null, photoCount: 0, photos: [] },
    errors: [],
  };
};

const meta = {
  title: "features/organize-media/SimilarReviewScreen",
  component: SimilarReviewScreen,
  args: {
    source: { state: "ready", result: resultOf([3, 6, 4]) },
    folderNames: [],
    onClose: () => {},
    onRetry: () => {},
    onSubmit: () => {},
  },
} satisfies Meta<typeof SimilarReviewScreen>;

export default meta;

export const Default: StoryObj<typeof meta> = {};
