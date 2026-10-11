import { useEffect, useId, useRef } from "react";

// Native dialogs contain focus, make the background inert, and restore focus.
export default function ModalFrame({ children, className, onClose, busy = false, error }) {
  const ref = useRef(null);
  const headingId = useId();
  useEffect(() => {
    const dialog = ref.current;
    const heading = dialog.querySelector("h2");
    if (heading) heading.id = headingId;
    dialog.showModal();
    return () => dialog.close();
  }, [headingId]);
  return <dialog ref={ref} className={"modal-frame " + className} aria-labelledby={headingId} onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    {error && <div className="modal-error" role="alert">{error}</div>}
    {children}
  </dialog>;
}
