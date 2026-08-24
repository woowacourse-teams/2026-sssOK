import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { colors, typography } from "@/shared/styles/tokens";
import { Modal } from "./Modal";

const meta = {
  title: "shared/ui/Modal",
  component: Modal,
} satisfies Meta<typeof Modal>;

export default meta;
type Story = StoryObj<typeof meta>;

export const DeleteConfirm: Story = {
  args: {
    onClose: () => {},
    children: (
      <div style={{ textAlign: "center" }}>
        <p
          style={{
            color: colors.textStrong,
            fontSize: typography.label1.fontSize,
            fontWeight: typography.label1.fontWeight,
            lineHeight: typography.label1.lineHeight,
          }}
        >
          {" "}
          사진 4장을 삭제할까요?
        </p>
        <p
          style={{
            marginTop: "16px",
            color: colors.textSecondary,
            fontSize: typography.body.fontSize,
            lineHeight: typography.body.lineHeight,
          }}
        >
          삭제한 사진은
          <br />
          모든 폴더에서 함께 삭제돼요.
        </p>
      </div>
    ),
  },
};
