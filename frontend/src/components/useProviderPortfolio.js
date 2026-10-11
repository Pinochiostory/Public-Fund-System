import { useCallback, useEffect, useState } from "react";
import api from "../services/api";
import { errorMessage } from "./projectData";
export default function useProviderPortfolio(kind) {
  const [data, setData] = useState({projects:[],phases:[],records:[]});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const refresh = useCallback(async () => {
    const responses = await Promise.all([api.get("/projects/my"), api.get("/project-phases/my"), api.get(kind === "payments" ? "/disbursements/my" : "/evidence/my")]);
    setData({projects:responses[0].data || [],phases:responses[1].data || [],records:responses[2].data || []});
  }, [kind]);
  useEffect(() => {
    let active = true;
    Promise.all([api.get("/projects/my"), api.get("/project-phases/my"), api.get(kind === "payments" ? "/disbursements/my" : "/evidence/my")])
      .then(responses => {if(active)setData({projects:responses[0].data || [],phases:responses[1].data || [],records:responses[2].data || []});})
      .catch(error => {if(active)setError(errorMessage(error,"Unable to load your projects. Please refresh and try again."));})
      .finally(() => {if(active)setLoading(false);});
    return () => {active=false;};
  }, [kind]);
  return {...data,loading,error,refresh};
}
