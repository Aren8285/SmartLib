import React, { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ClipboardList, RotateCcw, Search, X } from '../components/icons';
import { getLoans, issueLoan, lostLoan, returnLoan } from '../api';
import { useToast } from '../context/ToastContext';
import { formatDateTime, money } from '../utils/format';
import {
  Alert, Badge, Button, Card, ConfirmDialog, EmptyState, Modal, PageHeader, Pagination,
  Select, SkeletonTable, StatusBadge, Table,
} from '../components/ui';
import Countdown from '../components/Countdown';
import DevTools from '../components/DevTools';

const SIZE = 8;

export default function CirculationPage() {
  const toast = useToast();
  const [params] = useSearchParams();
  const queryParam = params.get('q') || '';
  const [deskQuery, setDeskQuery] = useState(queryParam);
  const [searchTerm, setSearchTerm] = useState(queryParam.trim());
  const [ready, setReady] = useState({ items: [], totalItems: 0, totalPages: 1 });
  const [active, setActive] = useState({ items: [], totalItems: 0, totalPages: 1 });
  const [readyPage, setReadyPage] = useState(0);
  const [activePage, setActivePage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const [returning, setReturning] = useState(null);
  const [condition, setCondition] = useState('GOOD');
  const [lost, setLost] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    Promise.all([
      getLoans({ status: 'READY_FOR_PICKUP', q: searchTerm, page: readyPage, size: SIZE }),
      getLoans({ status: 'ISSUED,OVERDUE', q: searchTerm, page: activePage, size: SIZE }),
    ])
      .then(([readyEnvelope, activeEnvelope]) => {
        setReady(readyEnvelope);
        setActive(activeEnvelope);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [readyPage, activePage, searchTerm]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    setDeskQuery(queryParam);
  }, [queryParam]);

  useEffect(() => {
    const timer = setTimeout(() => {
      const next = deskQuery.trim();
      if (next === searchTerm) return;
      setReadyPage(0);
      setActivePage(0);
      setSearchTerm(next);
    }, 250);
    return () => clearTimeout(timer);
  }, [deskQuery, searchTerm]);

  const doIssue = async (loan) => {
    setBusy(loan.id);
    try {
      const res = await issueLoan(loan.id);
      toast.success(res.message || 'Book issued.');
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  const doReturn = async () => {
    setBusy(returning.id);
    try {
      const res = await returnLoan(returning.id, condition === 'GOOD' ? null : condition);
      toast.success(res.message || 'Book returned.');
      setReturning(null);
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  const doLost = async () => {
    setBusy(lost.id);
    try {
      const res = await lostLoan(lost.id);
      toast.info(res.message || 'Copy marked lost.');
      setLost(null);
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  const devLoans = [...(ready.items || []), ...(active.items || [])];

  return (
    <div className="page">
      <PageHeader
        eyebrow="Librarian"
        title="Circulation"
        subtitle="Issue reserved copies, process returns and record damaged or lost items."
        actions={<Button variant="secondary" icon={RotateCcw} onClick={load}>Refresh</Button>}
      />

      {error && <Alert tone="error" title="Could not load circulation" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>}

      <section className="desk-lookup" aria-label="Desk lookup">
        <div className="desk-lookup__head">
          <div>
            <span className="desk-lookup__eyebrow">Desk lookup</span>
            <p>Find a pickup or active loan by student, book, ISBN or copy ID.</p>
          </div>
          {searchTerm && <Badge tone="info">Filtered</Badge>}
        </div>
        <div className="catalogue-search">
          <Search size={17} aria-hidden="true" />
          <label className="sr-only" htmlFor="desk-search">Search circulation</label>
          <input
            id="desk-search"
            className="input"
            placeholder="Name, title, ISBN or copy ID…"
            value={deskQuery}
            onChange={(e) => setDeskQuery(e.target.value)}
          />
          {deskQuery && (
            <button type="button" className="catalogue-search__clear" onClick={() => setDeskQuery('')} aria-label="Clear desk search">
              <X size={15} />
            </button>
          )}
        </div>
      </section>

      <Card
        title={`Ready for pickup (${ready.totalItems ?? (ready.items || []).length})`}
        subtitle="Reserved copies waiting at the desk; the countdown turns red under 6 hours."
      >
        {loading ? <SkeletonTable rows={3} columns={5} /> : (ready.items || []).length === 0 ? (
          <EmptyState
            icon={ClipboardList}
            title={searchTerm ? 'No matching pickups' : 'No copies waiting for pickup'}
            description={searchTerm ? `Nothing waiting for pickup matches “${searchTerm}”. Clear the search to see every hold.` : 'Reservations appear here as soon as a copy is held for a student.'}
          />
        ) : (
          <>
            <Table>
              <thead><tr><th>Student</th><th>Book</th><th>Copy</th><th>Pickup deadline</th><th><span className="sr-only">Actions</span></th></tr></thead>
              <tbody>
                {(ready.items || []).map((loan) => (
                  <tr key={loan.id}>
                    <td><strong>{loan.studentName}</strong></td>
                    <td>{loan.bookTitle}</td>
                    <td className="mono">{loan.copyId ? loan.copyId.slice(-6).toUpperCase() : '—'}</td>
                    <td><Countdown deadline={loan.pickupDeadline} /> <span className="cell-detail" title={formatDateTime(loan.pickupDeadline)}>{formatDateTime(loan.pickupDeadline)}</span></td>
                    <td><Button size="sm" variant="success" loading={busy === loan.id} onClick={() => doIssue(loan)}>Issue</Button></td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={ready.page || 0} totalPages={ready.totalPages || 1} totalItems={ready.totalItems} size={ready.size || SIZE} onChange={setReadyPage} />
          </>
        )}
      </Card>

      <Card
        title={`Issued & overdue (${active.totalItems ?? (active.items || []).length})`}
        subtitle="Returns release the copy to the next student in the queue."
      >
        {loading ? <SkeletonTable rows={4} columns={6} /> : (active.items || []).length === 0 ? (
          <EmptyState
            icon={ClipboardList}
            title={searchTerm ? 'No matching active loans' : 'No books are out right now'}
            description={searchTerm ? `No issued or overdue loan matches “${searchTerm}”.` : undefined}
          />
        ) : (
          <>
            <Table>
              <thead><tr><th>Student</th><th>Book</th><th>Status</th><th>Due</th><th>Fine</th><th><span className="sr-only">Actions</span></th></tr></thead>
              <tbody>
                {(active.items || []).map((loan) => (
                  <tr key={loan.id}>
                    <td><strong>{loan.studentName}</strong></td>
                    <td>{loan.bookTitle}</td>
                    <td><StatusBadge status={loan.status} /></td>
                    <td>{formatDateTime(loan.dueDate)}</td>
                    <td>{loan.accruedFine ? <span className="text-danger">{money(loan.accruedFine)}</span> : '—'}</td>
                    <td>
                      <div className="row-actions">
                        <Button size="sm" variant="primary" disabled={busy === loan.id} onClick={() => { setReturning(loan); setCondition('GOOD'); }}>Return</Button>
                        <Button size="sm" variant="ghost" disabled={busy === loan.id} onClick={() => setLost(loan)}>Lost</Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={active.page || 0} totalPages={active.totalPages || 1} totalItems={active.totalItems} size={active.size || SIZE} onChange={setActivePage} />
          </>
        )}
      </Card>

      <DevTools loans={devLoans} onRefresh={load} notify={toast} />

      <Modal
        open={Boolean(returning)}
        onClose={() => setReturning(null)}
        title={`Return “${returning?.bookTitle || ''}”`}
        description={returning ? `${returning.studentName} · due ${formatDateTime(returning.dueDate)}` : ''}
        footer={(
          <>
            <Button variant="ghost" onClick={() => setReturning(null)} disabled={busy === returning?.id}>Cancel</Button>
            <Button onClick={doReturn} loading={busy === returning?.id}>Confirm return</Button>
          </>
        )}
      >
        <Select label="Condition of the returned copy" value={condition} onChange={(e) => setCondition(e.target.value)}>
          <option value="GOOD">Good — normal return</option>
          <option value="DAMAGED">Damaged — damaged-book fine applies</option>
        </Select>
        <p className="cell-detail">
          A late return creates a fine for the overdue days. A damaged copy is removed from circulation and the
          student is charged the damaged-book fine plus any late amount.
        </p>
      </Modal>

      <ConfirmDialog
        open={Boolean(lost)}
        onClose={() => setLost(null)}
        onConfirm={doLost}
        loading={busy === lost?.id}
        title="Mark this copy as lost?"
        message={lost ? `“${lost.bookTitle}” held by ${lost.studentName} will be marked LOST. A replacement fine is imposed and the copy is not returned to the shelf.` : ''}
        confirmLabel="Mark lost"
      />
    </div>
  );
}
