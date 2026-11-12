import { startAppleLogin } from "../api/startAppleLogin";
import { useSocialLogin } from "./useSocialLogin";

export const useAppleLogin = (): {
  login: () => Promise<void>;
  isPending: boolean;
  error: string;
} => useSocialLogin("apple", startAppleLogin);
