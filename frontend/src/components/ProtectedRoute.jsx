import React from 'react';
import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { BookX } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

/** Guards a route by role. No role = any signed-in user. */
export default function ProtectedRoute({ role, children }) {
  const { user, booting } = useAuth();
  const location = useLocation();

  if (booting) {
    return (
      <div className="not-found" role="status" aria-label="Restoring session">
        <div>
          <div className="spinner" style={{ justifyContent: 'center' }}>Loading…</div>
        </div>
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  if (role && user.role !== role) {
    return (
      <div className="not-allowed">
        <BookX size={28} aria-hidden="true" />
        <h1>Not authorised</h1>
        <p>
          This page is for {role === 'LIBRARIAN' ? 'librarians' : 'students'}.
          You are signed in as {user.role.toLowerCase()} (<strong>{user.email}</strong>).
        </p>
        <Link to={user.role === 'LIBRARIAN' ? '/librarian/dashboard' : '/dashboard'} className="btn btn--primary">
          Go to my dashboard
        </Link>
      </div>
    );
  }
  return children || <Outlet />;
}
