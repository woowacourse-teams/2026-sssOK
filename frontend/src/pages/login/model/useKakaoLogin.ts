import { startKakaoLogin } from "../api/startKakaoLogin";
import { useSocialLogin } from "./useSocialLogin";

export const useKakaoLogin = (): {
  login: () => Promise<void>;
  isPending: boolean;
  error: string;
} => useSocialLogin("kakao", startKakaoLogin);
