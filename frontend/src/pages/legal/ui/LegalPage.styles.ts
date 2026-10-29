import styled from "@emotion/styled";

import { colors } from "@/shared/styles/tokens";

export const Page = styled.main`
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 100svh;
  color: ${colors.textPrimary};
  background: ${colors.backgroundDefault};
  overflow-wrap: anywhere;
  word-break: keep-all;

  a {
    color: inherit;
    text-underline-offset: 4px;
  }
  a:focus-visible,
  summary:focus-visible {
    outline: 2px solid ${colors.primary};
    outline-offset: 5px;
  }
`;

export const Header = styled.header`
  box-sizing: border-box;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
  max-width: 1040px;
  margin: 0 auto;
  padding: 24px;
  border-bottom: 1px solid ${colors.borderDefault};
  font-size: 13px;
  line-height: 24px;

  > a {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    text-decoration: none;
  }
  @media (max-width: 480px) {
    padding: 16px 20px;
    gap: 12px;
  }
`;

export const Navigation = styled.nav`
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  a {
    color: ${colors.textSecondary};
    text-decoration: none;
  }
  [aria-current="page"] {
    color: ${colors.textStrong};
    font-weight: 700;
    text-decoration: underline;
    text-decoration-color: ${colors.primary};
    text-decoration-thickness: 2px;
    text-underline-offset: 8px;
  }
`;

export const Content = styled.article`
  box-sizing: border-box;
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
  padding: 64px 24px 100px;
  font-size: 15px;
  line-height: 1.85;
  font-weight: 400;

  h1 {
    color: ${colors.textStrong};
    font-size: clamp(28px, 5vw, 38px);
    font-weight: 750;
    letter-spacing: -0.04em;
    line-height: 1.4;
    margin-bottom: 12px;
  }
  > h1 + p {
    color: ${colors.textSecondary};
    font-size: 13px;
  }
  p + p {
    margin-top: 12px;
  }
  ul,
  ol {
    padding-left: 22px;
    list-style: revert;
  }
  li + li {
    margin-top: 8px;
  }
  @media (max-width: 600px) {
    padding: 36px 20px 64px;
    font-size: 14px;
  }
`;

export const TableOfContents = styled.details`
  margin: 32px 0 48px;
  padding: 20px 0;
  summary {
    cursor: pointer;
    font-size: 14px;
    font-weight: 650;
    color: ${colors.textStrong};
  }
  nav {
    margin-top: 20px;
  }
  ol {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 24px;
    padding: 0;
    list-style: none;
  }
  li + li {
    margin: 0;
  }
  a {
    font-size: 13px;
    text-decoration: none;
  }
  a:hover {
    color: ${colors.primaryPressed};
    text-decoration: underline;
  }
  @media (max-width: 600px) {
    ol {
      grid-template-columns: 1fr;
    }
  }
`;

export const Section = styled.section`
  padding-top: 32px;
  margin-top: 32px;
  border-top: 1px solid ${colors.borderDefault};
  scroll-margin-top: 24px;
  h2 {
    color: ${colors.textStrong};
    font-size: 20px;
    line-height: 1.5;
    font-weight: 700;
    letter-spacing: -0.025em;
    margin-bottom: 20px;
  }
  h3 {
    color: ${colors.textStrong};
    font-size: 15px;
    font-weight: 650;
    margin: 28px 0 8px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 20px 0;
    table-layout: fixed;
    font-size: 13px;
    line-height: 1.75;
  }
  caption {
    text-align: left;
    margin-bottom: 8px;
  }
  th,
  td {
    padding: 14px 12px;
    text-align: left;
    vertical-align: top;
    border-bottom: 1px solid ${colors.borderDefault};
    overflow-wrap: anywhere;
    word-break: normal;
  }
  thead {
    color: ${colors.textStrong};
    background: ${colors.backgroundSubtle};
  }
  th {
    font-weight: 600;
  }
  th:first-of-type {
    width: 24%;
  }
  @media (max-width: 600px) {
    h2 {
      font-size: 18px;
    }
    th,
    td {
      padding: 10px 6px;
      font-size: 12px;
    }
  }
`;
