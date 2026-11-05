import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { RouterProvider, createBrowserRouter } from "react-router-dom";

import { AppPushNotifications } from "@/features/app-push";
import { isAppWebView } from "@/shared/lib";
import { routes } from "./routes";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 30,
      retry: 1,
    },
  },
});

export const App = () => {
  const [router] = useState(() => createBrowserRouter(routes));
  useEffect(() => {
    if (!isAppWebView()) return;
    const reportRoom = () => {
      const roomCode =
        /^\/rooms\/([A-Z2-9]{8})(?:\/|$)/.exec(router.state.location.pathname)?.[1] ?? null;
      window.ReactNativeWebView?.postMessage(
        JSON.stringify({ type: "push/current-room", roomCode }),
      );
    };
    reportRoom();
    return router.subscribe(reportRoom);
  }, [router]);
  return (
    <QueryClientProvider client={queryClient}>
      <div className="container">
        <RouterProvider router={router} />
      </div>
      {isAppWebView() && <AppPushNotifications />}
      {process.env.NODE_ENV === "development" && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  );
};
