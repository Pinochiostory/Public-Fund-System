import assert from "node:assert/strict";
import { projectPayments, sameId, projectName, validateEvidenceFile, MAX_EVIDENCE_BYTES } from "../src/components/projectData.js";

const phases = [{id:11,projectId:1},{id:12,projectId:1},{id:21,projectId:2}];
const payments = [{id:1,phaseId:11},{id:2,phaseId:12},{id:3,phaseId:21},{id:4,phaseId:99}];
assert.deepEqual(projectPayments("1",phases,payments).map(item=>item.id),[1,2]);
assert.deepEqual(projectPayments(2,phases,payments).map(item=>item.id),[3]);
assert.deepEqual(projectPayments(3,phases,payments),[]);
assert.equal(projectName({title:"Community Clinic"}),"Community Clinic");
assert.ok(sameId("12",12));
assert.ok(!sameId(undefined,undefined));
assert.equal(validateEvidenceFile({size:MAX_EVIDENCE_BYTES-1,type:"application/pdf"}),"");
assert.match(validateEvidenceFile({size:MAX_EVIDENCE_BYTES,type:"application/pdf"}),/less than 25 MB/);
assert.match(validateEvidenceFile({size:MAX_EVIDENCE_BYTES+1,type:"application/pdf"}),/less than 25 MB/);
assert.match(validateEvidenceFile({size:0,type:"application/pdf"}),/empty/);
assert.match(validateEvidenceFile({size:20,type:"application/x-msdownload"}),/JPEG/);
assert.match(validateEvidenceFile(null),/Choose/);
assert.equal(payments.length,4);
console.log("Project payment isolation, project titles, and upload validation checks passed.");
