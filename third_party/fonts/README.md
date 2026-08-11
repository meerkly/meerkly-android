# Bundled fonts

## Fraunces

`app/src/main/res/font/fraunces.ttf` — the variable font from
https://github.com/google/fonts/tree/main/ofl/fraunces

This is the brand's display face: `account-meerkly-com`'s Tailwind theme sets
`--font-display: "Fraunces"`, and before this the Android app fell back to
`FontFamily.Serif` (Noto Serif on Android), so the app and the website did not
look like the same product.

The **variable** file is bundled rather than static cuts because Fraunces'
`opsz` (optical size) axis matters a lot for a display face, and it lets the
theme pin `opsz` to the display end without shipping several files. Variable
fonts need API 26+, which is exactly this app's `minSdk`.

Licensed under SIL OFL 1.1 — see `Fraunces-OFL.txt`. Redistribution requires
shipping that license, which is why it lives here in the repo.
