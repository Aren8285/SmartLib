import React, { useCallback, useEffect, useState } from 'react';
import { Receipt, Wallet } from 'lucide-react';
import { getFinesByStatus } from '../api';
import { formatDateTime, money } from '../utils/format';
import {
  Alert, Button, EmptyState, PageHeader, SkeletonTable, StatCard, StatusBadge, Table, Tabs,
} from '../components/ui';

const TABS = [
  { id: 'PENDING', label: 'Pending' },
  { id: 'PAID', label: 'Paid' },
  { id: 'ALL', label: 'All fines' },
];

export default function LibrarianFinesPage() {
  const [status, setStatus] = useState('PENDING');
  const [fines, setFines] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    getFinesByStatus(status).then(setFines).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, [status]);

  useEffect(() => { load(); }, [load]);

  const pending = fines.filter((f) => f.status === 'PENDING');
  const collected = fines.filter((f) => f.status === 'PAID');

  return (
    <div className="page">
      <PageHeader
        eyebrow="Librarian"
        title="Fines"
        subtitle="Fines are imposed automatically at return. Students pay from their account; payments move the loan to reflection."
      />

      <Tabs tabs={TABS} active={status} onChange={setStatus} ariaLabel="Fine status" />

      {error && <Alert tone="error" title="Could not load fines" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>}

      {!loading && (
        <div className="stats-grid">
          <StatCard label="Pending" value={money(pending.reduce((s, f) => s + f.amount, 0))} icon={Wallet} tone={pending.length ? 'danger' : 'success'} hint={`${pending.length} fine${pending.length === 1 ? '' : 's'}`} />
          <StatCard label="Collected" value={money(collected.reduce((s, f) => s + f.amount, 0))} icon={Receipt} tone="success" hint={`${collected.length} paid`} />
          <StatCard label="Total records" value={fines.length} hint="In this view" />
        </div>
      )}

      {loading ? <SkeletonTable rows={5} columns={6} /> : fines.length === 0 ? (
        <EmptyState icon={Receipt} title="No fines in this view" description={status === 'PENDING' ? 'No student owes a fine right now.' : 'Settled fines appear here.'} />
      ) : (
        <Table>
          <thead><tr><th>Student</th><th>Reason</th><th>Amount</th><th>Breakdown</th><th>Status</th><th>Imposed</th><th>Paid</th></tr></thead>
          <tbody>
            {fines.map((fine) => (
              <tr key={fine.id}>
                <td><strong>{fine.studentName}</strong></td>
                <td>{fine.reason.replace(/_/g, ' ').toLowerCase()}</td>
                <td className={fine.status === 'PENDING' ? 'text-danger' : ''}>{money(fine.amount)}</td>
                <td>{fine.lateAmount != null && fine.damagedAmount != null ? `late ${money(fine.lateAmount)} + damaged ${money(fine.damagedAmount)}` : '—'}</td>
                <td><StatusBadge status={fine.status} /></td>
                <td>{formatDateTime(fine.issueDate)}</td>
                <td>{formatDateTime(fine.paidDate)}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <p className="cell-detail">Payments are simulated in this demo. Students tap “Pay fine” in My fines; librarians keep the audit view here.</p>
    </div>
  );
}
