import React, { useState } from 'react';
import { FlaskConical, Play, ShieldCheck, Wrench } from 'lucide-react';
import { devInvariants, devRepair, devRunJobs, devSetDueDate, devSetPickupDeadline } from '../api';
import { Button, Select, Input } from './ui';

/**
 * Demo-only helpers. Visible only when the frontend is built/run with
 * VITE_DEV_TOOLS=true and the backend runs the dev profile.
 */
export default function DevTools({ loans = [], onRefresh, notify }) {
  const enabled = import.meta.env.VITE_DEV_TOOLS === 'true';
  const [loanId, setLoanId] = useState('');
  const [days, setDays] = useState(0);
  const [minutes, setMinutes] = useState(0);
  const [busy, setBusy] = useState('');
  const [invariants, setInvariants] = useState(null);

  if (!enabled) return null;

  const call = async (key, fn, message) => {
    setBusy(key);
    try {
      const res = await fn();
      notify?.({ message: message || 'Done.', type: 'success' });
      onRefresh?.();
      return res;
    } catch (e) {
      notify?.({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  const run = async (key, fn) => {
    setBusy(key);
    try {
      const res = await fn();
      if (key === 'jobs') {
        notify?.({
          message: `Jobs run: ${res.expiredLoans} expired, ${res.overdueLoans} overdue, ${res.remindersSent} reminder(s).`,
          type: 'info',
        });
      } else {
        notify?.({ message: `Invariant check: ${res.count} violation(s).`, type: res.count ? 'error' : 'success' });
        setInvariants(res);
      }
      onRefresh?.();
    } catch (e) {
      notify?.({ message: e.message, type: 'error' });
    } finally {
      setBusy('');
    }
  };

  return (
    <section className="dev-panel" aria-label="Developer tools">
      <div className="spread">
        <strong style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: '0.86rem' }}>
          <FlaskConical size={15} aria-hidden="true" /> Demo time controls
        </strong>
        <span className="cell-detail">dev profile only</span>
      </div>

      <div className="dev-panel__row">
        <Select aria-label="Loan to adjust" value={loanId} onChange={(e) => setLoanId(e.target.value)}>
          <option value="">Select an active loan…</option>
          {loans.map((l) => (
            <option key={l.id} value={l.id}>
              {l.studentName} — {l.bookTitle} ({l.status})
            </option>
          ))}
        </Select>
        <Input aria-label="Due in days" type="number" value={days} onChange={(e) => setDays(e.target.value)} style={{ width: 90 }} />
        <Button
          size="sm"
          variant="secondary"
          disabled={!loanId}
          loading={busy === 'due'}
          onClick={() => call('due', () => devSetDueDate(loanId, days), `Due date set to ${days} day(s) from now.`)}
        >
          Set due date
        </Button>
      </div>

      <div className="dev-panel__row">
        <Input aria-label="Pickup deadline in minutes" type="number" value={minutes} onChange={(e) => setMinutes(e.target.value)} style={{ width: 110 }} />
        <Button
          size="sm"
          variant="secondary"
          disabled={!loanId}
          loading={busy === 'pickup'}
          onClick={() => call('pickup', () => devSetPickupDeadline(loanId, minutes), `Pickup deadline set to ${minutes} minute(s) from now.`)}
        >
          Set pickup deadline
        </Button>
        <Button size="sm" icon={Play} loading={busy === 'jobs'} onClick={() => run('jobs', devRunJobs)}>
          Run jobs now
        </Button>
      </div>

      <div className="dev-panel__row">
        <Button size="sm" variant="outline" icon={ShieldCheck} loading={busy === 'invariants'} onClick={() => run('invariants', devInvariants)}>
          Check invariants
        </Button>
        <Button
          size="sm"
          variant="ghost"
          icon={Wrench}
          loading={busy === 'repair'}
          onClick={() => call('repair', devRepair, 'Stuck queues repaired.')}
        >
          Repair stuck queues
        </Button>
        {invariants && (
          <span style={{ color: invariants.count ? 'var(--danger)' : 'var(--success)', fontWeight: 600 }}>
            {invariants.count === 0 ? 'All invariants hold' : `${invariants.count} violation(s)`}
          </span>
        )}
      </div>
      {invariants?.violations?.length > 0 && (
        <ul style={{ fontSize: '0.74rem', margin: 0, color: 'var(--danger)' }}>
          {invariants.violations.map((v, i) => <li key={i}>{v.message || v.type}</li>)}
        </ul>
      )}
    </section>
  );
}
