import React from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';

/** Global render-error boundary: a crash in one page never blanks the app. */
export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    // Kept in the console so the demo can show a trace without exposing it in the UI.
    console.error('SmartLib UI error:', error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="not-found">
          <div>
            <AlertTriangle size={34} style={{ margin: '0 auto 12px' }} aria-hidden="true" />
            <h1>Something went wrong</h1>
            <p>The page hit an unexpected error. Your data is safe; reload to continue.</p>
            <button type="button" className="btn btn--primary" onClick={() => window.location.reload()}>
              <RotateCcw size={16} /> Reload SmartLib
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
