import React from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight, BarChart3, BookOpen, Compass, ListOrdered, Map, Sparkles, Star, Trophy,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { Button } from '../components/ui';

const FEATURES = [
  {
    icon: ListOrdered,
    title: 'Fair FIFO waitlists',
    text: 'Every copy is reserved for the next student in line, with a 48-hour pickup window that automatically moves on when it expires.',
  },
  {
    icon: Map,
    title: 'Find it on the shelf',
    text: 'A visual floor plan shows the exact shelf for every title, colour-coded by subject, with live availability.',
  },
  {
    icon: Star,
    title: 'Points & reflections',
    text: 'Return on time and share what you learned to earn points. Librarian-approved reflections unlock more recommendations.',
  },
  {
    icon: Sparkles,
    title: 'Recommendations that fit',
    text: 'Suggestions come from your subjects and what similar readers borrowed, not from ads.',
  },
];

const DEMO_BOOKS = [
  ['Clean Code', 'Computer Science · Shelf A2'],
  ['Sapiens', 'History · Shelf D1'],
  ['The Feynman Lectures', 'Physics · Shelf B3'],
];

export default function LandingPage() {
  const { user } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const home = user?.role === 'LIBRARIAN' ? '/librarian/dashboard' : '/dashboard';

  return (
    <div className="landing">
      <header className="landing__nav">
        <Link to="/" className="brand">
          <span className="brand__mark"><BookOpen size={18} aria-hidden="true" /></span>
          <span>SmartLib<span className="brand__sub">University Library</span></span>
        </Link>
        <div className="row">
          <button type="button" className="icon-btn" onClick={toggleTheme} aria-label="Toggle colour theme">
            {isDark ? '☀' : '☾'}
          </button>
          {user ? (
            <Link to={home} className="btn btn--primary">Open my dashboard <ArrowRight size={15} /></Link>
          ) : (
            <>
              <Link to="/login" className="btn btn--ghost">Sign in</Link>
              <Link to="/register" className="btn btn--primary">Get started</Link>
            </>
          )}
        </div>
      </header>

      <section className="landing__hero">
        <div>
          <div className="eyebrow">Smart university library</div>
          <h1>Every book, every shelf, every reader in sync.</h1>
          <p className="lead">
            SmartLib replaces the paper sign-up sheet with a transparent borrowing experience:
            live availability, a fair waitlist, pickup deadlines, reflection points and reports
            that librarians actually use.
          </p>
          <div className="landing__cta">
            {user ? (
              <Link to={home} className="btn btn--primary btn--lg">Continue to SmartLib <ArrowRight size={17} /></Link>
            ) : (
              <>
                <Link to="/register" className="btn btn--primary btn--lg">Create a student account</Link>
                <Link to="/login" className="btn btn--secondary btn--lg">Sign in</Link>
              </>
            )}
          </div>
          <div className="landing__trust">
            <span><ListOrdered size={14} /> Fair queue</span>
            <span><Trophy size={14} /> Reading points</span>
            <span><BarChart3 size={14} /> Live librarian reports</span>
          </div>
        </div>

        <aside className="hero-panel" aria-label="Sample catalogue">
          <div className="hero-panel__head">
            <strong>On the shelf today</strong>
            <span className="badge badge--success">AVAILABLE</span>
          </div>
          {DEMO_BOOKS.map(([title, meta]) => (
            <div className="hero-book" key={title}>
              <span className="hero-book__spine" aria-hidden="true" />
              <div><strong>{title}</strong><span>{meta}</span></div>
              <Compass size={15} style={{ marginLeft: 'auto', color: 'var(--muted)' }} aria-hidden="true" />
            </div>
          ))}
          <div className="hero-panel__head" style={{ marginTop: 4 }}>
            <span className="cell-detail">Search · reserve · collect · read · reflect</span>
            <BookOpen size={15} aria-hidden="true" />
          </div>
        </aside>
      </section>

      <section className="landing__section">
        <div className="eyebrow">Built around the borrowing lifecycle</div>
        <h2>Less admin. More reading.</h2>
        <div className="features">
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <article className="feature" key={title}>
              <span className="feature__icon"><Icon size={19} aria-hidden="true" /></span>
              <h3>{title}</h3>
              <p>{text}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="landing__section" style={{ paddingTop: 0 }}>
        <div className="hero-panel" style={{ maxWidth: 760 }}>
          <div className="hero-panel__head">
            <div>
              <div className="eyebrow">For librarians</div>
              <h2 style={{ marginTop: 2 }}>The numbers, without the spreadsheet.</h2>
            </div>
            <Button variant="outline" onClick={() => { window.location.href = '/login'; }}>
              Librarian sign in
            </Button>
          </div>
          <p style={{ color: 'var(--muted)', fontSize: '0.86rem' }}>
            Circulation charts, overdue lists, fine collection, queue lengths and inventory in one
            place — with CSV exports for the records you still have to keep.
          </p>
        </div>
      </section>

      <footer className="landing__footer">
        SmartLib · A university library management system built for the final-year project demo.
      </footer>
    </div>
  );
}
