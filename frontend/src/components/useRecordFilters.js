import { useState } from "react";

export function filterRecords(records, query = "", status = "") {
  const search = query.trim().toLowerCase();
  return records.filter(record =>
    (!status || record.status === status) &&
    (!search || Object.values(record).some(value =>
      value != null && typeof value !== "object" &&
      String(value).toLowerCase().includes(search)
    ))
  );
}

export function useRecordFilters(records) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const statuses = [...new Set(records.map(record => record.status).filter(Boolean))].sort();
  return {
    query, setQuery, status, setStatus, statuses,
    filtered: filterRecords(records, query, status),
    total: records.length,
  };
}
