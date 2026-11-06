import { startAppleLogin } from "../api/startAppleLogin";
import { useSocialLogin } from "./useSocialLogin";

export const useAppleLogin = () => useSocialLogin("apple", startAppleLogin);
