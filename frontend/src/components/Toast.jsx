import React, { useState, useEffect, useRef } from 'react';

export default function Toast({ message, type = 'success', onClose }) {
  useEffect(() => {
    if (!message) return;
    const t = setTimeout(onClose, 4000);
    return () => clearTimeout(t);
  }, [message, onClose]);

  if (!message) return null;

  return (
    <div className={`toast toast--${type}`}>
      <span>{message}</span>
      <button className="toast__close" onClick={onClose}>×</button>
    </div>
  );
}

