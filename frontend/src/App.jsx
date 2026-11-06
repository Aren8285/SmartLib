import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { ToastProvider } from './context/ToastContext';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import ErrorBoundary from './components/ErrorBoundary';

import LandingPage from './pages/LandingPage';
import Login from './pages/Login';
import Register from './pages/Register';
import StudentDashboard from './pages/StudentDashboard';
import BooksPage from './pages/BooksPage';
import BookDetailsPage from './pages/BookDetailsPage';
import MyLoansPage from './pages/MyLoansPage';
import MyFinesPage from './pages/MyFinesPage';
import Profile from './pages/Profile';
import LibraryMapPage from './pages/LibraryMapPage';
import LeaderboardPage from './pages/LeaderboardPage';
import LibrarianDashboard from './pages/LibrarianDashboard';
import CirculationPage from './pages/CirculationPage';
import ReportsPage from './pages/ReportsPage';
import ReflectionsPage from './pages/ReflectionsPage';
import ManageBooksPage from './pages/ManageBooksPage';
import ShelvesPage from './pages/ShelvesPage';
import WaitlistsPage from './pages/WaitlistsPage';
import UsersPage from './pages/UsersPage';
import LibrarianFinesPage from './pages/FinesPage';
import NotFoundPage from './pages/NotFoundPage';

export default function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <ThemeProvider>
          <ToastProvider>
            <AuthProvider>
              <Routes>
                {/* Public */}
                <Route path="/" element={<LandingPage />} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />

                {/* Shared authenticated area */}
                <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
                  <Route path="/map" element={<LibraryMapPage />} />
                  <Route path="/leaderboard" element={<LeaderboardPage />} />
                  <Route path="/profile" element={<Profile />} />

                  {/* Student */}
                  <Route element={<ProtectedRoute role="STUDENT" />}>
                    <Route path="/dashboard" element={<StudentDashboard />} />
                    <Route path="/books" element={<BooksPage />} />
                    <Route path="/books/:id" element={<BookDetailsPage />} />
                    <Route path="/my-loans" element={<MyLoansPage />} />
                    <Route path="/my-fines" element={<MyFinesPage />} />
                  </Route>

                  {/* Librarian */}
                  <Route element={<ProtectedRoute role="LIBRARIAN" />}>
                    <Route path="/librarian/dashboard" element={<LibrarianDashboard />} />
                    <Route path="/librarian/circulation" element={<CirculationPage />} />
                    <Route path="/librarian/reports" element={<ReportsPage />} />
                    <Route path="/librarian/reflections" element={<ReflectionsPage />} />
                    <Route path="/librarian/books" element={<ManageBooksPage />} />
                    <Route path="/librarian/shelves" element={<ShelvesPage />} />
                    <Route path="/librarian/fines" element={<LibrarianFinesPage />} />
                    <Route path="/librarian/waitlists" element={<WaitlistsPage />} />
                    <Route path="/librarian/users" element={<UsersPage />} />
                  </Route>

                  <Route path="/librarian" element={<Navigate to="/librarian/dashboard" replace />} />
                </Route>

                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </AuthProvider>
          </ToastProvider>
        </ThemeProvider>
      </BrowserRouter>
    </ErrorBoundary>
  );
}
