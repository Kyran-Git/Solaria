"use strict";
(() => {
  const el = (id) => document.getElementById(id);
  let token = "", health = null, records = [], selected = null;
  let busy = false, generation = 0, timer = null;
  const playerNumbers = new Map();
  const playerExpanded = new Map();
  const text = (id, value) => { el(id).textContent = value; };
  function notice(message, kind = "") {
    text("notice", message);
    el("notice").className = `notice ${kind}`;
  }
  function controls() {
    const connected = Boolean(token && health);
    const hasScans = connected && records.length > 0;
    el("token").disabled = busy;
    el("connect").disabled = busy;
    el("disconnect").disabled = !token;
    el("refresh").disabled = busy || !connected;
    el("expand-all").disabled = busy || !hasScans;
    el("collapse-all").disabled = busy || !hasScans;
    el("clear-selection").disabled = busy || !selected;
    el("save-override").disabled = busy || !connected || !selected || selected.prediction.quality.status !== "pass" || selected.revision >= 20;
    text("connection-badge", connected ? "Connected" : "Disconnected");
    el("connection-badge").className = `badge${connected ? " connected" : ""}`;
  }
  function buildPlayerGroups() {
    const groupsByKey = new Map();
    for (const record of records) {
      const rawId = record.student_id || "";
      const key = rawId || "__unidentified__";
      let group = groupsByKey.get(key);
      if (!group) {
        if (rawId && !playerNumbers.has(key)) playerNumbers.set(key, playerNumbers.size + 1);
        group = {
          key,
          rawId,
          label: rawId ? `Player ${playerNumbers.get(key)}` : "Unidentified student",
          shortId: rawId ? (rawId.length > 12 ? rawId.slice(0, 8) : rawId) : "",
          scans: [],
        };
        groupsByKey.set(key, group);
      }
      group.scans.push(record);
    }
    return Array.from(groupsByKey.values());
  }
  function statusForScan(record) {
    const p = record.prediction;
    if (p.quality && p.quality.status !== "pass") {
      return {
        tone: "err",
        text: `quality fail${p.reason_code ? ` (${p.reason_code})` : ""} · not accepted`,
      };
    }
    if (!record.accepted && record.decider === "teacher") {
      return { tone: "err", text: `rejected · rev ${record.revision}` };
    }
    if (!record.accepted) {
      return { tone: "warn", text: p.reason_code || "needs review" };
    }
    if (record.decider === "teacher") {
      return { tone: "info", text: `accepted · teacher · rev ${record.revision}` };
    }
    return { tone: "ok", text: `accepted · ${record.decider} · rev ${record.revision}` };
  }
  function disconnect() {
    generation++;
    clearTimeout(timer);
    token = ""; health = null; records = []; selected = null;
    playerNumbers.clear();
    playerExpanded.clear();
    el("token").value = "";
    el("override-note").value = "";
    el("override-label").replaceChildren();
    el("review-feed").replaceChildren();
    el("history").replaceChildren();
    el("history").className = "well";
    el("top3").replaceChildren();
    for (const id of ["review-student", "ai-result", "quality", "final-result", "quality-hint", "application-status"]) text(id, "");
    el("quality-hint").className = "quality-hint";
    el("thumbnail").hidden = true;
    el("thumbnail").removeAttribute("src");
    el("decision").value = "approve";
    el("review-empty").hidden = false;
    el("review-content").hidden = true;
    el("mock-warning").hidden = true;
    el("review-mock-warning").hidden = true;
    text("mode", "Not connected");
    for (const id of ["model", "threshold", "queue"]) text(id, "—");
    el("classes").replaceChildren(document.createTextNode("Connect to load supported classes."));
    text("scan-count", "0 scans");
    text("feed-empty", "Connect to monitor scans. No sample or student results are fabricated.");
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
    el("review-empty").hidden = Boolean(record);
    el("review-content").hidden = !record;
    if (!record) {
      el("review-mock-warning").hidden = true;
      el("thumbnail").hidden = true;
      el("thumbnail").removeAttribute("src");
      el("override-note").value = "";
      el("top3").replaceChildren();
      el("history").replaceChildren();
      el("history").className = "well";
      for (const id of ["review-student", "ai-result", "quality", "final-result", "quality-hint", "application-status"]) text(id, "");
      el("quality-hint").className = "quality-hint";
      controls();
      return;
    }
    text("application-status", record.application ? `Minecraft: ${record.application.status} · revision ${record.application.revision} (not a travel confirmation)` : "Minecraft has not acknowledged this decision.");
    const p = record.prediction;
    el("review-mock-warning").hidden = !p.mock;
    const rawId = record.student_id || "";
    const studentDisplay = rawId && playerNumbers.has(rawId)
      ? `Player ${playerNumbers.get(rawId)} (${rawId})`
      : (rawId || "Unidentified student");
    text("review-student", `${studentDisplay} · req ${p.request_id} · ${new Date(record.received_at).toLocaleString()}`);
    text("ai-result", `${p.label} · ${(p.confidence * 100).toFixed(1)}%${p.mock ? " · MOCK" : ""}`);
    text("quality", `${p.quality.status}${p.reason_code ? ` · ${p.reason_code}` : ""}`);
    text("quality-hint", p.quality.hint);
    el("quality-hint").className = p.quality.status === "pass" ? "quality-hint" : "quality-hint is-fail";
    text("final-result", `${record.final_label} · ${record.accepted ? "accepted" : "not accepted"} · ${record.decider} · rev ${record.revision}`);
    el("thumbnail").hidden = !p.thumbnail;
    el("thumbnail").removeAttribute("src");
    if (p.thumbnail && p.thumbnail.content_type === "image/png") el("thumbnail").src = `data:image/png;base64,${p.thumbnail.base64}`;
    el("top3").replaceChildren(...p.top3.map((prediction) => {
      const item = document.createElement("li");
      const nameSpan = document.createElement("span");
      nameSpan.textContent = prediction.label;
      const confSpan = document.createElement("span");
      confSpan.textContent = `${(prediction.confidence * 100).toFixed(1)}%`;
      item.append(nameSpan, confSpan);
      return item;
    }));
    if (reset) {
      el("override-note").value = "";
      el("override-label").value = health && health.classes.includes(record.final_label) ? record.final_label : (health?.classes[0] || "");
      el("decision").value = record.accepted ? "approve" : "reject";
    }
    if (record.overrides.length === 0) {
      el("history").className = "well empty-history";
      const emptyItem = document.createElement("li");
      emptyItem.textContent = "No teacher overrides yet.";
      el("history").replaceChildren(emptyItem);
    } else {
      el("history").className = "well";
      el("history").replaceChildren(...record.overrides.map((entry) => {
        const item = document.createElement("li");
        item.textContent = `${new Date(entry.at).toLocaleString()} · ${entry.label} · ${entry.note || "No note"}`;
        return item;
      }));
    }
    controls();
  }
  function createScanRow(record) {
    const p = record.prediction;
    const button = document.createElement("button");
    button.type = "button";
    button.className = "scan-row secondary";
    button.setAttribute("aria-pressed", String(selected?.scan_id === record.scan_id));

    const topRow = document.createElement("div");
    topRow.className = "scan-row-top";

    const titleWrap = document.createElement("span");
    titleWrap.className = "scan-title";
    const labelSpan = document.createElement("span");
    labelSpan.textContent = p.label === "unknown" && record.accepted && record.final_label && record.final_label !== p.label
      ? `${record.final_label} (AI: ${p.label})`
      : p.label;
    const confSpan = document.createElement("span");
    confSpan.className = "scan-conf";
    confSpan.textContent = `(${(p.confidence * 100).toFixed(1)}% AI${p.reason_code ? ` · ${p.reason_code}` : ""})`;
    titleWrap.append(labelSpan, confSpan);

    const timeSpan = document.createElement("span");
    timeSpan.className = "scan-time";
    timeSpan.textContent = new Date(record.received_at).toLocaleTimeString();
    topRow.append(titleWrap, timeSpan);

    const subRow = document.createElement("div");
    subRow.className = "scan-row-sub";
    const effSpan = document.createElement("span");
    effSpan.textContent = `${record.final_label}${p.mock ? " · MOCK" : ""}`;
    const statusInfo = statusForScan(record);
    const statusTag = document.createElement("span");
    statusTag.className = `status-tag ${statusInfo.tone}`;
    statusTag.textContent = statusInfo.text;
    subRow.append(effSpan, statusTag);

    button.append(topRow, subRow);
    button.addEventListener("click", () => {
      if (!busy) {
        renderDetail(record, true);
        renderFeed();
      }
    });
    return button;
  }
  function renderFeed() {
    const groups = buildPlayerGroups();
    text("scan-count", records.length === 0
      ? "0 scans"
      : `${records.length} ${records.length === 1 ? "scan" : "scans"} · ${groups.length} ${groups.length === 1 ? "player" : "players"}`);
    el("feed-empty").hidden = records.length > 0;
    text("feed-empty", token && health
      ? "Waiting for in-game scan submissions. Minecraft integration is not wired yet."
      : "Connect to monitor scans. No sample or student results are fabricated.");

    groups.forEach((group, idx) => {
      if (!playerExpanded.has(group.key)) {
        const hasSelected = Boolean(selected && group.scans.some((scan) => scan.scan_id === selected.scan_id));
        playerExpanded.set(group.key, groups.length <= 4 || idx === 0 || hasSelected);
      }
    });

    el("review-feed").replaceChildren(...groups.map((group) => {
      const expanded = Boolean(playerExpanded.get(group.key));
      const container = document.createElement("div");
      container.className = "player-group";

      const headerBtn = document.createElement("button");
      headerBtn.type = "button";
      headerBtn.className = "player-toggle";
      headerBtn.setAttribute("aria-expanded", String(expanded));

      const leftSpan = document.createElement("span");
      leftSpan.className = "player-toggle-left";
      const chevron = document.createElement("span");
      chevron.className = "chevron";
      chevron.setAttribute("aria-hidden", "true");
      chevron.textContent = "\u25be";
      const nameSpan = document.createElement("span");
      nameSpan.textContent = group.label;
      leftSpan.append(chevron, nameSpan);
      if (group.shortId) {
        const idTag = document.createElement("span");
        idTag.className = "player-id-tag";
        idTag.textContent = group.shortId;
        idTag.title = group.rawId;
        leftSpan.append(idTag);
      }

      const countPill = document.createElement("span");
      countPill.className = "player-count-pill";
      countPill.textContent = `${group.scans.length} ${group.scans.length === 1 ? "scan" : "scans"}`;

      headerBtn.append(leftSpan, countPill);
      headerBtn.addEventListener("click", () => {
        playerExpanded.set(group.key, !expanded);
        renderFeed();
      });

      container.append(headerBtn);
      if (expanded) {
        const scanList = document.createElement("div");
        scanList.className = "player-scans";
        scanList.append(...group.scans.map(createScanRow));
        container.append(scanList);
      }
      return container;
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
    const classesLabel = document.createElement("span");
    classesLabel.className = "classes-label";
    classesLabel.textContent = "Classes:";
    const classChips = health.classes.map((cls) => {
      const chip = document.createElement("span");
      chip.className = "class-chip";
      chip.textContent = cls;
      return chip;
    });
    el("classes").replaceChildren(classesLabel, ...classChips);
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
  el("expand-all").addEventListener("click", () => {
    for (const group of buildPlayerGroups()) playerExpanded.set(group.key, true);
    renderFeed();
  });
  el("collapse-all").addEventListener("click", () => {
    for (const group of buildPlayerGroups()) playerExpanded.set(group.key, false);
    renderFeed();
  });
  el("clear-selection").addEventListener("click", () => {
    if (busy) return;
    renderDetail(null, true);
    renderFeed();
  });
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
