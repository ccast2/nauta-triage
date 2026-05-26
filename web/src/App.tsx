import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TenantProvider } from "@/lib/tenant-context";
import { Layout } from "@/components/Layout";
import DashboardPage from "@/pages/DashboardPage";
import ContainerNewPage from "@/pages/ContainerNewPage";
import ContainerDetailPage from "@/pages/ContainerDetailPage";
import SourcesPage from "@/pages/SourcesPage";
import SourceNewPage from "@/pages/SourceNewPage";
import SourceDetailPage from "@/pages/SourceDetailPage";
import StubsPage from "@/pages/StubsPage";
import TenantsPage from "@/pages/TenantsPage";

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 5_000, refetchOnWindowFocus: false } },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TenantProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Layout />}>
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="dashboard" element={<DashboardPage />} />
              <Route path="containers/new" element={<ContainerNewPage />} />
              <Route path="containers/:businessId" element={<ContainerDetailPage />} />
              <Route path="sources" element={<SourcesPage />} />
              <Route path="sources/new" element={<SourceNewPage />} />
              <Route path="sources/:id" element={<SourceDetailPage />} />
              <Route path="stubs" element={<StubsPage />} />
              <Route path="stubs/:sourceName" element={<StubsPage />} />
              <Route path="tenants" element={<TenantsPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </TenantProvider>
    </QueryClientProvider>
  );
}
