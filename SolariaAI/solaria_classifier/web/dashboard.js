"use strict";
(() => {
  const el = (id) => document.getElementById(id);
  let token = "";
  let health = null;
  let file = null;
  let previewURL = null;
  let busy = false;
  let selection = 0;

  function notice(message, kind = "") {
    el("notice").textContent = message;
    el("notice").className = `notice ${kind}`;
  }
  function controls() {
    const connected = Boolean(token && health);
    el("token").disabled = busy;
    el("connect").disabled = busy;
    el("refresh").disabled = busy || !connected;
    el("disconnect").disabled = busy || !connected;
    el("classify").disabled = busy || !connected || !file;
    el("image-file").disabled = busy;
    el("sample").disabled = busy;
    el("clear").disabled = busy;
    el("mock-controls").disabled = busy || !connected || !(health && health.mock);
    el("connection-badge").textContent = connected ? "Connected" : "Disconnected";
    el("connection-badge").className = `badge${connected ? " connected" : ""}`;
  }
  function resetResult() {
    el("result").hidden = true;
    el("result-empty").hidden = false;
    el("thumbnail").hidden = true;
    el("thumbnail").removeAttribute("src");
    el("top3").replaceChildren();
    el("raw-result").textContent = "";
  }
  function disconnect() {
    token = "";
    health = null;
    el("token").value = "";
    el("mode").textContent = "Not connected";
    for (const id of ["model", "threshold", "queue"]) el(id).textContent = "—";
    el("classes").textContent = "Connect to load supported classes.";
    el("mock-controls").hidden = true;
    el("mock-warning").hidden = true;
    el("mock-label").replaceChildren();
    resetResult();
    controls();
  }
  async function api(path, options = {}) {
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), 20000);
    try {
      const response = await fetch(path, {
        ...options,
        headers: { ...options.headers, Authorization: `Bearer ${token}` },
        credentials: "omit", cache: "no-store", redirect: "error", signal: abort.signal,
      });
      const payload = await response.json();
      if (response.status === 401) {
        disconnect();
        throw new Error("UNAUTHORIZED: paste the exact startup token used by this service. Open 'Where do I get a token?' for instructions.");
      }
      if (!response.ok) throw new Error(`${payload.reason_code || response.status}: ${payload.message || "Request failed."}`);
      return payload;
    } catch (error) {
      if (error.name === "AbortError") throw new Error("The service took too long to respond. Refresh health and retry.");
      if (error instanceof TypeError) throw new Error("Cannot reach the service. Check Docker is running, then refresh health.");
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }
  async function loadHealth() {
    health = await api("/v1/health");
    el("mode").textContent = health.mock ? "MOCK · synthetic" : "ONNX · real inference";
    el("model").textContent = health.model;
    el("threshold").textContent = `${(health.threshold * 100).toFixed(0)}%`;
    el("queue").textContent = `${health.queue.in_flight} / ${health.queue.pending}`;
    el("classes").textContent = `Supported classes: ${health.classes.join(", ")}`;
    el("mock-warning").hidden = !health.mock;
    el("mock-controls").hidden = !health.mock;
    const previous = el("mock-label").value || "earth";
    el("mock-label").replaceChildren(...health.classes.map((label) => {
      const option = document.createElement("option");
      option.value = label;
      option.textContent = label;
      return option;
    }));
    if (health.classes.includes(previous)) el("mock-label").value = previous;
  }
  async function action(work) {
    if (busy) return;
    busy = true;
    controls();
    try { await work(); }
    catch (error) { notice(error.message || "Request failed. Please retry.", "error"); }
    finally { busy = false; controls(); }
  }
  function clearFile() {
    selection += 1;
    file = null;
    el("image-file").value = "";
    if (previewURL) URL.revokeObjectURL(previewURL);
    previewURL = null;
    el("preview").removeAttribute("src");
    el("preview-box").hidden = true;
    el("file-note").textContent = "No drawing selected.";
    resetResult();
    controls();
  }

  async function selectFile(candidate) {
    clearFile();
    if (!candidate) return;
    const current = selection;
    try {
      if (!["image/png", "image/jpeg"].includes(candidate.type)) throw new Error("Choose a PNG or JPEG image.");
      if (candidate.size === 0 || candidate.size > 2 * 1024 * 1024) throw new Error("Choose a nonempty image no larger than 2 MiB.");
      const image = await createImageBitmap(candidate);
      const pixels = image.width * image.height;
      image.close();
      if (pixels > 4000000) throw new Error("The image exceeds 4 megapixels. Resize it before uploading.");
      if (current !== selection) return;
      file = candidate;
      previewURL = URL.createObjectURL(candidate);
      el("preview").src = previewURL;
      el("preview-box").hidden = false;
      el("file-note").textContent = `${candidate.name} · ${(candidate.size / 1024).toFixed(1)} KiB`;
      notice("Drawing ready. Connect and choose Classify drawing.");
    } catch (error) {
      if (current === selection) notice(error.message || "Cannot decode this image.", "error");
    }
    controls();
  }
  function renderResult(result) {
    el("result-empty").hidden = true;
    el("result").hidden = false;
    el("result-mode").textContent = result.mock ? "SYNTHETIC MOCK RESULT" : "ONNX INFERENCE RESULT";
    el("result-label").textContent = result.label;
    el("result-summary").textContent = `${result.accepted ? "Accepted" : "Not accepted"} · ${(result.confidence * 100).toFixed(1)}% confidence · ${result.elapsed_ms} ms`;
    el("quality-hint").textContent = `Quality: ${result.quality.status}. ${result.quality.hint || ""} ${result.reason_code || ""}`;
    el("thumbnail").hidden = !result.thumbnail;
    if (result.thumbnail && result.thumbnail.content_type === "image/png") {
      el("thumbnail").src = `data:image/png;base64,${result.thumbnail.base64}`;
    }
    el("top3").replaceChildren(...result.top3.map((prediction) => {
      const item = document.createElement("li");
      const name = document.createElement("span");
      name.textContent = prediction.label;
      const value = document.createElement("span");
      value.textContent = `${(prediction.confidence * 100).toFixed(1)}%`;
      const bar = document.createElement("progress");
      bar.max = 1;
      bar.value = prediction.confidence;
      bar.setAttribute("aria-label", `${prediction.label} confidence`);
      item.append(name, bar, value);
      return item;
    }));
    const raw = { ...result, thumbnail: result.thumbnail ? { ...result.thumbnail, base64: "(displayed above)" } : null };
    el("raw-result").textContent = JSON.stringify(raw, null, 2);
  }
  el("endpoint").textContent = window.location.origin;
  el("token").value = "";
  el("connect-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const entered = el("token").value;
    action(async () => {
      disconnect();
      if (!entered || entered.length > 256 || !/^[\x20-\x7e]+$/.test(entered)) {
        throw new Error("Enter the printable startup token exactly, without the Bearer prefix.");
      }
      token = entered;
      try { await loadHealth(); }
      catch (error) { disconnect(); throw error; }
      notice("Connected. Choose a drawing or use the sample to test the service.", "success");
    });
  });
  el("disconnect").addEventListener("click", () => {
    disconnect();
    notice("Disconnected. Startup token cleared from this page.");
  });
  el("refresh").addEventListener("click", () => action(async () => {
    await loadHealth();
    notice("Service status refreshed.", "success");
  }));
  el("image-file").addEventListener("change", (event) => selectFile(event.target.files[0]));
  el("clear").addEventListener("click", () => { clearFile(); notice("Drawing and result cleared."); });
  el("sample").addEventListener("click", () => action(async () => {
    const canvas = document.createElement("canvas");
    canvas.width = 640;
    canvas.height = 480;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "white";
    ctx.fillRect(0, 0, 640, 480);
    ctx.strokeStyle = "black";
    ctx.lineWidth = 8;
    ctx.beginPath();
    ctx.ellipse(320, 240, 120, 120, 0, 0, 2 * Math.PI);
    ctx.stroke();
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
    if (!blob) throw new Error("Could not create sample drawing.");
    await selectFile(new File([blob], "sample-circle.png", { type: "image/png" }));
  }));
  el("classify").addEventListener("click", () => action(async () => {
    if (!file || !health) return;
    resetResult();
    notice("Processing drawing…");
    const headers = { "Content-Type": file.type, "X-Request-ID": crypto.randomUUID() };
    if (health.mock) {
      headers["X-Solaria-Label"] = el("mock-label").value;
      headers["X-Solaria-Scenario"] = el("scenario").value;
    }
    const result = await api("/v1/classify", { method: "POST", headers, body: file });
    renderResult(result);
    notice(result.accepted ? "Classification complete." : "Scan completed but was not accepted. See the quality or confidence reason below.", result.accepted ? "success" : "warning");
  }));
  window.addEventListener("pagehide", () => { disconnect(); clearFile(); });
  controls();
})();
