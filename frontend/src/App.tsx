import { lazy, Suspense } from "react";
import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { Brand } from "./components/Brand";
import { AccessDenied, AppShell } from "./components/AppShell";
import { AppDataProvider } from "./contexts/AppDataContext";
import { useAuth } from "./contexts/AuthContext";

const LoginPage = lazy(() => import("./pages/LoginPage").then((module) => ({ default: module.LoginPage })));
const DashboardPage = lazy(() => import("./pages/DashboardPage").then((module) => ({ default: module.DashboardPage })));
const EventsPage = lazy(() => import("./pages/EventsPage").then((module) => ({ default: module.EventsPage })));
const EventDetailsPage = lazy(() => import("./pages/EventsPage").then((module) => ({ default: module.EventDetailsPage })));
const LiveEventsPage = lazy(() => import("./pages/EventsPage").then((module) => ({ default: module.LiveEventsPage })));
const PredictionsPage = lazy(() => import("./pages/PredictionsPage").then((module) => ({ default: module.PredictionsPage })));
const PointsPage = lazy(() => import("./pages/PredictionsPage").then((module) => ({ default: module.PointsPage })));
const PoolsPage = lazy(() => import("./pages/PoolsPage").then((module) => ({ default: module.PoolsPage })));
const RankingsPage = lazy(() => import("./pages/PerformancePages").then((module) => ({ default: module.RankingsPage })));
const StatisticsPage = lazy(() => import("./pages/PerformancePages").then((module) => ({ default: module.StatisticsPage })));
const ChallengesPage = lazy(() => import("./pages/PerformancePages").then((module) => ({ default: module.ChallengesPage })));
const AchievementsPage = lazy(() => import("./pages/PerformancePages").then((module) => ({ default: module.AchievementsPage })));
const CommunityPage = lazy(() => import("./pages/CommunityPage").then((module) => ({ default: module.CommunityPage })));
const NotificationsPage = lazy(() => import("./pages/AccountPages").then((module) => ({ default: module.NotificationsPage })));
const ProfilePage = lazy(() => import("./pages/AccountPages").then((module) => ({ default: module.ProfilePage })));
const HelpPage = lazy(() => import("./pages/HelpPage").then((module) => ({ default: module.HelpPage })));
const LegalPage = lazy(() => import("./pages/HelpPage").then((module) => ({ default: module.LegalPage })));
const AdminDashboardPage = lazy(() => import("./pages/AdminPages").then((module) => ({ default: module.AdminDashboardPage })));
const AdminResourcePage = lazy(() => import("./pages/AdminPages").then((module) => ({ default: module.AdminResourcePage })));

function RouteLoader() {
  return <div className="app-loader"><Brand /><span className="loader-orbit" /><p>Carregando sua experiência...</p></div>;
}

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
    <Suspense fallback={<RouteLoader />}>
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
            <Route path="/challenges" element={<ChallengesPage />} />
            <Route path="/achievements" element={<AchievementsPage />} />
            <Route path="/community" element={<CommunityPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/account" element={<ProfilePage />} />
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
    </Suspense>
  );
}
