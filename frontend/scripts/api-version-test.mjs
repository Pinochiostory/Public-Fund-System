import assert from "node:assert/strict";
import {createServer} from "vite";
const server=await createServer({server:{middlewareMode:true},appType:"custom"});
const originalStorage=globalThis.localStorage;
globalThis.localStorage={getItem:()=>null};
try{
  const {default:api}=await server.ssrLoadModule("/src/services/api.js");
  const calls=[];
  api.defaults.adapter=async config=>{
    calls.push({url:config.url,version:config.headers.get("If-Match")});
    const data=config.method==="get"?[{id:1,version:7},{id:2,version:9}]:
      config.url.includes("/reconciliation/")?{id:2,version:42}:{};
    return {data,status:200,statusText:"OK",headers:{},config};
  };
  await api.get("/projects/my");
  await api.post("/projects/1/reconciliation/returns",{},{headers:{"If-Match":"3"}});
  await api.post("/projects/2/complete",{});
  assert.equal(calls.at(-1).version,"9","Return IDs must not overwrite project versions");
  await api.post("/projects/1/reconciliation/2/review",{},{headers:{"If-Match":"42"}});
  assert.equal(calls.at(-1).version,"42","Review must use the submission version");
  await api.post("/projects/1/complete",{});
  assert.equal(calls.at(-1).version,"7");
  console.log("PASS reconciliation and project version caches remain separate.");
}finally{globalThis.localStorage=originalStorage;await server.close();}

