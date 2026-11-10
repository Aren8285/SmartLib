import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BookOpen, ShieldCheck, Sparkles, Star } from '../components/icons';
import { useAuth } from '../context/AuthContext';
import { getSubjects } from '../api';
import { Alert, Button, Chip, Input, PageHeader } from '../components/ui';

const EMPTY = { name: '', email: '', password: '', confirm: '', preferredSubjects: [] };

export default function Register() {
  const { register, user } = useAuth();
  const navigate = useNavigate();
  const [subjects, setSubjects] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getSubjects().then(setSubjects).catch(() => {});
    if (user) navigate('/dashboard', { replace: true });
  }, [user, navigate]);

  const validate = () => {
    const next = {};
    if (form.name.trim().length < 2) next.name = 'Name must be at least 2 characters.';
    if (!form.email.trim()) next.email = 'Email is required.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Enter a valid email address.';
    if (!form.password) next.password = 'Password is required.';
    else if (form.password.length < 8) next.password = 'Password must be at least 8 characters.';
    else if (!(/[A-Za-z]/.test(form.password) && /\d/.test(form.password))) {
      next.password = 'Password must contain at least one letter and one digit.';
    }
    if (form.confirm !== form.password) next.confirm = 'Passwords do not match.';
    return next;
  };

  const toggleSubject = (subject) => {
    setForm((current) => ({
      ...current,
      preferredSubjects: current.preferredSubjects.includes(subject)
        ? current.preferredSubjects.filter((s) => s !== subject)
        : [...current.preferredSubjects, subject],
    }));
  };

  const submit = async (event) => {
    event.preventDefault();
    setServerError('');
    const next = validate();
    setErrors(next);
    if (Object.keys(next).length) return;
    setSubmitting(true);
    try {
      await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        preferredSubjects: form.preferredSubjects,
      });
      navigate('/dashboard');
    } catch (error) {
      setServerError(error.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-shell">
      <aside className="auth-aside">
        <Link to="/" className="brand">
          <span className="brand__mark"><BookOpen size={18} /></span>
          <span>SmartLib<span className="brand__sub">University Library</span></span>
        </Link>
        <div>
          <div className="eyebrow" style={{ color: '#e9c18c' }}>Join the library</div>
          <h1>Read more. Wait less. Learn together.</h1>
          <p>Student accounts are free. Librarian accounts are created by library staff.</p>
          <ul className="auth-aside__features">
            <li><Sparkles size={16} /> Personal recommendations</li>
            <li><Star size={16} /> Points for every approved reflection</li>
            <li><ShieldCheck size={16} /> Your data stays inside the university</li>
          </ul>
        </div>
        <p style={{ fontSize: '0.75rem' }}>SmartLib · Iteration 4 demo</p>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <PageHeader
            eyebrow="Student registration"
            title="Create your account"
            subtitle="Librarian accounts cannot be created here — ask a librarian."
          />
          {serverError && <Alert tone="error" title="Could not register">{serverError}</Alert>}
          <form onSubmit={submit} noValidate style={{ marginTop: 16 }}>
            <Input
              label="Full name"
              autoComplete="name"
              value={form.name}
              error={errors.name}
              placeholder="Aarav Sharma"
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
            <Input
              label="University email"
              type="email"
              autoComplete="email"
              value={form.email}
              error={errors.email}
              placeholder="you@university.edu"
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
            <div className="field-row">
              <Input
                label="Password"
                type="password"
                autoComplete="new-password"
                value={form.password}
                error={errors.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
              />
              <Input
                label="Confirm password"
                type="password"
                autoComplete="new-password"
                value={form.confirm}
                error={errors.confirm}
                onChange={(e) => setForm({ ...form, confirm: e.target.value })}
              />
            </div>
            <div className="field">
              <span className="field__label">Preferred subjects (optional)</span>
              <div className="chip-group">
                {subjects.map((subject) => (
                  <Chip key={subject} active={form.preferredSubjects.includes(subject)} onClick={() => toggleSubject(subject)}>
                    {subject}
                  </Chip>
                ))}
                {subjects.length === 0 && <span className="field__hint">Subjects load once the catalogue is available.</span>}
              </div>
            </div>
            <Button type="submit" variant="primary" loading={submitting} className="btn--block">
              {submitting ? 'Creating account…' : 'Create account'}
            </Button>
          </form>
          <p className="auth-switch">Already registered? <Link to="/login">Sign in</Link></p>
        </div>
      </main>
    </div>
  );
}
