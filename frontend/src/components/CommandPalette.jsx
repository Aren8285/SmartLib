import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import {
  ArrowRight, BarChart3, BookMarked, BookOpen, ClipboardList, Compass, LayoutDashboard,
  Library, ListOrdered, Loader2, Map, PenLine, Search, Star, Users, Wallet,
} from './icons';
import { getAllUsers, getBooks } from '../api';
import { useAuth } from '../context/AuthContext';

const NAVIGATION = {
  STUDENT: [
    { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard, hint: 'Loans, deadlines and recommendations' },
    { label: 'Catalogue', to: '/books', icon: BookOpen, hint: 'Search and reserve a title' },
    { label: 'Library map', to: '/map', icon: Map, hint: 'Find a shelf on the floor plan' },
    { label: 'My loans', to: '/my-loans', icon: BookMarked, hint: 'Renew, cancel or write a reflection' },
    { label: 'Fines', to: '/my-fines', icon: Wallet, hint: 'Review and pay outstanding fines' },
    { label: 'Leaderboard', to: '/leaderboard', icon: Star, hint: 'See the reading points ranking' },
    { label: 'Profile', to: '/profile', icon: Users, hint: 'Preferred subjects and account' },
  ],
  LIBRARIAN: [
    { label: 'Dashboard', to: '/librarian/dashboard', icon: LayoutDashboard, hint: 'Today’s library pulse' },
    { label: 'Circulation', to: '/librarian/circulation', icon: ClipboardList, hint: 'Issue, return and desk lookup' },
    { label: 'Books', to: '/librarian/books', icon: BookOpen, hint: 'Titles, copies and shelves' },
    { label: 'Shelves', to: '/librarian/shelves', icon: Library, hint: 'Library floor layout' },
    { label: 'Reports', to: '/librarian/reports', icon: BarChart3, hint: 'Circulation, fines and inventory' },
    { label: 'Reflections', to: '/librarian/reflections', icon: PenLine, hint: 'Review student reflections' },
    { label: 'Fines', to: '/librarian/fines', icon: Wallet, hint: 'Collect outstanding fines' },
    { label: 'Waitlists', to: '/librarian/waitlists', icon: ListOrdered, hint: 'Inspect FIFO queues' },
    { label: 'Users', to: '/librarian/users', icon: Users, hint: 'Students and staff accounts' },
    { label: 'Map', to: '/map', icon: Compass, hint: 'Open the reading room plan' },
  ],
};

const kindLabel = { nav: 'Page', book: 'Book', person: 'Person' };

