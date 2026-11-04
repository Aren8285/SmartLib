import React, { useState, useEffect } from 'react';
import { updateMe, getSubjects } from '../api';
import { useAuth } from '../context/AuthContext';
import Toast from '../components/Toast';

export default function Profile() {
  const { user, refreshUser } = useAuth();
  const [subjects, setSubjects] = useState([]);
  const [form, setForm] = useState({ name: '', preferredSubjects: [] });
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (user) setForm({ name: user.name, preferredSubjects: user.preferredSubjects || [] });
  }, [user]);

  useEffect(() => {
    getSubjects().then(setSubjects).catch(() => {});
  }, []);

  const toggle = (s) => {
    setForm((f) => ({
      ...f,
      preferredSubjects: f.preferredSubjects.includes(s)
        ? f.preferredSubjects.filter((x) => x !== s)
        : [...f.preferredSubjects, s],
    }));
  };

  const save = async () => {
    setSaving(true);
    try {
      await updateMe({ name: form.name.trim(), preferredSubjects: form.preferredSubjects });
      await refreshUser();
      setToast({ message: 'Profile updated.', type: 'success' });
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    } finally {
      setSaving(false);
    }
  };

  if (!user) return <div className="loading">Loading…</div>;

  return (
    <div className="page">
      <h1>My profile</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />

      <div className="cards">
        <div className="card"><div className="card__num">{user.points}</div><div className="card__label">loyalty points</div></div>
        <div className="card"><div className="card__num">{user.preferredSubjects?.length || 0}</div><div className="card__label">preferred subjects</div></div>
      </div>

      <div className="panel">
        <label className="field">
          <span>Name</span>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </label>
        <label className="field">
          <span>Email</span>
          <input value={user.email} disabled />
        </label>
        <div className="field">
          <span>Preferred subjects</span>
          <div className="chip-group">
            {subjects.map((s) => (
              <button
                type="button"
                key={s}
                className={`chip ${form.preferredSubjects.includes(s) ? 'chip--on' : ''}`}
                onClick={() => toggle(s)}
              >
                {s}
              </button>
            ))}
          </div>
        </div>
        <button className="btn btn--primary" disabled={saving} onClick={save}>
          {saving ? 'Saving…' : 'Save changes'}
        </button>
      </div>
    </div>
  );
}
