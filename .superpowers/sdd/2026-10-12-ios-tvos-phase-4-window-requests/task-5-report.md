# Task 5 report — Docs closure (phase 4 UIKit window requests)

Worktree: /Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f (branch zcode/sess-56645e3f, base daddcb8d)
Status: DONE
Commit: (see below — single commit `docs: close the phase 4 UIKit window-request perimeter`)

Three files edited, nothing else. `capabilities/uikit.md` (Task 4) untouched. `.superpowers/` is gitignored, so the commit contains only the three docs.

---

## 1. kadre/DESIGN.md — §15.2 progression bullet (line 1906)

**Anchor:** the last bullet of §15.2 « UIKit et SwiftUI », immediately after « Le host, et non un singleton Kadre, conserve la factory et corrèle le `WindowRequestId` avec les options de connexion de scène. » and before « ### 15.3 Web ».

**Old (verbatim):**
```
- L’attach, le lifecycle par scène et les propriétaires sont livrés (phase 1) ; les observations dynamiques de surface (métriques, apparence, visibilité — re-lectures sur déclencheurs SDK réels), le redraw coalescé, l’apply fermé (les quatre champs update refusés `Unsupported(UpdateSurface)`) et le bail natif `withUIKitView` sont livrés (phase 2) ; la façade Swift (phase 3) et les demandes de scènes (phase 4) restent à livrer.
```

**New (verbatim):**
```
- L’attach, le lifecycle par scène et les propriétaires sont livrés (phase 1) ; les observations dynamiques de surface (métriques, apparence, visibilité — re-lectures sur déclencheurs SDK réels), le redraw coalescé, l’apply fermé (les quatre champs update refusés `Unsupported(UpdateSurface)`) et le bail natif `withUIKitView` sont livrés (phase 2) ; la façade Swift est livrée (phase 3) ; les demandes de scènes `requestWindow` sont livrées (phase 4) — `Window.apply`, l’attention et la fermeture de fenêtres enfants restent hors périmètre : une fenêtre enfant n’est jamais une `Window` Kadre.
```

Untouched in the same section: the tvOS mono-scène bullet (l.1903 « le parcours tvOS est mono-scène : une seule session par application, aucune seconde `UIWindowScene` demandée »), the code block, and all other bullets.

## 2. kadre/BACKEND-CAPABILITIES.md — §6.2 (after the « Livré en phase 1/2 » paragraph, line ~239)

**Anchor:** the paragraph beginning « Livré en phase 1 : » in §6.2 UIKit, followed by the « Disponibilité tvOS : » paragraph.

**Old (verbatim):** the phase-1/2 paragraph ended with the stale sentence:
```
Les demandes de scènes (`requestWindow`) restent couvertes par la phase 4.
```
That sentence was removed (the phase-1/2 text itself is unchanged) and a new paragraph inserted between it and « Disponibilité tvOS : ».

**New paragraph (verbatim):**
```
Livré en phase 4 : les demandes de scènes `requestWindow` par activation corrélée de scène. `UIKitWindowSceneProvider` reste app-owned — l’app, seule à connaître sa topologie de scènes, active une `UIWindowScene` corrélée au `WindowRequestId` et résout via le `UIKitWindowSceneRequestResolver` reçu à l’admission ; le succès attache une session enfant complète et indépendante (sa propre scène, surface et lifecycle — la fermeture du requérant n’atteint pas l’enfant) et termine la demande `OpenedInNewSession`. Sans provider, comme sur tvOS mono-scène, `requestWindow` retourne `Rejected(Unsupported(RequestWindow))` sans aucun effet natif. `Window.apply`, l’attention et la fermeture de fenêtres enfants restent hors périmètre : une fenêtre enfant n’est jamais une `Window` Kadre.
```

Untouched: the « Disponibilité tvOS : » paragraph (its « `requestWindow` y retourne un état `Rejected(Unsupported(RequestWindow))` (livré en phase 4) » is now literally true) and the providerless contract paragraph.

## 3. kadre/PUBLIC-API-CATALOG.md — §2 index row, line 30

**Anchor:** the `org.graphiks.kadre.platform.uikit` row of the « Index fermé par package » table, between the android and web rows.

