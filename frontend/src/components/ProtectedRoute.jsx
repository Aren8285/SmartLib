import React, { useEffect, useState, useCallback } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/** Guards a route by role; students visiting librarian pages land on a clear notice page. */
export default function ProtectedRoute({ role, children }) {
  const { user, booting } = useAuth();
  const location = useLocation();

  if (booting) {
    return <div className="loading">Loading…</div>;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  if (role && user.role !== role) {
    return (
      <div className="not-allowed">
        <h1>Not authorised</h1>
        <p>
          This page is for {role === 'LIBRARIAN' ? 'librarians' : 'students'}.
          You are signed in as {user.role.toLowerCase()} (<strong>{user.email}</strong>).
        </p>
      </div>
    );
  }
  return children;
}
