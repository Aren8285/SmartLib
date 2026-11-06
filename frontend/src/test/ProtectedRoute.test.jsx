import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ProtectedRoute from '../components/ProtectedRoute';

let auth = { user: null, booting: false };

vi.mock('../context/AuthContext', () => ({
  useAuth: () => auth,
}));

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>Login screen</div>} />
        <Route
          path="/protected"
          element={(
            <ProtectedRoute role="STUDENT">
              <div>Secret student content</div>
            </ProtectedRoute>
          )}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  it('redirects a signed-out visitor to login', () => {
    auth = { user: null, booting: false };
    renderAt('/protected');
    expect(screen.getByText('Login screen')).toBeInTheDocument();
  });

  it('waits while the session is being restored', () => {
    auth = { user: null, booting: true };
    renderAt('/protected');
    expect(screen.getByLabelText('Restoring session')).toBeInTheDocument();
    expect(screen.queryByText('Login screen')).not.toBeInTheDocument();
  });

  it('renders the page for the right role', () => {
    auth = { user: { name: 'Aarav', role: 'STUDENT', email: 'aarav@smartlib.test' }, booting: false };
    renderAt('/protected');
    expect(screen.getByText('Secret student content')).toBeInTheDocument();
  });

  it('blocks a student from a librarian-only page', () => {
    auth = { user: { name: 'Aarav', role: 'STUDENT', email: 'aarav@smartlib.test' }, booting: false };
    render(
      <MemoryRouter>
        <Routes>
          <Route path="/" element={<ProtectedRoute role="LIBRARIAN"><div>Staff only</div></ProtectedRoute>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('Not authorised')).toBeInTheDocument();
    expect(screen.queryByText('Staff only')).not.toBeInTheDocument();
  });
});
