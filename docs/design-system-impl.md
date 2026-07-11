# Design system implementation (C-late)

The neo-brutal visual system from `nachweis-docs/wiki/design-tokens.md` (normative) and
`design-system.md` (direction), realised on the app's real UI state contracts. C-early defined the
spec; this is the running Compose implementation.

## Theme

`ui/theme/` binds the tokens to Material 3:

- `Color.kt` — the palette (paper/ink/blue/teal/over/amber/yellow/steel/grey + lightened dark
  variants).
- `Theme.kt` — `NachweisTheme`: light/dark `ColorScheme`, `NachweisTypography`, `NachweisShapes`,
  and the `NachweisColors`/`LocalReducedMotion` composition locals. **No dynamic colour** — the
  brand identity must not be repainted by Material You (design-system.md). Surfaces are all paper
  (dark: the dark surface); separation is a 4dp ink border, never a tonal tint, so every
  `surfaceContainer*` slot stays the base surface.
- `NachweisColors.kt` — the roles M3 lacks (success/warning containers, selection) plus the two
  structural constants (`borderWeight` 4dp, `shadowOffset` 6dp), as a `staticCompositionLocalOf`.
- `Shape.kt` — every shape slot 0dp (hard edges).
- `Type.kt` — the three bundled faces mapped to the `Typography` slots; `MonoTextStyle` for machine
  values.
- `Motion.kt` — motion durations and the reduced-motion signal.

## Fonts

Bundled as OFL TTFs in `res/font` (no runtime fetch — a wallet must not phone home for fonts):

| Face | File | Role |
|---|---|---|
| Bricolage Grotesque (variable) | `bricolage_grotesque.ttf` | display / title |
| Familjen Grotesk (variable) | `familjen_grotesk.ttf` | body / label |
| Space Mono (static R+B) | `space_mono_regular.ttf`, `space_mono_bold.ttf` | claim paths, hashes, DCQL |

Bricolage and Familjen are variable fonts; each `FontFamily` entry sets the `wght` axis explicitly
via `FontVariation.Settings(FontVariation.weight(…))`, so one TTF serves every weight. Licenses are
under `assets/licenses/fonts/` (SIL OFL 1.1); all three are OFL, sourced from the Google Fonts
repository.

## Distinctive elements (the neo-brutal layer)

- `components/NeoSurface.kt` — the building block: a rectangular filled face with a 4dp ink border
  over a hard, blur-free offset shadow. `NeoClickableSurface` adds the press-offset (the face slides
  into the shadow on press); under reduced motion the slide is instant.
- `components/NeoButton.kt` — `PrimaryButton` (blue fill, paper label), `SecondaryButton` (paper
  fill, ink label). Grey disabled fill with ink label; ≥48dp target; announced as dimmed when
  disabled.
- `components/StatusChip.kt` — the fixed status vocabulary with **drawn** glyphs (Canvas, not an
  icon font) so shapes differ by outline alone: check, warning triangle, failure octagon-x,
  outside-registration slash-circle, selected filled dot, pending outline dot.
- `components/StatusBanner.kt` — the D1 consent verdict banner: status-hue fill, ink text and glyph,
  ink border, polite live region.
- `components/NeoComponents.kt` — `CredentialCard`, `EmptyState`, `ErrorState`.

Applied on `DocumentListScreen` (cards, empty state, primary action), `WalletScreen` (the consent
verdict banner + mono claim paths; the consent surface itself stays sober — no press choreography,
per §6.3), `ScanScreen` (buttons), and `HomeScreen` (display type).

## Accessibility (the binding law)

- **Contrast:** chromatic hues are only ever fills; text on a hue fill is always ink (light) — e.g.
  the outside-registration banner is ink on over-red (5.48:1). Error/verdict *text on paper* uses
  the deep red (`OverDeep`, 4.91:1), never bright over (3.28:1).
- **Non-colour cues:** every status carries a distinct glyph shape + label; unambiguous in
  grayscale / under red-green colour blindness.
- **Targets:** ≥48dp on every interactive element.
- **TalkBack:** cards/buttons/banners carry content descriptions; the verdict banner is a polite
  live region; decorative glyphs are cleared from the semantics tree.
- **Font scaling:** all `sp`; claim rows wrap, consent scrolls, nothing truncates.
- **Reduced motion:** `LocalReducedMotion` is derived from the system animator duration scale; when
  animations are off, the press-offset degrades to an instant state change.

## The D1 labeling law

The consent verdict banner shows exactly **"outside this verifier's registration"** for
`RegistrationVerdict.OutsideRegistration`; the phrase "over-ask" never appears in user-facing copy.
Guarded by `StatusVocabularyTest` (JVM) and `DesignSystemInstrumentedTest` (device).

## Screenshot evidence

Captured on Pixel 8a API 35 from the debug-only `PreviewGalleryActivity`
(`app/src/debug`, never shipped). Launch with:

```
adb shell am start -n com.quellkern.nachweis.demo/com.quellkern.nachweis.debug.PreviewGalleryActivity
```
