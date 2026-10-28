import { Component, ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RefreshCw, Eye } from 'lucide-react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onFallbackTo2D?: () => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class CanvasErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('CanvasErrorBoundary caught WebGL/3D render error:', error, errorInfo);
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  public render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div style={{
          background: 'rgba(15, 23, 42, 0.95)',
          border: '1px solid var(--accent-danger)',
          borderRadius: '8px',
          padding: '32px 24px',
          textAlign: 'center',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: '14px',
          minHeight: '400px',
          justifyContent: 'center'
        }}>
          <AlertTriangle size={42} style={{ color: 'var(--accent-danger)' }} />
          <div>
            <h4 style={{ margin: '0 0 6px 0', fontSize: '1.1rem', color: '#f87171' }}>
              3D Viewport Hardware / WebGL Rendering Failure
            </h4>
            <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--text-muted)', maxWidth: '560px' }}>
              The 3D WebGL visualizer encountered a rendering error ({this.state.error?.message || 'WebGL context failure'}).
              All spatial data, containment checks, and zero-trust validation results remain intact and authoritative.
            </p>
          </div>

          <div style={{ display: 'flex', gap: '12px', marginTop: '12px' }}>
            {this.props.onFallbackTo2D && (
              <button
                onClick={this.props.onFallbackTo2D}
                style={{
                  padding: '8px 16px',
                  background: 'var(--accent-primary)',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '6px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}
              >
                <Eye size={15} /> Switch to 2D Floor-Plan &amp; Table
              </button>
            )}

            <button
              onClick={this.handleReset}
              style={{
                padding: '8px 16px',
                background: 'rgba(255, 255, 255, 0.05)',
                color: 'var(--text-main)',
                border: '1px solid var(--border-color)',
                borderRadius: '6px',
                fontSize: '0.85rem',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
            >
              <RefreshCw size={14} /> Retry 3D Engine
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
