# Wayland Phase 0 — Décisions contractuelles

## But

Fermer les décisions de la Phase 0 (`WAYLAND-IMPLEMENTATION-ROADMAP.md` §5, item 3) qui conditionnent la première verticale : le défaut `WindowDecorations.System` sur Wayland (D1), la frontière renderer/commit/lease de la surface (D2) et la représentation du lifecycle sans observation inventée (D3). Ce document ne modifie ni l'API publique, ni `DESIGN.md`, ni le statut des capabilities : il précise la lecture Wayland des contrats existants. La spécification du probe `isAvailable()` (règles R1-R8) complète ce document dans sa section dédiée.

Exclusions : toute sémantique de session (failures avant session, panne après admission, fermeture, scénarios) vit dans `WAYLAND-PHASE-1-HOST-DESIGN.md` ; les planschers d'environnement vivent dans `WAYLAND-PHASE-0-KFFI-AUDIT.md` §Baseline.

## Préconditions KFFI

- `KFFI-WAYLAND-001` (connexion par fd) : conditionne le support `WAYLAND_SOCKET` au run (règle R7 du probe) ; sans elle, un run avec fd transmis échoue avant session — D1-D3 n'en dépendent pas.
- D1 dépend de la négociation xdg-decoration, couverte par les primitives existantes de kffi (interface `zxdg_decoration_manager_v1` générée, audit §verdicts domaine 10) — aucun gap à ouvrir pour lever D1 en phase 5.

## D1 — Défaut `WindowDecorations.System` sur Wayland

Le défaut public du catalogue reste `WindowSpec.decorations = WindowDecorations.System` (`PUBLIC-API-CATALOG.md:271`) ; il n'est pas changé par backend. Sur Wayland, toute configuration exigeant `System` exige une négociation xdg-decoration côté serveur effectivement conclue en mode serveur. Tant que cette négociation n'est pas prouvée sur le compositor courant :

- la demande est refusée exactement — outcome `Unsupported(Decorations)` sur le verbe de création concerné — sans repli silencieux vers `Borderless`, sans chrome dessiné par Kadre, sans « succès » approximatif (reprise verbatim de la limite §3.5 de la roadmap) ;
- conséquence assumée et documentée : jusqu'à levée de la limite (négociation prouvée en phase 5), les `WindowSpec` Wayland passent `Borderless` explicitement ;
- la matrice §4 de `BACKEND-CAPABILITIES.md` ne porte aucune ligne « decorations » : rien à réconcilier ; l'état livré par phase sera enregistré dans `kadre/capabilities/wayland.md` à partir de la phase 1 (topologie §4).

## D2 — Frontière renderer/commit/lease

Un seul écrivain de l'état pending d'une `wl_surface` : le peer de surface du runtime. Table d'ownership :

| Opération | Owner | Contrat |
|---|---|---|
| `attach`, `commit`, frame callbacks, `set_buffer_scale`, viewport | runtime (peer de surface) | aucun second composant n'écrit l'état pending ; double commit impossible par construction, vérifié par trace |
| buffers du rendu | renderer externe | il possède ses buffers ; Kadre ne dessine rien, ne possède aucun buffer de présentation |
| `DesktopNativeWindowHandle.Wayland(displayAddress, surfaceAddress)` | lease de callback | adresses valides uniquement pendant l'appel de `Window.withDesktopHandle`, sur le thread host ; rien n'est retenu après le retour |
| invalidation du lease | runtime | le lease est révoqué AVANT la destruction des proxies ; une course lease/close voit un lease invalidé, jamais une surface libre après destruction |

Interdits verbatim (roadmap §3.4) : aucune API `present` publique, aucun handle permanent ni lease public, aucune promesse d'intégration renderer au-delà du contrat actuel — un renderer qui retient les adresses après retour viole le contrat, et cette violation est documentée comme telle (comportement indéfini côté renderer, jamais un crash du runtime). La seule frontière publique reste `Window.withDesktopHandle` ; tout amendement (lease étendu, handle permanent) exige une décision normative préalable, pas une extension discrète.

## D3 — Représentation du lifecycle sans observation inventée

Le mapping normatif `DESIGN.md:337-343` (ligne Desktop `Standalone`) est relu sur Wayland avec, pour chaque axe, la source protocolaire réelle — jamais une observation synthétisée :

| Axe (DESIGN.md) | Source Wayland (observation compositor) | À défaut de source |
|---|---|---|
| `Active`/`Inactive` | état `activated` de `xdg_toplevel` configuré | session headless sans fenêtre : `Inactive` fixe |
| `Foreground`/`Background` | `wl_surface.enter`/`leave` d'output — la surface est mappée sur au moins un output ; c'est le seul fait protocolaire disponible, ce n'est PAS une mesure de visibilité utilisateur | jamais d'`enter` reçu (headless) : `Background` fixe |
| `Detached` | teardown du host / arrêt standalone | — |

Règles : aucune occlusion ou visibilité « inventée » — aucun événement de visibilité synthétisé, aucune heuristique de recouvrement ; les transitions publiées ne proviennent que des sources ci-dessus, dédupliquées conformément à `DESIGN.md:331` (snapshots atomiques valides, `Background` publie toujours `Inactive` auparavant ou dans le même snapshot). La ligne « lifecycle à trois axes = G » de la matrice §4 reste valide : ce sont les sources des axes qui sont définies ici, pas une rétraction de garantie. Le signal de pression mémoire n'a pas de primitive Wayland : `LifecycleCapabilities.memoryPressure` sera `Unsupported` sur ce backend (colonne `C` de la matrice — pilotée à runtime — sans contradiction).
