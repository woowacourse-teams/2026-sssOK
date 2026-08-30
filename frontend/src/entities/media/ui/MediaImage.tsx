import type { ComponentPropsWithoutRef } from "react";

import { resolveMediaSource } from "../lib/resolveMediaSource";

type MediaImageProps = Omit<ComponentPropsWithoutRef<"img">, "src" | "srcSet"> & {
  src?: string | null;
};

/** 이미지 경로에 API 베이스 URL만 붙여 브라우저가 직접 요청하게 한다. */
export const MediaImage = ({ src, alt, ...props }: MediaImageProps) => (
  <img {...props} src={resolveMediaSource(src)} alt={alt} />
);
