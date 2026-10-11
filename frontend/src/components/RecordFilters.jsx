import Icon from "./Icon";
export default function RecordFilters({ filter, label = "records" }) {
  const {query, setQuery, status, setStatus, statuses, filtered, total} = filter;
  return <section className="record-tools" aria-label={"Filter " + label}>
    <div className="record-controls"><label className="record-search"><Icon name="search" size={18}/><input aria-label={"Search " + label} placeholder={"Search " + label + "…"} value={query} onChange={e => setQuery(e.target.value)} /></label>
    {statuses.length > 0 && <select aria-label="Filter by status" value={status} onChange={e => setStatus(e.target.value)}><option value="">All statuses</option>{statuses.map(s => <option key={s} value={s}>{s.replaceAll("_"," ").toLowerCase()}</option>)}</select>}
    {(query || status) && <button onClick={() => {setQuery("");setStatus("");}}>Clear filters</button>}</div>
    <p role="status">{filtered.length} of {total} {label}{total > 0 && filtered.length === 0 ? " · No matches. Try a different search or clear your filters." : ""}</p>
  </section>;
}
