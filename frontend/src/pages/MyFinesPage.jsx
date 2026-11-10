import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Receipt, Wallet } from '../components/icons';
import { getMyFines, payFine } from '../api';
import { useToast } from '../context/ToastContext';
import { formatDateTime, money } from '../utils/format';
import {
  Alert, Button, ConfirmDialog, EmptyState, PageHeader, SkeletonTable, StatCard, StatusBadge, Table,
} from '../components/ui';

export default function MyFinesPage() {
  const toast = useToast();
  const [fines, setFines] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [confirm, setConfirm] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    getMyFines().then(setFines).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const pending = fines.filter((f) => f.status === 'PENDING');
  const paid = fines.filter((f) => f.status === 'PAID');
  const pendingTotal = pending.reduce((sum, f) => sum + f.amount, 0);

  const doPay = async () => {
    setBusy(true);
    try {
      await payFine(confirm.id);
      toast.success('Payment recorded. You can continue with your reflection.');
      setConfirm(null);
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Account"
        title="My fines"
        subtitle="Fines are calculated at return time. Paying one unlocks the reflection step for that loan."
      />

      {error && <Alert tone="error" title="Could not load your fines" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>}

      {!loading && (
        <div className="stats-grid">
          <StatCard label="Outstanding" value={money(pendingTotal)} icon={Wallet} tone={pendingTotal > 0 ? 'danger' : 'success'} hint={`${pending.length} unpaid fine${pending.length === 1 ? '' : 's'}`} />
          <StatCard label="Paid to date" value={money(paid.reduce((s, f) => s + f.amount, 0))} icon={Receipt} tone="success" hint={`${paid.length} settled`} />
        </div>
      )}

      {loading ? <SkeletonTable rows={4} columns={6} /> : fines.length === 0 ? (
        <EmptyState
          icon={Receipt}
          title="No fines — keep it up"
          description="Return books on time and in good condition to keep your record clean."
          action={<Link to="/my-loans" className="btn btn--primary btn--sm">View my loans</Link>}
        />
      ) : (
        <Table>
          <thead>
            <tr><th>Reason</th><th>Amount</th><th>Breakdown</th><th>Status</th><th>Imposed</th><th>Paid</th><th><span className="sr-only">Actions</span></th></tr>
          </thead>
          <tbody>
            {fines.map((fine) => (
              <tr key={fine.id}>
                <td><strong>{fine.reason.replace(/_/g, ' ').toLowerCase()}</strong></td>
                <td className={fine.status === 'PENDING' ? 'text-danger' : ''}>{money(fine.amount)}</td>
                <td>
                  {fine.lateAmount != null && fine.damagedAmount != null
                    ? `late ${money(fine.lateAmount)} + damaged ${money(fine.damagedAmount)}`
                    : '—'}
                </td>
                <td><StatusBadge status={fine.status} /></td>
                <td>{formatDateTime(fine.issueDate || fine.imposedDate)}</td>
                <td>{formatDateTime(fine.paidDate)}</td>
                <td>
                  {fine.status === 'PENDING' && (
                    <Button size="sm" onClick={() => setConfirm(fine)}>Pay fine</Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <ConfirmDialog
        open={Boolean(confirm)}
        onClose={() => setConfirm(null)}
        onConfirm={doPay}
        loading={busy}
        title="Pay this fine?"
        message={confirm ? `${money(confirm.amount)} for ${confirm.reason.replace(/_/g, ' ').toLowerCase()}. This is a simulated payment for the demo.` : ''}
        confirmLabel="Pay now"
        variant="primary"
      />
    </div>
  );
}
