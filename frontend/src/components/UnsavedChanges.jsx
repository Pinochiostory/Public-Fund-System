import { useContext, useEffect } from "react";
import { UNSAFE_DataRouterContext, useBlocker } from "react-router-dom";
import ModalFrame from "./ModalFrame";

export const discardMessage="You have unsaved changes. Leaving this page will discard them.";
export const confirmDiscard=dirty=>!dirty||window.confirm(discardMessage);

function NavigationGuard({dirty}) {
  const blocker=useBlocker(({currentLocation,nextLocation})=>dirty&&currentLocation.pathname!==nextLocation.pathname);
  return blocker.state==="blocked"?<ModalFrame className="provider-project-modal-backdrop" onClose={()=>blocker.reset()}>
    <div className="provider-project-modal"><h2>Unsaved changes</h2><p>{discardMessage}</p>
      <div className="provider-project-modal-actions"><button className="project-action secondary" onClick={()=>blocker.reset()}>Keep editing</button>
      <button className="project-action" onClick={()=>blocker.proceed()}>Leave page</button></div></div>
  </ModalFrame>:null;
}
export default function UnsavedChanges({dirty}) {
  const router=useContext(UNSAFE_DataRouterContext);
  useEffect(()=>{
    if(!dirty)return;
    const warn=event=>{event.preventDefault();event.returnValue=discardMessage;};
    window.addEventListener("beforeunload",warn);
    return()=>window.removeEventListener("beforeunload",warn);
  },[dirty]);
  return router?<NavigationGuard dirty={dirty}/>:null;
}
