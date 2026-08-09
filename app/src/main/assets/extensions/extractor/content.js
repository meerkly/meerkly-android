// Runs at document_idle in every top-level page. Sends page HTML to the app
// (via the background relay) as "snapshots":
//   - An early fire-and-forget snapshot at DOMContentLoaded (final=false), sent
//     BEFORE any round-trip so it's reliable even if the headless page's context
//     is torn down moments later — the app's best-effort fallback.
//   - A "final" snapshot (final=true) once the wait condition holds.
// The app waits for the final snapshot, falling back to the early one on timeout.
//
// Wait modes (from the spec, a JSON string {waitFor, settleMs, waitRules, detectMs}):
//   - stable (default): the DOM has been STRUCTURALLY quiet for QUIET_MS, capped
//     at settle_ms. We observe content only (childList + characterData), NOT
//     attributes, so CSS animations/spinners don't keep it from settling.
//   - domcontentloaded: snapshot at DOMContentLoaded (no settle).
//   - networkidle: snapshot at the window load event.
//   - <selector>: wait until a matching element is visible, then stable-settle.
//
// wait_rules (optional): an ordered list of { if: <guard sel>, then: <target> }.
// All guards are probed for up to detect_ms (default 800ms); the first rule (by
// list order) whose guard becomes visible wins → we run its `then` target (like
// a wait mode). If no guard appears within detect_ms, we run `waitFor` as the
// fallback. `matchedRule` (the winning index, or -1) rides along on every
// snapshot so the app/gateway can report which rule fired.
(function () {
  // The wait_rules index that matched (-1 = none/fallback or no rules). Set before
  // the final snapshot so it's reported even if a required target later times out.
  var matchedRule = -1;

  // Capture options from the spec: scripts/styles are stripped by default to
  // shrink the payload. Initialized to "keep everything" because the early
  // fallback snapshot fires BEFORE the spec round-trip — a full document is
  // always a correct superset, while stripping something a job asked to keep
  // would lose data on the timeout-fallback path.
  var includeScripts = true;
  var includeStyles = true;

  // Serializes the document per the capture options. Stripping clones the root
  // first — the live DOM is never mutated (later snapshots must see reality).
  // Inline style attributes are always kept. Mirrors the desktop SDK's
  // captureHtmlExpr and the headless snippets.py.
  function captureHtml() {
    if (includeScripts && includeStyles) return document.documentElement.outerHTML;
    var r = document.documentElement.cloneNode(true);
    var kill = [];
    if (!includeScripts) kill.push("script");
    if (!includeStyles) kill.push("style", 'link[rel="stylesheet"]');
    var nodes = r.querySelectorAll(kill.join(","));
    for (var i = 0; i < nodes.length; i++) nodes[i].remove();
    return r.outerHTML;
  }

  // A browser does not hand back JSON, it renders it -- Gecko into a <pre>, or
  // the JSON viewer's own node when devtools are enabled. Either way
  // outerHTML would bury the payload in markup a caller that asked for an API
  // endpoint cannot use. This content script runs in an isolated world, so
  // reading the DOM here is unaffected by the page's CSP.
  function jsonPayload() {
    try {
      var ct = (document.contentType || "").split(";")[0].trim().toLowerCase();
      var isJson = ct === "application/json" || ct === "text/json" || /\+json$/.test(ct);
      if (!isJson) return null;
      var el = document.querySelector("pre") || document.getElementById("json");
      var text = el ? el.textContent : document.body ? document.body.textContent : null;
      return text && text.trim() ? text : null;
    } catch (e) {
      return null;
    }
  }

  function snapshot(final) {
    try {
      var payload = jsonPayload();
      browser.runtime.sendMessage({
        kind: "page",
        url: location.href,
        title: document.title,
        html: payload !== null ? payload : captureHtml(),
        format: payload !== null ? "json" : "html",
        final: !!final,
        matchedRule: matchedRule,
      });
    } catch (e) {}
  }

  function visible(el) {
    return !!(
      el &&
      (el.offsetWidth || el.offsetHeight || el.getClientRects().length) &&
      getComputedStyle(el).visibility !== "hidden"
    );
  }

  function onceDOMReady(cb) {
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", cb, { once: true });
    else cb();
  }

  function onLoad(cb) {
    if (document.readyState === "complete") cb();
    else window.addEventListener("load", cb, { once: true });
  }

  // Resolves once an element matching `sel` is visible (MutationObserver-driven,
  // not throttled). Never times out — the app owns the deadline.
  function waitSelector(sel) {
    return new Promise((resolve) => {
      let done = false;
      const finish = () => {
        if (done) return;
        done = true;
        try { mo.disconnect(); } catch (e) {}
        clearInterval(iv);
        resolve();
      };
      const check = () => {
        let el;
        try { el = document.querySelector(sel); } catch (e) { return; }
        if (visible(el)) finish();
      };
      const mo = new MutationObserver(check);
      const iv = setInterval(check, 250);
      const observe = () => {
        try { mo.observe(document.documentElement || document, { childList: true, subtree: true, attributes: true }); } catch (e) {}
        check();
      };
      if (document.documentElement) observe();
      else document.addEventListener("DOMContentLoaded", observe, { once: true });
    });
  }

  // Probes all rule guards; resolves the FIRST rule (by list order) whose `if`
  // selector is visible, or -1 once `detectMs` elapses with no guard. Like
  // waitSelector it's MutationObserver + interval driven (and watches attributes,
  // since guard visibility can flip via class/style).
  function matchFirstRule(rules, detectMs) {
    return new Promise((resolve) => {
      var done = false;
      var finish = function (idx) {
        if (done) return;
        done = true;
        try { mo.disconnect(); } catch (e) {}
        clearInterval(iv);
        clearTimeout(cap);
        resolve(idx);
      };
      var check = function () {
        for (var i = 0; i < rules.length; i++) {
          var el;
          try { el = document.querySelector(rules[i].if); } catch (e) { el = null; }
          if (visible(el)) { finish(i); return; }
        }
      };
      var mo = new MutationObserver(check);
      var iv = setInterval(check, 250);
      var cap = setTimeout(function () { finish(-1); }, Math.min(detectMs || 800, 25000));
      var observe = function () {
        try { mo.observe(document.documentElement || document, { childList: true, subtree: true, attributes: true }); } catch (e) {}
        check();
      };
      if (document.documentElement) observe();
      else document.addEventListener("DOMContentLoaded", observe, { once: true });
    });
  }

  // Sends a richer fallback snapshot now, then the FINAL snapshot once the DOM has
  // been structurally quiet for QUIET_MS (or at the cap). Observes content only
  // (childList + characterData) — NOT attributes — so animations don't pin it open.
  function stableThenSnapshot(maxMs) {
    var QUIET_MS = 500;
    var MAX_MS = Math.min(maxMs || 5000, 25000);
    snapshot(false);
    var done = false;
    var quiet;
    var cap;
    var mo;
    var finish = function () {
      if (done) return;
      done = true;
      try { mo.disconnect(); } catch (e) {}
      clearTimeout(quiet);
      clearTimeout(cap);
      snapshot(true);
    };
    mo = new MutationObserver(function () {
      clearTimeout(quiet);
      quiet = setTimeout(finish, QUIET_MS);
    });
    try { mo.observe(document.documentElement || document, { childList: true, subtree: true, characterData: true }); } catch (e) {}
    quiet = setTimeout(finish, QUIET_MS);
    cap = setTimeout(finish, MAX_MS);
  }

  // Runs one wait mode (keyword or CSS selector) and produces the final snapshot.
  function applyWait(mode, settleMs) {
    if (mode === "domcontentloaded") {
      onceDOMReady(function () { snapshot(true); });
    } else if (mode === "networkidle") {
      onLoad(function () { snapshot(true); });
    } else if (mode === "stable" || !mode) {
      onceDOMReady(function () { stableThenSnapshot(settleMs); });
    } else {
      waitSelector(mode).then(function () { stableThenSnapshot(settleMs); });
    }
  }

  // Reliable fallback FIRST, before any await — this must always reach the app.
  onceDOMReady(function () { snapshot(false); });

  (async function () {
    var spec = {};
    try { spec = JSON.parse(await browser.runtime.sendMessage({ kind: "spec" })) || {}; } catch (e) {}
    var waitFor = spec.waitFor || "stable";
    var settleMs = spec.settleMs;
    var rules = Array.isArray(spec.waitRules) ? spec.waitRules : [];
    var detectMs = spec.detectMs;
    includeScripts = spec.includeScripts === true;
    includeStyles = spec.includeStyles === true;

    if (rules.length) {
      onceDOMReady(async function () {
        var idx = await matchFirstRule(rules, detectMs);
        matchedRule = idx;
        if (idx >= 0) {
          snapshot(false); // record the matched index even if the target later times out
          applyWait(rules[idx].then, settleMs);
        } else {
          applyWait(waitFor, settleMs); // fallback
        }
      });
    } else {
      applyWait(waitFor, settleMs);
    }
  })();
})();
