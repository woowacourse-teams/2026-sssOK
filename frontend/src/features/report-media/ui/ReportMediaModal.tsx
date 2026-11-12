import { isApiError } from "@/shared/api";
import type { ReportReason } from "../api/reportMedia";
import { useReportMedia } from "../model/useReportMedia";
import { useId, useState } from "react";
import styled from "@emotion/styled";

import { colors, typography } from "@/shared/styles/tokens";
import { Button } from "@/shared/ui/button";
import { Modal } from "@/shared/ui/modal";
import { Row } from "@/shared/ui/row";
import { Stack } from "@/shared/ui/stack";

const reasons = [
  { value: "INAPPROPRIATE_CONTENT", label: "선정적·폭력적 콘텐츠" },
  { value: "PRIVACY_VIOLATION", label: "개인정보·초상권 침해" },
  { value: "COPYRIGHT_VIOLATION", label: "저작권 침해" },
  { value: "HARASSMENT", label: "괴롭힘·혐오 표현" },
] as const;

export const ReportMediaModal = ({
  container,
  roomId,
  mediaId,
  onClose,
  onSubmit,
}: {
  container?: Element | null;
  roomId: number;
  mediaId: number;
  onClose: () => void;
  onSubmit: () => void;
}) => {
  const [reason, setReason] = useState<ReportReason | null>(null);
  const groupName = useId();
  const mutation = useReportMedia({ roomId, mediaId, onSuccess: onSubmit });
  const close = (): void => {
    if (!mutation.isPending) onClose();
  };

  return (
    <Modal container={container} onClose={close} showClose={!mutation.isPending} size="md">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (reason !== null && mutation.canReport && !mutation.isPending) mutation.mutate(reason);
        }}
      >
        <Stack gap={20}>
          <Title>사진 신고</Title>
          <Options>
            <Legend>신고 사유를 선택해 주세요.</Legend>
            {reasons.map((option) => (
              <Option key={option.value}>
                <input
                  type="radio"
                  name={groupName}
                  value={option.value}
                  checked={reason === option.value}
                  disabled={mutation.isPending}
                  onChange={() => {
                    setReason(option.value);
                    mutation.reset();
                  }}
                />
                <span>{option.label}</span>
              </Option>
            ))}
          </Options>
          {mutation.sessionError && (
            <ErrorMessage role="alert">{mutation.sessionError}</ErrorMessage>
          )}
          {mutation.isError && (
            <ErrorMessage role="alert">
              {isApiError(mutation.error)
                ? mutation.error.code === "MEDIA_ALREADY_REPORTED"
                  ? "이미 신고한 사진이에요."
                  : mutation.error.message
                : "신고를 접수하지 못했어요. 다시 시도해 주세요."}
            </ErrorMessage>
          )}
          <Row gap={12}>
            <Button variant="default" onClick={close} disabled={mutation.isPending}>
              취소
            </Button>
            <Button
              type="submit"
              variant="danger"
              disabled={reason === null || !mutation.canReport || mutation.isPending}
              aria-busy={mutation.isPending}
            >
              {mutation.isPending ? "신고 중…" : "신고"}
            </Button>
          </Row>
        </Stack>
      </form>
    </Modal>
  );
};

const Title = styled.h2`
  text-align: center;
  ${typography.heading3}
`;
const Options = styled.fieldset`
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
`;
const Legend = styled.legend`
  margin-bottom: 12px;
  color: ${colors.textSecondary};
  ${typography.body}
`;
const Option = styled.label`
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  margin-top: 8px;
  padding: 12px;
  border: 1px solid ${colors.borderDefault};
  border-radius: 12px;
  cursor: pointer;
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease,
    background-color 150ms ease;
  ${typography.body}

  &:has(input:checked) {
    border-color: ${colors.borderPrimary};
    box-shadow: inset 0 0 0 1px ${colors.borderPrimary};
    background: ${colors.primarySubtle};
  }
  &:has(input:focus-visible) {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: -2px;
  }
  input {
    appearance: none;
    flex: none;
    width: 18px;
    height: 18px;
    margin: 0;
    border: 1.5px solid ${colors.borderDefault};
    border-radius: 50%;
    outline: none;
    background: ${colors.backgroundDefault};

    &:checked {
      border-color: ${colors.primary};
      background: ${colors.primary};
      box-shadow: inset 0 0 0 4px ${colors.backgroundDefault};
    }
  }
`;

const ErrorMessage = styled.p`
  color: ${colors.danger};
  ${typography.caption1}
`;
