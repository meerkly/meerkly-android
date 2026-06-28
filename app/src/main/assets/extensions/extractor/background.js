// Persistent extension context. Receives page payloads from content scripts and
// forwards them to the native app (Kotlin) over native messaging. Doing the
// native send here (not from the content script) avoids "context unloaded"
// failures: this background context outlives any individual page.
browser.runtime.onMessage.addListener((msg) => {
  if (msg && msg.kind === "page") {
    browser.runtime.sendNativeMessage("meerkly", msg);
  }
});
