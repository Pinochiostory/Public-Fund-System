export default function PageHeader({ title, description, children }) {
  return <header className="page-heading">
    <div><h1>{title}</h1>{description && <p>{description}</p>}</div>
    {children && <div className="page-heading-actions">{children}</div>}
  </header>;
}
