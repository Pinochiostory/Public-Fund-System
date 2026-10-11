import { useActionFeedback } from "./ActionFeedback";
import { useState } from "react";
import api from "../services/api";
import { useAuth } from "../context/AuthContext";
import { errorMessage, formatDate } from "./projectData";
import useConfirmation from "./useConfirmation";

export default function PaymentProof({payment,onChanged}) {
  const {user}=useAuth();
  const admin=user?.role==="ADMINISTRATOR";
  const [busy,setBusy]=useState(false);
  const [progress,setProgress]=useState(0);
  const notify=useActionFeedback();
  const [error,setError]=useState("");
  const {confirm,dialog}=useConfirmation();
  const upload=async event=>{
    const file=event.target.files?.[0];event.target.value="";if(!file||busy)return;
    setError("");
    if(file.size>=25*1024*1024){setError("The uploaded content must be less than 25 MB.");return;}
    if(!/\.(pdf|png|jpe?g)$/i.test(file.name)){setError("Only PDF, JPG and PNG files are accepted.");return;}
    if(!await confirm({title:"Attach proof of payment",message:"Attach "+file.name+" ("+(file.size/1024).toFixed(1)+" KB) to payment "+(payment.paymentReference||payment.id)+"? The file will be retained in the payment history."}))return;
    setBusy(true);setProgress(0);
    try {
      const data=new FormData();data.append("file",file);
      await api.post("/disbursements/"+payment.id+"/proof",data,{onUploadProgress:e=>{if(e.total)setProgress(Math.round(e.loaded/e.total*100));}});
      notify("Proof of payment uploaded successfully.", "Proof uploaded");
      try{await onChanged?.();}catch{setError("Proof was saved, but the page could not refresh. Reload it; do not upload again.");}
    }catch(e){setError("Payment remains recorded, but proof was not uploaded. "+errorMessage(e,"Upload failed. Please try again."));}
    finally{setBusy(false);}
  };
  const download=async()=>{
    setBusy(true);setError("");
    try {
      const response=await api.get("/disbursements/"+payment.id+"/proof",{responseType:"blob"});
      const url=URL.createObjectURL(response.data);const a=document.createElement("a");
      a.href=url;a.download=payment.proofName;a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);
    }catch{setError("Proof could not be downloaded. Please try again or contact the system owner.");}finally{setBusy(false);}
  };
  return <div className="payment-proof">{dialog}
    {payment.proofName?<><p>{payment.proofName} · {payment.proofType} · {payment.proofSize==null?"Size not recorded":(payment.proofSize/1024).toFixed(1)+" KB"}</p>
      <small>Uploaded {formatDate(payment.proofUploadedAt)}</small><button className="project-action secondary" disabled={busy} onClick={download}>{busy?"Please wait…":"Download proof"}</button></>:
      <><small>No proof of payment uploaded yet.</small>{admin&&payment.status==="RELEASED"&&<label>Upload proof (PDF, PNG or JPG, less than 25 MB)<input type="file" accept=".pdf,.png,.jpg,.jpeg" disabled={busy} onChange={upload}/></label>}</>}
    {busy&&progress>0&&<div role="status"><progress max="100" value={progress}/>{progress<100?"Uploading… "+progress+"%":"Validating and saving…"}</div>}
    {error&&<p className="project-error" role="alert">{error}</p>}
  </div>;
}
