import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, LayoutGrid, List, MapPin, Search, SlidersHorizontal } from 'lucide-react';
import { getBooks, getSubjects, requestLoan } from '../api';
import { useToast } from '../context/ToastContext';
import { initials } from '../utils/format';
import {
  Alert, Badge, Button, EmptyState, PageHeader, Pagination, Select, Skeleton, StatusBadge, Table,
} from '../components/ui';

const COVER_TONES = ['', 'book-cover--amber', 'book-cover--green', 'book-cover--plum'];

function BookCard({ book, busy, onRequest }) {
  return (
    <article className="book-card">
      <div className="book-card__top">
        <span className={`book-cover ${COVER_TONES[book.title.length % COVER_TONES.length]}`} aria-hidden="true">
          {initials(book.title)}
        </span>
        <div>
          <h3><Link to={`/books/${book.id}`}>{book.title}</Link></h3>
          <div className="book-card__author">{book.author}</div>
        </div>
      </div>
      <div className="book-card__meta">
        <Badge tone="neutral">{book.subject}</Badge>
        {book.shelfCode && <span className="shelf-tag"><MapPin size={11} /> {book.shelfCode}</span>}
      </div>
      <div className="book-card__meta">
        <StatusBadge status={book.availableCopies > 0 ? 'AVAILABLE' : 'WAITLISTED'} label={book.availableCopies > 0 ? `${book.availableCopies} of ${book.totalCopies} available` : 'All copies out'} />
        {book.waitlistSize > 0 && <span>{book.waitlistSize} waiting</span>}
      </div>
      <div className="book-card__actions">
        <Button size="sm" loading={busy === book.id} onClick={() => onRequest(book)}>
          {book.availableCopies > 0 ? 'Request' : 'Join waitlist'}
        </Button>
        <Link to={`/books/${book.id}`} className="btn btn--ghost btn--sm">Details</Link>
        {book.shelfCode && (
          <Link to={`/map?shelf=${book.shelfCode}`} className="btn btn--ghost btn--sm" aria-label={`Locate ${book.title} on the map`}>
            <MapPin size={13} /> Map
          </Link>
        )}
      </div>
    </article>
  );
}

