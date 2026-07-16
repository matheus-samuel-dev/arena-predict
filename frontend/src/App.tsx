import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { Brand } from "./components/Brand";
import { AccessDenied, AppShell } from "./components/AppShell";
import { AppDataProvider } from "./contexts/AppDataContext";
import { useAuth } from "./contexts/AuthContext";
import { AchievementsPage, RankingsPage, StatisticsPage } from "./pages/PerformancePages";
import { AdminDashboardPage, AdminResourcePage } from "./pages/AdminPages";
import { CommunityPage } from "./pages/CommunityPage";
import { DashboardPage } from "./pages/DashboardPage";
import { EventDetailsPage, EventsPage, LiveEventsPage } from "./pages/EventsPage";
import { HelpPage, LegalPage } from "./pages/HelpPage";
import { LoginPage } from "./pages/LoginPage";
import { NotificationsPage, ProfilePage } from "./pages/AccountPages";
import { PointsPage, PredictionsPage } from "./pages/PredictionsPage";
import { PoolsPage } from "./pages/PoolsPage";

function ProtectedRoute() {
  const { session, initializing } = useAuth();
  const location = useLocation();
  if (initializing) return <div className="app-loader"><Brand /><span className="loader-orbit" /><p>Preparando sua arena...</p></div>;
  if (!session) return <Navigate to="/login" replace state={{ from: location }} />;
  return <Outlet />;
}

function AdminRoute() {
  const { user } = useAuth();
  return user?.role === "ADMIN" ? <Outlet /> : <AccessDenied />;
}

function NotFoundPage() {
  const { session } = useAuth();
  return <div className="standalone-state"><Brand /><span className="standalone-state__code">404</span><h1>Essa arquibancada não existe</h1><p>O endereço pode ter mudado ou não estar mais disponível.</p><a className="button button--primary button--md" href={session ? "/app" : "/login"}>Voltar para a Arena</a></div>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/terms" element={<LegalPage type="terms" />} />
      <Route path="/privacy" element={<LegalPage type="privacy" />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppDataProvider><AppShell /></AppDataProvider>}>
          <Route index element={<Navigate to="/app" replace />} />
          <Route path="/app" element={<DashboardPage />} />
          <Route path="/events" element={<EventsPage />} />
          <Route path="/events/:id" element={<EventDetailsPage />} />
          <Route path="/live" element={<LiveEventsPage />} />
          <Route path="/predictions" element={<PredictionsPage />} />
          <Route path="/points" element={<PointsPage />} />
          <Route path="/pools" element={<PoolsPage />} />
          <Route path="/leagues" element={<PoolsPage leaguesOnly />} />
          <Route path="/rankings" element={<RankingsPage />} />
          <Route path="/statistics" element={<StatisticsPage />} />
          <Route path="/achievements" element={<AchievementsPage />} />
          <Route path="/community" element={<CommunityPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/help" element={<HelpPage />} />
          <Route element={<AdminRoute />}>
            <Route path="/admin" element={<AdminDashboardPage />} />
            <Route path="/admin/:resource" element={<AdminResourcePage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
