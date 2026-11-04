import React from 'react';
import { Link } from 'react-router-dom';

export default function NotAllowed({ role }) {
  return (
    <div className="not-allowed">
      <h1>Not authorised</h1>
      <p>
        This page is for {role === 'LIBRARIAN' ? 'librarians' : 'students'}.
        Use the navigation above to reach the pages for your role.
      </p>
      <Link to="/">Go home</Link>
    </div>
  );
}
