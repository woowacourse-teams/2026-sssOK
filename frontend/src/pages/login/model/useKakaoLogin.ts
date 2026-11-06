import { startKakaoLogin } from "../api/startKakaoLogin";
import { useSocialLogin } from "./useSocialLogin";

export const useKakaoLogin = () => useSocialLogin("kakao", startKakaoLogin);
