import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AlertTriangle, BookOpen, Compass, MapPin } from 'lucide-react';
import { getShelfBooks, getShelves } from '../api';
import { useAuth } from '../context/AuthContext';
import {
  Alert, Badge, Button, Card, EmptyState, PageHeader, Skeleton, StatusBadge,
} from '../components/ui';

const SECTION_COLORS = ['#2f5d8a', '#8a6a2f', '#5c7c5a', '#75536b', '#a3543f', '#3f7373'];

export default function LibraryMapPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const selectedCode = params.get('shelf') || '';
  const [shelves, setShelves] = useState([]);
  const [books, setBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [booksLoading, setBooksLoading] = useState(false);
  const [error, setError] = useState('');
  const [booksError, setBooksError] = useState('');

  const loadShelves = useCallback(() => {
    setLoading(true);
    setError('');
    getShelves().then(setShelves).catch((e) => setError(e.message)).finally(() => setLoading(false));
  }, []);

  useEffect(() => { loadShelves(); }, [loadShelves]);

  useEffect(() => {
    if (!selectedCode) { setBooks([]); setBooksError(''); return; }
    setBooksLoading(true);
    setBooksError('');
    getShelfBooks(selectedCode)
      .then(setBooks)
      .catch((e) => setBooksError(e.message))
      .finally(() => setBooksLoading(false));
  }, [selectedCode]);

  const sections = useMemo(
    () => [...new Set(shelves.map((s) => s.section))].sort(),
    [shelves],
  );
  const colorFor = (section) => SECTION_COLORS[Math.max(0, sections.indexOf(section)) % SECTION_COLORS.length];
  const rows = useMemo(() => {
    const grouped = {};
    shelves.forEach((shelf) => { (grouped[shelf.row] ||= []).push(shelf); });
    Object.values(grouped).forEach((list) => list.sort((a, b) => a.column - b.column));
    return Object.entries(grouped).sort(([a], [b]) => Number(a) - Number(b));
  }, [shelves]);

  const selectedShelf = shelves.find((s) => s.code === selectedCode);
  const invalidSelection = selectedCode && !loading && !selectedShelf;

  const select = (code) => {
    if (selectedCode === code) setParams({}, { replace: true });
    else setParams({ shelf: code });
  };

  if (loading) {
    return (
      <div className="page">
        <PageHeader eyebrow="Wayfinding" title="Library map" />
        <div className="map-layout">
          <div className="card"><Skeleton height={330} /></div>
          <div className="card"><Skeleton width="50%" height={18} /><Skeleton height={110} style={{ marginTop: 18 }} /></div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <PageHeader eyebrow="Wayfinding" title="Library map" />
        <Alert tone="error" title="Could not load the floor plan" action={<Button size="sm" variant="secondary" onClick={loadShelves}>Retry</Button>}>{error}</Alert>
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Wayfinding"
        title="Library map"
        subtitle="Click a shelf to see what is on it. Colours group shelves by subject section."
        actions={user?.role === 'LIBRARIAN' ? <Link to="/librarian/shelves" className="btn btn--primary"><MapPin size={15} /> Manage shelves</Link> : null}
      />

      {invalidSelection && (
        <Alert tone="warning" title={`Shelf “${selectedCode}” was not found`}>
          It may have been renamed or removed. Pick another shelf on the plan.
        </Alert>
      )}

      <div className="map-layout">
        <section className="floor-plan" aria-label="Library floor plan">
          <div className="floor-plan__head">
            <div>
              <h2>Floor plan</h2>
              <p className="cell-detail">4 rows · {shelves.length} shelves · click to inspect</p>
            </div>
            <Badge tone="info"><Compass size={12} /> Entrance at the front</Badge>
          </div>

          {shelves.length === 0 ? (
            <EmptyState
              title="No shelves have been set up"
              description="A librarian can add shelves and assign books to them."
              action={user?.role === 'LIBRARIAN' ? <Link to="/librarian/shelves" className="btn btn--primary btn--sm">Create shelves</Link> : null}
            />
          ) : (
            <>
              <div className="floor-plan__grid" style={{ gridTemplateColumns: `18px repeat(${Math.max(...shelves.map((s) => s.column), 1)}, minmax(0, 1fr))` }}>
                {rows.map(([row, list]) => (
                  <React.Fragment key={row}>
                    <div className="floor-plan__aisle"><span>R{row}</span></div>
                    {list.map((shelf) => {
                      const color = colorFor(shelf.section);
                      const empty = shelf.bookCount === 0;
                      return (
                        <button
                          type="button"
                          key={shelf.id}
                          className={`shelf-btn ${selectedCode === shelf.code ? 'shelf-btn--selected' : ''}`}
                          style={{ '--shelf-color': color }}
                          aria-pressed={selectedCode === shelf.code}
                          aria-label={`Shelf ${shelf.code}, ${shelf.section}, ${shelf.bookCount} of ${shelf.capacity} books`}
                          onClick={() => select(shelf.code)}
                        >
                          {empty && <span className="shelf-btn__empty" aria-label="Empty shelf"><AlertTriangle size={11} /></span>}
                          <span className="shelf-btn__code">{shelf.code}</span>
                          <span className="shelf-btn__section">{shelf.section}</span>
                          <span className="shelf-btn__capacity">{shelf.bookCount}/{shelf.capacity} books</span>
                        </button>
                      );
                    })}
                  </React.Fragment>
                ))}
              </div>
              <div className="legend" aria-label="Subject legend">
                {sections.map((section) => (
                  <span className="legend__item" key={section}>
                    <span className="legend__swatch" style={{ '--swatch': colorFor(section) }} />
                    {section}
                  </span>
                ))}
              </div>
            </>
          )}
        </section>

        <Card
          className="shelf-panel"
          title={selectedShelf ? `Shelf ${selectedShelf.code}` : 'Select a shelf'}
          subtitle={selectedShelf ? `${selectedShelf.section} · ${selectedShelf.bookCount}/${selectedShelf.capacity} books` : 'Shelf contents and availability will appear here.'}
          actions={selectedShelf && <Badge tone="neutral">{selectedShelf.label || selectedShelf.section}</Badge>}
        >
          {!selectedCode ? (
            <EmptyState icon={MapPin} title="Pick a shelf on the plan" description="Every coloured block is a real shelf. Click it to see the titles held there." />
          ) : booksLoading ? (
            <div className="stack"><Skeleton height={46} /><Skeleton height={46} /><Skeleton height={46} /></div>
          ) : booksError ? (
            <Alert tone="error" title="Could not load this shelf">{booksError}</Alert>
          ) : books.length === 0 ? (
            <EmptyState icon={BookOpen} title="This shelf is empty" description="A librarian can assign books to it from the book editor." />
          ) : (
            <div className="shelf-books">
              {books.map((book) => (
                <div className="shelf-book" key={book.id}>
                  <div>
                    <strong>{book.title}</strong>
                    <span>{book.author}</span>
                    <div style={{ marginTop: 4 }}>
                      <StatusBadge status={book.availableCopies > 0 ? 'AVAILABLE' : 'WAITLISTED'} label={book.availableCopies > 0 ? `${book.availableCopies} available` : 'All copies out'} />
                    </div>
                  </div>
                  {user?.role === 'STUDENT' && (
                    <Link to={`/books/${book.id}`} className="btn btn--secondary btn--sm">View</Link>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      <Card title="How the map works" subtitle="One floor, twelve shelves">
        <div className="reports-grid" style={{ gap: 12 }}>
          <div className="inline-list">
            <div className="inline-row"><span>Shelf code</span><strong>Row letter + column number</strong></div>
            <div className="inline-row"><span>Colour</span><strong>Subject section</strong></div>
            <div className="inline-row"><span>Capacity</span><strong>Available book slots</strong></div>
          </div>
          <p className="cell-detail">
            “Locate on map” links from the catalogue open this page with the matching shelf highlighted
            and pulsing, so you can walk straight to it.
          </p>
        </div>
      </Card>
    </div>
  );
}
