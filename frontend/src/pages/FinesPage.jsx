import React, { useState, useEffect, useCallback } from 'react';
import { getFinesByStatus, payFine } from '../api';
import Toast from '../components/Toast';

const fmtDate = (iso) => (iso ? new Date(iso).toLocaleString('en-IN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—');

export default function LibrarianFinesPage() {
  const [status, setStatus] = useState('PENDING');
  const [fines, setFines] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    getFinesByStatus(status)
      .then(setFines)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [status]);

  useEffect(() => { load(); }, [load]);

  const pay = async (f) => {
    setBusy(f.id);
    try {
      await payFine(f.id);
      setToast({ message: 'Payment recorded — the student moves to their reflection step.', type: 'success' });
      load();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  return (
    <div className="page">
      <h1>Fines</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {error && <div className="banner banner--warning">{error}</div>}

      <div className="tabs">
        {['PENDING', 'PAID', 'ALL'].map((s) => (
          <button key={s} className={`tab ${status === s ? 'tab--on' : ''}`} onClick={() => setStatus(s)}>{s}</button>
        ))}
      </div>

      {loading ? <div className="loading">Loading…</div> : fines.length === 0
        ? <p className="empty-state">No fines in this view.</p> : (
        <table className="table">
          <thead>
            <tr><th>Student</th><th>Reason</th><th>Amount</th><th>Breakdown</th><th>Status</th><th>Imposed</th><th></th></tr>
          </thead>
          <tbody>
            {fines.map((f) => (
              <tr key={f.id}>
                <td>{f.studentName}</td>
                <td>{f.reason.replace(/_/g, ' ')}</td>
                <td className={f.status === 'PENDING' ? 'text-red' : ''}>₹{f.amount}</td>
                <td>{f.lateAmount != null && f.damagedAmount != null ? `late ₹${f.lateAmount} + damaged ₹${f.damagedAmount}` : '—'}</td>
                <td><span className={`badge ${f.status === 'PENDING' ? 'badge--red' : 'badge--green'}`}>{f.status}</span></td>
                <td>{fmtDate(f.imposedDate)}</td>
                <td>
                  {f.status === 'PENDING' && (
                    <button className="btn btn--primary" disabled={busy === f.id} onClick={() => pay(f)}>
                      {busy === f.id ? 'Paying…' : 'Record payment'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