export default function CommandPalette() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const role = user?.role === 'LIBRARIAN' ? 'LIBRARIAN' : 'STUDENT';
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const [books, setBooks] = useState([]);
  const [people, setPeople] = useState([]);
  const [bookLoading, setBookLoading] = useState(false);
  const [peopleLoaded, setPeopleLoaded] = useState(false);
  const inputRef = useRef(null);
  const previousFocus = useRef(null);

  const openPalette = useCallback(() => {
    setQuery('');
    setActive(0);
    setOpen(true);
  }, []);

  useEffect(() => {
    const onKeyDown = (event) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        if (open) setOpen(false);
        else openPalette();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    const onRequest = () => openPalette();
    window.addEventListener('smartlib:open-command', onRequest);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('smartlib:open-command', onRequest);
    };
  }, [open, openPalette]);

  useEffect(() => {
    if (!open) return undefined;
    previousFocus.current = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusTimer = window.setTimeout(() => inputRef.current?.focus(), 20);
    return () => {
      window.clearTimeout(focusTimer);
      document.body.style.overflow = previousOverflow;
      previousFocus.current?.focus?.();
    };
  }, [open]);

  useEffect(() => {
    setActive(0);
  }, [query]);

  useEffect(() => {
    if (!open || role !== 'LIBRARIAN' || peopleLoaded) return undefined;
    let cancelled = false;
    getAllUsers()
      .then((list) => {
        if (!cancelled) {
          setPeople(list || []);
          setPeopleLoaded(true);
        }
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [open, peopleLoaded, role]);

  useEffect(() => {
    if (!open) return undefined;
    const term = query.trim();
    if (term.length < 2) {
      setBooks([]);
      setBookLoading(false);
      return undefined;
    }
    let cancelled = false;
    setBookLoading(true);
    const timer = window.setTimeout(() => {
      getBooks({ query: term, size: 6 })
        .then((envelope) => {
          if (!cancelled) setBooks(envelope?.items || []);
        })
        .catch(() => {
          if (!cancelled) setBooks([]);
        })
        .finally(() => {
          if (!cancelled) setBookLoading(false);
        });
    }, 180);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [open, query]);

  const results = useMemo(() => {
    const term = query.trim().toLowerCase();
    const navigation = NAVIGATION[role]
      .filter((item) => !term || `${item.label} ${item.hint} ${item.to}`.toLowerCase().includes(term))
      .map((item) => ({
        id: `nav-${item.to}`,
        kind: 'nav',
        label: item.label,
        meta: item.hint,
        icon: item.icon,
        action: () => navigate(item.to),
      }));

    const bookResults = term.length >= 2
      ? books.slice(0, 6).map((book) => ({
        id: `book-${book.id}`,
        kind: 'book',
        label: book.title,
        meta: `${book.author} · ${book.subject}`,
        badge: book.shelfCode ? `Shelf ${book.shelfCode}` : 'Unshelved',
        icon: BookOpen,
        action: () => navigate(role === 'LIBRARIAN'
          ? `/librarian/books?q=${encodeURIComponent(book.title)}`
          : `/books/${book.id}`),
      }))
      : [];

    const peopleResults = role === 'LIBRARIAN' && term
      ? people
        .filter((person) => `${person.name} ${person.email}`.toLowerCase().includes(term))
        .slice(0, 5)
        .map((person) => ({
          id: `person-${person.id}`,
          kind: 'person',
          label: person.name,
          meta: person.email,
          badge: person.role,
          icon: Users,
          action: () => navigate(`/librarian/circulation?q=${encodeURIComponent(person.name)}`),
        }))
      : [];

    return [...navigation, ...bookResults, ...peopleResults];
  }, [books, navigate, people, query, role]);

  useEffect(() => {
    if (active >= results.length) setActive(0);
  }, [active, results.length]);

  useEffect(() => {
    if (!open) return;
    const activeResult = results[active];
    if (!activeResult) return;
    document.getElementById(`command-${activeResult.id}`)?.scrollIntoView({ block: 'nearest' });
  }, [active, open, results]);

  const choose = (result) => {
    if (!result) return;
    setOpen(false);
    result.action();
  };

  const onInputKeyDown = (event) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      setOpen(false);
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive((current) => (results.length ? (current + 1) % results.length : 0));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((current) => (results.length ? (current - 1 + results.length) % results.length : 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      choose(results[active]);
    }
  };

  if (!open) return null;

  return createPortal(
    <div className="command-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setOpen(false); }}>
      <section
        className="command"
        role="dialog"
        aria-modal="true"
        aria-label="SmartLib quick find"
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault();
            setOpen(false);
          }
        }}
      >
        <div className="command__search">
          <Search size={19} aria-hidden="true" />
          <input
            ref={inputRef}
            id="command-input"
            className="command__input"
            placeholder={role === 'LIBRARIAN' ? 'Find a book, student, or page…' : 'Find a book, author, ISBN, or page…'}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={onInputKeyDown}
            role="combobox"
            aria-expanded="true"
            aria-controls="command-results"
            aria-activedescendant={results[active] ? `command-${results[active].id}` : undefined}
            aria-autocomplete="list"
          />
          {bookLoading ? <Loader2 size={17} className="spin command__loader" aria-label="Searching" /> : <kbd>ESC</kbd>}
        </div>

        <div className="command__body">
          {results.length === 0 ? (
            <div className="command__empty">
              <Search size={22} aria-hidden="true" />
              <strong>No matches for “{query}”</strong>
              <span>Try a title, author, ISBN, student name or page name.</span>
            </div>
          ) : (
            <div id="command-results" className="command__results" role="listbox" aria-label="Quick find results">
              {results.map((result, index) => {
                const Icon = result.icon;
                return (
                  <button
                    type="button"
                    key={result.id}
                    id={`command-${result.id}`}
                    role="option"
                    aria-selected={index === active}
                    className={`command__result ${index === active ? 'command__result--active' : ''}`}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => choose(result)}
                  >
                    <span className={`command__result-icon command__result-icon--${result.kind}`}>
                      <Icon size={17} aria-hidden="true" />
                    </span>
                    <span className="command__result-copy">
                      <strong>{result.label}</strong>
                      <span>{result.meta}</span>
                    </span>
                    {result.badge && <span className="command__badge">{result.badge}</span>}
                    <span className="command__kind">{kindLabel[result.kind]}</span>
                    <ArrowRight size={14} className="command__arrow" aria-hidden="true" />
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <footer className="command__foot">
          <span><kbd>↑</kbd><kbd>↓</kbd> move</span>
          <span><kbd>↵</kbd> open</span>
          <span className="command__foot-hint">{role === 'LIBRARIAN' ? 'Books and people are searchable' : 'Live catalogue search'}</span>
        </footer>
      </section>
    </div>,
    document.body,
  );
}
