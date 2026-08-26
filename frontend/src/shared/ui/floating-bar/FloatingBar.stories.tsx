import type { Meta, StoryObj } from "@storybook/react-webpack5";
import styled from "@emotion/styled";
import { LuLoaderCircle } from "react-icons/lu";
import { colors, radius, spacing, typography } from "@/shared/styles/tokens";
import { FloatingBar } from "./FloatingBar";

const meta = {
  title: "shared/ui/FloatingBar",
  component: FloatingBar,
} satisfies Meta<typeof FloatingBar>;

export default meta;
type Story = StoryObj<typeof meta>;

const Count = styled.span`
  flex-shrink: 0;
  font-size: ${typography.label2.fontSize};
  font-weight: ${typography.label2.fontWeight};
  line-height: ${typography.label2.lineHeight};
  color: ${colors.textStrong};
`;

const Status = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${spacing[4]};
  flex: 1;
  padding: ${spacing[8]} ${spacing[12]};
  border-radius: ${radius.full};
  background: ${colors.primarySubtle};
  font-size: ${typography.caption1.fontSize};
  font-weight: ${typography.caption1.fontWeight};
  line-height: ${typography.caption1.lineHeight};
  color: ${colors.textAccent};

  svg {
    width: 16px;
    height: 16px;
  }

  .spin {
    animation: spin 1s linear infinite;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
`;

const Cancel = styled.button`
  flex-shrink: 0;
  font-size: ${typography.label2.fontSize};
  font-weight: ${typography.label2.fontWeight};
  line-height: ${typography.label2.lineHeight};
  color: ${colors.textStrong};
`;

export const Default: Story = {
  args: {
    children: (
      <>
        <Count>20 / 24</Count>
        <Status>
          <LuLoaderCircle className="spin" />
          업로드 중... 62%
        </Status>
        <Cancel type="button">취소</Cancel>
      </>
    ),
  },
};
