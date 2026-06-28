// Runs in every top-level page. On load it reads the full HTML and hands it to
// the background script via fast intra-extension messaging. The background (a
// persistent context, unlike this page) performs the native-messaging send to
// the app — the page context is torn down too quickly for a native round-trip.
(function () {
  function send() {
    try {
      browser.runtime.sendMessage({
        kind: "page",
        url: location.href,
        title: document.title,
        html: document.documentElement.outerHTML,
      });
    } catch (e) {
      // Background not ready / page gone; the app falls back to an extract timeout.
    }
  }

  if (document.readyState === "complete") {
    send();
  } else {
    window.addEventListener("load", send, { once: true });
  }
})();
