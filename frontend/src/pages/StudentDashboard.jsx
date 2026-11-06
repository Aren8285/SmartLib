import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookMarked, Clock3, Compass, Sparkles, Star, Wallet } from 'lucide-react';
import { getRecommendations, requestLoan, studentDashboard } from '../api';
import { useToast } from '../context/ToastContext';
import { relativeTime, formatDateTime, initials } from '../utils/format';
import {
  Alert, Badge, Button, Card, EmptyState, PageHeader, Skeleton, StatCard, StatusBadge,
} from '../components/ui';
import Countdown from '../components/Countdown';

export default function StudentDashboard() {
  const toast = useToast();
  const [dash, setDash] = useState(null);
  const [recs, setRecs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    Promise.all([studentDashboard(), getRecommendations()])
      .then(([dashboard, recommendations]) => {
        setDash(dashboard);
        setRecs(recommendations || []);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const request = async (book) => {
    setBusy(book.id);
    try {
      const res = await requestLoan(book.id);
      toast.success(res.message || 'Request placed.');
      load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy('');
    }
  };

  if (loading) {
    return (
      <div className="page">
        <PageHeader eyebrow="Student" title="My dashboard" />
        <div className="stats-grid">
          {Array.from({ length: 4 }).map((_, i) => (
            <div className="stat-card" key={i}><Skeleton width="50%" height={10} /><Skeleton width="35%" height={26} style={{ marginTop: 12 }} /></div>
          ))}
        </div>
        <div className="card"><Skeleton width="30%" height={18} /><Skeleton height={12} style={{ marginTop: 16 }} /><Skeleton height={12} style={{ marginTop: 8 }} /></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <PageHeader eyebrow="Student" title="My dashboard" />
        <Alert tone="error" title="Could not load your dashboard" action={<Button variant="secondary" onClick={load}>Try again</Button>}>
          {error}
        </Alert>
      </div>
    );
  }

  const actions = dash?.actionsNeeded || [];
  const nextDue = dash?.nextDueDate;

  return (
    <div className="page">
      <PageHeader
        eyebrow="Student"
        title="My dashboard"
        subtitle="Your loans, deadlines, points and the next books picked for you."
        actions={<Link to="/books" className="btn btn--primary"><Compass size={15} /> Browse catalogue</Link>}
      />

      <div className="stats-grid">
        <StatCard label="Reading points" value={dash.points} icon={Star} tone="accent" hint="Approved reflections add 20" />
        <StatCard label="Active loans" value={dash.activeLoans} icon={BookMarked} hint="Max 3 at a time" />
        <StatCard label="In queue" value={dash.waitlisted} icon={Clock3} tone="warning" hint="FIFO waitlist" />
        <StatCard label="Fines due" value={`₹${dash.pendingFinesAmount || 0}`} icon={Wallet} tone={dash.pendingFinesAmount > 0 ? 'danger' : 'success'} hint={dash.pendingFinesAmount > 0 ? 'Pay to keep borrowing' : 'Nothing outstanding'} />
      </div>

      <div className="reports-grid">
        <Card title="Action needed" subtitle="Time-sensitive items from your loans">
          {actions.length === 0 ? (
            <EmptyState title="All caught up" description="Nothing needs your attention right now." />
          ) : (
            <ul className="action-list">
              {actions.map((action) => (
                <li className="action-item" key={action.loanId}>
                  <StatusBadge status={action.status} />
                  <strong>{action.bookTitle}</strong>
                  <span className="text-muted">{action.action}</span>
                  <span className="action-item__meta">
                    {action.pickupDeadline && (
                      <>
                        <Countdown deadline={action.pickupDeadline} />
                        <span title={formatDateTime(action.pickupDeadline)}>{relativeTime(action.pickupDeadline)}</span>
                      </>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="Next due date" subtitle="Return on time to earn points">
          {nextDue ? (
            <>
              <div style={{ fontSize: '1.5rem', fontWeight: 750, letterSpacing: '-0.02em' }}>{relativeTime(nextDue)}</div>
              <p className="text-muted" style={{ fontSize: '0.82rem' }} title={formatDateTime(nextDue)}>{formatDateTime(nextDue)}</p>
              <div className="row" style={{ marginTop: 16 }}>
                <Link to="/my-loans" className="btn btn--secondary btn--sm">View my loans</Link>
                <Badge tone="info">Fines accrue at ₹5/day</Badge>
              </div>
            </>
          ) : (
            <EmptyState icon={BookMarked} title="No books out" description="Reserve a title and it will show up here." action={<Link to="/books" className="btn btn--primary btn--sm">Browse catalogue</Link>} />
          )}
        </Card>
      </div>

      <Card
        title="Recommended for you"
        subtitle="Based on your subjects and readers with similar taste"
        actions={<Badge tone="accent" icon={Sparkles}>Personalised</Badge>}
      >
        {recs.length === 0 ? (
          <EmptyState title="No recommendations yet" description="Borrow and reflect on a book to unlock suggestions from similar readers." />
        ) : (
          <div className="book-grid">
            {recs.map(({ book, reason }) => (
              <article className="book-card" key={book.id}>
                <div className="book-card__top">
                  <span className="book-cover book-cover--amber" aria-hidden="true">{initials(book.title)}</span>
                  <div>
                    <h3>{book.title}</h3>
                    <div className="book-card__author">{book.author}</div>
                  </div>
                </div>
                <p style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>{reason}</p>
                <div className="book-card__meta">
                  <span>{book.availableCopies > 0 ? `${book.availableCopies} available` : 'No copies free'}</span>
                  {book.waitlistSize > 0 && <span>· {book.waitlistSize} waiting</span>}
                  {book.shelfCode && <span className="shelf-tag">{book.shelfCode}</span>}
                </div>
                <div className="book-card__actions">
                  <Button size="sm" loading={busy === book.id} onClick={() => request(book)}>
                    {book.availableCopies > 0 ? 'Request' : 'Join waitlist'}
                  </Button>
                  <Link to={`/map?shelf=${book.shelfCode || ''}`} className="btn btn--ghost btn--sm">Locate</Link>
                </div>
              </article>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
