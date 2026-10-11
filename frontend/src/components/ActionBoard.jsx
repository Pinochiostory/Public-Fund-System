import { Link } from "react-router-dom";
import Icon from "./Icon";

export default function ActionBoard({title="Your next steps",items}) {
  return <section className="action-board" aria-label={title}>
    <div className="action-board-heading"><span className="section-icon"><Icon name="clock" size={21}/></span><h2>{title}</h2></div>
    <div className="action-board-list">{items.map(item => <Link className="action-board-item" key={item.title} to={item.to}>
      <div><h3>{item.title}</h3><p>{item.description}</p></div>
      {item.count !== undefined && <span className="action-count">{item.count}</span>}
      <Icon name="arrowRight" size={19}/>
    </Link>)}</div>
  </section>;
}
