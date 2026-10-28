import { HiArrowLeft } from "react-icons/hi2";
import { Link } from "react-router-dom";

import { ROUTES } from "@/shared/config";
import { privacyPolicy } from "../content/privacyPolicy";
import { termsOfService } from "../content/termsOfService";
import type { LegalDocument } from "../content/types";
import { Content, Header, Navigation, Page, Section, TableOfContents } from "./LegalPage.styles";

const LegalPage = ({ document }: { document: LegalDocument }) => (
  <Page>
    <Header>
      <Link to={ROUTES.login}>
        <HiArrowLeft aria-hidden="true" /> 로그인 화면
      </Link>
      <Navigation aria-label="약관 및 정책">
        <Link to={ROUTES.terms} aria-current={document === termsOfService ? "page" : undefined}>
          이용약관
        </Link>
        <Link to={ROUTES.privacy} aria-current={document === privacyPolicy ? "page" : undefined}>
          개인정보처리방침
        </Link>
      </Navigation>
    </Header>
    <Content>
      <h1>{document.title}</h1>
      <p>시행일: 공개 전 확정</p>
      <p>{document.introduction}</p>
      <TableOfContents>
        <summary>목차 펼치기</summary>
        <nav aria-label={`${document.title} 목차`}>
          <ol>
            {document.sections.map((section) => (
              <li key={section.id}>
                <a href={`#${section.id}`}>{section.title}</a>
              </li>
            ))}
          </ol>
        </nav>
      </TableOfContents>
      {document.sections.map((section) => (
        <Section key={section.id} id={section.id}>
          <h2>{section.title}</h2>
          {section.content}
        </Section>
      ))}
    </Content>
  </Page>
);

export const PrivacyPolicyPage = () => <LegalPage document={privacyPolicy} />;
export const TermsOfServicePage = () => <LegalPage document={termsOfService} />;
