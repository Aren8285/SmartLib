import React, { useState, useEffect, useCallback } from 'react';
import { getMyFines, payFine } from '../api';
import Toast from '../components/Toast';

function fmtDate(iso) {
  return iso ? new Date(iso).toLocaleString('en-IN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
}

export default function MyFinesPage() {
  const [fines, setFines] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    getMyFines()
      .then(setFines)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const pay = async (f) => {
    setBusy(f.id);
    try {
      const res = await payFine(f.id);
      setToast({ message: res.message || 'Fine paid.', type: 'success' });
      load();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  if (error) return <div className="banner banner--warning">{error}</div>;

  return (
    <div className="page">
      <h1>My fines</h1>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {loading ? <div className="loading">Loading…</div> : (
        fines.length === 0 ? <p className="empty-state">No fines — keep it up! ⭐</p> : (
          <table className="table">
            <thead>
              <tr><th>Reason</th><th>Amount</th><th>Breakdown</th><th>Status</th><th>Imposed</th><th>Paid</th><th></th></tr>
            </thead>
            <tbody>
              {fines.map((f) => (
                <tr key={f.id}>
                  <td>{f.reason.replace(/_/g, ' ')}</td>
                  <td className={f.status === 'PENDING' ? 'text-red' : ''}>₹{f.amount}</td>
                  <td>
                    {f.lateAmount != null && f.damagedAmount != null
                      ? `late ₹${f.lateAmount} + damaged ₹${f.damagedAmount}`
                      : '—'}
                  </td>
                  <td>
                    <span className={`badge ${f.status === 'PENDING' ? 'badge--red' : 'badge--green'}`}>
                      {f.status}
                    </span>
                  </td>
                  <td>{fmtDate(f.imposedDate)}</td>
                  <td>{fmtDate(f.paidDate)}</td>
                  <td>
                    {f.status === 'PENDING' && (
                      <button className="btn btn--primary" disabled={busy === f.id} onClick={() => pay(f)}>
                        {busy === f.id ? 'Paying…' : 'Pay'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      )}
    </div>
  );
}
