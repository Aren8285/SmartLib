import React, { useState } from 'react';
import { devRunJobs, devSetPickupDeadline, devSetDueDate } from '../api';

/**
 * Circulation sandbox controls — only rendered when VITE_DEV_TOOLS=true.
 * Uses the dev endpoints, which the backend restricts to librarian + dev profile.
 */
export default function DevTools({ onRefresh, notify, loans = [] }) {
  if (import.meta.env.VITE_DEV_TOOLS !== 'true') return null;

  const doJobs = async () => {
    try {
      await devRunJobs();
      notify({ message: 'Reminder / expirer / overdue job run.', type: 'success' });
      onRefresh?.();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    }
  };

  const moveDue = async (loanId, days) => {
    try {
      await devSetDueDate(loanId, Number(days));
      notify({ message: `Due date moved ${Number(days) > 0 ? 'forward' : 'back'} ${Math.abs(Number(days))} day(s).`, type: 'info' });
      onRefresh?.();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    }
  };

  const movePickup = async (loanId, minutes) => {
    try {
      await devSetPickupDeadline(loanId, Number(minutes));
      notify({ message: `Pickup deadline moved ${Number(minutes)} minutes.`, type: 'info' });
      onRefresh?.();
    } catch (e) {
      notify({ message: e.message, type: 'error' });
    }
  };

  return (
    <div className="dev-panel">
      <strong>🛠 Dev sandbox</strong> — simulate time, then run jobs to see reminders, expiry and overdue marks.
      <div>
        <button className="btn btn--small" onClick={doJobs}>Run jobs now</button>
      </div>
      {loans.length > 0 && (
        <table className="table table--compact">
          <thead><tr><th>Loan</th><th>Pickup deadline</th><th>Due date</th></tr></thead>
          <tbody>
            {loans.map((l) => (
              <tr key={l.id}>
                <td>{l.studentName} · {l.bookTitle}</td>
                <td>
                  {l.pickupDeadline ? (
                    <div className="dev-panel__row">
                      <button className="btn btn--small" onClick={() => movePickup(l.id, -125)}>→ expired (−2h)</button>
                      <button className="btn btn--small" onClick={() => movePickup(l.id, 120)}>+2h</button>
                    </div>
                  ) : '—'}
                </td>
                <td>
                  {l.dueDate ? (
                    <div className="dev-panel__row">
                      <button className="btn btn--small" onClick={() => moveDue(l.id, -5)}>→ overdue (−5d)</button>
                      <button className="btn btn--small" onClick={() => moveDue(l.id, 5)}>+5d</button>
                    </div>
                  ) : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
