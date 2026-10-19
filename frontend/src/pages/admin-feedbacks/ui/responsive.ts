import { spacing } from "@/shared/styles/tokens";

export const NARROW = "@media (max-width: 599px)";

/** 좁은 화면에서 칩·탭 줄을 줄바꿈 대신 옆으로 밀고, 오른쪽 끝을 흐려 더 있음을 보여준다. */
export const edgeScrollRow = `
  flex-wrap: nowrap;
  margin-inline: 0;
  padding-inline: ${spacing[16]};
  overflow-x: auto;
  scrollbar-width: none;
  mask-image: linear-gradient(to right, #000 calc(100% - 28px), transparent);

  &::-webkit-scrollbar {
    display: none;
  }

  & > * {
    flex-shrink: 0;
  }
`;
