import { useEffect, useRef } from "react";
import { installScrollReveal } from "./scrollReveal";

export default function useScrollReveal(pageKey) {
  const root = useRef(null);
  useEffect(() => installScrollReveal(root.current), [pageKey]);
  return root;
}
