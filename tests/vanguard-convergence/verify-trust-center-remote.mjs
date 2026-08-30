import crypto from "node:crypto";
import { createClient } from "@supabase/supabase-js";

const projectRef = process.env.MEET_SUPABASE_PROJECT_REF;
const rawKeys = process.env.MEET_SUPABASE_KEYS_JSON;
if (!projectRef || !rawKeys) {
  throw new Error("Remote Trust Center probe requires project ref and API keys");
}

const keys = JSON.parse(rawKeys);
const keyValue = (entry) => entry?.api_key ?? entry?.key ?? entry?.value;
const anonKey = keyValue(keys.find((entry) => entry.name === "anon"));
const serviceKey = keyValue(keys.find((entry) => entry.name === "service_role"));
if (!anonKey || !serviceKey) throw new Error("Required Supabase keys were not resolved");

const url = `https://${projectRef}.supabase.co`;
const options = { auth: { persistSession: false, autoRefreshToken: false } };
const admin = createClient(url, serviceKey, options);
const applicant = createClient(url, anonKey, options);
const categories = [
  "PASSENGER", "RIDE_DRIVER", "TOW_TRUCK", "MECHANIC", "PARTS_STORE",
  "SERVICE_PROVIDER", "WORKSHOP", "AUTO_LOCKSMITH", "LAWYER", "NOTARY",
  "PROPERTY_BROKER", "PROPERTY_SELLER", "FUEL_STATION_STAFF", "FLEET_OPERATOR",
];
const email = `meet-trust-probe-${Date.now()}@example.test`;
const password = `Probe-${crypto.randomUUID()}-9a!`;
let userId;
let applicationIds = [];
let channel;
let summary;

const withTimeout = (promise, label, timeoutMs = 20_000) => Promise.race([
  promise,
  new Promise((_, reject) => setTimeout(() => reject(new Error(`${label}_TIMEOUT`)), timeoutMs)),
]);

async function cleanup() {
  const failures = [];
  const check = async (label, operation) => {
    try {
      const result = await operation;
      if (result?.error) failures.push(`${label}:${result.error.code ?? "FAILED"}`);
    } catch {
      failures.push(`${label}:FAILED`);
    }
  };
  if (channel) await check("CHANNEL", applicant.removeChannel(channel));
  applicant.realtime.disconnect();
  if (applicationIds.length > 0) {
    await check(
      "AUDIT_ROWS",
      admin.from("service_verification_audit_events").delete().in("application_id", applicationIds),
    );
    await check(
      "APPLICATION_ROWS",
      admin.from("service_verification_applications").delete().in("id", applicationIds),
    );
  }
  if (userId) {
    await check("CAPABILITIES", admin.from("principal_capabilities").delete().eq("principal_id", userId));
    await check("PRINCIPAL_PROFILE", admin.from("principal_profiles").delete().eq("principal_id", userId));
    await check("PRINCIPAL", admin.from("principals").delete().eq("principal_id", userId));
    await check("AUTH_USER", admin.auth.admin.deleteUser(userId));
  }
  if (failures.length > 0) throw new Error(`REMOTE_PROBE_CLEANUP_FAILED:${failures.join(",")}`);
}

try {
  const ownerGrants = await admin.from("platform_authority_grants")
    .select("user_id")
    .eq("role", "PLATFORM_OWNER")
    .eq("active", true);
  if (ownerGrants.error || ownerGrants.data?.length !== 1) {
    throw ownerGrants.error ?? new Error("PLATFORM_OWNER_CARDINALITY_INVALID");
  }
  const ownerIdentity = await admin.auth.admin.getUserById(ownerGrants.data[0].user_id);
  if (ownerIdentity.error || ownerIdentity.data.user?.email?.toLowerCase() !== "jordelmir@gmail.com") {
    throw ownerIdentity.error ?? new Error("PLATFORM_OWNER_IDENTITY_INVALID");
  }

  const created = await admin.auth.admin.createUser({ email, password, email_confirm: true });
  if (created.error || !created.data.user) throw created.error ?? new Error("USER_CREATE_FAILED");
  userId = created.data.user.id;

  const signedIn = await applicant.auth.signInWithPassword({ email, password });
  if (signedIn.error || !signedIn.data.session) {
    throw signedIn.error ?? new Error("SIGN_IN_FAILED");
  }
  await applicant.realtime.setAuth(signedIn.data.session.access_token);

  let realtimeEventResolve;
  const realtimeEvent = new Promise((resolve) => { realtimeEventResolve = resolve; });
  channel = applicant.channel(`trust-probe-${crypto.randomUUID()}`)
    .on(
      "postgres_changes",
      {
        event: "INSERT",
        schema: "public",
        table: "service_verification_applications",
        filter: `applicant_user_id=eq.${userId}`,
      },
      () => realtimeEventResolve(true),
    );
  await withTimeout(new Promise((resolve, reject) => {
    channel.subscribe((status) => {
      if (status === "SUBSCRIBED") resolve(true);
      if (["CHANNEL_ERROR", "TIMED_OUT", "CLOSED"].includes(status)) reject(new Error(status));
    });
  }), "REALTIME_SUBSCRIBE");

  for (const [index, serviceType] of categories.entries()) {
    const result = await applicant.rpc("meet_submit_service_verification_v2", {
      p_service_type: serviceType,
      p_profile_reference: `remote-probe-${serviceType.toLowerCase()}`,
      p_display_name: "Remote Trust Probe",
      p_correlation_id: crypto.randomUUID(),
    });
    if (result.error || !result.data?.id || result.data.status !== "PENDING") {
      throw result.error ?? new Error(`SUBMISSION_FAILED_${serviceType}`);
    }
    applicationIds.push(result.data.id);
    if (index === 0) await withTimeout(realtimeEvent, "REALTIME_EVENT");
  }

  const own = await applicant.rpc("meet_own_verification_applications_v1");
  if (own.error) throw own.error;
  const returnedTypes = new Set(own.data?.items?.map((item) => item.service_type));
  if (categories.some((category) => !returnedTypes.has(category))) {
    throw new Error("OWN_QUEUE_INCOMPLETE");
  }

  const denied = await applicant.rpc("meet_owner_verification_queue_v2", {
    p_status: "ALL",
    p_limit: 100,
  });
  if (!denied.error?.message?.includes("PLATFORM_OWNER_REQUIRED")) {
    throw new Error("NON_OWNER_QUEUE_WAS_NOT_DENIED");
  }

  const retry = await applicant.rpc("meet_submit_service_verification_v2", {
    p_service_type: "MECHANIC",
    p_profile_reference: "remote-probe-mechanic",
    p_display_name: "Remote Trust Probe",
    p_correlation_id: crypto.randomUUID(),
  });
  if (retry.error || retry.data?.status !== "PENDING") throw retry.error ?? new Error("RETRY_FAILED");

  summary = {
    result: "PASS",
    categories: categories.length,
    receipts: applicationIds.length,
    realtimeInsertObserved: true,
    rlsNonOwnerDenied: true,
    idempotentRetry: true,
    activePlatformOwners: 1,
  };
} finally {
  await cleanup();
}

process.stdout.write(JSON.stringify(summary) + "\n", () => process.exit(0));
