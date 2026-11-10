import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AlertTriangle, ArrowRight, BookOpen, MapPin } from '../components/icons';
import { getShelfBooks, getShelves } from '../api';
import { useAuth } from '../context/AuthContext';
import {
  Alert, Badge, Button, Card, EmptyState, PageHeader, Skeleton, StatusBadge,
} from '../components/ui';

const SECTION_COLORS = ['#365663', '#b47a38', '#4f7862', '#75536b', '#9a4a3f', '#3f7373'];

function buildWalkingPath(points, cornerRadius = 9) {
  if (points.length < 2) return '';
  const round = (value) => Math.round(value * 10) / 10;
  let path = `M ${round(points[0].x)} ${round(points[0].y)}`;

  for (let index = 1; index < points.length - 1; index += 1) {
    const previous = points[index - 1];
    const current = points[index];
    const next = points[index + 1];
    const incoming = Math.hypot(current.x - previous.x, current.y - previous.y) || 1;
    const outgoing = Math.hypot(next.x - current.x, next.y - current.y) || 1;
    const radius = Math.min(cornerRadius, incoming / 2, outgoing / 2);
    const before = {
      x: current.x - ((current.x - previous.x) / incoming) * radius,
      y: current.y - ((current.y - previous.y) / incoming) * radius,
    };
    const after = {
      x: current.x + ((next.x - current.x) / outgoing) * radius,
      y: current.y + ((next.y - current.y) / outgoing) * radius,
    };
    path += ` L ${round(before.x)} ${round(before.y)} Q ${round(current.x)} ${round(current.y)} ${round(after.x)} ${round(after.y)}`;
  }

  const last = points[points.length - 1];
  return `${path} L ${round(last.x)} ${round(last.y)}`;
}

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
  const roomRef = useRef(null);
  const shelfRefs = useRef(new Map());
  const [route, setRoute] = useState(null);

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
  const columnCount = useMemo(
    () => Math.max(1, ...shelves.map((shelf) => shelf.column)),
    [shelves],
  );

  const selectedShelf = shelves.find((s) => s.code === selectedCode);
  const invalidSelection = selectedCode && !loading && !selectedShelf;

  const select = (code) => {
    if (selectedCode === code) setParams({}, { replace: true });
    else setParams({ shelf: code });
  };

  useLayoutEffect(() => {
    const room = roomRef.current;
    const shelfElement = selectedShelf ? shelfRefs.current.get(selectedShelf.code) : null;
    if (!room || !shelfElement) { setRoute(null); return undefined; }

    const measure = () => {
      const roomRect = room.getBoundingClientRect();
      const shelfRect = shelfElement.getBoundingClientRect();
      const door = room.querySelector('.floor-plan__door');
      const shelfGrid = room.querySelector('.floor-plan__shelves');
      const objects = room.querySelector('.floor-plan__objects');
      if (!door || !shelfGrid) return;

      const doorRect = door.getBoundingClientRect();
      const gridRect = shelfGrid.getBoundingClientRect();
      const rowIndex = rows.findIndex(([row]) => String(row) === String(selectedShelf.row));
      const walkways = room.querySelectorAll('.floor-plan__walkway');
      const walkway = walkways[rowIndex];
      const originX = roomRect.left + room.clientLeft;
      const originY = roomRect.top + room.clientTop;
      const localX = (value) => value - originX;
      const localY = (value) => value - originY;

      const shelfBottom = localY(shelfRect.bottom);
      const objectsTop = objects ? localY(objects.getBoundingClientRect().top) : null;
      const laneY = walkway
        ? localY(walkway.getBoundingClientRect().top + walkway.getBoundingClientRect().height / 2)
        : shelfBottom + Math.max(6, ((objectsTop ?? shelfBottom + 16) - shelfBottom) / 2);

      const gridLeft = localX(gridRect.left);
      const gridRight = localX(gridRect.right);
      const leftGap = Math.max(6, gridLeft / 2);
      const rightGap = Math.max(6, (room.clientWidth - gridRight) / 2);
      const corridorX = selectedShelf.column <= Math.ceil(columnCount / 2)
        ? gridLeft - leftGap
        : gridRight + rightGap;

      const start = {
        x: localX(doorRect.left) + doorRect.width / 2,
        y: localY(doorRect.top) - 6,
      };
      const shelfCenter = localX(shelfRect.left) + shelfRect.width / 2;
      const points = [
        start,
        { x: corridorX, y: start.y },
        { x: corridorX, y: laneY },
        { x: shelfCenter, y: laneY },
        { x: shelfCenter, y: shelfBottom + 3 },
      ];

      setRoute({
        d: buildWalkingPath(points),
        start,
        end: points[points.length - 1],
      });
    };

    measure();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(room);
    observer?.observe(shelfElement);
    window.addEventListener('resize', measure);
    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', measure);
    };
  }, [columnCount, rows, selectedShelf]);

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
        subtitle="A plan of the main reading room. Click a shelf bay to see the titles held there."
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
              <p className="floor-plan__eyebrow">Level 01 · Main reading room</p>
              <h2>Floor plan</h2>
              <p className="floor-plan__caption">{rows.length} shelf rows · {shelves.length} bays · click a bay to inspect</p>
            </div>
            <div className="floor-plan__orientation">
              <Badge tone="info"><ArrowRight size={12} style={{ transform: 'rotate(-90deg)' }} aria-hidden="true" /> North at top</Badge>
              <span className="floor-plan__scale-label">Not to scale</span>
            </div>
          </div>

          {shelves.length === 0 ? (
            <EmptyState
              title="No shelves have been set up"
              description="A librarian can add shelves and assign books to them."
              action={user?.role === 'LIBRARIAN' ? <Link to="/librarian/shelves" className="btn btn--primary btn--sm">Create shelves</Link> : null}
            />
          ) : (
            <>
              <div className="floor-plan__canvas">
                <div className="floor-plan__room" ref={roomRef}>
                  <div className="floor-plan__windows" aria-hidden="true">
                    <span /><span /><span /><span />
                  </div>
                  <div className="floor-plan__compass" aria-hidden="true"><span>N</span><i /></div>

                  {selectedShelf && route && (
                    <svg
                      className="floor-plan__route"
                      role="img"
                      aria-label={`Walking route from the main entrance to Shelf ${selectedShelf.code}`}
                    >
                      <path className="floor-plan__route-casing" d={route.d} />
                      <path className="floor-plan__route-line" d={route.d} />
                      <circle className="floor-plan__route-start" cx={route.start.x} cy={route.start.y} r={4.5} />
                      <circle className="floor-plan__route-end" cx={route.end.x} cy={route.end.y} r={4.5} />
                    </svg>
                  )}

                  <div
                    className="floor-plan__shelves"
                    style={{ gridTemplateColumns: `32px repeat(${columnCount}, minmax(0, 1fr))` }}
                  >
                    {rows.map(([row, list], rowIndex) => (
                      <React.Fragment key={row}>
                        <div className="floor-plan__row-label" aria-hidden="true"><span>R{row}</span></div>
                        {Array.from({ length: columnCount }, (_, index) => index + 1).map((column) => {
                          const shelf = list.find((item) => item.column === column);
                          if (!shelf) return <span className="shelf-plot shelf-plot--void" key={`${row}-${column}`} aria-hidden="true" />;
                          const color = colorFor(shelf.section);
                          const empty = shelf.bookCount === 0;
                          return (
                            <button
                              type="button"
                              key={shelf.id}
                              ref={(node) => {
                                if (node) shelfRefs.current.set(shelf.code, node);
                                else shelfRefs.current.delete(shelf.code);
                              }}
                              className={`shelf-plot ${empty ? 'shelf-plot--empty' : ''} ${selectedCode === shelf.code ? 'shelf-plot--selected' : ''}`}
                              style={{ '--shelf-color': color }}
                              aria-pressed={selectedCode === shelf.code}
                              aria-label={`Shelf ${shelf.code}, ${shelf.section}, ${shelf.bookCount} of ${shelf.capacity} books`}
                              onClick={() => select(shelf.code)}
                            >
                              <span className="shelf-plot__top">
                                <span className="shelf-plot__code">{shelf.code}</span>
                                {empty && <span className="shelf-plot__empty" aria-hidden="true"><AlertTriangle size={11} /></span>}
                              </span>
                              <span className="shelf-plot__section">{shelf.section}</span>
                              <span className="shelf-plot__count">{shelf.bookCount}<span>/{shelf.capacity}</span></span>
                            </button>
                          );
                        })}
                        {rowIndex < rows.length - 1 && (
                          <div className="floor-plan__walkway" style={{ gridColumn: '1 / -1' }} aria-hidden="true">
                            <span>Aisle {rowIndex + 1}</span>
                          </div>
                        )}
                      </React.Fragment>
                    ))}
                  </div>

                  <div className="floor-plan__objects" aria-hidden="true">
                    <div className="map-object map-object--desk"><span>Information desk</span></div>
                    <div className="map-object map-object--tables"><span>Reading tables</span></div>
                  </div>

                  <div className="floor-plan__door" aria-hidden="true"><i /><span>Main entrance</span></div>
                </div>
              </div>
              <div className="floor-plan__foot">
                <div className="legend" aria-label="Map legend">
                  {selectedShelf && route && (
                    <span className="legend__item">
                      <span className="legend__route-swatch" aria-hidden="true" />
                      Walking route
                    </span>
                  )}
                  {sections.map((section) => (
                    <span className="legend__item" key={section}>
                      <span className="legend__swatch" style={{ '--swatch': colorFor(section) }} />
                      {section}
                    </span>
                  ))}
                </div>
                <span className="floor-plan__footnote">
                  {selectedShelf
                    ? `Follow the dashed line from the main entrance to Shelf ${selectedShelf.code}.`
                    : 'Walk between shelf rows using the marked aisles.'}
                </span>
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

      <Card title="Reading the plan" subtitle="One floor, twelve shelf bays">
        <div className="reports-grid" style={{ gap: 12 }}>
          <div className="inline-list">
            <div className="inline-row"><span>Shelf code</span><strong>Row letter + column number</strong></div>
            <div className="inline-row"><span>Colour</span><strong>Subject section</strong></div>
            <div className="inline-row"><span>Footprint</span><strong>Books on shelf / capacity</strong></div>
          </div>
          <p className="cell-detail">
            North is at the top of the plan and the main entrance is at the front. “Locate on map” links
            from the catalogue open this page with the matching shelf outlined. Select a shelf to trace the
            dashed walking route from the entrance.
          </p>
        </div>
      </Card>
    </div>
  );
}
