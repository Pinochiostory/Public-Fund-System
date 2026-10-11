export const sameId = (a, b) => a != null && b != null && String(a) === String(b);
export const projectName = project => project?.title?.trim() || "Untitled project";
export const formatMoney = amount => new Intl.NumberFormat("en-ZA", {style:"currency",currency:"ZAR"}).format(Number(amount || 0));
export const formatStatus = status => (status || "Not available").replaceAll("_", " ").toLowerCase();
export const formatDate = value => value ? new Date(value).toLocaleString("en-ZA") : "Not yet";
export function projectPayments(projectId, phases, payments) {
  const ids = new Set(phases.filter(phase => sameId(phase.projectId, projectId)).map(phase => String(phase.id)));
  return payments.filter(payment => ids.has(String(payment.phaseId)));
}
export const MAX_EVIDENCE_BYTES = 25 * 1024 * 1024;
export function validateEvidenceFile(file) {
  if (!file) return "Choose an evidence file first.";
  if (!file.size) return "The selected file is empty.";
  if (file.size >= MAX_EVIDENCE_BYTES) return "The uploaded content must be less than 25 MB.";
  if (!["image/jpeg","image/png","application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document"].includes(file.type)) return "Choose a JPEG, PNG, PDF or DOCX file.";
  return "";
}
export function validateEvidenceFiles(files) {
  if (!files?.length) return "Choose at least one evidence file.";
  if (files.length > 10) return "Select no more than 10 files per submission.";
  for (const file of files) {
    const problem = validateEvidenceFile(file);
    if (problem) return file.name + ": " + problem;
  }
  if (files.reduce((sum, file) => sum + file.size, 0) >= 100 * 1024 * 1024)
    return "The complete submission must be less than 100 MB.";
  const keys = files.map(file => file.name + ":" + file.size + ":" + file.lastModified);
  if (new Set(keys).size !== keys.length) return "The same file was selected twice. Remove the duplicate.";
  return "";
}
export function errorMessage(error, fallback) {
  const data = error.response?.data;
  return typeof data === "string" ? data : data?.message || fallback;
}