**Old (verbatim):**
```
| `org.graphiks.kadre.platform.uikit` | `KadreIos.attach`; callback `withUIKitView` (disponibilité iOS/iPadOS et tvOS) |
```

**New (verbatim):**
```
| `org.graphiks.kadre.platform.uikit` | `KadreIos.attach` (+ `windowSceneProvider` optionnel); `UIKitWindowSceneProvider`, `UIKitWindowSceneRequestResolver` (demandes de scènes iOS/iPadOS; `requestWindow` reste refusé sur tvOS); callback `withUIKitView` (disponibilité iOS/iPadOS et tvOS) |
```

Names verified against the shipped code: `UIKitWindowSceneProvider` (public fun interface, `open(requestId, spec, resolver)`) and `UIKitWindowSceneRequestResolver` (public class, `resolve`/`abandon`) in kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/UIKitWindowSceneProvider.kt; `windowSceneProvider: UIKitWindowSceneProvider? = null` is the last parameter of `KadreIos.attach` (KadreIos.kt l.109). Catalog §1 rule (« union des déclarations listées ici et des signatures imprimées dans DESIGN.md ») respected; member `open` needs no top-level entry, same convention as `WebWindowProvider`.

---

## 4. Grep verification outputs

**No stale/future-phase claims (exit 1 = no matches):**
```
$ grep -n "phase 5\|phase 6\|restent à livrer\|reste à livrer\|restent couvertes par la phase" DESIGN.md BACKEND-CAPABILITIES.md PUBLIC-API-CATALOG.md
(exit 1)
```
Global sweep across all docs (same patterns + « demandes de scènes (phase 4) restent »): no matches.

**Remaining « phase 4 » mentions — all accurate:**
- BACKEND-CAPABILITIES.md:194 — Web phase-4 row (unrelated, untouched).
- BACKEND-CAPABILITIES.md:241 — the new Livré en phase 4 paragraph.
- BACKEND-CAPABILITIES.md:243 — tvOS paragraph, « (livré en phase 4) » now literally true.
- DESIGN.md:1906 — the updated progression bullet.

**tvOS mono-scène statements intact (no weakening):**
```
BACKEND-CAPABILITIES.md:243  Disponibilité tvOS : … topologie mono-scène ; `requestWindow` y retourne … `Rejected(Unsupported(RequestWindow))` (livré en phase 4). … une preuve iOS ne couvre pas tvOS.
PUBLIC-API-CATALOG.md:1012   Les points d’attachement UIKit s’appliquent à iOS/iPadOS et tvOS ; le parcours tvOS est mono-scène.
DESIGN.md:1903               … le parcours tvOS est mono-scène : une seule session par application, aucune seconde `UIWindowScene` demandée.
```
The new texts only reinforce them (catalog row: « `requestWindow` reste refusé sur tvOS »; §6.2: « comme sur tvOS mono-scène … sans aucun effet natif »).

**Diff stat:** `kadre/BACKEND-CAPABILITIES.md | 4 +++-`, `kadre/DESIGN.md | 2 +-`, `kadre/PUBLIC-API-CATALOG.md | 2 +-` — 5 insertions, 3 deletions, nothing else.

## 5. Self-review

- Scope: only the three requested edits; DESIGN §15.2 code block untouched (its attach signature predates the optional param and was deliberately left alone — the brief scopes the §15.2 edit to the progression bullet only); capabilities/uikit.md untouched.
- Requirements coverage: window requests livrés (phase 4) with attention/close hors périmètre (DESIGN bullet + §6.2 paragraph, shared closing clause verbatim from Task 4's vocabulary « une fenêtre enfant n'est jamais une `Window` Kadre »); façade Swift (phase 3) déjà livrée; provider app-owned + activation corrélée + sessions enfants indépendantes + refus tvOS sans effet natif (§6.2); provider + resolver in the catalog row with the tvOS refusal qualifier.
- No phase-5+ item claimed present; no tvOS statement weakened.
- French typography preserved (U+2019 apostrophes, espaces fines before « : » and « ; » consistent with surrounding text).
