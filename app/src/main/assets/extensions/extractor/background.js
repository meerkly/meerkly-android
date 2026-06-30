// Persistent extension context. Relays the content script's "spec" request to
// the app (returning the reply — must be a primitive, see GeckoBrowserManager)
// and forwards captured page HTML to the app via native messaging.
browser.runtime.onMessage.addListener(async (msg) => {
  if (!msg) return;
  if (msg.kind === "spec") {
    return await browser.runtime.sendNativeMessage("meerkly", { kind: "spec" });
  }
  if (msg.kind === "page") {
    browser.runtime.sendNativeMessage("meerkly", msg);
  }
});
