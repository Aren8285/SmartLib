import React, { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, MapPin } from 'lucide-react';
import { getBook, requestLoan } from '../api';
import { useToast } from '../context/ToastContext';
import { initials } from '../utils/format';
import {
  Alert, Badge, Button, Card, EmptyState, PageHeader, Skeleton, StatusBadge,
} from '../components/ui';

export default function BookDetailsPage() {
  const { id } = useParams();
  const toast = useToast();
  const [book, setBook] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    getBook(id).then(setBook).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const request = async () => {
    setBusy(true);
    try {
      const res = await requestLoan(book.id);
      toast.success(res.message || 'Request placed.');
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <div className="page">
        <Link to="/books" className="btn btn--ghost btn--sm"><ArrowLeft size={14} /> Back to catalogue</Link>
        <div className="card"><Skeleton width="40%" height={26} /><Skeleton width="25%" height={12} style={{ marginTop: 10 }} /><Skeleton height={70} style={{ marginTop: 24 }} /></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <Link to="/books" className="btn btn--ghost btn--sm"><ArrowLeft size={14} /> Back to catalogue</Link>
        <Alert tone="error" title="Book not found" action={<Button variant="secondary" onClick={load}>Retry</Button>}>{error}</Alert>
      </div>
    );
  }

  return (
    <div className="page">
      <Link to="/books" className="btn btn--ghost btn--sm" style={{ alignSelf: 'flex-start' }}>
        <ArrowLeft size={14} /> Back to catalogue
      </Link>
      <PageHeader
        eyebrow={book.subject}
        title={book.title}
        subtitle={book.author}
        actions={(
          <>
            {book.shelfCode && (
              <Link to={`/map?shelf=${book.shelfCode}`} className="btn btn--secondary">
                <MapPin size={15} /> Locate on map
              </Link>
            )}
            <Button loading={busy} onClick={request}>
              {book.availableCopies > 0 ? 'Request this book' : 'Join the waitlist'}
            </Button>
          </>
        )}
      />

      <div className="reports-grid">
        <Card>
          <div className="book-detail">
            <span className="book-cover book-cover--amber" aria-hidden="true">{initials(book.title)}</span>
            <div>
              <StatusBadge status={book.availableCopies > 0 ? 'AVAILABLE' : 'WAITLISTED'} label={book.availableCopies > 0 ? 'On the shelf' : 'All copies out'} />
              <p style={{ color: 'var(--muted)', marginTop: 10, fontSize: '0.86rem' }}>
                {book.availableCopies > 0
                  ? `${book.availableCopies} of ${book.totalCopies} copies are available right now.`
                  : 'Every copy is currently with a reader. Join the queue and you will be notified when one is free.'}
              </p>
              {book.waitlistSize > 0 && <Badge tone="warning">{book.waitlistSize} students waiting</Badge>}
            </div>
          </div>
          <dl className="meta-list">
            <div><dt>ISBN</dt><dd className="mono">{book.isbn || 'Not recorded'}</dd></div>
            <div><dt>Subject</dt><dd>{book.subject}</dd></div>
            <div><dt>Shelf</dt><dd>{book.shelfCode || 'Unassigned'}</dd></div>
            <div><dt>Section</dt><dd>{book.shelfSection || '—'}</dd></div>
            <div><dt>Copies</dt><dd>{book.availableCopies} available / {book.totalCopies} total</dd></div>
            <div><dt>Queue</dt><dd>{book.waitlistSize || 0} waiting</dd></div>
          </dl>
        </Card>

        <Card title="Where to find it" subtitle="The library floor is a 4 × 3 grid">
          {book.shelfCode ? (
            <EmptyState
              icon={MapPin}
              title={`Shelf ${book.shelfCode}`}
              description={`${book.shelfSection} · use the library map to see the shelf highlighted on the floor plan.`}
              action={<Link to={`/map?shelf=${book.shelfCode}`} className="btn btn--primary btn--sm">Open library map</Link>}
            />
          ) : (
            <EmptyState icon={MapPin} title="No shelf assigned" description="A librarian has not placed this title on the floor plan yet." />
          )}
        </Card>
      </div>
    </div>
  );
}
