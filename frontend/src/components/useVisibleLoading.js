import { useEffect, useRef, useState } from "react";

// Keep a real loading skeleton visible briefly to avoid a distracting flash.
// Network requests are never delayed.
export default function useVisibleLoading(loading, minimum = 420) {
  const startedAt = useRef(0);
  const [retained, setRetained] = useState(loading);
  useEffect(() => {
    if (loading) {
      startedAt.current = performance.now();
      // This state deliberately retains the network loading UI through a short
      // visual transition, rather than deriving application data in an effect.
      // eslint-disable-next-line react/set-state-in-effect
      setRetained(true);
      return;
    }
    const remaining = Math.max(0, minimum - (performance.now() - startedAt.current));
    const timer = setTimeout(() => setRetained(false), remaining);
    return () => clearTimeout(timer);
  }, [loading, minimum]);
  return loading || retained;
}
