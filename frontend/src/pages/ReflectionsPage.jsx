import React, { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, PenLine, XCircle } from '../components/icons';
import { approveReflection, reflectionsByStatus, rejectReflection } from '../api';
import { useToast } from '../context/ToastContext';
import { formatDateTime } from '../utils/format';
import {
  Alert, Button, ConfirmDialog, EmptyState, Modal, PageHeader, Skeleton, StatusBadge, Tabs, Textarea,
} from '../components/ui';

const TABS = [
  { id: 'PENDING', label: 'Pending' },
  { id: 'APPROVED', label: 'Approved' },
  { id: 'REJECTED', label: 'Rejected' },
];

export default function ReflectionsPage() {
  const toast = useToast();
  const [tab, setTab] = useState('PENDING');
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState(null);
  const [approveTarget, setApproveTarget] = useState(null);
  const [rejectTarget, setRejectTarget] = useState(null);
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState('');
  const [busy, setBusy] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    reflectionsByStatus(tab).then(setItems).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, [tab]);

  useEffect(() => { setExpanded(null); load(); }, [load]);

  const doApprove = async () => {
    setBusy('approve');
    try {
      await approveReflection(approveTarget.id);
      toast.success('Approved. The student has been awarded 20 points.');
      setApproveTarget(null);
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  const doReject = async () => {
    if (!note.trim()) {
      setNoteError('A review note is required when rejecting.');
      return;
    }
    setBusy('reject');
    try {
      await rejectReflection(rejectTarget.id, note.trim());
      toast.success('Reflection rejected with your note.');
      setRejectTarget(null);
      setNote('');
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Librarian"
        title="Reading reflections"
        subtitle="Approve thoughtful reflections for points, or reject with a note that helps the student improve."
      />

      <Tabs tabs={TABS} active={tab} onChange={setTab} ariaLabel="Reflection status" />

      {error && <Alert tone="error" title="Could not load reflections" action={<Button size="sm" variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>}

      {loading ? (
        <div className="stack">{Array.from({ length: 2 }).map((_, i) => <div className="card" key={i}><Skeleton width="35%" height={14} /><Skeleton height={50} style={{ marginTop: 14 }} /></div>)}</div>
      ) : items.length === 0 ? (
        <EmptyState icon={PenLine} title={tab === 'PENDING' ? 'No reflections waiting' : 'Nothing in this view'} description={tab === 'PENDING' ? 'Students submit a reflection after returning a book on time or paying a fine.' : 'Reviewed reflections will appear here.'} />
      ) : (
        <div className="stack">
          {items.map((reflection) => {
            const isOpen = expanded === reflection.id;
            const long = reflection.content.length > 220;
            return (
              <article className="reflection-card" key={reflection.id}>
                <div className="reflection-card__head">
                  <div>
                    <strong>{reflection.studentName}</strong>
                    <span className="text-muted"> on </span>
                    <strong>{reflection.bookTitle}</strong>
                    <div className="cell-detail">{formatDateTime(reflection.submissionDate)}</div>
                  </div>
                  <StatusBadge status={reflection.status} />
                </div>
                <div className="reflection-card__body">
                  {long && !isOpen ? `${reflection.content.slice(0, 220)}…` : reflection.content}
                </div>
                {long && (
                  <button type="button" className="btn btn--ghost btn--sm" onClick={() => setExpanded(isOpen ? null : reflection.id)}>
                    {isOpen ? 'Show less' : 'Show full reflection'}
                  </button>
                )}
                {reflection.status !== 'PENDING' && reflection.reviewNote && (
                  <div className="alert alert--info" style={{ marginTop: 10 }}>
                    Librarian note: “{reflection.reviewNote}”
                  </div>
                )}
                {reflection.status === 'PENDING' && (
                  <div className="reflection-card__actions">
                    <Button size="sm" variant="secondary" icon={XCircle} onClick={() => { setRejectTarget(reflection); setNote(''); setNoteError(''); }}>Reject</Button>
                    <Button size="sm" variant="success" icon={CheckCircle2} onClick={() => setApproveTarget(reflection)}>Approve (+20 pts)</Button>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}

      <ConfirmDialog
        open={Boolean(approveTarget)}
        onClose={() => setApproveTarget(null)}
        onConfirm={doApprove}
        loading={busy === 'approve'}
        title="Approve this reflection?"
        message={approveTarget ? `${approveTarget.studentName}'s reflection on “${approveTarget.bookTitle}” will be approved, the loan closed and 20 points awarded exactly once.` : ''}
        confirmLabel="Approve & award 20 points"
        variant="success"
      />

      <Modal
        open={Boolean(rejectTarget)}
        onClose={() => setRejectTarget(null)}
        title="Reject this reflection"
        description={rejectTarget ? `${rejectTarget.studentName} · ${rejectTarget.bookTitle}` : ''}
        footer={(
          <>
            <Button variant="ghost" onClick={() => setRejectTarget(null)} disabled={busy === 'reject'}>Cancel</Button>
            <Button variant="danger" onClick={doReject} loading={busy === 'reject'}>Reject reflection</Button>
          </>
        )}
      >
        <Textarea
          label="Review note (required)"
          rows={4}
          maxLength={500}
          value={note}
          error={noteError}
          hint={`${note.trim().length}/500 · shared with the student`}
          placeholder="Explain what is missing and how the student can improve."
          onChange={(e) => { setNote(e.target.value); setNoteError(''); }}
        />
      </Modal>
    </div>
  );
}
