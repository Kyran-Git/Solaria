"use strict";
(() => {
  const el = (id) => document.getElementById(id);
  let token = "", health = null, records = [], selected = null;
  let busy = false, generation = 0, timer = null;
  const text = (id, value) => { el(id).textContent = value; };
  function notice(message, kind = "") {
    text("notice", message);
    el("notice").className = `notice ${kind}`;
  }
  function controls() {
    const connected = Boolean(token && health);
    el("token").disabled = busy;
    el("connect").disabled = busy;
    el("disconnect").disabled = !token;
    el("refresh").disabled = busy || !connected;
    el("save-override").disabled = busy || !connected || !selected || selected.prediction.quality.status !== "pass" || selected.revision >= 20;
    text("connection-badge", connected ? "Connected" : "Disconnected");
    el("connection-badge").className = `badge${connected ? " connected" : ""}`;
  }
  function disconnect() {
    generation++;
    clearTimeout(timer);
    token = ""; health = null; records = []; selected = null;
    el("token").value = "";
    el("override-note").value = "";
    el("override-label").replaceChildren();
    el("review-feed").replaceChildren();
    el("history").replaceChildren();
    el("top3").replaceChildren();
    for (const id of ["review-student", "ai-result", "quality", "final-result", "quality-hint"]) text(id, "");
    el("thumbnail").removeAttribute("src");
    text("application-status", "");
    el("decision").value = "approve";
    el("review-panel").hidden = true;
    el("mock-warning").hidden = true;
    text("mode", "Not connected");
    for (const id of ["model", "threshold", "queue"]) text(id, "—");
    text("classes", "Connect to load supported classes.");
    text("scan-count", "0 scans");
    text("feed-empty", "Connect to monitor scans.");
    el("feed-empty").hidden = false;
    controls();
  }
  async function api(path, options = {}) {
    const session = generation;
    const abort = new AbortController();
    const timeout = setTimeout(() => abort.abort(), 20000);
    try {
      const response = await fetch(path, {
        ...options, headers: { ...options.headers, Authorization: `Bearer ${token}` },
        credentials: "omit", cache: "no-store", redirect: "error", signal: abort.signal,
      });
      const payload = await response.json();
      if (session !== generation) throw new Error("Connection changed; reconnect to refresh.");
      if (response.status === 401) disconnect();
      if (!response.ok) throw new Error(`${payload.reason_code || response.status}: ${payload.message || "Request failed."}`);
      return payload;
    } finally { clearTimeout(timeout); }
  }
  function renderDetail(record, reset = false) {
    selected = record;
    el("review-panel").hidden = !record;
    if (!record) {
      el("thumbnail").removeAttribute("src");
      el("override-note").value = "";
      controls(); return;
    }
    text("application-status", record.application ? `Minecraft: ${record.application.status} · revision ${record.application.revision} (not a travel confirmation)` : "Minecraft has not acknowledged this decision.");
    const p = record.prediction;
    text("review-student", `${record.student_id || "Unidentified student"} · request ${p.request_id} · ${new Date(record.received_at).toLocaleString()}`);
    text("ai-result", `${p.label} · ${(p.confidence * 100).toFixed(1)}%${p.mock ? " · MOCK" : ""}`);
    text("quality", `${p.quality.status}${p.reason_code ? ` · ${p.reason_code}` : ""}`);
    text("quality-hint", p.quality.hint);
    text("final-result", `${record.final_label} · ${record.accepted ? "accepted" : "not accepted"} · ${record.decider} · revision ${record.revision}`);
    el("thumbnail").hidden = !p.thumbnail;
    el("thumbnail").removeAttribute("src");
    if (p.thumbnail && p.thumbnail.content_type === "image/png") el("thumbnail").src = `data:image/png;base64,${p.thumbnail.base64}`;
    el("top3").replaceChildren(...p.top3.map((prediction) => {
      const item = document.createElement("li");
      item.textContent = `${prediction.label}: ${(prediction.confidence * 100).toFixed(1)}%`;
      return item;
    }));
    if (reset) {
      el("override-note").value = "";
      el("override-label").value = health.classes.includes(record.final_label) ? record.final_label : health.classes[0];
    }
    el("history").replaceChildren(...record.overrides.map((entry) => {
      const item = document.createElement("li");
      item.textContent = `${new Date(entry.at).toLocaleString()} · ${entry.label} · ${entry.note || "No note"}`;
      return item;
    }));
    controls();
  }
  function renderFeed() {
    text("scan-count", `${records.length} scans`);
    el("feed-empty").hidden = records.length > 0;
    text("feed-empty", "Waiting for in-game scan submissions. Minecraft integration is not wired yet.");
    el("review-feed").replaceChildren(...records.map((record) => {
      const button = document.createElement("button");
      button.type = "button"; button.className = "scan-row secondary";
      button.setAttribute("aria-pressed", String(selected?.scan_id === record.scan_id));
      const p = record.prediction;
      button.textContent = `${record.student_id || "Unidentified student"} · ${p.label} (${(p.confidence * 100).toFixed(1)}%) → ${record.final_label} · ${record.decider} · ${record.accepted ? "accepted" : (p.reason_code || "not accepted")}${p.mock ? " · MOCK" : ""} · ${p.request_id}`;
      button.addEventListener("click", () => { if (!busy) { renderDetail(record, true); renderFeed(); } });
      return button;
    }));
  }
  async function refresh() {
    const nextHealth = await api("/v1/health");
    const feed = await api("/v1/reviews");
    health = nextHealth; records = feed.reviews;
    text("mode", health.mock ? "MOCK · synthetic" : "ONNX · inference");
    text("model", health.model);
    text("threshold", `${(health.threshold * 100).toFixed(0)}%`);
    text("queue", `${health.queue.in_flight} / ${health.queue.pending}`);
    text("classes", `Classes: ${health.classes.join(", ")}`);
    el("mock-warning").hidden = !health.mock;
    if (!el("override-label").options.length) el("override-label").replaceChildren(...health.classes.map((label) => {
      const option = document.createElement("option"); option.value = label; option.textContent = label; return option;
    }));
    text("retention", `Refresh every 3 seconds · newest ${feed.retention.capacity} scans · ${feed.retention.ttl_seconds / 3600} hour expiry · memory only`);
    if (selected) {
      const next = records.find((record) => record.scan_id === selected.scan_id);
      // Do not silently replace a revision while the teacher is composing an override.
      // The server will reject stale submissions; selecting the row reloads it.
      if (next && next.revision === selected.revision) {
        selected = next;
        text("application-status", next.application ? `Minecraft: ${next.application.status} · revision ${next.application.revision} (not a travel confirmation)` : "Minecraft has not acknowledged this decision.");
      }
      if (!next) renderDetail(null);
      else if (next.revision !== selected.revision) {
        notice("This review changed in another console. Select its row again before overriding.", "warning");
      }
    }
    renderFeed(); controls();
  }
  function schedule() {
    clearTimeout(timer);
    if (token && health) timer = setTimeout(() => action(refresh), 3000);
  }
  async function action(fn) {
    if (busy) return;
    busy = true; clearTimeout(timer); controls();
    try { await fn(); }
    catch (error) { notice(error.name === "AbortError" ? "Service timed out; retry." : error.message, "error"); }
    finally { busy = false; controls(); schedule(); }
  }
  text("endpoint", window.location.origin);
  el("token").value = "";
  el("connect-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const entered = el("token").value;
    action(async () => {
      disconnect();
      if (!entered || entered.length > 256 || !/^[\x20-\x7e]+$/.test(entered)) throw new Error("Enter the exact printable startup token, without Bearer.");
      token = entered;
      try { await refresh(); } catch (error) { disconnect(); throw error; }
      notice("Connected. Monitoring in-game classifications.", "success");
    });
  });
  el("disconnect").addEventListener("click", () => { disconnect(); notice("Disconnected. Token and classroom results cleared from this page."); });
  el("refresh").addEventListener("click", () => action(refresh));
  el("override-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (!selected) return;
    const record = selected;
    const payload = { label: el("override-label").value, note: el("override-note").value, revision: record.revision, accepted: el("decision").value === "approve" };
    action(async () => {
      const result = await api(`/v1/reviews/${record.scan_id}/override`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
      });
      renderDetail(result.review, true);
      await refresh();
      notice("Teacher decision saved. Wait for Minecraft acknowledgement below; this is not a build or travel confirmation.", "success");
    });
  });
  window.addEventListener("pagehide", disconnect);
  controls();
})();
