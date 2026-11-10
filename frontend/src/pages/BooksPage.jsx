import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, LayoutGrid, List, MapPin, Search, SlidersHorizontal, X } from '../components/icons';
import { getBooks, getShelves, getSubjects, requestLoan } from '../api';
import { useToast } from '../context/ToastContext';
import { coverMark, coverTone, subjectSlug } from '../utils/bookDisplay';
import {
  Alert, Badge, Button, EmptyState, PageHeader, Pagination, Select, Skeleton, StatusBadge, Table,
} from '../components/ui';

function BookCard({ book, busy, onRequest }) {
  const available = book.availableCopies > 0;
  return (
    <article className="book-card">
      <div className="book-card__top">
        <span className={`book-cover ${coverTone(book.subject)}`} aria-hidden="true">
          <span className="book-cover__mark">{coverMark(book.title)}</span>
          <span className="book-cover__rule" />
          <span className="book-cover__subject">{book.subject}</span>
        </span>
        <div className="book-card__identity">
          <span className="book-card__kicker">{book.subject}</span>
          <h3><Link to={`/books/${book.id}`}>{book.title}</Link></h3>
          <div className="book-card__author">{book.author}</div>
        </div>
      </div>
      <div className="book-card__availability">
        <div className="availability-bar" aria-hidden="true">
          <span style={{ width: `${book.totalCopies ? (book.availableCopies / book.totalCopies) * 100 : 0}%` }} />
        </div>
        <div className="book-card__meta">
          <StatusBadge status={available ? 'AVAILABLE' : 'WAITLISTED'} label={available ? `${book.availableCopies} of ${book.totalCopies} available` : 'All copies out'} />
          {book.waitlistSize > 0 && <span className="queue-note">{book.waitlistSize} waiting</span>}
        </div>
      </div>
      <div className="book-card__foot">
        {book.shelfCode ? (
          <Link to={`/map?shelf=${book.shelfCode}`} className="book-card__shelf">
            <MapPin size={13} /> Shelf <span>{book.shelfCode}</span>
          </Link>
        ) : <span className="book-card__shelf muted">Not shelved yet</span>}
        <Link to={`/books/${book.id}`} className="book-card__details">Details</Link>
      </div>
      <div className="book-card__actions">
        <Button size="sm" variant={available ? 'primary' : 'secondary'} loading={busy === book.id} onClick={() => onRequest(book)}>
          {available ? 'Reserve a copy' : 'Join the queue'}
        </Button>
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
  const [shelves, setShelves] = useState([]);
  const [filters, setFilters] = useState({ query: '', subject: '', shelf: '', availableOnly: false, sort: 'title' });
  const [page, setPage] = useState(0);
  const [view, setView] = useState('grid');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const [filtersOpen, setFiltersOpen] = useState(false);
  const searchRef = useRef(null);
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

  useEffect(() => {
    getSubjects().then(setSubjects).catch(() => {});
    getShelves().then((rows) => setShelves(rows || [])).catch(() => {});
  }, []);
  useEffect(() => {
    const timer = setTimeout(load, 250);
    return () => clearTimeout(timer);
  }, [load]);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key !== '/' || event.metaKey || event.ctrlKey) return;
      const tag = document.activeElement?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
      event.preventDefault();
      searchRef.current?.focus();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

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

  const hasFilters = Boolean(filters.query || filters.subject || filters.shelf || filters.availableOnly);
  const books = data.items || [];
  const shelvedCount = useMemo(() => books.filter((b) => b.shelfCode).length, [books]);
  const clearFilters = () => { setFilters({ query: '', subject: '', shelf: '', availableOnly: false, sort: 'title' }); setPage(0); };
  const activeCount = [filters.subject, filters.shelf, filters.availableOnly].filter(Boolean).length;
  const selectedShelf = shelves.find((shelf) => shelf.code === filters.shelf);

  return (
    <div className="page">
      <PageHeader
        eyebrow="The catalogue"
        title="Find something worth staying up for"
        subtitle="Search the catalogue across six collections, then walk straight to the shelf."
      />

      <section className="catalogue-intro" aria-label="Catalogue search">
        <div className="catalogue-search">
          <Search size={18} aria-hidden="true" />
          <label className="sr-only" htmlFor="book-search">Search books</label>
          <input
            ref={searchRef}
            id="book-search"
            className="input"
            placeholder="Search a title, author, subject or ISBN…"
            value={filters.query}
            onChange={(e) => updateFilter({ query: e.target.value })}
          />
          {filters.query ? (
            <button type="button" className="catalogue-search__clear" onClick={() => updateFilter({ query: '' })} aria-label="Clear search">
              <X size={15} />
            </button>
          ) : <kbd className="catalogue-search__hint">/</kbd>}
        </div>
        <div className="catalogue-subjects" aria-label="Filter by subject">
          <button type="button" className={`subject-pill ${!filters.subject ? 'subject-pill--on' : ''}`} aria-pressed={!filters.subject} onClick={() => updateFilter({ subject: '' })}>
            All collections
          </button>
          {subjects.map((subject) => (
            <button
              type="button"
              key={subject}
              className={`subject-pill ${filters.subject === subject ? 'subject-pill--on' : ''}`}
              aria-pressed={filters.subject === subject}
              onClick={() => updateFilter({ subject: subject === filters.subject ? '' : subject })}
            >
              <span className={`subject-pill__dot subject-dot--${subjectSlug(subject)}`} />
              {subject}
            </button>
          ))}
        </div>
        <div className="catalogue-toolbar">
          <div className="catalogue-toolbar__left">
            <span className="catalogue-result">
              <strong>{data.totalItems || 0}</strong> title{(data.totalItems || 0) === 1 ? '' : 's'}
              {hasFilters ? ` ${(data.totalItems || 0) === 1 ? 'matches' : 'match'} your filters` : ' in the library'}
            </span>
            {shelvedCount > 0 && <span className="catalogue-result__note">{shelvedCount} on this page {shelvedCount === 1 ? 'is' : 'are'} shelved</span>}
          </div>
          <div className="catalogue-toolbar__right">
            <button type="button" className={`filter-toggle ${filtersOpen ? 'filter-toggle--open' : ''}`} onClick={() => setFiltersOpen((open) => !open)} aria-expanded={filtersOpen}>
              <SlidersHorizontal size={15} /> Filters {activeCount > 0 && <span className="filter-toggle__count">{activeCount}</span>}
            </button>
            <div className="segmented" role="group" aria-label="View mode">
              <button type="button" aria-pressed={view === 'grid'} onClick={() => setView('grid')}><LayoutGrid size={15} /> Cards</button>
              <button type="button" aria-pressed={view === 'table'} onClick={() => setView('table')}><List size={15} /> Table</button>
            </div>
          </div>
        </div>
        {filtersOpen && (
          <div className="catalogue-filters">
            <Select label="Shelf" value={filters.shelf} onChange={(e) => updateFilter({ shelf: e.target.value })}>
              <option value="">Any shelf</option>
              {shelves.map((shelf) => (
                <option key={shelf.id} value={shelf.code}>{shelf.code} — {shelf.section} ({shelf.bookCount}/{shelf.capacity})</option>
              ))}
            </Select>
            <Select label="Sort by" value={filters.sort} onChange={(e) => updateFilter({ sort: e.target.value })}>
              <option value="title">Title A–Z</option>
              <option value="title_desc">Title Z–A</option>
              <option value="author">Author A–Z</option>
              <option value="author_desc">Author Z–A</option>
              <option value="newest">Newest first</option>
              <option value="oldest">Oldest first</option>
            </Select>
            <label className="availability-toggle">
              <input type="checkbox" checked={filters.availableOnly} onChange={(e) => updateFilter({ availableOnly: e.target.checked })} />
              <span><strong>Available now</strong><small>Hide titles with every copy out</small></span>
            </label>
            {hasFilters && <Button variant="ghost" size="sm" onClick={clearFilters}>Clear all</Button>}
          </div>
        )}
        {(filters.subject || filters.shelf || filters.availableOnly) && (
          <div className="active-filters" aria-label="Active filters">
            {filters.subject && <button type="button" className="active-filter" onClick={() => updateFilter({ subject: '' })}>{filters.subject}<X size={12} /></button>}
            {filters.shelf && <button type="button" className="active-filter" onClick={() => updateFilter({ shelf: '' })}>Shelf {filters.shelf}{selectedShelf ? ` · ${selectedShelf.section}` : ''}<X size={12} /></button>}
            {filters.availableOnly && <button type="button" className="active-filter" onClick={() => updateFilter({ availableOnly: false })}>Available now<X size={12} /></button>}
          </div>
        )}
      </section>

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
          action={hasFilters ? <Button variant="secondary" onClick={clearFilters}>Clear filters</Button> : null}
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
