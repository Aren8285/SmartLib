import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import StudentDashboard from './pages/StudentDashboard';
import BooksPage from './pages/BooksPage';
import MyLoansPage from './pages/MyLoansPage';
import MyFinesPage from './pages/MyFinesPage';
import Profile from './pages/Profile';
import LibrarianDashboard from './pages/LibrarianDashboard';
import CirculationPage from './pages/CirculationPage';
import ReflectionsPage from './pages/ReflectionsPage';
import ManageBooksPage from './pages/ManageBooksPage';
import WaitlistsPage from './pages/WaitlistsPage';
import UsersPage from './pages/UsersPage';
import LibrarianFinesPage from './pages/FinesPage';

function HomeRedirect() {
  return <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public */}
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          {/* Student area */}
          <Route element={<ProtectedRoute role="STUDENT"><Layout /></ProtectedRoute>}>
            <Route path="/dashboard" element={<StudentDashboard />} />
            <Route path="/books" element={<BooksPage />} />
            <Route path="/my-loans" element={<MyLoansPage />} />
            <Route path="/my-fines" element={<MyFinesPage />} />
            <Route path="/profile" element={<Profile />} />
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
          </Route>

          {/* Librarian area */}
          <Route element={<ProtectedRoute role="LIBRARIAN"><Layout /></ProtectedRoute>}>
            <Route path="/librarian/dashboard" element={<LibrarianDashboard />} />
            <Route path="/librarian/circulation" element={<CirculationPage />} />
            <Route path="/librarian/reflections" element={<ReflectionsPage />} />
            <Route path="/librarian/books" element={<ManageBooksPage />} />
            <Route path="/librarian/fines" element={<LibrarianFinesPage />} />
            <Route path="/librarian/waitlists" element={<WaitlistsPage />} />
            <Route path="/librarian/users" element={<UsersPage />} />
            <Route path="/librarian" element={<Navigate to="/librarian/dashboard" replace />} />
          </Route>

          <Route path="*" element={<HomeRedirect />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
