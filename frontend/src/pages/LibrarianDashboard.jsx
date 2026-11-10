import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle, BarChart3, BookOpen, ClipboardList, Clock3, PenLine, Wallet,
} from '../components/icons';
import { librarianDashboard } from '../api';
import { formatDateTime } from '../utils/format';
import {
  Alert, Badge, Button, Card, EmptyState, PageHeader, Skeleton, StatCard, StatusBadge, Table,
} from '../components/ui';

export default function LibrarianDashboard() {
  const [dash, setDash] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    librarianDashboard().then(setDash).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  if (loading) {
    return (
      <div className="page">
        <PageHeader eyebrow="Librarian" title="Library at a glance" />
        <div className="stats-grid">{Array.from({ length: 6 }).map((_, i) => <div className="stat-card" key={i}><Skeleton width="55%" height={10} /><Skeleton width="35%" height={26} style={{ marginTop: 12 }} /></div>)}</div>
        <div className="card"><Skeleton height={160} /></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <PageHeader eyebrow="Librarian" title="Library at a glance" />
        <Alert tone="error" title="Could not load the dashboard" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>
      </div>
    );
  }

  const actions = dash.oldestActionsNeeded || [];
  const statuses = Object.entries(dash.copiesByStatus || {});

  const why = (action) => {
    if (action.status === 'READY_FOR_PICKUP') return `Pickup deadline ${action.pickupDeadline ? formatDateTime(action.pickupDeadline) : '—'} — release if missed`;
    if (action.status === 'OVERDUE') return `Was due ${action.dueDate ? formatDateTime(action.dueDate) : '—'} — fine accruing`;
    return `Due ${action.dueDate ? formatDateTime(action.dueDate) : '—'}`;
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Librarian"
        title="Library at a glance"
        subtitle="Everything that needs a decision today, plus a live view of the collection."
        actions={(
          <>
            <Link to="/librarian/reports" className="btn btn--secondary"><BarChart3 size={15} /> Reports</Link>
            <Link to="/librarian/circulation" className="btn btn--primary"><ClipboardList size={15} /> Open circulation</Link>
          </>
        )}
      />

      <div className="stats-grid">
        <StatCard label="Ready for pickup" value={dash.readyForPickup} icon={Clock3} tone="warning" hint="48-hour hold" />
        <StatCard label="Issued" value={dash.issued} icon={BookOpen} tone="success" hint="On loan" />
        <StatCard label="Overdue" value={dash.overdue} icon={AlertTriangle} tone={dash.overdue > 0 ? 'danger' : 'success'} hint="Fines accruing" />
        <StatCard label="Reflections" value={dash.pendingReflections} icon={PenLine} hint="Awaiting review" />
        <StatCard label="Pending fines" value={dash.pendingFines} icon={Wallet} tone={dash.pendingFines > 0 ? 'warning' : 'success'} hint="To collect" />
        <StatCard label="Collection" value={dash.totalBooks} icon={BookOpen} hint={`${dash.totalCopies} copies`} />
      </div>

      <div className="reports-grid">
        <Card title="Circulation overview" subtitle="Copies by current status">
          {statuses.length === 0 ? <EmptyState title="No copy records yet" /> : (
            <Table>
              <thead><tr><th>Status</th><th>Count</th></tr></thead>
              <tbody>
                {statuses.map(([status, count]) => (
                  <tr key={status}><td><StatusBadge status={status} /></td><td><strong>{count}</strong></td></tr>
                ))}
              </tbody>
            </Table>
          )}
          <div className="row" style={{ marginTop: 12 }}>
            <Link to="/librarian/books" className="btn btn--ghost btn--sm">Manage books</Link>
            <Link to="/librarian/shelves" className="btn btn--ghost btn--sm">Manage shelves</Link>
          </div>
        </Card>

        <Card title="Oldest loans needing action" subtitle="Pickups, overdue returns and pending reflection work">
          {actions.length === 0 ? (
            <EmptyState title="All clear" description="No pickup, overdue or reflection items need attention." />
          ) : (
            <Table>
              <thead><tr><th>Book</th><th>Status</th><th>Why it matters</th></tr></thead>
              <tbody>
                {actions.map((action) => (
                  <tr key={action.loanId}>
                    <td><strong>{action.bookTitle}</strong></td>
                    <td><StatusBadge status={action.status} /></td>
                    <td>{why(action)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      </div>

      <Card title="Quick actions" subtitle="The jobs that keep the queue moving">
        <div className="row">
          <Link to="/librarian/circulation" className="btn btn--primary">Issue / return a book</Link>
          <Link to="/librarian/reflections" className="btn btn--secondary">Review reflections <Badge tone="warning" style={{ marginLeft: 4 }}>{dash.pendingReflections}</Badge></Link>
          <Link to="/librarian/waitlists" className="btn btn--secondary">Inspect waitlists</Link>
          <Link to="/librarian/fines" className="btn btn--secondary">Collect fines</Link>
        </div>
      </Card>
    </div>
  );
}
