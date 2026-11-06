import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { RouterProvider, createBrowserRouter } from "react-router-dom";

import { AppPushNotifications } from "@/features/app-push";
import { isAppWebView } from "@/shared/lib";
import { ToastProvider } from "@/shared/ui/toast";
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
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <div className="container">
          <RouterProvider router={router} />
        </div>
        {isAppWebView() && <AppPushNotifications />}
      </ToastProvider>
      {process.env.NODE_ENV === "development" && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  );
};
