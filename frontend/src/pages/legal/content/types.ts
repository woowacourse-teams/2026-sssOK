import type { ReactNode } from "react";

export interface LegalDocument {
  title: string;
  version: string;
  effectiveDate: string;
  introduction: string;
  sections: { id: string; title: string; content: ReactNode }[];
}

export const LEGAL_CONTACT_EMAIL = "yundoll777@gmail.com";
