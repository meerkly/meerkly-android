// Persistent extension context. Relays the content script's "spec" request to
// the app (returning the reply — must be a primitive, see GeckoBrowserManager)
// and forwards captured page HTML to the app via native messaging.
//
// It also tracks the crawled page's main-document HTTP status (drives credits):
// content scripts can't see response codes, so we observe them here via
// webRequest. `lastMainFrameStatus` holds the latest top-level navigation's
// status — reset when a new main_frame request starts (clearing the prior
// crawl's value) and set from the response headers. Server redirects stay on one
// requestId (onBeforeRequest fires once), so the final hop's status wins; a
// client-side redirect is a fresh navigation that resets then re-captures.
// If webRequest is unavailable (some GeckoView builds), the status stays 0 ⇒ the
// crawl earns 0 credits rather than a false payout.
var lastMainFrameStatus = 0;

if (typeof browser !== "undefined" && browser.webRequest) {
  var mainFrameFilter = { urls: ["<all_urls>"], types: ["main_frame"] };
  try {
    browser.webRequest.onBeforeRequest.addListener(function () {
      lastMainFrameStatus = 0;
    }, mainFrameFilter);
    // onHeadersReceived carries statusCode and fires before the body/DOM is
    // parsed, so the status is known by the time snapshots are sent.
    browser.webRequest.onHeadersReceived.addListener(function (d) {
      lastMainFrameStatus = d.statusCode || 0;
    }, mainFrameFilter);
  } catch (e) {}
}

browser.runtime.onMessage.addListener(async (msg) => {
  if (!msg) return;
  if (msg.kind === "spec") {
    return await browser.runtime.sendNativeMessage("meerkly", { kind: "spec" });
  }
  if (msg.kind === "page") {
    msg.httpStatus = lastMainFrameStatus;
    browser.runtime.sendNativeMessage("meerkly", msg);
  }
});
