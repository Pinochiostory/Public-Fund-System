import ActionFeedback from "../../components/ActionFeedback";
import UnsavedChanges from "../../components/UnsavedChanges";
import useConfirmation from "../../components/useConfirmation";
import { useRef, useState } from "react";
import PageHeader from "../../components/PageHeader";
import { Link, useParams } from "react-router-dom";
import api from "../../services/api";
import AppShell from "../../components/AppShell";
import Icon from "../../components/Icon";
import LoadingSkeleton from "../../components/LoadingSkeleton";
import useVisibleLoading from "../../components/useVisibleLoading";
import useProviderPortfolio from "../../components/useProviderPortfolio";
import ProjectDirectory from "../../components/ProjectDirectory";
import { sameId, projectName, formatStatus, formatDate, validateEvidenceFiles, errorMessage } from "../../components/projectData";

export default function ProviderEvidence() {
  const {projectId,phaseId} = useParams();
  const {confirm,dialog}=useConfirmation();
  const {projects,phases,records,loading,error,refresh} = useProviderPortfolio("evidence");
  const showLoading = useVisibleLoading(loading);
  const [selectedProject,setSelectedProject] = useState("");
  const [selectedPhase,setSelectedPhase] = useState("");
  const [files,setFiles] = useState([]);
  const [uploading,setUploading] = useState(false);
  const [progress,setProgress] = useState(0);
  const [uploadError,setUploadError] = useState("");
  const [confirmation,setConfirmation] = useState("");
  const [downloading,setDownloading] = useState(null);
  const inputRef = useRef(null);
  const uploadBusy = useRef(false);
  const project = projects.find(item=>sameId(item.id,projectId));
  const projectPhases = phases.filter(item=>sameId(item.projectId,projectId)).sort((a,b)=>a.phaseOrder-b.phaseOrder);
  const phase = projectPhases.find(item=>sameId(item.id,phaseId));
  const history = records.filter(item=>sameId(item.phaseId,phaseId)).sort((a,b)=>new Date(b.uploadedAt)-new Date(a.uploadedAt));
  const uploadProjectId = selectedProject || String(projects[0]?.id || "");
  const availablePhases = phases.filter(item=>sameId(item.projectId,uploadProjectId) && item.status==="IN_PROGRESS");
  const pending = id => records.some(item=>sameId(item.phaseId,id) && item.status==="PENDING");
  const uploadPhaseId = selectedPhase || String(availablePhases.find(item=>!pending(item.id))?.id || "");
  const uploadPhase = availablePhases.find(item=>sameId(item.id,uploadPhaseId));
  const clearFile = () => {setFiles([]);setUploadError("");setConfirmation("");if(inputRef.current)inputRef.current.value="";};
  const submit = async event => {
    event.preventDefault();
    if (uploadBusy.current) return;
    const validation = validateEvidenceFiles(files);
    if(validation){setUploadError(validation);return;}
    if(!uploadPhase || pending(uploadPhase.id)){setUploadError("Choose an in-progress phase without evidence awaiting review.");return;}
    uploadBusy.current = true;
    setUploading(true);setProgress(0);setUploadError("");setConfirmation("");
    const data = new FormData();files.forEach(file => data.append("files",file));
    try {
      await api.post("/evidence/phase/"+uploadPhase.id+"/batch",data,{
        onUploadProgress: event => {
          if(event.total) setProgress(Math.min(100,Math.round(event.loaded / event.total * 100)));
        },
      });
      setProgress(100);
      setConfirmation(files.length+" evidence "+(files.length === 1 ? "file has" : "files have")+" been uploaded for "+uploadPhase.phaseName+". Every file will be reviewed separately.");
      setFiles([]);if(inputRef.current)inputRef.current.value="";
      try { await refresh(); } catch { setUploadError("Upload succeeded, but the history could not refresh. Reload the page to view it; do not upload again."); }
    } catch(error) {setUploadError(errorMessage(error,"Upload could not be confirmed. Check evidence history before trying again."));}
    finally {uploadBusy.current=false;setUploading(false);}
  };
  const download = async item => {
    setDownloading(item.id);setUploadError("");
    try {
      const response = await api.get("/evidence/"+item.id+"/file",{responseType:"blob"});
      const url=URL.createObjectURL(response.data);
      const link=document.createElement("a");link.href=url;link.download=item.fileName || "evidence";
      document.body.appendChild(link);link.click();link.remove();
      setTimeout(()=>URL.revokeObjectURL(url),10000);
    }catch{setUploadError("Unable to download this evidence file. Please try again.");}
    finally{setDownloading(null);}
  };
  const withdraw=async item=>{
    const decision=await confirm({title:"Replace evidence",message:"This file will remain in history as withdrawn. Once the remaining files have been reviewed, submit a complete corrected set for this phase.",label:"Reason for replacement",action:"Withdraw for replacement"});
    if(!decision)return;setDownloading(item.id);setUploadError("");
    try{await api.post("/evidence/"+item.id+"/withdraw",new URLSearchParams({reason:decision.value}));setConfirmation("Evidence withdrawn. Once all remaining files have been reviewed, return to Upload evidence and submit a complete corrected set.");await refresh();}
    catch(e){setUploadError(errorMessage(e,"Evidence could not be withdrawn."));}finally{setDownloading(null);}
  };
  const title = !projectId ? "Evidence" : phaseId ? phase?.phaseName || "Phase evidence" : projectName(project);
  return <AppShell>{dialog}<UnsavedChanges dirty={files.length > 0 && !uploading}/><main className="project-page">
    <PageHeader title={title} description={!projectId ? "Upload supporting files and explore evidence history by project." : phaseId ? projectName(project)+" · Evidence history" : "Choose a phase to view its evidence history."}>
      {projectId && <Link className="project-action secondary" to={phaseId ? "/provider/evidence/"+projectId : "/provider/evidence"}>{phaseId ? "All project phases" : "All projects"}</Link>}
    </PageHeader>
    {(error || uploadError) && <div className="project-error" role="alert">{error || uploadError}</div>}
    <ActionFeedback message={confirmation} title={confirmation.startsWith("Evidence withdrawn") ? "Evidence withdrawn" : "Evidence uploaded"}/>
    {showLoading ? <LoadingSkeleton/> : !error && (!projectId ? <>
      <section className="upload-section"><div className="section-heading"><h2>Upload evidence</h2><p>Select up to 10 files together. Each must be less than 25 MB, with less than 100 MB in total. JPEG, PNG, PDF and DOCX are supported.</p></div>
        <form className="evidence-upload-square" onSubmit={submit} aria-busy={uploading}>
          <span className="upload-emblem"><Icon name="upload" size={30}/></span>
          <label htmlFor="upload-project">Project</label>
          <select id="upload-project" value={uploadProjectId} disabled={uploading || !projects.length} onChange={event=>{setSelectedProject(event.target.value);setSelectedPhase("");clearFile();}}>
            {!projects.length && <option value="">No projects available</option>}{projects.map(item=><option value={item.id} key={item.id}>{projectName(item)}</option>)}
          </select>
          <label htmlFor="upload-phase">Phase</label>
          <select id="upload-phase" value={uploadPhaseId} disabled={uploading || !availablePhases.length} onChange={event=>{setSelectedPhase(event.target.value);clearFile();}}>
            <option value="">Choose an active phase</option>{availablePhases.map(item=><option key={item.id} value={item.id} disabled={pending(item.id)}>{item.phaseName}{pending(item.id) ? " - awaiting review" : ""}</option>)}
          </select>
          <label className={"evidence-file-picker"+(!uploadPhase || uploading ? " is-disabled" : "")} htmlFor="evidence-file"><Icon name="document" size={22}/><span>{files.length ? files.length+(files.length === 1 ? " file selected. Add more" : " files selected. Add more") : "Choose evidence files"}</span>
            <input ref={inputRef} id="evidence-file" type="file" multiple accept=".jpg,.jpeg,.png,.pdf,.docx" disabled={!uploadPhase || uploading || pending(uploadPhase.id)} onChange={event=>{
              const next=[...files,...Array.from(event.target.files || [])];
              setFiles(next);setUploadError(validateEvidenceFiles(next));setConfirmation("");event.target.value="";
            }}/>
          </label>
          {files.length > 0 && <ul className="evidence-selected-files" aria-label="Selected evidence files">{files.map((file,index)=><li key={index}>
            <div><strong>{file.name}</strong><small>{file.type} · {(file.size/1024).toFixed(1)} KB · Not submitted</small></div>
            <button type="button" className="project-action secondary" disabled={uploading} aria-label={"Remove "+file.name} onClick={()=>{
              const next=files.filter((_,i)=>i!==index);setFiles(next);setUploadError(next.length ? validateEvidenceFiles(next) : "");
            }}><Icon name="close" size={18}/></button>
          </li>)}</ul>}
          <small>{availablePhases.length ? "All files must be reviewed before the phase can advance. If any are rejected or withdrawn, submit a complete corrected set after the review finishes." : "Start a funded phase before uploading evidence."}</small>
          {uploading && <div className="upload-progress" role="status"><progress max="100" value={progress} aria-label="Evidence upload progress"/><span>{progress<100 ? "Uploading… "+progress+"%" : "Upload transferred. Saving and validating…"}</span></div>}
          <button className="project-action" type="submit" disabled={uploading || !uploadPhase || !files.length || !!validateEvidenceFiles(files) || pending(uploadPhase.id)}>{uploading ? "Uploading…" : "Upload evidence"}<Icon name="upload" size={18}/></button>
        </form>
      </section>
      <section><div className="section-heading"><h2>Evidence history</h2><p>Select a project to review evidence for each phase.</p></div><ProjectDirectory projects={projects} basePath="/provider/evidence" action="View evidence history" summary={item=>phases.filter(phase=>sameId(phase.projectId,item.id)).length+" phases"}/></section>
    </> : !project || (phaseId && !phase) ? <div className="project-empty">This project or phase is unavailable for your account.</div> : !phaseId ?
      <section><div className="project-directory-grid">{projectPhases.map(item=><article className="project-directory-card" key={item.id}>
        <div className="project-directory-heading"><span className="project-folder"><Icon name="folder"/></span><span className="status-pill">{formatStatus(item.status)}</span></div>
        <p>Phase {item.phaseOrder}</p><h2>{item.phaseName}</h2><p>{item.description}</p>
        <div className="project-directory-summary">{records.filter(record=>sameId(record.phaseId,item.id)).length} submissions</div>
        <Link className="project-action" to={"/provider/evidence/"+projectId+"/phases/"+item.id}>View evidence<Icon name="arrowRight" size={18}/></Link>
      </article>)}</div>{!projectPhases.length && <div className="project-empty">This project has no phases yet.</div>}</section> :
      <section className="project-directory-grid">{!history.length && <div className="project-empty">No evidence has been submitted for this phase yet.</div>}
        {history.map(item=><article className="evidence-history-card" key={item.id}><div className="project-directory-heading"><Icon name="document" size={25}/><span className="status-pill">{formatStatus(item.status)}</span></div>
          <h2>{item.fileName || "Evidence file"}</h2><p>{item.fileType} · {item.fileSize==null?"Size not recorded":(item.fileSize/1024).toFixed(1)+" KB"}</p><dl><div><dt>Uploaded</dt><dd>{formatDate(item.uploadedAt)}</dd></div><div><dt>Verified</dt><dd>{formatDate(item.verifiedAt)}</dd></div></dl>
          {item.verificationComment && <div className="evidence-feedback"><strong>{item.status==="REJECTED" ? "Changes requested" : "Review comment"}</strong><p>{item.verificationComment}</p></div>}
          {item.status==="PENDING"&&<button className="project-action secondary" disabled={downloading!==null} onClick={()=>withdraw(item)}>Replace incorrect evidence</button>}
          <button className="project-action" onClick={()=>download(item)} disabled={downloading!==null}>{downloading===item.id ? "Downloading…" : "Download file"}<Icon name="document" size={18}/></button>
        </article>)}
      </section>)}
  </main></AppShell>;
}
