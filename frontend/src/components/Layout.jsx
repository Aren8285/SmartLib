import React, { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  BarChart3, BookOpen, BookMarked, ClipboardList, Compass, LayoutDashboard,
  Library, ListOrdered, LogOut, Map, Menu, Moon, Sun, Users, Wallet, X, PenLine,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import NotificationBell from './NotificationBell';

const STUDENT_LINKS = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/books', label: 'Catalogue', icon: BookOpen },
  { to: '/map', label: 'Library map', icon: Map },
  { to: '/my-loans', label: 'My loans', icon: BookMarked },
  { to: '/my-fines', label: 'Fines', icon: Wallet },
  { to: '/leaderboard', label: 'Leaderboard', icon: BarChart3 },
];

const LIBRARIAN_LINKS = [
  { to: '/librarian/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/librarian/circulation', label: 'Circulation', icon: ClipboardList },
  { to: '/librarian/books', label: 'Books', icon: BookOpen },
  { to: '/librarian/shelves', label: 'Shelves', icon: Library },
  { to: '/librarian/reports', label: 'Reports', icon: BarChart3 },
  { to: '/librarian/reflections', label: 'Reflections', icon: PenLine },
  { to: '/librarian/fines', label: 'Fines', icon: Wallet },
  { to: '/librarian/waitlists', label: 'Waitlists', icon: ListOrdered },
  { to: '/librarian/users', label: 'Users', icon: Users },
  { to: '/map', label: 'Map', icon: Compass },
];

function NavItems({ links, onNavigate }) {
  return links.map((link) => {
    const Icon = link.icon;
    return (
      <NavLink
        key={link.to}
        to={link.to}
        className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
        onClick={onNavigate}
      >
        <Icon size={14} style={{ display: 'inline', marginRight: 5, verticalAlign: '-2px' }} aria-hidden="true" />
        {link.label}
      </NavLink>
    );
  });
}

export default function Layout() {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const links = user?.role === 'LIBRARIAN' ? LIBRARIAN_LINKS : STUDENT_LINKS;

  useEffect(() => { setMenuOpen(false); }, [location.pathname]);

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar__inner">
          <Link to={user?.role === 'LIBRARIAN' ? '/librarian/dashboard' : '/dashboard'} className="brand" aria-label="SmartLib home">
            <span className="brand__mark"><BookOpen size={18} aria-hidden="true" /></span>
            <span>
              SmartLib
              <span className="brand__sub">University Library</span>
            </span>
          </Link>

          <nav className="nav" aria-label="Main navigation">
            <NavItems links={links} />
          </nav>

          <div className="topbar__actions">
            {user?.role === 'STUDENT' && (
              <span className="points-pill" title="Loyalty points">★ {user.points} pts</span>
            )}
            <NotificationBell />
            <button
              type="button"
              className="icon-btn"
              onClick={toggleTheme}
              aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
              title={theme === 'dark' ? 'Light theme' : 'Dark theme'}
            >
              {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <div className="user-chip">
              <span className="avatar" aria-hidden="true">
                {(user?.name || '?').split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase()}
              </span>
              <span className="user-chip__name">{user?.name}</span>
              <button type="button" className="icon-btn" onClick={logout} aria-label="Log out" title="Log out">
                <LogOut size={17} />
              </button>
            </div>
            <button
              type="button"
              className="icon-btn nav-toggle"
              onClick={() => setMenuOpen((o) => !o)}
              aria-expanded={menuOpen}
              aria-controls="mobile-nav"
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            >
              {menuOpen ? <X size={19} /> : <Menu size={19} />}
            </button>
          </div>
        </div>
      </header>

      <div id="mobile-nav" className={`mobile-nav ${menuOpen ? 'open' : ''}`}>
        <div className="mobile-nav__links">
          <NavItems links={links} onNavigate={() => setMenuOpen(false)} />
        </div>
        <div className="mobile-nav__footer">
          <span className="text-muted" style={{ fontSize: '0.78rem' }}>
            {user?.name} · {user?.role?.toLowerCase()}
          </span>
          <button type="button" className="btn btn--ghost btn--sm" onClick={logout}>
            <LogOut size={14} /> Log out
          </button>
        </div>
      </div>

      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
