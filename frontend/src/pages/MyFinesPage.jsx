import React, { useState, useEffect, useCallback } from 'react';
import { getFines, payFine } from '../api';
import { useUser } from '../context/UserContext';
import Toast from '../components/Toast';

function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', {
    year: 'numeric', month: 'short', day: 'numeric'
  });
}

export default function MyFinesPage() {
  const { currentUser, refreshUser } = useUser();
  const [fines, setFines] = useState([]);
  const [loading, setLoading] = useState(true);
  const [payingId, setPayingId] = useState(null);
  const [toast, setToast] = useState({ message: '', type: 'success' });

  const fetchFines = useCallback(() => {
    if (!currentUser) return;
    setLoading(true);
    getFines({ studentId: currentUser.id })
      .then(setFines)
      .catch((e) => setToast({ message: e.message, type: 'error' }))
      .finally(() => setLoading(false));
  }, [currentUser]);

  useEffect(() => { fetchFines(); }, [fetchFines]);

  const handlePay = async (fineId) => {
    setPayingId(fineId);
    try {
      const result = await payFine(fineId, currentUser.id);
      setToast({ message: result.message || 'Fine paid.', type: 'success' });
      fetchFines();
      refreshUser();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    } finally {
      setPayingId(null);
    }
  };

  if (!currentUser || currentUser.role !== 'STUDENT') {
    return <div className="page"><p>This page is for students only.</p></div>;
  }

  return (
    <div className="page">
      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'success' })}
      />
      <h1>My Fines</h1>
      <p className="page-subtitle">
        Pending fines block new book requests. Paying a fine also closes its loan.
      </p>
      {loading ? (
        <p className="loading">Loading…</p>
      ) : fines.length === 0 ? (
        <p>No fines. Keep it that way! 🎉</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Reason</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Issued</th>
              <th>Paid</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {fines.map((fine) => (
              <tr key={fine.id}>
                <td>{fine.reason.replace('_', ' ')}</td>
                <td>₹{fine.amount}</td>
                <td>
                  <span className={`badge badge--${fine.status === 'PENDING' ? 'orange' : 'green'}`}>
                    {fine.status}
                  </span>
                </td>
                <td>{formatDate(fine.issueDate)}</td>
                <td>{formatDate(fine.paidDate)}</td>
                <td>
                  {fine.status === 'PENDING' ? (
                    <button
                      className="btn btn--primary"
                      disabled={payingId === fine.id}
                      onClick={() => handlePay(fine.id)}
                    >
                      {payingId === fine.id ? 'Paying…' : 'Pay'}
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
