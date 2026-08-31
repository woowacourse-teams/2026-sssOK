import styled from "@emotion/styled";

import { colors, radius, spacing, typography } from "@/shared/styles/tokens";

export const Options = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${spacing[12]};
`;

/**
 * 카드 전체가 라디오 하나다. `<label>` 이 감싸고 있어서 어디를 눌러도 안의 `input` 이 선택된다.
 * 선택 상태는 `input:checked` 가 진실이고, 아래 색은 그걸 따라 그린 것뿐이다 —
 * 키보드·스크린리더가 읽는 것과 눈에 보이는 것이 어긋나지 않게 하려는 것이다.
 */
export const Option = styled.label<{ $selected: boolean }>`
  display: flex;
  align-items: center;
  gap: ${spacing[16]};
  padding: ${spacing[16]};
  border: 1.5px solid ${({ $selected }) => ($selected ? colors.primary : colors.borderDefault)};
  border-radius: ${radius[12]};
  cursor: pointer;

  /* 화면에서만 숨긴다. 지우거나 display:none 을 주면 키보드로 고를 수 없다. */
  input {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip-path: inset(50%);
    white-space: nowrap;
  }

  /*
   * 포커스 링은 숨긴 input 이 아니라 카드에 그린다. 안 그리면 어디에 있는지 보이지 않는다.
   *
   * focus-within 이 아니라 focus-visible 이어야 한다. 앞엣것은 마우스·터치로 눌러도
   * 걸려서, 고른 카드에 주황 테두리와 주황 링이 겹쳐 두 겹으로 보인다.
   * 키보드로 옮겨왔을 때만 링이 뜨면 된다.
   */
  &:has(input:focus-visible) {
    outline: 2px solid ${colors.borderPrimary};
    outline-offset: 2px;
  }
`;

export const IconSlot = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: ${radius[12]};
  background: ${colors.primarySubtle};
  color: ${colors.textAccent};

  svg {
    width: 20px;
    height: 20px;
  }
`;

export const Texts = styled.span`
  display: flex;
  flex-direction: column;
  gap: ${spacing[4]};
  /* 파일명이 길어도 카드를 밀지 않는다. */
  min-width: 0;
  flex: 1;
`;

export const Label = styled.span`
  font-size: ${typography.label2.fontSize};
  font-weight: ${typography.label2.fontWeight};
  line-height: ${typography.label2.lineHeight};
  color: ${colors.textStrong};
`;

export const Description = styled.span`
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: ${typography.caption3.fontSize};
  font-weight: ${typography.caption3.fontWeight};
  line-height: ${typography.caption3.lineHeight};
  color: ${colors.textSecondary};
`;

export const Check = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: ${radius.full};
  background: ${colors.primary};
  color: ${colors.textInverse};

  svg {
    width: 15px;
    height: 15px;
  }
`;

export const Notice = styled.p`
  margin-top: ${spacing[12]};
  font-size: ${typography.caption3.fontSize};
  font-weight: ${typography.caption3.fontWeight};
  line-height: ${typography.caption3.lineHeight};
  color: ${colors.textSecondary};
`;

export const Submit = styled.button`
  width: 100%;
  height: 56px;
  margin-top: ${spacing[20]};
  border-radius: ${radius[12]};
  background: ${colors.primary};
  font-size: ${typography.label2.fontSize};
  font-weight: ${typography.label2.fontWeight};
  line-height: ${typography.label2.lineHeight};
  color: ${colors.textInverse};

  &:active {
    background: ${colors.primaryPressed};
  }
`;
