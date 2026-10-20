# Win32 phase 12 — hôte embarqué AWT (EDT), sessions isolées sans seconde pompe

## 1. Intention et critères de réussite

Attacher des sessions kadre à un hôte **AWT** dont l'`EventDispatchThread` tourne déjà, sans seconde pompe cachée : les HWND kadre sont créés sur l'EDT (thread propriétaire enregistré par le port), les messages sont drainés par des **ticks EDT bornés**, la boucle AWT reste propriétaire et survit au détachement, deux sessions embarquées sont isolées, et aucun détachement ne poste `WM_QUIT`. JavaFX = intégration séparée future (pas de référence livrée ; glass possède sa propre pompe native). `supportedIntegrations` reste **vide** tant que le consumer et la preuve O3 ne sont pas livrés ensemble (posture D3 de la phase 8 Wayland) — l'attach est atteignable au niveau provider uniquement.

## 2. Audit et choix d'architecture

- Précédent direct : **Wayland phase 8** (`WaylandEmbeddedPump` + attach validation D1-D4) — même famille pompe-possédée-par-le-backend. AppKit diffère (pas de pompe kadre du tout).
- Windows vs Wayland : pas de fd pollable ; **la délivrance des messages est thread-affine** et l'EDT ne tourne pas de boucle `GetMessage` (le nom EDT ne prouve pas l'ownership Win32 — roadmap). Donc : ticks EDT auto-planifiés (`invokeLater`) drainant `pump.runOnce(0)` (limite 64 messages/tick), backoff 8 ms via scheduler mono-thread quand rien à dispatcher, re-tick immédiat sous charge. Jamais de `waitForMessage` bloquant sur l'EDT (affamerait les événements Java AWT), jamais de poll sur un autre thread (thread-affine).
- `KffiWin32HostPort` enregistre `createdThread` au constructeur : **le port créé dans `attach` sur l'EDT** fait de l'EDT le thread UI enregistré — le marshaling worker→UI existant (`Win32UiCommandQueue`, `postWake`) fonctionne sans changement.
- COM/STA : TSF (`sta-initialization` typé) et OLE drop (`OleInitialize`, `S_FALSE`/`RPC_E_CHANGED_MODE` gérés) vivent dans KFFI ; l'état d'apartment de l'EDT est celui laissé par AWT/host — publié comme restriction de topologie (§7).

## 3. Admission et registre

- Ordre de refus (miroir Wayland D1) : intégration ≠ `AwtEventDispatchThread` → `Unsupported(HostAttach)` ; thread appelant ≠ EDT (sonde par réflexion `EventQueue.isDispatchThread`, zéro dépendance AWT dans le runtime) → `InvalidRequest("options")` ; indisponible → `Unsupported(HostAttach)`.
- **Registre de possession** (`Win32HostOwnership`, remplace l'intérieur de `Win32HostLease`) : standalone XOR N embarqués (mutuelle exclusion dans les deux sens, style AppKit) — la gate exige ≥2 sessions embarquées concurrentes, une lease exclusive embarquée l'interdirait.
- `onLastWindowClosed = null` : la fermeture de la dernière fenêtre n'arrête PAS une session embarquée (gate).
- `supportedIntegrations` reste `emptySet()` dans cette tranche.

## 4. Session embarquée

- Driver réel + composants réels (ports display/raw/gamepad/capture) sur l'EDT ; `initialLifecycleState` = (Attached, Background, Inactive) ; observation de lifecycle hôte = tranche future (état initial seul, consigné).
- `sessionStopHandler` ferme la pompe ; `parentScope.invokeOnCompletion` → pompe.close + driver.close + session.teardown + port.close + désenregistrement registre — la boucle AWT n'est jamais arrêtée.
- Failure reporter : `controller.fail(...)` sur échec natif (même sémantique que standalone, sans arrêt de pompe direct).

## 5. Matrice de réception et gates

| Contrat proposé | Scénarios essentiels | Sentinelle |
| --- | --- | --- |
| W32-029 — admission embarquée | sonde EDT (refus hors EDT), intégration refusée normative, registre standalone↔embarqués, deux sessions isolées | win32-embedded-no-relocation |
| W32-030 — session embarquée vivante | ticks EDT bornés, fenêtres réelles de deux sessions coexistantes, fermeture d'une session préserve l'autre et la boucle hôte, dernier fenêtre ≠ arrêt, détachement sans `WM_QUIT` | win32-embedded-no-wm-quit |

Tests portables O2 (gate) : attach hors-EDT refusé, mauvaise intégration refusée, registre (standalone refusé si embarqués, embarqué refusé si standalone), pompe bornée/coalescée. Preuve O3 AWT (JFrame hôte réel, deux sessions réelles sur l'EDT via `provider.attach`, isolation + survie + détachement) : tournée **localement** (verte) mais hors gate CI — le runner ne garantit pas la boucle EDT/bureau interactif qu'exige la preuve ; elle passe au cahier de qualification avec le consumer `integration:awt`, conformément à la règle « une intégration n'est déclarée qu'avec son consumer et sa preuve O3 ». Charter manuel : app AWT réelle, attention, DPI.
Tests portables O2 : attach hors-EDT refusé, mauvaise intégration refusée, registre (standalone refusé si embarqués, embarqué refusé si standalone), pompe bornée/coalescée. Natif O3 (gate Windows) : JFrame hôte réel, deux sessions réelles sur l'EDT via `provider.attach`, isolation + survie + détachement. Charter manuel : app AWT réelle, attention, DPI.

## 6. Budgets et teardown

Tick ≤ 64 messages ; backoff 8 ms ; teardown via `invokeOnCompletion` idempotent ; aucune fuite de contrôle window (teardown existant).

## 7. Hors périmètre de cette tranche

- **JavaFX** : intégration séparée (glass = pompe native distincte, qualification propre).
- **Observation de lifecycle hôte** (foreground/hide) : tranche suivante — état initial seul.
- **Consumer `integration:awt`** et bascule `supportedIntegrations` : livraison séparée avec sa preuve.
- **Apartment de l'EDT** : publié comme restriction (TSF/drop peuvent refuser selon l'hôte) — pas de conversion cachée.
