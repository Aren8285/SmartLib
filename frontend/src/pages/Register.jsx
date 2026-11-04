import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getSubjects } from '../api';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [subjects, setSubjects] = useState([]);
  const [form, setForm] = useState({
    name: '', email: '', password: '', confirm: '', preferredSubjects: [],
  });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getSubjects().then(setSubjects).catch(() => {});
  }, []);

  const validate = () => {
    const e = {};
    if (!form.name.trim() || form.name.trim().length < 2) e.name = 'Name must be at least 2 characters.';
    if (!form.email.trim()) e.email = 'Email is required.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) e.email = 'Enter a valid email address.';
    if (!form.password) e.password = 'Password is required.';
    else {
      if (form.password.length < 8) e.password = 'Password must be at least 8 characters.';
      else if (!(/[A-Za-z]/.test(form.password) && /\d/.test(form.password)))
        e.password = 'Password must contain at least one letter and one digit.';
    }
    if (form.confirm !== form.password) e.confirm = 'Passwords do not match.';
    return e;
  };

  const toggleSubject = (s) => {
    setForm((f) => ({
      ...f,
      preferredSubjects: f.preferredSubjects.includes(s)
        ? f.preferredSubjects.filter((x) => x !== s)
        : [...f.preferredSubjects, s],
    }));
  };

  const submit = async (ev) => {
    ev.preventDefault();
    setServerError('');
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length) return;
    setSubmitting(true);
    try {
      await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        preferredSubjects: form.preferredSubjects,
      });
      navigate('/dashboard');
    } catch (err) {
      setServerError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Create a student account</h1>
        <p className="page-subtitle">Librarian accounts cannot be created here — a librarian creates them.</p>
        {serverError && <div className="banner banner--warning">{serverError}</div>}
        <form onSubmit={submit} noValidate>
          <label className="field">
            <span>Name</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            {errors.name && <em className="field-error">{errors.name}</em>}
          </label>
          <label className="field">
            <span>Email</span>
            <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            {errors.email && <em className="field-error">{errors.email}</em>}
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
            {errors.password && <em className="field-error">{errors.password}</em>}
          </label>
          <label className="field">
            <span>Confirm password</span>
            <input type="password" value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} />
            {errors.confirm && <em className="field-error">{errors.confirm}</em>}
          </label>
          <div className="field">
            <span>Preferred subjects (optional)</span>
            <div className="chip-group">
              {subjects.map((s) => (
                <button
                  type="button"
                  key={s}
                  className={`chip ${form.preferredSubjects.includes(s) ? 'chip--on' : ''}`}
                  onClick={() => toggleSubject(s)}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
          <button className="btn btn--primary btn--block" disabled={submitting}>
            {submitting ? 'Creating…' : 'Register'}
          </button>
        </form>
        <p className="auth-switch">
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
