import { useRef, useState, useEffect } from "react";
import UnsavedChanges, { confirmDiscard } from "./UnsavedChanges";
import ModalFrame from "./ModalFrame";

export default function useConfirmation() {
  const [request,setRequest]=useState(null);
  const [value,setValue]=useState("");
  const resolver=useRef(null);
  useEffect(()=>()=>{resolver.current?.(null);},[]);
  const confirm=options=>new Promise(resolve=>{
    resolver.current?.(null); resolver.current=resolve; setValue(""); setRequest(options);
  });
  const close=result=>{if(result===null&&!confirmDiscard(Boolean(value)))return;resolver.current?.(result);resolver.current=null;setRequest(null);};
  const dialog=request && <ModalFrame className="provider-project-modal-backdrop" onClose={()=>close(null)}>
    <div className="provider-project-modal"><UnsavedChanges dirty={Boolean(value)}/>
      <h2>{request.title}</h2><p>{request.message}</p>
      <form onSubmit={e=>{e.preventDefault();if(request.label&&!value.trim())return;close({value:value.trim()});}}>
        {request.label&&<label>{request.label}<textarea autoFocus value={value} onChange={e=>setValue(e.target.value)} required maxLength={request.maxLength||4000} rows={request.rows||3}/></label>}
        <div className="provider-project-modal-actions"><button className="project-action secondary" type="button" onClick={()=>close(null)}>Cancel</button>
          <button className="project-action" type="submit">{request.action||"Confirm"}</button></div>
      </form>
    </div>
  </ModalFrame>;
  return {confirm,dialog};
}
