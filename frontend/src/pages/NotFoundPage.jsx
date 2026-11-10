import React from 'react';
import { Link } from 'react-router-dom';
import { BookOpen } from '../components/icons';

export default function NotFoundPage() {
  return (
    <div className="not-found">
      <div>
        <div className="not-found__code">404</div>
        <h1>This page is off the shelf</h1>
        <p>The link may be old or mistyped. The catalogue and your dashboard are still right where you left them.</p>
        <div className="row" style={{ justifyContent: 'center' }}>
          <Link to="/" className="btn btn--primary"><BookOpen size={15} /> SmartLib home</Link>
          <Link to="/dashboard" className="btn btn--secondary">My dashboard</Link>
        </div>
      </div>
    </div>
  );
}
