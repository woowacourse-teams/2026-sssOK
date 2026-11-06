import styled from "@emotion/styled";

import { colors, radius } from "@/shared/styles/tokens";

/*
 * 비슷한 사진 나누기 검토 화면의 이름 입력칸. 피그마 "AI-S2 · 이름 붙이기 전"(655:2696),
 * "AI-S3 · 이름 붙인 후"(655:2932) 값을 옮겼다. 나머지 틀은 사람별 검토(PersonReview.styles)와 같다.
 */

/** 자리표시 글자색. 토큰에 없는 시안 값이다. */
const PLACEHOLDER = "#757575";

export const NameField = styled.form`
  padding-top: 10px;
`;

/** 이름을 쓰면 주황 테두리(1.5px)로 바뀐다. 두께가 달라도 칸 높이는 44px 로 같다. */
export const NameInput = styled.input<{ $filled: boolean }>`
  display: block;
  width: 100%;
  height: 44px;
  padding: 0 14px;
  border: ${({ $filled }) =>
    $filled ? `1.5px solid ${colors.borderPrimary}` : `1px solid ${colors.borderDefault}`};
  border-radius: ${radius.full};
  outline: none;
  background: ${colors.backgroundDefault};
  font-size: 14px;
  font-weight: 700;
  color: ${colors.textStrong};

  &::placeholder {
    color: ${PLACEHOLDER};
  }

  &:focus {
    border-color: ${colors.borderPrimary};
  }

  /* iOS 는 16px 보다 작은 입력창에 포커스하면 화면을 확대하고 되돌리지 않는다. */
  @media (pointer: coarse) {
    font-size: 16px;
  }
`;
