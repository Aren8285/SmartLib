import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { BookOpen, Clock3, ListOrdered, Map, Sparkles, Star } from '../components/icons';
import { useAuth } from '../context/AuthContext';
import { Alert, Button, Input, PageHeader } from '../components/ui';

export default function Login() {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const banner = location.state?.message;

  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (user) navigate(user.role === 'LIBRARIAN' ? '/librarian/dashboard' : '/dashboard', { replace: true });
  }, [user, navigate]);

  const validate = () => {
    const next = {};
    if (!form.email.trim()) next.email = 'Email is required.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Enter a valid email address.';
    if (!form.password) next.password = 'Password is required.';
    return next;
  };

  const submit = async (event) => {
    event.preventDefault();
    setServerError('');
    const next = validate();
    setErrors(next);
    if (Object.keys(next).length) return;
    setSubmitting(true);
    try {
      const signedIn = await login(form.email.trim(), form.password);
      navigate(signedIn.role === 'LIBRARIAN' ? '/librarian/dashboard' : '/dashboard');
    } catch (error) {
      setServerError(error.message);
    } finally {
      setSubmitting(false);
    }
  };

  const fillDemo = (role) => {
    setForm({ email: role === 'librarian' ? 'meera@smartlib.test' : 'aarav@smartlib.test', password: 'Password123' });
    setErrors({});
    setServerError('');
  };

  return (
    <div className="auth-shell">
      <aside className="auth-aside">
        <Link to="/" className="brand">
          <span className="brand__mark"><BookOpen size={18} /></span>
          <span>SmartLib<span className="brand__sub">University Library</span></span>
        </Link>
        <div>
          <div className="eyebrow" style={{ color: '#e9c18c' }}>Welcome back</div>
          <h1>Your next book is waiting.</h1>
          <p>Sign in to reserve titles, track due dates, pay fines and earn reading points.</p>
          <ul className="auth-aside__features">
            <li><ListOrdered size={16} /> Transparent, first-come queue</li>
            <li><Clock3 size={16} /> Live pickup countdowns</li>
            <li><Map size={16} /> Shelf-level location map</li>
            <li><Sparkles size={16} /> Recommendations from your reading</li>
          </ul>
        </div>
        <p style={{ fontSize: '0.75rem' }}>SmartLib · Iteration 4 demo</p>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <PageHeader eyebrow="Sign in" title="SmartLib" subtitle="Use your university library account." />
          {banner && <Alert tone="info">{banner}</Alert>}
          {serverError && <Alert tone="error" title="Could not sign in">{serverError}</Alert>}
          <form onSubmit={submit} noValidate style={{ marginTop: 16 }}>
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              autoFocus
              value={form.email}
              error={errors.email}
              placeholder="you@university.edu"
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
            <Input
              label="Password"
              type="password"
              autoComplete="current-password"
              value={form.password}
              error={errors.password}
              placeholder="Your password"
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
            <Button type="submit" variant="primary" loading={submitting} className="btn--block">
              {submitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>

          <div className="auth-demo">
            Demo accounts (password <code>Password123</code>):
            <div className="row" style={{ marginTop: 6 }}>
              <button type="button" className="chip" onClick={() => fillDemo('student')}>Student — Aarav</button>
              <button type="button" className="chip" onClick={() => fillDemo('librarian')}>Librarian — Meera</button>
            </div>
          </div>

          <p className="auth-switch">New here? <Link to="/register">Create a student account</Link></p>
        </div>
      </main>
    </div>
  );
}
