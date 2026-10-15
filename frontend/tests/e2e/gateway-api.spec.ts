import { expect, test, type APIRequestContext } from "@playwright/test";
// Gateway deployment tests run only against an already-started Compose/Kubernetes environment.
test.skip(!process.env.PLAYWRIGHT_SKIP_WEBSERVER, "Requires deployed gateway and synthetic bootstrap data");
// APIRequestContext uses Node DNS; local browser .localhost resolution is independent.
// A test-only physical origin supports CA-isolated HTTPS without changing the OS hosts file.
test.use({
  baseURL: process.env.PLAYWRIGHT_REQUEST_BASE_URL ?? process.env.PLAYWRIGHT_BASE_URL,
  extraHTTPHeaders: { Host: "clinicflow.localhost" },
});
const apiHost = { Host: "api.clinicflow.localhost" };
async function login(request: APIRequestContext, email: string) {
  const response = await request.post("/api/v1/auth/login", { headers:apiHost, data:{email,password:"ClinicFlowDemo!2026"} });
  expect(response.status()).toBe(200);
  return (await response.json()).accessToken as string;
}
test("direct bearer APIs enforce backend roles and clinical/notification ownership", async ({ request }) => {
  const psychologist = await login(request,"sofia.almeida@demo.clinicflow.local");
  const patient = await login(request,"ana.martins@demo.clinicflow.local");
  const administrator = await login(request,"admin@demo.clinicflow.local");
  const signed = (token:string) => ({ ...apiHost, Authorization:`Bearer ${token}` });
  for (const path of ["/api/v1/auth/users/me","/api/v1/patients","/api/v1/appointments","/api/v1/clinical/clinical-cases","/api/v1/notifications"]) {
    expect((await request.get(path,{headers:apiHost})).status()).toBe(401);
    expect((await request.get(path,{headers:signed("invalid.jwt.token")})).status()).toBe(401);
  }
  expect((await request.get("/api/v1/patients",{headers:signed(patient)})).status()).toBe(403);
  expect((await request.get("/api/v1/clinical/practice-profile",{headers:signed(patient)})).status()).toBe(403);
  expect((await request.get("/api/v1/notifications",{headers:signed(patient)})).status()).toBe(403);
  const me = await request.get("/api/v1/auth/users/me",{headers:signed(psychologist)});
  expect(me.status()).toBe(200);
  const userId = (await me.json()).id;
  expect((await request.get(`/api/v1/auth/users/${userId}`,{headers:signed(psychologist)})).status()).toBe(403);
  expect((await request.get(`/api/v1/auth/users/${userId}`,{headers:signed(administrator)})).status()).toBe(200);
  const stamp=Date.now(),email=`gateway.owner.${stamp}@example.com`,password="TestingPass123!";
  const registration=await request.post("/api/v1/auth/register",{headers:apiHost,data:{email,password,role:"PSYCHOLOGIST",fullName:"Synthetic Gateway Owner"}});
  expect(registration.status()).toBe(201);
  const newLogin=await request.post("/api/v1/auth/login",{headers:apiHost,data:{email,password}});
  const owner=(await newLogin.json()).accessToken as string;
  const created=await request.post("/api/v1/patients",{headers:signed(owner),data:{email:`gateway.patient.${stamp}@example.com`,fullName:"Synthetic API Patient",consentStatus:"GRANTED"}});
  expect(created.status()).toBe(201);
  const patientId=(await created.json()).id;
  const createdCase=await request.post("/api/v1/clinical/clinical-cases",{headers:signed(owner),data:{patientId,presentingConcern:"Synthetic gateway ownership check"}});
  expect(createdCase.status()).toBe(201);
  const caseId=(await createdCase.json()).id;
  expect((await request.get(`/api/v1/clinical/clinical-cases/${caseId}`,{headers:signed(psychologist)})).status()).toBe(403);
  const history=await request.get(`/api/v1/clinical/patients/${patientId}/clinical-history`,{headers:signed(owner)});
  expect(history.status()).toBe(200);
  expect((await history.json()).patientId).toBe(patientId);
  const otherHistory=await request.get(`/api/v1/clinical/patients/${patientId}/clinical-history`,{headers:signed(psychologist)});
  expect(otherHistory.status()).toBe(200);
  expect(await otherHistory.json()).toMatchObject({cases:[],carePlans:[],sessionRecords:[]});
  expect((await request.get(`/api/v1/patients/${patientId}/clinical-history`,{headers:signed(owner)})).status()).toBe(404);
  // Patient creation emits a notification; its owner must survive the async event boundary.
  await expect.poll(async()=>{
    const response=await request.get("/api/v1/notifications",{headers:signed(owner)});
    return (await response.json()).length as number;
  }).toBeGreaterThan(0);
  const notifications=await (await request.get("/api/v1/notifications",{headers:signed(owner)})).json();
  for (const notification of notifications) {
    expect((await request.get(`/api/v1/notifications/${notification.id}`,{headers:signed(psychologist)})).status()).toBe(404);
    expect((await request.get(`/api/v1/notifications/${notification.id}`,{headers:signed(administrator)})).status()).toBe(200);
  }
  for (const path of ["/actuator/health","/api/v1/unknown","/dashboard","/api/v1/patients-other"]) expect((await request.get(path,{headers:apiHost})).status()).toBe(404);
});
test("BFF session cookies respect transport configuration and mutations reject missing CSRF",async({request})=>{
  const response=await request.post("/api/auth/login",{data:{email:"sofia.almeida@demo.clinicflow.local",password:"ClinicFlowDemo!2026"}});
  expect(response.status()).toBe(200);
  const cookies=response.headersArray().filter(h=>h.name.toLowerCase()==="set-cookie").map(h=>h.value);
  const secure=(process.env.PLAYWRIGHT_BASE_URL||"").startsWith("https:");
  expect(cookies.length).toBe(2);
  for (const cookie of cookies) expect(/; secure/i.test(cookie)).toBe(secure);
  expect((await request.post("/api/patients",{data:{}})).status()).toBe(403);
  expect((await request.get("/api/patients")).status()).toBe(200);
  const csrf=(await request.storageState()).cookies.find(c=>c.name==="clinicflow_csrf")?.value;
  expect(csrf).toBeTruthy();
  expect((await request.post("/api/auth/logout",{headers:{"x-csrf-token":csrf!}})).status()).toBe(200);
  expect((await request.get("/api/patients")).status()).toBe(401);
});
