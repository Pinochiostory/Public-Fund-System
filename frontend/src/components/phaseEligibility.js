export function phaseStartBlockedReason(phase, project, phases) {
  if (!project || project.status !== "ACTIVE") return "This project is not active. No phase can begin.";
  if (phase.status !== "FUNDED") return "Phase funding must be released before work can begin.";
  const others = phases.filter(p => String(p.projectId) === String(phase.projectId) && String(p.id) !== String(phase.id));
  const underway = others.find(p => ["IN_PROGRESS", "EVIDENCE_APPROVED"].includes(p.status));
  const previous = others.filter(p => Number(p.phaseOrder) < Number(phase.phaseOrder) &&
    !["COMPLETED", "REJECTED"].includes(p.status)).sort((a,b) => a.phaseOrder - b.phaseOrder)[0];
  const blocker = underway || previous;
  return blocker ? `${blocker.phaseName || blocker.name || "Phase " + blocker.phaseOrder} must be formally completed by an Administrator before this phase can begin.` : "";
}
