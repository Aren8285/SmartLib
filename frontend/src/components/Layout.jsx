import React from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import NotificationBell from './NotificationBell';

const STUDENT_LINKS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/books', label: 'Books' },
  { to: '/my-loans', label: 'My Loans' },
  { to: '/my-fines', label: 'My Fines' },
  { to: '/profile', label: 'Profile' },
];

const LIBRARIAN_LINKS = [
  { to: '/librarian/dashboard', label: 'Dashboard' },
  { to: '/librarian/circulation', label: 'Circulation' },
  { to: '/librarian/reflections', label: 'Reflections' },
  { to: '/librarian/books', label: 'Books' },
  { to: '/librarian/fines', label: 'Fines' },
  { to: '/librarian/waitlists', label: 'Waitlists' },
  { to: '/librarian/users', label: 'Users' },
];

export default function Layout() {
  const { user, logout } = useAuth();
  const links = user?.role === 'LIBRARIAN' ? LIBRARIAN_LINKS : STUDENT_LINKS;

  return (
    <>
      <nav className="navbar">
        <div className="navbar__brand"><Link to="/">📚 SmartLib</Link></div>
        <div className="navbar__links">
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} className={({ isActive }) => (isActive ? 'active' : '')}>
              {l.label}
            </NavLink>
          ))}
        </div>
        {user && (
          <div className="navbar__user">
            {user.role === 'STUDENT' && <span className="points-badge" title="Loyalty points">⭐ {user.points} pts</span>}
            <NotificationBell />
            <span className="navbar__name">{user.name}</span>
            <button className="btn btn--small" onClick={logout}>Logout</button>
          </div>
        )}
      </nav>
      <main className="main-content">
        <Outlet />
      </main>
    </>
  );
}
