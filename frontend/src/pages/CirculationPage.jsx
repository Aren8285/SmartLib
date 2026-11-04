import React, { useState, useEffect, useCallback } from 'react';
import { getLoansByStatus, issueLoan, returnLoan, lostLoan } from '../api';
import Toast from '../components/Toast';
import Countdown from '../components/Countdown';
import DevTools from '../components/DevTools';

const fmtDT = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—');

export default function CirculationPage() {
  const [ready, setReady] = useState([]);
  const [issued, setIssued] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [returning, setReturning] = useState(null); // loan being returned
  const [condition, setCondition] = useState('GOOD');
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([getLoansByStatus('READY_FOR_PICKUP'), getLoansByStatus('ISSUED'), getLoansByStatus('OVERDUE')])
      .then(([r, i, o]) => { setReady(r); setIssued([...i, ...o]); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const notify = (payload) => setToast(payload);

  const doIssue = async (loanId) => {
    setBusy(loanId);
    try {
      const res = await issueLoan(loanId);
      notify({ message: res.message || 'Issued.', type: 'success' });
      load();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  const doReturn = async () => {
    setBusy(returning.id);
    try {
      const res = await returnLoan(returning.id, condition === 'GOOD' ? null : condition);
      notify({ message: res.message || 'Returned.', type: 'success' });
      setReturning(null);
      load();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  const doLost = async (loanId) => {
    if (!window.confirm('Record this book as LOST? A replacement fine is imposed.')) return;
    setBusy(loanId);
    try {
      const res = await lostLoan(loanId);
      notify({ message: res.message || 'Marked lost.', type: 'success' });
      load();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  const setDue = async (loanId, days) => {
    try {
      const { devSetDueDate } = await import('../api');
      await devSetDueDate(loanId, days);
      notify({ message: `Due date moved by ${days} day(s) — run jobs to refresh overdue status.`, type: 'info' });
      load();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    }
  };

  return (
    <div className="page">
      <h1>Circulation</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {error && <div className="banner banner--warning">{error}</div>}

      <section className="section">
        <h2>Ready for pickup ({ready.length})</h2>
        {loading ? <div className="loading">Loading…</div> : ready.length === 0
          ? <p className="empty-state">No copies waiting for pickup.</p> : (
          <table className="table">
            <thead><tr><th>Student</th><th>Book</th><th>Copy</th><th>Pickup deadline</th><th></th></tr></thead>
            <tbody>
              {ready.map((l) => (
                <tr key={l.id}>
                  <td>{l.studentName}</td>
                  <td>{l.bookTitle}</td>
                  <td>{l.copyId?.slice(-6).toUpperCase()}</td>
                  <td><Countdown deadline={l.pickupDeadline} /> ({fmtDT(l.pickupDeadline)})</td>
                  <td>
                    <button className="btn btn--success" disabled={busy === l.id} onClick={() => doIssue(l.id)}>
                      {busy === l.id ? '…' : 'Issue'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="section">
        <h2>Issued &amp; overdue ({issued.length})</h2>
        {!loading && issued.length === 0 && <p className="empty-state">No books are out right now.</p>}
        {!loading && issued.length > 0 && (
          <table className="table">
            <thead><tr><th>Student</th><th>Book</th><th>Status</th><th>Due</th><th>Fine</th><th></th></tr></thead>
            <tbody>
              {issued.map((l) => (
                <tr key={l.id}>
                  <td>{l.studentName}</td>
                  <td>{l.bookTitle}</td>
                  <td><span className={`badge ${l.status === 'OVERDUE' ? 'badge--red' : 'badge--green'}`}>{l.status}</span></td>
                  <td>{fmtDT(l.dueDate)}</td>
                  <td>{l.accruedFine ? <span className="text-red">₹{l.accruedFine}</span> : '—'}</td>
                  <td className="row-actions">
                    <button className="btn btn--primary" disabled={busy === l.id} onClick={() => { setReturning(l); setCondition('GOOD'); }}>
                      Return
                    </button>
                    <button className="btn btn--danger" disabled={busy === l.id} onClick={() => doLost(l.id)}>
                      Mark lost
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {returning && (
        <div className="modal-backdrop" onClick={() => setReturning(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>Return “{returning.bookTitle}”</h3>
            <p className="page-subtitle">{returning.studentName} · due {fmtDT(returning.dueDate)}</p>
            <label className="field">
              <span>Condition of the returned copy</span>
              <select value={condition} onChange={(e) => setCondition(e.target.value)}>
                <option value="GOOD">GOOD — fine book</option>
                <option value="DAMAGED">DAMAGED — damaged-book fine applies</option>
              </select>
            </label>
            <div className="modal__actions">
              <button className="btn" onClick={() => setReturning(null)}>Cancel</button>
              <button className="btn btn--primary" disabled={busy === returning.id} onClick={doReturn}>
                {busy === returning.id ? 'Processing…' : 'Confirm return'}
              </button>
            </div>
          </div>
        </div>
      )}

      <DevTools onRefresh={load} notify={notify} loans={[...ready, ...issued]} />
    </div>
  );
}
