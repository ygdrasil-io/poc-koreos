# New Kadre Desktop Provider Implementation Plan

> **For agentic workers:** use `superpowers:executing-plans` and `superpowers:test-driven-development`. Sub-agents are forbidden in this side-conversation workspace.

**Goal:** remplacer les stubs Desktop par une sélection de backend déterministe et paresseuse, sans charger AppKit/KFFI ni implémenter encore une boucle native.

**Stack:** cette PR part de `codex/runtime-kernel`, publie `codex/desktop-provider` et cible la PR 3.

**Architecture:** `runtime` définit le SPI JVM technique que les artifacts `backend:*` peuvent implémenter sans dépendre de `platform:desktop`. `platform:desktop` reste propriétaire des enums/options publics, détecte la famille OS, traduit vers le SPI, découvre les providers avec `ServiceLoader` et délègue exactement une fois au provider sélectionné. Le catalogue par défaut peut être process-wide et paresseux, mais ne contient aucune session courante.

## Contraintes globales

- JVM 25 et metadata common uniquement.
- Aucun backend, SDK, KFFI ou binding n'entre dans cette PR.
- Aucun fallback après sélection ou après appel du provider.
- Les providers techniques sont `public` uniquement dans `org.graphiks.kadre.internal.*` pour traverser les artifacts ; aucun de leurs types ne fuit dans l'ABI Desktop contractuelle.
- Les tests injectent OS et catalogue de providers ; ils ne dépendent ni de la machine CI ni de `ServiceLoader` global.
- Les erreurs de découverte ou exceptions provider deviennent une `PlatformFailure` stable ; une `CancellationException` reste une cancellation.
- La gate reste limitée à `:kadre-new:` et ignore les tests historiques.

## Task 1 — SPI backend technique dans runtime

**Files:** `runtime/.../desktop/DesktopBackendProvider.kt`, tests de compilation runtime.

1. Définir les kinds AppKit/Win32/X11/Wayland et AppKit/AWT/JavaFX internes à la liaison.
2. Définir les requêtes embedded/standalone transportant factory, policy et ownership requis.
3. Définir `DesktopBackendProvider` avec backend, integrations supportées, attach embedded et run standalone.
4. Garder toute déclaration dans le namespace artifact interne et hors du catalogue public.

## Task 2 — Détection OS et sélection pure

**Files:** `platform/desktop/.../DesktopEnvironment.kt`, `DesktopProviderSelector.kt`, tests.

1. Normaliser `os.name` en macOS, Windows, Linux ou unsupported.
2. Refuser avant découverte un backend explicitement incompatible avec l'OS via `InvalidRequest("options")`.
3. Pour `Auto`, utiliser un ordre fermé : AppKit sur macOS, Win32 sur Windows, Wayland puis X11 sur Linux.
4. Sélectionner indépendamment de l'ordre retourné par le catalogue.
5. Retourner `Unsupported(HostAttach)` si aucun provider compatible n'est présent et une failure stable si le provider sélectionnable est ambigu.

## Task 3 — Catalogue ServiceLoader paresseux

**Files:** `platform/desktop/.../DesktopProviderCatalog.kt`, tests avec classloader isolé si utile.

1. Charger `DesktopBackendProvider` uniquement au premier attach/run réel et sonder sa disponibilité sans initialiser le SDK natif.
2. Mettre en cache la liste immutable sans état de session.
3. Ne pas initialiser KFFI depuis le catalogue ; cette responsabilité sera prouvée avec le provider AppKit dans la PR suivante.
4. Traduire `ServiceConfigurationError` et autres erreurs de découverte en `PlatformFailure` sans fuite d'exception.

## Task 4 — Façade Desktop

**Files:** `DesktopHost.kt`, `DesktopHostFacade.kt`, tests Desktop.

1. Faire déléguer les deux overloads embedded au même chemin factory.
2. Traduire options et policy sans les modifier.
3. Faire lever `KadreException` par le runner seulement sur `Failure` avant session ; retourner le `SessionOutcome` fourni sur `Success`.
4. Ne jamais appeler un second provider après une failure ou exception du premier.
5. Normaliser toute failure provider hors du domaine HostAttach en `PlatformFailure`.

## Task 5 — Preuves et ABI

**Files:** tests Desktop, `contracts/registry/contracts.tsv`, ABI Desktop.

1. Couvrir macOS Auto/AppKit, provider absent, backend OS incompatible, integration incompatible, doublon, exception et failure provider.
2. Couvrir Windows et priorité Linux sans dépendre de l'OS courant.
3. Prouver une délégation unique et la conservation factory/policy/options.
4. Activer uniquement les contrats de sélection réellement prouvés.
5. Mettre à jour puis vérifier le dump ABI Desktop ; aucune signature publique documentée ne doit changer.

## Task 6 — Audit et PR empilée

1. Exécuter `:kadre-new:platform:desktop:jvmTest`, la validation ABI puis la gate `:kadre-new:check` avec `--rerun-tasks`.
2. Vérifier les metadata common/JVM 25 et l'absence de SDK/KFFI/anciens imports.
3. Pousser `codex/desktop-provider` et ouvrir la PR contre `codex/runtime-kernel`.

## Hors scope

- provider ou artifact `backend:appkit` réel ;
- appels KFFI/Objective-C ;
- vérification du main thread et main loop AppKit ;
- création de session standalone/embedded réelle ;
- fenêtres, surfaces et input ;
- AWT, JavaFX, Win32, X11 ou Wayland réels.
