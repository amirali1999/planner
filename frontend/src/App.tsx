import { Navigate, Route, Routes } from 'react-router';
import { ActivityLogPage } from './features/activity/ActivityLogPage';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { GoalDetailPage } from './features/goals/GoalDetailPage';
import { GoalsPage } from './features/goals/GoalsPage';
import { GoalTreePage } from './features/goals/GoalTreePage';
import { TodayBoardPage } from './features/planning/TodayBoardPage';
import { ArchivePage } from './features/archive/ArchivePage';
import { WeeklyPlanPage } from './features/planning/WeeklyPlanPage';
import { SettingsPage } from './features/settings/SettingsPage';
import { FocusDetailPage } from './features/focus/FocusDetailPage';
import { FocusPage } from './features/focus/FocusPage';
import { FocusTreePage } from './features/focus/FocusTreePage';
import { InboxPage } from './features/inbox/InboxPage';
import { TasksPage } from './features/tasks/TasksPage';
import { AuthPage } from './features/auth/AuthPage';
import { RequireAuth } from './features/auth/RequireAuth';
import { Layout } from './pages/Layout';
import { NotFoundPage } from './pages/NotFoundPage';

export function App() {
  return (
    <Routes>
      <Route path="login" element={<AuthPage mode="login" />} />
      <Route path="register" element={<AuthPage mode="register" />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="goals" element={<GoalsPage />} />
        <Route path="goals/tree" element={<GoalTreePage />} />
        <Route path="goals/:goalId" element={<GoalDetailPage />} />
        <Route path="tasks" element={<TasksPage />} />
        <Route path="inbox" element={<InboxPage />} />
        <Route path="focus" element={<FocusTreePage />} />
        <Route path="weekly" element={<WeeklyPlanPage />} />
        <Route path="focus/list" element={<FocusPage />} />
        <Route path="focus/:focusItemId" element={<FocusDetailPage />} />
        <Route path="today" element={<TodayBoardPage />} />
        <Route path="activity-log" element={<ActivityLogPage />} />
        <Route path="archive" element={<ArchivePage />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
