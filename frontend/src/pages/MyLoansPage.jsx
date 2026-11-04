import React, { useState, useEffect, useCallback } from 'react';
import { getMyLoans, myReflections, cancelLoan, skipReflection, submitReflection } from '../api';
import { useAuth } from '../context/AuthContext';
import Toast from '../components/Toast';
import Countdown from '../components/Countdown';

function fmtDate(iso) {
  return iso ? new Date(iso).toLocaleDateString('en-IN', { month: 'short', day: 'numeric' }) : '—';
}
function fmtDT(iso) {
  return iso ? new Date(iso).toLocaleString('en-IN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
}

const TABS = [
  { id: 'active', label: 'Active' },
  { id: 'waitlisted', label: 'Waitlisted' },
  { id: 'reflection', label: 'Awaiting reflection' },
  { id: 'history', label: 'History' },
];

function badgeClass(status) {
  switch (status) {
    case 'READY_FOR_PICKUP': return 'badge--blue';
    case 'ISSUED': return 'badge--green';
    case 'OVERDUE': return 'badge--red';
    case 'FINE_PENDING': return 'badge--orange';
    case 'REFLECTION_PENDING': return 'badge--yellow';
    case 'EXPIRED': case 'CANCELLED': case 'LOST': return 'badge--gray';
    case 'CLOSED': return 'badge--gray';
    default: return 'badge--gray';
  }
}

function ReflectionBox({ loan, onDone, toast }) {
  const MIN = 50;
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    try {
      await submitReflection(loan.id, text);
      toast({ message: 'Reflection submitted — your librarian will review it shortly.', type: 'success' });
      onDone();
    } catch (e) {
      toast({ message: e.message, type: 'error' });
    } finally {
      setBusy(false);
    }
  };

  const skip = async () => {
    if (!window.confirm('Skip the reflection? The loan closes without reflection points.')) return;
    setBusy(true);
    try {
      const res = await skipReflection(loan.id);
      toast({ message: res.message || 'Reflection skipped.', type: 'success' });
      onDone();
    } catch (e) {
      toast({ message: e.message, type: 'error' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="reflection-box">
      <textarea
        rows={4}
        value={text}
        placeholder={`What did you take away from "${loan.bookTitle}"? (${MIN} characters minimum)`}
        onChange={(e) => setText(e.target.value)}
      />
      <div className="reflection-box__meta">
        <span className={text.trim().length >= MIN ? '' : 'text-red'}>
          {text.trim().length} / {MIN} characters
        </span>
        <div className="reflection-box__actions">
          <button className="btn" disabled={busy} onClick={skip}>Skip</button>
          <button className="btn btn--primary" disabled={busy || text.trim().length < MIN} onClick={submit}>
            {busy ? 'Submitting…' : 'Submit reflection'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function MyLoansPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState('active');
  const [loans, setLoans] = useState([]);
  const [reflByLoan, setReflByLoan] = useState({});
  const [loading, setLoading] = useState(true);
  const [openReflection, setOpenReflection] = useState(null);
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([getMyLoans(), myReflections()])
      .then(([ls, rs]) => {
        setLoans(ls);
        setReflByLoan(Object.fromEntries((rs || []).map((r) => [r.loanId, r])));
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const notify = (payload) => setToast(payload);
  const finish = () => { setOpenReflection(null); load(); };

  const cancel = async (loanId) => {
    try {
      const res = await cancelLoan(loanId);
      notify({ message: res.message || 'Cancelled.', type: 'success' });
      load();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    }
  };

  const CANCELABLE = ['WAITLISTED', 'READY_FOR_PICKUP'];
  const active = loans.filter((l) => ['READY_FOR_PICKUP', 'ISSUED', 'OVERDUE', 'FINE_PENDING'].includes(l.status));
  const waitlisted = loans.filter((l) => l.status === 'WAITLISTED');
  const reflectionPending = loans.filter((l) => l.status === 'REFLECTION_PENDING');
  const history = loans.filter((l) => ['CLOSED', 'RETURNED', 'EXPIRED', 'CANCELLED', 'LOST'].includes(l.status));
  const shown = { active, waitlisted, reflection: reflectionPending, history }[tab];

  return (
    <div className="page">
      <h1>My loans</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {error && <div className="banner banner--warning">{error}</div>}

      <div className="tabs">
        {TABS.map((t) => (
          <button key={t.id} className={`tab ${tab === t.id ? 'tab--on' : ''}`} onClick={() => setTab(t.id)}>
            {t.label} ({{
              active: active.length, waitlisted: waitlisted.length,
              reflection: reflectionPending.length, history: history.length,
            }[t.id]})
          </button>
        ))}
      </div>

      {loading ? <div className="loading">Loading…</div> : (
        shown.length === 0 ? <p className="empty-state">Nothing here yet.</p> : (
          <table className="table">
            <thead>
              <tr>
                <th>Book</th><th>Status</th><th>Requested</th><th>Due</th><th>Notable</th><th></th>
              </tr>
            </thead>
            <tbody>
              {shown.map((l) => {
                const refl = reflByLoan[l.id];
                return (
                  <React.Fragment key={l.id}>
                    <tr>
                      <td>{l.bookTitle}</td>
                      <td><span className={`badge ${badgeClass(l.status)}`}>{l.status.replace(/_/g, ' ')}</span></td>
                      <td>{fmtDT(l.requestDate)}</td>
                      <td>{fmtDate(l.dueDate)}</td>
                      <td>
                        {l.status === 'READY_FOR_PICKUP' && <>Pick up before <Countdown deadline={l.pickupDeadline} /></>}
                        {l.status === 'OVERDUE' && l.accruedFine != null && <span className="text-red">₹{l.accruedFine} accrued</span>}
                        {l.status === 'FINE_PENDING' && l.fine && (
                          <>fine ₹{l.fine.amount} ({l.fine.reason === 'DAMAGED_BOOK'
                            ? `damaged ₹${l.fine.damagedAmount} + late ₹${l.fine.lateAmount}`
                            : l.fine.reason.replace(/_/g, ' ')}){!l.fine.paidDate && ' — unpaid'}</>
                        )}
                        {l.status === 'WAITLISTED' && l.queuePosition != null && <>position #{l.queuePosition}</>}
                        {tab === 'history' && reflByLoan[l.id] && (
                          <span className={`badge ${reflByLoan[l.id].status === 'APPROVED' ? 'badge--green' : reflByLoan[l.id].status === 'REJECTED' ? 'badge--red' : 'badge--yellow'}`}>
                            reflection {reflByLoan[l.id].status.toLowerCase()}
                            {reflByLoan[l.id].status === 'REJECTED' ? ` — "${reflByLoan[l.id].reviewNote}"` : ''}
                          </span>
                        )}
                      </td>
                      <td>
                        {CANCELABLE.includes(l.status) && (
                          <button className="btn btn--danger" onClick={() => cancel(l.id)}>Cancel</button>
                        )}
                        {l.status === 'REFLECTION_PENDING' && !reflByLoan[l.id] && (
                          <button
                            className="btn btn--primary"
                            onClick={() => setOpenReflection(openReflection === l.id ? null : l.id)}
                          >
                            {openReflection === l.id ? 'Close' : 'Write reflection'}
                          </button>
                        )}
                      </td>
                    </tr>
                    {openReflection === l.id && (
                      <tr className="detail-row">
                        <td colSpan={6}><ReflectionBox loan={l} onDone={finish} toast={notify} /></td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        )
      )}
      {active.some((l) => l.status === 'READY_FOR_PICKUP') && (
        <p className="page-subtitle">Pick up your reserved copies at the library desk before the deadline expires.</p>
      )}
    </div>
  );
}
