function LoadingSkeleton({ cards = 4 }) {
  return (
    <div
      className="skeleton-screen"
      role="status"
      aria-label="Loading content"
      aria-busy="true"
    >
      <span className="sr-only">Loading content</span>

      <div className="skeleton-metrics">
        {Array.from({ length: cards }).map((_, index) => (
          <div className="skeleton-metric" key={index}>
            <span />
            <strong />
            <small />
          </div>
        ))}
      </div>

      <div className="skeleton-panel">
        <div className="skeleton-panel-heading">
          <span />
          <small />
        </div>

        {Array.from({ length: 4 }).map((_, index) => (
          <div className="skeleton-row" key={index}>
            <span />
            <span />
            <span />
          </div>
        ))}
      </div>
    </div>
  );
}

export default LoadingSkeleton;