export default function BooksPage() {
  const toast = useToast();
  const [data, setData] = useState({ items: [], totalItems: 0, totalPages: 1, page: 0, size: 12 });
  const [subjects, setSubjects] = useState([]);
  const [filters, setFilters] = useState({ query: '', subject: '', availableOnly: false, sort: 'title' });
  const [page, setPage] = useState(0);
  const [view, setView] = useState('grid');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const size = 12;

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    getBooks({ ...filters, page, size })
      .then((envelope) => {
        setData(envelope?.items ? envelope : { items: envelope || [], totalItems: (envelope || []).length, totalPages: 1, page: 0, size });
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [filters, page]);

  useEffect(() => { getSubjects().then(setSubjects).catch(() => {}); }, []);
  useEffect(() => {
    const timer = setTimeout(load, 250);
    return () => clearTimeout(timer);
  }, [load]);

  const updateFilter = (patch) => {
    setPage(0);
    setFilters((current) => ({ ...current, ...patch }));
  };

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

  const hasFilters = Boolean(filters.query || filters.subject || filters.availableOnly);
  const books = data.items || [];
  const shelves = useMemo(() => books.filter((b) => b.shelfCode).length, [books]);

  return (
    <div className="page">
      <PageHeader
        eyebrow="Catalogue"
        title="Find your next read"
        subtitle="Search the full catalogue, filter by subject, and jump straight to the shelf."
      />

      <div className="toolbar">
        <div className="field toolbar__search" style={{ margin: 0 }}>
          <label className="sr-only" htmlFor="book-search">Search books</label>
          <div style={{ position: 'relative' }}>
            <Search size={15} aria-hidden="true" style={{ position: 'absolute', left: 11, top: '50%', transform: 'translateY(-50%)', color: 'var(--muted)' }} />
            <input
              id="book-search"
              className="input"
              style={{ paddingLeft: 33 }}
              placeholder="Search title, author or subject…"
              value={filters.query}
              onChange={(e) => updateFilter({ query: e.target.value })}
            />
          </div>
        </div>
        <Select aria-label="Filter by subject" value={filters.subject} onChange={(e) => updateFilter({ subject: e.target.value })}>
          <option value="">All subjects</option>
          {subjects.map((subject) => <option key={subject} value={subject}>{subject}</option>)}
        </Select>
        <Select aria-label="Sort books" value={filters.sort} onChange={(e) => updateFilter({ sort: e.target.value })}>
          <option value="title">Title A–Z</option>
          <option value="title_desc">Title Z–A</option>
          <option value="author">Author A–Z</option>
          <option value="author_desc">Author Z–A</option>
          <option value="newest">Newest first</option>
        </Select>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={filters.availableOnly}
            onChange={(e) => updateFilter({ availableOnly: e.target.checked })}
          />
          Available only
        </label>
        <div className="segmented" role="group" aria-label="View mode">
          <button type="button" aria-pressed={view === 'grid'} onClick={() => setView('grid')}><LayoutGrid size={14} /> Cards</button>
          <button type="button" aria-pressed={view === 'table'} onClick={() => setView('table')}><List size={14} /> Table</button>
        </div>
      </div>

      <div className="spread" style={{ color: 'var(--muted)', fontSize: '0.78rem' }}>
        <span>
          <SlidersHorizontal size={13} style={{ verticalAlign: '-2px' }} /> {data.totalItems || 0} title{(data.totalItems || 0) === 1 ? '' : 's'}
          {hasFilters ? ' matching your filters' : ' in the library'}
        </span>
        {shelves > 0 && <span>{shelves} on this page are already shelved</span>}
      </div>

      {error && (
        <Alert tone="error" title="Could not load the catalogue" action={<Button variant="secondary" size="sm" onClick={load}>Retry</Button>}>
          {error}
        </Alert>
      )}

      {loading ? (
        view === 'grid' ? (
          <div className="book-grid" aria-label="Loading books" role="status">
            {Array.from({ length: 6 }).map((_, i) => (
              <div className="book-card" key={i}>
                <div className="book-card__top"><Skeleton width={46} height={62} /><div style={{ flex: 1 }}><Skeleton width="80%" height={14} /><Skeleton width="55%" height={10} style={{ marginTop: 8 }} /></div></div>
                <Skeleton width="45%" height={18} /><Skeleton width="60%" height={10} />
              </div>
            ))}
          </div>
        ) : <SkeletonTable rows={6} columns={5} />
      ) : books.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title="No books match those filters"
          description={hasFilters ? 'Try a different search term or clear the filters.' : 'The catalogue is empty. A librarian can add books.'}
          action={hasFilters ? <Button variant="secondary" onClick={() => { setFilters({ query: '', subject: '', availableOnly: false, sort: 'title' }); setPage(0); }}>Clear filters</Button> : null}
        />
      ) : view === 'grid' ? (
        <div className="book-grid">
          {books.map((book) => <BookCard key={book.id} book={book} busy={busy} onRequest={request} />)}
        </div>
      ) : (
        <Table>
          <thead>
            <tr><th>Title</th><th>Author</th><th>Subject</th><th>Shelf</th><th>Available</th><th>Queue</th><th><span className="sr-only">Actions</span></th></tr>
          </thead>
          <tbody>
            {books.map((book) => (
              <tr key={book.id}>
                <td><Link to={`/books/${book.id}`}><strong>{book.title}</strong></Link></td>
                <td>{book.author}</td>
                <td><Badge tone="neutral">{book.subject}</Badge></td>
                <td>{book.shelfCode ? <span className="shelf-tag"><MapPin size={11} /> {book.shelfCode}</span> : '—'}</td>
                <td className={book.availableCopies === 0 ? 'text-danger' : ''}>{book.availableCopies} / {book.totalCopies}</td>
                <td>{book.waitlistSize || '—'}</td>
                <td>
                  <div className="row-actions">
                    <Button size="sm" loading={busy === book.id} onClick={() => request(book)}>
                      {book.availableCopies > 0 ? 'Request' : 'Join waitlist'}
                    </Button>
                    {book.shelfCode && <Link to={`/map?shelf=${book.shelfCode}`} className="btn btn--ghost btn--sm">Map</Link>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      {!loading && books.length > 0 && (
        <Pagination page={data.page ?? page} totalPages={data.totalPages || 1} totalItems={data.totalItems} size={data.size || size} onChange={setPage} />
      )}
    </div>
  );
}
