import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import ModalFrame from "./ModalFrame";
import Icon from "./Icon";

const FeedbackContext = createContext(() => {});
export const useActionFeedback = () => useContext(FeedbackContext);

// Transient action receipts only, not a second persistent notification system.
// Lives above routes so refreshes/navigation cannot swallow an upload confirmation.
export function ActionFeedbackProvider({ children }) {
  const [queue, setQueue] = useState([]);
  const nextId = useRef(0);
  const notify = useCallback((message, title = "Action completed") => {
    if (!message) return;
    const theme = document.querySelector(".workspace,.public-surface")?.dataset.theme || "light";
    const id = ++nextId.current;
    setQueue(items => [...items, { id, message, title, theme }]);
  }, []);
  const current = queue[0];
  return <FeedbackContext.Provider value={notify}>
    {children}
    {current && <div data-theme={current.theme} className="action-feedback-surface">
      <ModalFrame key={current.id} className="action-success-popup" onClose={() => setQueue(items => items.slice(1))}>
        <span className="success-popup-icon"><Icon name="check" size={30}/></span>
        <h2>{current.title}</h2>
        <p role="status">{current.message}</p>
        <button autoFocus type="button" className="project-action" onClick={() => setQueue(items => items.slice(1))}>Done</button>
      </ModalFrame>
    </div>}
  </FeedbackContext.Provider>;
}

// Adapts existing page-level success state without changing its workflow.
export default function ActionFeedback({ message, title }) {
  const notify = useActionFeedback();
  const previous = useRef("");
  useEffect(() => {
    if (message && message !== previous.current) notify(message, title);
    previous.current = message;
  }, [message, title, notify]);
  return null;
}
