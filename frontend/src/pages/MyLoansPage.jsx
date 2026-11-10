import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookMarked } from '../components/icons';
import {
  cancelLoan, getMyLoans, myReflections, renewLoan, skipReflection, submitReflection,
} from '../api';
import { useToast } from '../context/ToastContext';
import { formatDate, formatDateTime, relativeTime } from '../utils/format';
import {
  Alert, Button, ConfirmDialog, EmptyState, PageHeader, Pagination, SkeletonTable,
  StatusBadge, Table, Tabs, Textarea, Tooltip,
} from '../components/ui';
import Countdown from '../components/Countdown';

const TABS = [
  { id: 'active', label: 'Active' },
  { id: 'waitlisted', label: 'Waitlisted' },
  { id: 'reflection', label: 'Reflection' },
  { id: 'history', label: 'History' },
];

const STATUS_BY_TAB = {
  active: 'READY_FOR_PICKUP,ISSUED,OVERDUE,FINE_PENDING',
  waitlisted: 'WAITLISTED',
  reflection: 'REFLECTION_PENDING',
  history: 'CLOSED,RETURNED,EXPIRED,CANCELLED,LOST',
};

function ReflectionForm({ loan, onDone, onCancel, busy, setBusy, toast }) {
  const MIN = 50;
  const [text, setText] = useState('');
  const length = text.trim().length;

  const submit = async () => {
    setBusy('reflection');
    try {
      await submitReflection(loan.id, text);
      toast.success('Reflection submitted — your librarian will review it shortly.');
      onDone();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  const skip = async () => {
    setBusy('skip');
    try {
      const res = await skipReflection(loan.id);
      toast.info(res.message || 'Reflection skipped.');
      onDone();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  return (
    <div style={{ padding: '14px 4px' }}>
      <Textarea
        label={`What did you take away from “${loan.bookTitle}”?`}
        rows={4}
        value={text}
        maxLength={2000}
        placeholder="Share the idea, chapter or argument that stayed with you…"
        hint={`${length} / ${MIN} characters minimum · ${2000 - length} left`}
        onChange={(e) => setText(e.target.value)}
      />
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <Button variant="ghost" onClick={onCancel} disabled={Boolean(busy)}>Close</Button>
        <Button variant="secondary" onClick={skip} loading={busy === 'skip'} disabled={Boolean(busy)}>Skip reflection</Button>
        <Button onClick={submit} loading={busy === 'reflection'} disabled={length < MIN || Boolean(busy)}>Submit reflection</Button>
      </div>
    </div>
  );
}

export default function MyLoansPage() {
  const toast = useToast();
  const [tab, setTab] = useState('active');
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ items: [], totalItems: 0, totalPages: 1 });
  const [reflections, setReflections] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [openReflection, setOpenReflection] = useState(null);
  const [busy, setBusy] = useState('');
  const [confirm, setConfirm] = useState(null);
  const size = 8;

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    Promise.all([
      getMyLoans({ page, size, status: STATUS_BY_TAB[tab] }),
      tab === 'history' || tab === 'reflection' ? myReflections() : Promise.resolve([]),
    ])
      .then(([envelope, reflectionList]) => {
        setData(envelope?.items ? envelope : { items: envelope || [], totalItems: 0, totalPages: 1 });
        setReflections(Object.fromEntries((reflectionList || []).map((r) => [r.loanId, r])));
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [page, tab]);

  useEffect(() => { load(); }, [load]);

  const switchTab = (next) => { setTab(next); setPage(0); setOpenReflection(null); };

  const doCancel = async () => {
    setBusy('cancel');
    try {
      const res = await cancelLoan(confirm.id);
      toast.success(res.message || 'Request cancelled.');
      setConfirm(null);
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  const doRenew = async (loan) => {
    setBusy(`renew-${loan.id}`);
    try {
      const res = await renewLoan(loan.id);
      toast.success(res.message || 'Loan renewed.');
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  const loans = data.items || [];

  return (
    <div className="page">
      <PageHeader
        eyebrow="Borrowing"
        title="My loans"
        subtitle="Track requests, due dates, pickup deadlines and reflections."
        actions={<Link to="/books" className="btn btn--primary">Find another book</Link>}
      />

      <Tabs tabs={TABS} active={tab} onChange={switchTab} ariaLabel="Loan lists" />

      {error && (
        <Alert tone="error" title="Could not load your loans" action={<Button variant="secondary" size="sm" onClick={load}>Retry</Button>}>
          {error}
        </Alert>
      )}

      {loading ? <SkeletonTable rows={5} columns={6} /> : loans.length === 0 ? (
        <EmptyState
          icon={BookMarked}
          title={tab === 'history' ? 'No past loans yet' : 'Nothing in this list'}
          description={tab === 'history' ? 'Closed and cancelled loans will appear here.' : 'When you reserve a book it will appear in the matching tab.'}
          action={tab === 'active' ? <Link to="/books" className="btn btn--primary btn--sm">Browse the catalogue</Link> : null}
        />
      ) : (
        <>
          <Table>
            <thead>
              <tr><th>Book</th><th>Status</th><th>Requested</th><th>Due / deadline</th><th>Details</th><th><span className="sr-only">Actions</span></th></tr>
            </thead>
            <tbody>
              {loans.map((loan) => {
                const reflection = reflections[loan.id];
                const cancelable = ['WAITLISTED', 'READY_FOR_PICKUP'].includes(loan.status);
                return (
                  <React.Fragment key={loan.id}>
                    <tr>
                      <td><strong>{loan.bookTitle}</strong></td>
                      <td><StatusBadge status={loan.status} /></td>
                      <td>{formatDate(loan.requestDate)}</td>
                      <td>
                        {loan.dueDate && <Tooltip text={formatDateTime(loan.dueDate)}>{relativeTime(loan.dueDate)}</Tooltip>}
                        {!loan.dueDate && loan.pickupDeadline && (
                          <span>Pick up <Countdown deadline={loan.pickupDeadline} /></span>
                        )}
                        {!loan.dueDate && !loan.pickupDeadline && '—'}
                      </td>
                      <td>
                        {loan.status === 'OVERDUE' && loan.accruedFine != null && (
                          <span className="text-danger">₹{loan.accruedFine} accrued</span>
                        )}
                        {loan.status === 'FINE_PENDING' && loan.fine && (
                          <span className="text-danger">₹{loan.fine.amount} · {loan.fine.reason.replace(/_/g, ' ').toLowerCase()}</span>
                        )}
                        {loan.status === 'WAITLISTED' && loan.queuePosition != null && (
                          <span className="text-muted">Queue position #{loan.queuePosition}</span>
                        )}
                        {loan.status === 'ISSUED' && loan.renewalCount > 0 && (
                          <span className="cell-detail">Renewed {loan.renewalCount}/{loan.renewalLimit}</span>
                        )}
                        {reflection && (
                          <div className="row" style={{ gap: 4 }}>
                            <StatusBadge status={reflection.status} label={`Reflection ${reflection.status.toLowerCase()}`} />
                            {reflection.reviewNote && <span className="cell-detail">“{reflection.reviewNote}”</span>}
                          </div>
                        )}
                        {!reflection && loan.status === 'REFLECTION_PENDING' && <span className="text-muted">Reflection needed</span>}
                      </td>
                      <td>
                        <div className="row-actions">
                          {cancelable && <Button size="sm" variant="danger" onClick={() => setConfirm(loan)}>Cancel</Button>}
                          {loan.status === 'ISSUED' && loan.canRenew && (
                            <Button
                              size="sm"
                              variant="secondary"
                              loading={busy === `renew-${loan.id}`}
                              onClick={() => doRenew(loan)}
                            >
                              Renew
                            </Button>
                          )}
                          {loan.status === 'REFLECTION_PENDING' && !reflection && (
                            <Button
                              size="sm"
                              variant={openReflection === loan.id ? 'secondary' : 'primary'}
                              onClick={() => setOpenReflection(openReflection === loan.id ? null : loan.id)}
                            >
                              {openReflection === loan.id ? 'Close' : 'Write reflection'}
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                    {openReflection === loan.id && (
                      <tr>
                        <td colSpan={6} style={{ background: 'var(--surface-2)' }}>
                          <ReflectionForm
                            loan={loan}
                            toast={toast}
                            busy={busy}
                            setBusy={setBusy}
                            onCancel={() => setOpenReflection(null)}
                            onDone={() => { setOpenReflection(null); load(); }}
                          />
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </Table>
          <Pagination page={data.page || 0} totalPages={data.totalPages || 1} totalItems={data.totalItems} size={data.size || size} onChange={setPage} />
        </>
      )}

      <ConfirmDialog
        open={Boolean(confirm)}
        onClose={() => setConfirm(null)}
        onConfirm={doCancel}
        loading={busy === 'cancel'}
        title="Cancel this request?"
        message={confirm ? `“${confirm.bookTitle}” will be removed from your ${confirm.status === 'WAITLISTED' ? 'waitlist' : 'reserved copies'} and offered to the next student.` : ''}
        confirmLabel="Yes, cancel request"
      />
    </div>
  );
}
