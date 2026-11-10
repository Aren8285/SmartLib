import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Bell } from './icons';
import {
  getNotifications, unreadCount, markNotificationRead, markAllNotificationsRead,
} from '../api';
import { relativeTime } from '../utils/format';

/** Bell with unread badge (polled every 30s) and a dropdown of recent notifications. */
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [count, setCount] = useState(0);
  const boxRef = useRef(null);

  const refresh = useCallback(() => {
    unreadCount().then((r) => setCount(r.count)).catch(() => {});
    getNotifications().then((list) => setItems((list || []).slice(0, 15))).catch(() => {});
  }, []);

  useEffect(() => {
    refresh();
    const poll = setInterval(refresh, 30000);
    return () => clearInterval(poll);
  }, [refresh]);

  useEffect(() => {
    const close = (event) => {
      if (boxRef.current && !boxRef.current.contains(event.target)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const markAll = async () => {
    try {
      await markAllNotificationsRead();
      refresh();
    } catch { /* the bell is non-critical */ }
  };

  const markOne = async (id) => {
    try {
      await markNotificationRead(id);
      refresh();
    } catch { /* ignore */ }
  };

  return (
    <div className="notif" ref={boxRef}>
      <button
        type="button"
        className="icon-btn icon-wrap"
        aria-label={`Notifications${count > 0 ? `: ${count} unread` : ''}`}
        aria-expanded={open}
        onClick={() => { setOpen((o) => !o); if (!open) refresh(); }}
      >
        <Bell size={18} />
        {count > 0 && <span className="notif__badge" aria-hidden="true">{count > 9 ? '9+' : count}</span>}
      </button>
      {open && (
        <div className="notif__dropdown">
          <div className="notif__head">
            <strong>Notifications</strong>
            <button type="button" className="btn btn--ghost btn--sm" onClick={markAll}>Mark all read</button>
          </div>
          {items.length === 0 && <div className="notif__empty">Nothing here yet.</div>}
          <ul className="notif__list">
            {items.map((n) => (
              <li
                key={n.id}
                className={`notif__item ${n.read ? '' : 'notif__item--unread'}`}
                onClick={() => !n.read && markOne(n.id)}
                onKeyDown={(e) => { if (!n.read && e.key === 'Enter') markOne(n.id); }}
                role={n.read ? undefined : 'button'}
                tabIndex={n.read ? undefined : 0}
                title={n.read ? '' : 'Click to mark read'}
              >
                <div className="notif__title">{n.title}</div>
                <div className="notif__message">{n.message}</div>
                <div className="notif__time">{relativeTime(n.createdAt)}</div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
