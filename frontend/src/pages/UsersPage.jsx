import React, { useState, useEffect, useCallback } from 'react';
import { getAllUsers, createLibrarian } from '../api';
import Toast from '../components/Toast';

const fmtDate = (iso) => (iso ? new Date(iso).toLocaleDateString('en-IN', { month: 'short', day: 'numeric', year: 'numeric' }) : '—');

export default function UsersPage() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [error, setError] = useState('');

  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [creating, setCreating] = useState(false);
  const [formErr, setFormErr] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    getAllUsers()
      .then(setUsers)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const submit = async (e) => {
    e.preventDefault();
    const err = [];
    if (form.name.trim().length < 2) err.push('Name must be 2-60 characters.');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) err.push('Valid email required.');
    if (!(form.password.length >= 8 && /[A-Za-z]/.test(form.password) && /\d/.test(form.password)))
      err.push('Password needs 8+ chars with a letter and a digit.');
    if (err.length) { setFormErr(err.join(' ')); return; }
    setCreating(true);
    setFormErr('');
    try {
      await createLibrarian(form);
      setToast({ message: 'Librarian account created.', type: 'success' });
      setForm({ name: '', email: '', password: '' });
      load();
    } catch (ex) {
      setFormErr(ex.message);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="page">
      <h1>Users</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {error && <div className="banner banner--warning">{error}</div>}

      <form className="panel" onSubmit={submit}>
        <h2>Create a librarian account</h2>
        {formErr && <div className="banner banner--warning">{formErr}</div>}
        <div className="form-grid">
          <label className="field"><span>Name</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </label>
          <label className="field"><span>Email</span>
            <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </label>
          <label className="field"><span>Temporary password</span>
            <input type="text" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
          </label>
        </div>
        <button className="btn btn--primary" disabled={creating}>{creating ? 'Creating…' : 'Create librarian'}</button>
      </form>

      {loading ? <div className="loading">Loading…</div> : (
        <table className="table">
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Points</th><th>Joined</th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.name}</td>
                <td>{u.email}</td>
                <td><span className={`badge ${u.role === 'LIBRARIAN' ? 'badge--yellow' : ''}`}>{u.role}</span></td>
                <td>{u.points}</td>
                <td>{fmtDate(u.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
