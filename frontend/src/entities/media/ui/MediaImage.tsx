import type { ComponentPropsWithoutRef } from "react";

type MediaImageProps = Omit<ComponentPropsWithoutRef<"img">, "src" | "srcSet"> & {
  src?: string | null;
};

/** 백엔드가 내려준 presigned URL을 그대로 사용한다. */
export const MediaImage = ({ src, alt, ...props }: MediaImageProps) => (
  <img {...props} src={src ?? undefined} alt={alt} />
);
