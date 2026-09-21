import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const Page = styled.main`
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: ${spacing[24]};
  padding-block: ${spacing[24]};
`;

export const TitleRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${spacing[16]};
  padding-inline: ${spacing[16]};
`;

export const TitleGroup = styled.div`
  display: flex;
  align-items: center;
  gap: ${spacing[8]};
`;

export const Title = styled.h1`
  color: ${colors.textStrong};

  ${typography.heading2}
`;

export const Count = styled.span`
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding-inline: ${spacing[8]};
  border-radius: ${radius.full};
  background: ${colors.backgroundSubtle};
  color: ${colors.textSecondary};

  ${typography.caption2}
`;

/**
 * 목록 행 동작·헤더 버튼용 작은 버튼 (디자인의 `Admin/Button` Small).
 * 공용 Button 은 폼 제출 크기(55px)부터라 여기서 따로 둔다.
 */
export const SmallButton = styled.button<{ tone: "primary" | "default" | "danger" }>`
  flex-shrink: 0;
  height: 36px;
  padding-inline: 14px;
  border: ${({ tone }) => (tone === "primary" ? 0 : `1.25px solid ${colors.borderDefault}`)};
  border-radius: 10px;
  background: ${({ tone }) => (tone === "primary" ? colors.primary : colors.backgroundDefault)};
  color: ${({ tone }) =>
    tone === "primary"
      ? colors.textInverse
      : tone === "danger"
        ? colors.danger
        : colors.textStrong};
  white-space: nowrap;

  ${typography.caption1}

  font-weight: 700;

  &:disabled {
    cursor: not-allowed;
  }
`;

export const RowButton = styled(SmallButton)`
  width: 88px;
`;

/** 표는 좁은 화면에서 줄이지 않고 옆으로 밀어 본다. 칸을 줄이면 아이디가 잘린다. */
export const TableScroll = styled.div`
  overflow-x: auto;
  padding-inline: ${spacing[16]};
`;

export const Table = styled.table`
  width: 100%;
  min-width: 880px;
  border-collapse: collapse;
`;

export const HeadCell = styled.th<{ width?: number }>`
  width: ${({ width }) => (width ? `${width}px` : "auto")};
  padding: 0 ${spacing[16]} ${spacing[12]};
  border-bottom: 1px solid ${colors.borderDefault};
  color: ${colors.textSecondary};
  text-align: left;

  ${typography.caption1}
`;

export const Row = styled.tr`
  height: 68px;
  border-bottom: 1px solid ${colors.borderDefault};
`;

export const Cell = styled.td`
  padding-inline: ${spacing[16]};
  vertical-align: middle;
`;

export const NameCell = styled.div`
  display: flex;
  align-items: center;
  gap: ${spacing[8]};
`;

export const Name = styled.span`
  color: ${colors.textStrong};
  white-space: nowrap;

  ${typography.label4}

  line-height: 24px;
`;

export const MeTag = styled.span`
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding-inline: ${spacing[8]};
  border: 1px solid ${colors.borderDefault};
  border-radius: ${radius.full};
  color: ${colors.textSecondary};

  ${typography.caption4}
`;

export const LoginId = styled.span`
  color: ${colors.textPrimary};

  ${typography.caption1}

  font-weight: 500;
`;

export const CreatedAt = styled.span`
  color: ${colors.textSecondary};

  ${typography.caption1}

  font-weight: 500;
`;

export const Actions = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: ${spacing[8]};
`;

export const Notice = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: ${spacing[12]};
  padding-inline: ${spacing[16]};
  color: ${colors.textSecondary};

  ${typography.body}
`;
