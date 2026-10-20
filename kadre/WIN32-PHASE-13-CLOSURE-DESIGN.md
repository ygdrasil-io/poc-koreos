# Win32 phase 13 — fermeture contractuelle : robustesse, consumers, cahiers

## 1. Intention

Fermer le contrat win32 : preuves de robustesse sur cycles répétés (mirroring de la phase 11 Wayland), qualification de réutilisation et classloaders, consumer réel via l'umbrella publié et la découverte ServiceLoader, capabilities complètes et mesurées.

## 2. Contenu de la tranche

- **Stress natif** (`Win32StressNativeContractTest`, tâche dédiée `win32StressTests`, O3 réel) : N cycles complets `provider.run` (50 sessions, mirroring `WaylandStressLinuxTest.repeatedTeardownStaysBoundedAndLeakFree`), churn de fenêtres en rafales avec comptage de handles, interleave standalone↔embarqué sur `Win32HostOwnership`, réutilisation du broker raw input process-wide entre sessions. Nettoyage : retrait du `println("DBG ...")` résiduel (`KffiWin32HostPort.kt`), `SessionRawInputTarget.port` également délié sur la voie embarquée.
- **Classloader** (`Win32ClassloaderReuseTest`) : échange du context classloader entre sessions séquentielles — la découverte ServiceLoader (`DesktopProviderCatalog`) et les brokers process-wide ne retiennent aucun loader sans owner vivant.
- **Consumer** : publication de `:kadre:backend:win32` (+ kffi-win32) dans `publishContractArtifacts` ; consumer `consumers/kotlin` étendu d'un test **exécuté** sur Windows : découverte ServiceLoader → session standalone headless → `SessionOutcome`. Clôture le résidu wayland-D4 partagé.
- **Closure docs** : réparation de la table `capabilities/win32.md` (ligne embedded hors table, doublon fullscreen exclusif), mesure des durées de gate dans `run_phase` (logs + p95 TEST-STRATEGY), `WIN32-PHASE-13-MANUAL.md` consolidant les réceptions manuelles ouvertes et les limites.

## 3. Contrats

| Contrat | Scénarios essentiels | Sentinelle |
| --- | --- | --- |
| W32-031 — robustesse sur cycles | 50 cycles standalone, churn fenêtres, interleave standalone↔embarqué, réutilisation broker | win32-stress-handle-bound |
| W32-032 — réutilisation et classloaders | sessions séquentielles sous classloaders distincts, découverte ServiceLoader après échange, brokers sans loader retenu | win32-reuse-no-stale-loader |

Oracle O3 (cycles réels sur Windows) ; tâche dédiée à JVM isolée (même discipline que `win32EmbeddedTests`).

## 4. Hors périmètre

Consumer Java exécuté (compile-only conservé), samples riches (input/capture/embedded) au-delà de hello-kadre adapté, retrait des références historiques (après audit de couverture de la 13).
