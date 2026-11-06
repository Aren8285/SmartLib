import React, { useEffect, useState } from 'react';
import { Save, Star } from 'lucide-react';
import { getSubjects, updateMe } from '../api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import {
  Alert, Button, Card, Chip, Input, PageHeader, Skeleton, StatCard,
} from '../components/ui';

export default function Profile() {
  const { user, refreshUser } = useAuth();
  const toast = useToast();
  const [subjects, setSubjects] = useState([]);
  const [form, setForm] = useState({ name: '', preferredSubjects: [] });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (user) setForm({ name: user.name, preferredSubjects: user.preferredSubjects || [] });
  }, [user]);

  useEffect(() => { getSubjects().then(setSubjects).catch(() => {}); }, []);

  const toggle = (subject) => {
    setForm((current) => ({
      ...current,
      preferredSubjects: current.preferredSubjects.includes(subject)
        ? current.preferredSubjects.filter((s) => s !== subject)
        : [...current.preferredSubjects, subject],
    }));
  };

  const save = async (event) => {
    event.preventDefault();
    if (form.name.trim().length < 2) {
      setError('Name must be at least 2 characters.');
      return;
    }
    setError('');
    setSaving(true);
    try {
      await updateMe({ name: form.name.trim(), preferredSubjects: form.preferredSubjects });
      await refreshUser();
      toast.success('Profile updated.');
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  };

  if (!user) return <div className="card"><Skeleton width="40%" height={22} /><Skeleton height={60} style={{ marginTop: 16 }} /></div>;

  return (
    <div className="page">
      <PageHeader eyebrow="Account" title="My profile" subtitle="Your name and reading interests shape recommendations." />

      <div className="stats-grid">
        <StatCard label="Loyalty points" value={user.points} icon={Star} tone="accent" hint="Return on time + approved reflections" />
        <StatCard label="Preferred subjects" value={user.preferredSubjects?.length || 0} hint="Used for recommendations" />
        <StatCard label="Role" value={user.role.toLowerCase()} hint={user.email} />
      </div>

      <Card title="Personal details" subtitle="Your email address cannot be changed.">
        {error && <Alert tone="error">{error}</Alert>}
        <form onSubmit={save} style={{ marginTop: 12 }}>
          <Input label="Full name" value={form.name} maxLength={100} onChange={(e) => setForm({ ...form, name: e.target.value })} error={error && form.name.trim().length < 2 ? error : undefined} />
          <Input label="Email" value={user.email} disabled />
          <div className="field">
            <span className="field__label">Preferred subjects</span>
            <div className="chip-group">
              {subjects.map((subject) => (
                <Chip key={subject} active={form.preferredSubjects.includes(subject)} onClick={() => toggle(subject)}>
                  {subject}
                </Chip>
              ))}
              {subjects.length === 0 && <span className="field__hint">No subjects available yet.</span>}
            </div>
          </div>
          <Button type="submit" icon={Save} loading={saving}>Save changes</Button>
        </form>
      </Card>
    </div>
  );
}
