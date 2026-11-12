import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { Toast, type ToastProps } from "./Toast";

export interface ToastOptions extends Pick<ToastProps, "message" | "tone" | "bottom"> {
  duration?: number;
}

interface ToastContextValue {
  showToast: (options: ToastOptions) => () => void;
  dismissToast: () => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export const ToastProvider = ({ children }: { children: ReactNode }) => {
  const [toast, setToast] = useState<ToastOptions | null>(null);
  const [container, setContainer] = useState<Element | null>(null);
  const showToast = useCallback((options: ToastOptions) => {
    const nextToast = { ...options };
    setToast(nextToast);
    // 다른 알림으로 교체됐다면 그대로 두고, 이 호출로 띄운 알림만 닫는다.
    return () => setToast((current) => (current === nextToast ? null : current));
  }, []);
  const dismissToast = useCallback(() => setToast(null), []);
  const value = useMemo(() => ({ showToast, dismissToast }), [showToast, dismissToast]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(dismissToast, toast.duration ?? 2000);
    return () => window.clearTimeout(timer);
  }, [toast, dismissToast]);

  // dialog의 최상위 레이어에서도 알림을 보여주고, 닫힌 뒤에는 본문으로 돌려놓는다.
  useLayoutEffect(() => {
    if (!toast) return;
    const updateContainer = (): void => {
      const dialogs = document.querySelectorAll("dialog[open]");
      const target = dialogs.item(dialogs.length - 1);
      setContainer(target);
    };
    updateContainer();
    const observer = new MutationObserver(updateContainer);
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["open"],
    });
    return () => observer.disconnect();
  }, [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {toast && <Toast {...toast} container={container} onClose={dismissToast} />}
    </ToastContext.Provider>
  );
};

export const useToast = (): ToastContextValue => {
  const context = useContext(ToastContext);
  if (!context) throw new Error("useToast는 ToastProvider 안에서 사용해야 합니다.");
  return context;
};
