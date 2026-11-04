import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  getNotifications, unreadCount, markNotificationRead, markAllNotificationsRead,
} from '../api';

function timeAgo(iso) {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

/** Bell with unread badge (polled every 30s) and a dropdown of recent notifications. */
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [count, setCount] = useState(0);
  const boxRef = useRef(null);

  const refresh = useCallback(() => {
    unreadCount().then((r) => setCount(r.count)).catch(() => {});
    getNotifications().then((list) => setItems(list.slice(0, 15) || [])).catch(() => {});
  }, []);

  useEffect(() => {
    refresh();
    const poll = setInterval(refresh, 30000);
    return () => clearInterval(poll);
  }, [refresh]);

  useEffect(() => {
    const close = (e) => {
      if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const markAll = async () => {
    try {
      await markAllNotificationsRead();
      refresh();
    } catch { /* ignore */ }
  };

  const markOne = async (id) => {
    try {
      await markNotificationRead(id);
      refresh();
    } catch { /* ignore */ }
  };

  return (
    <div className="bell" ref={boxRef}>
      <button
        className="bell__button"
        aria-label={`Notifications: ${count} unread`}
        onClick={() => { setOpen(!open); if (!open) refresh(); }}
      >
        🔔
        {count > 0 && <span className="bell__badge">{count}</span>}
      </button>
      {open && (
        <div className="bell__dropdown">
          <div className="bell__head">
            <strong>Notifications</strong>
            <button className="link-button" onClick={markAll}>Mark all read</button>
          </div>
          {items.length === 0 && <div className="bell__empty">Nothing here yet.</div>}
          <ul className="bell__list">
            {items.map((n) => (
              <li
                key={n.id}
                className={`bell__item ${n.read ? '' : 'bell__item--unread'}`}
                onClick={() => !n.read && markOne(n.id)}
                title={n.read ? '' : 'Click to mark read'}
              >
                <div className="bell__title">{n.title}</div>
                <div className="bell__message">{n.message}</div>
                <div className="bell__time">{timeAgo(n.createdAt)}</div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
