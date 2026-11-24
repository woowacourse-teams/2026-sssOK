import styled from "@emotion/styled";

import { colors } from "@/shared/styles/tokens";

/** 갤러리 페이지(`pages/gallery` 의 `Page`)와 같은 틀. 시트 뒤에서 같은 자리에 보이도록 맞춘다. */
export const Preview = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  background-color: ${colors.backgroundDefault};
  overflow: hidden;
`;
