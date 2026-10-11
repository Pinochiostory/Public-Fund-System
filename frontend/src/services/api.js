import axios from "axios";

const recordVersions = new Map();

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || `http://${typeof window !== "undefined" && window.location?.hostname === "127.0.0.1" ? "127.0.0.1" : "localhost"}:8080/api`,
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    const match=config.url?.match(/^\/(applications|projects|project-phases|evidence|disbursements)\/(\d+)\/(approve|reject|start|conclude|complete|request-completion|release|processing|proof|withdraw|closeout-review)$/);
    if (match && config.method === "post") {
      const version=recordVersions.get(match[1]+"/"+match[2]);
      if (version!==undefined) config.headers["If-Match"]=String(version);
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export default api;

api.interceptors.response.use(response => {
  const prefix=response.config.url?.match(/^\/(applications|projects|project-phases|evidence|disbursements)(?:\/|$)/)?.[1];
  // Nested reconciliation responses contain submission IDs, not project IDs.
  const reconciliationResponse = /^\/projects\/\d+\/reconciliation(?:\/|$)/.test(response.config.url || "");
  if(prefix && !reconciliationResponse) {
    const records=Array.isArray(response.data)?response.data:[response.data];
    records.forEach(row=>{if(row?.id!=null && row?.version!=null)recordVersions.set(prefix+"/"+row.id,row.version);});
  }
  if (response.config.method === "post" && response.config.url?.startsWith("/notifications/")) {
    window.dispatchEvent(new Event("publicfund:notifications-changed"));
  }
  return response;
}, error => {
  if (error.response?.status === 401) window.dispatchEvent(new Event("publicfund:session-expired"));
  if (error.response?.status === 403 && error.response?.data?.code === "PASSWORD_CHANGE_REQUIRED") {
    window.dispatchEvent(new Event("publicfund:password-change-required"));
  }
  return Promise.reject(error);
});
