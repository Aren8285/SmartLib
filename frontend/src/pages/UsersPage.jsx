import React, { useCallback, useEffect, useState } from 'react';
import { UserPlus, Users } from 'lucide-react';
import { createLibrarian, getAllUsers } from '../api';
import { useToast } from '../context/ToastContext';
import { formatDate } from '../utils/format';
import {
  Alert, Badge, Button, Card, EmptyState, Input, PageHeader, SkeletonTable, Table,
} from '../components/ui';

const EMPTY = { name: '', email: '', password: '' };

export default function UsersPage() {
  const toast = useToast();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [creating, setCreating] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    getAllUsers().then(setUsers).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const validate = () => {
    const next = {};
    if (form.name.trim().length < 2) next.name = 'Name must be at least 2 characters.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Enter a valid email address.';
    if (form.password.length < 8) next.password = 'At least 8 characters.';
    else if (!(/[A-Za-z]/.test(form.password) && /\d/.test(form.password))) next.password = 'Include a letter and a digit.';
    return next;
  };

  const submit = async (event) => {
    event.preventDefault();
    setFormError('');
    const next = validate();
    setErrors(next);
    if (Object.keys(next).length) return;
    setCreating(true);
    try {
      await createLibrarian({ ...form, name: form.name.trim(), email: form.email.trim() });
      toast.success('Librarian account created.');
      setForm(EMPTY);
      load();
    } catch (e) {
      setFormError(e.message);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Librarian"
        title="Users"
        subtitle="Create staff accounts and review everyone registered with SmartLib."
      />

      <Card title="Create a librarian account" subtitle="Students register themselves; staff accounts are created here.">
        {formError && <Alert tone="error" title="Could not create the account">{formError}</Alert>}
        <form onSubmit={submit} noValidate style={{ marginTop: 12 }}>
          <div className="field-row">
            <Input label="Full name" value={form.name} error={errors.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <Input label="Email" type="email" value={form.email} error={errors.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <Input label="Temporary password" type="text" value={form.password} error={errors.password} hint="Minimum 8 characters with a letter and a digit." onChange={(e) => setForm({ ...form, password: e.target.value })} />
          <Button type="submit" icon={UserPlus} loading={creating}>Create librarian</Button>
        </form>
      </Card>

      {error && <Alert tone="error" title="Could not load users" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>}

      {loading ? <SkeletonTable rows={6} columns={5} /> : users.length === 0 ? (
        <EmptyState icon={Users} title="No users yet" />
      ) : (
        <Table>
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Points</th><th>Joined</th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td><strong>{u.name}</strong></td>
                <td>{u.email}</td>
                <td><Badge tone={u.role === 'LIBRARIAN' ? 'accent' : 'info'}>{u.role}</Badge></td>
                <td>{u.points}</td>
                <td>{formatDate(u.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <p className="cell-detail">Password hashes are never included in any API response.</p>
    </div>
  );
}
