# Rapport de la vague finale de correctifs fullscreen AppKit

Date : 2026-08-31  
Base imposée : `2ffdd7fcd1d7aad5e2b8e12e5632675c8ad92360`  
Branche locale : `codex/appkit-phase-5-fullscreen-design`  
État : correctifs C1, C2, I1, I2, I3 et I4 implémentés et vérifiés ; aucun push.

## Sources d'autorité et limites respectées

La vague a été conduite contre :

- `kadre/APPKIT-PHASE-5-WINDOW-FULLSCREEN-DESIGN.md` ;
- `kadre/implementation-plans/2026-08-30-appkit-phase-5-window-fullscreen-stack.md` ;
- le ledger `.superpowers/sdd/2026-08-30-appkit-phase-5-window-fullscreen-stack/progress.md` ;
- le paquet de review `review-42a366ab..2ffdd7fc.diff`.

Les invariants demandés restent inchangés : aucun terminal
`WindowUpdateOutcome.Accepted`, aucun binding KFFI réécrit, callbacks ObjC
rejoués par la queue AppKit, selector généré conservé, normalisation temporaire
du level conservée, capability publique limitée à `Borderless`, ouverture
initiale `Borderless`/`Exclusive` toujours rejetée avant peer, harness manuel
hors CI/evidence, et aucun override KFFI ajouté au repository Kadre.

## C1 — admission et frontière de commit réelles

### Cause racine

`RuntimeWindowManager` passait la barrière de `PreparedLocal` à
`InvokingSelector` dès le retour de `WindowCommandPort.requestUpdate`. Pour le
driver AppKit asynchrone, ce retour signifiait seulement « commande enfilée » :
le code n'avait pas encore atteint `AppKitWindowMutationCommit.beforeFirstSetter()`.
Une cancellation pendant l'attente dans la queue était donc traitée comme trop
tardive. Un `Will` externe déjà enfilé pouvait aussi être faussement corrélé à
la mutation locale et provoquer un second toggle.

### Test rouge puis correction

Les tests déterministes
`fullscreenCancellationWhileAppKitQueueIsBusyStillWinsBeforeFirstSetter` et
`externalWillQueuedBeforeFullscreenCommitWinsWithoutADoubleToggle` ont d'abord
échoué sur l'ancien comportement.

La correction introduit une notification bridge→runtime étroite :

- l'enfilage par `requestUpdate` laisse la barrière en `PreparedLocal` ;
- `beforeFirstSetter()` marque d'abord le commit bridge puis appelle
  `WindowUpdateCommand.fullscreenSelectorInvoking()` ;
- le runtime ne passe à `InvokingSelector` qu'à cet instant ;
- si le runtime refuse ce passage parce qu'une cancellation ou un stimulus
  externe a gagné, le bridge retire la commande sans setter ni toggle ;
- une cancellation acceptée avant cette frontière retire aussi la barrière
  `PreparedLocal` ;
- un `Will`/`Did` observé avant commit bridge est routé sans operation ID comme
  transition externe, et l'entrée locale préparée est terminalisée
  `TemporarilyUnavailable(retryable = true)` ;
- le bookkeeping `dispatchedWindowUpdates` est retiré aussi lorsqu'un terminal
  non corrélé déloge une commande préparée.

Résultat : cancellation pré-setter sans toggle, et un `Will` externe
pré-selector gagne sans double toggle.

## C2 — ordre FIFO des terminaux réentrants

### Cause racine

Les callbacks ObjC étaient bien déposés dans la queue AppKit, mais une exception
du selector était convertie en `selector-threw` de façon synchrone dans le job
courant. Un `Did` ou `DidFail` réentrant, déjà admis dans la queue avant le
retour du selector, pouvait donc être dépassé.

### Test rouge puis correction

Les tests `reentrantDidWithoutWillWinsBeforeSelectorThrow` et
`reentrantDidFailWithoutWillWinsBeforeSelectorThrow` ont reproduit
respectivement un résultat erroné `selector-threw` à la place du `Did` et du
`DidFail` premiers.

Le retour du selector, succès ou exception, est désormais lui-même déposé par
`submitFollowUp`. Les callbacks réentrants déposés pendant l'appel natif sont
donc rejoués avant ce marqueur de retour. Le runtime reste en
`InvokingSelector`, bufferise le terminal, passe à `DrainingTerminals` au
marqueur, puis consomme le premier terminal FIFO. Le fallback
`selector-threw` n'est créé que si la commande est encore pendante et qu'aucun
`Will` n'a été observé.

Résultat : `Did` complète `Applied`, `DidFail` complète `enter-failed`, et
l'exception ultérieure ne remplace pas le terminal déjà admis.

## I1 — restore et readback dissociés

### Cause racine

`AppKitWindowPeer.completeFullscreen` exécutait restore puis readback dans le
même bloc. Une exception de `restoreWindowLevel` empêchait donc le readback et
était classée à tort `level-readback-failed`, avec fermeture native.

### Test rouge puis correction

Les tests
`fullscreenRestoreFailureStillPublishesSuccessfulReadbackWithoutClosing` et
`fullscreenReadbackFailureRemainsDistinctAndClosesAfterRestoreFailure` couvrent
les deux branches.

`completeFullscreen` tente maintenant toujours le readback après une exception
de restore et retourne séparément le snapshot et la restore failure :

- readback réussi : publication de l'état effectif, terminal
  `level-restore-failed`, fenêtre toujours `Open`, aucune fermeture ;
- readback réellement échoué : terminal distinct `level-readback-failed` et
  fermeture native ; la restore failure est ajoutée comme exception supprimée
  au readback failure ;
- la restore failure brute n'est reportée qu'une fois, sans ajouter un second
  diagnostic sémantique identique.

## I2 — autorité de `desiredLevel`

### Cause racine

Le runtime pouvait accepter un `Set(level)` d'intention seule, sans setter ni
révision, et mettre à jour son `desiredLevel`. Le cache `PeerEntry.desiredLevel`
du bridge ne voyait pas cette acceptation et restait sur l'ancienne valeur. La
transition externe suivante restaurait donc un level périmé.

### Test rouge puis correction

`runtimeOnlyLevelRealignmentControlsTheNextExternalFullscreenDid` a d'abord
observé les restores `[Floating, Floating]` au lieu de
`[Floating, Normal]`.

Le runtime est désormais l'autorité unique. Le sink fullscreen instable expose
une lecture étroite `desiredLevel(windowId)` ; le driver la consulte pour chaque
terminal externe et ne maintient plus de copie dans `PeerEntry`. Les commandes
locales continuent d'embarquer le `desiredLevel` figé par le runtime lors de
leur dispatch.

Résultat : le réalignement sans setter est utilisé par le `Did` externe suivant.

## I3 — rejet structurel des updates fullscreen mixtes

### Cause racine

Le runtime acceptait une mutation fullscreen accompagnée d'autres champs et le
driver appliquait les setters ordinaires avant le selector. Ce comportement
provenait d'une ancienne direction de review, mais contredisait l'autorité des
lignes 167–174 de la spec.

### Test rouge puis correction

`mixedFullscreenUpdateFailsStructureBeforeRevisionDomainAvailabilityAndBarrier`
couvre explicitement `Borderless + title` et `Exclusive + title`, avec revision
stale, disponibilité refusée, barrière externe et `title` hors capability. Le
test a d'abord reçu la failure de revision au lieu de la forme structurelle.

Le runtime rejette maintenant tout payload qui modifie `fullscreen` et un autre
champ persistant par `InvalidRequest("fullscreen")`, après la fenêtre fermée
mais avant clear/candidat, revision, domaine, disponibilité et barrière. Le
chemin bridge devenu inatteignable qui appliquait une mutation ordinaire avant
fullscreen a été supprimé. Les anciens tests consacrant les mutations mixtes
ont été supprimés ou réécrits ;
`mixedFullscreenTitleIsRejectedBeforeNativeDispatch` vérifie aussi l'absence de
setter/toggle sur la stack AppKit.

## I4 — gate CI atomique pour WIN-005 et APK-010

### Cause racine

`scripts/test-kadre-appkit-contracts.sh` ne lançait que
`generateAppKitContractEvidence` et ne vérifiait que les fichiers `APK-*`. La
stack active pourtant simultanément `WIN-005` et `APK-010`.

### Test rouge puis correction

Le driver shell a d'abord échoué avec « second phase did not generate runtime
contract evidence ». Le script réel lance maintenant, dans la même phase
evidence :

- `:kadre:contracts:validator:generateRuntimeContractEvidence` ;
- `:kadre:contracts:validator:generateAppKitContractEvidence`.

Il supprime les anciens outputs puis exige `WIN-005.json` et tous les fichiers
AppKit, dont `APK-010.json`. Le fake Gradle produit les deux familles. Le driver
couvre le succès, l'absence d'`APK-010`, l'absence de `WIN-005`, et la
propagation exacte d'un status de test `17` sans phase evidence.

## Vérifications exécutées

Toutes les preuves AppKit natives utilisent le composite local demandé :
`--include-build /Users/chaos/.codex/worktrees/cf31/kffi`.

### Tests ciblés finaux

```text
rtk ./gradlew :kadre:runtime:jvmTest \
  --tests 'org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest.mixedFullscreenUpdateFailsStructureBeforeRevisionDomainAvailabilityAndBarrier' \
  --no-daemon --console=plain
```

Résultat : `BUILD SUCCESSFUL`.

```text
rtk ./gradlew :kadre:backend:appkit:jvmTest \
  --tests '...fullscreenRestoreFailureStillPublishesSuccessfulReadbackWithoutClosing' \
  --tests '...fullscreenReadbackFailureRemainsDistinctAndClosesAfterRestoreFailure' \
  --tests '...runtimeOnlyLevelRealignmentControlsTheNextExternalFullscreenDid' \
  --tests '...reentrantDidWithoutWillWinsBeforeSelectorThrow' \
  --tests '...reentrantDidFailWithoutWillWinsBeforeSelectorThrow' \
  --tests '...fullscreenCancellationWhileAppKitQueueIsBusyStillWinsBeforeFirstSetter' \
  --tests '...externalWillQueuedBeforeFullscreenCommitWinsWithoutADoubleToggle' \
  --tests '...mixedFullscreenTitleIsRejectedBeforeNativeDispatch' \
  --include-build /Users/chaos/.codex/worktrees/cf31/kffi \
  --no-daemon --console=plain
```

Résultat : `BUILD SUCCESSFUL` ; 8 tests ciblés verts.

### Suites et gates intégrés

```text
rtk ./gradlew :kadre:runtime:jvmTest :kadre:backend:appkit:jvmTest \
  --include-build /Users/chaos/.codex/worktrees/cf31/kffi \
  --no-daemon --console=plain
```

Résultat : `BUILD SUCCESSFUL`.

```text
rtk ./gradlew :kadre:backend:appkit:appKitNativeTests \
  --include-build /Users/chaos/.codex/worktrees/cf31/kffi \
  --rerun-tasks --no-daemon --stacktrace --console=plain
```

Résultat : `BUILD SUCCESSFUL in 1m 40s`, 41 tâches exécutées.

```text
rtk ./gradlew :kadre:runtime:jvmTest \
  :kadre:backend:appkit:appKitNativeTests \
  :kadre:contracts:validator:generateRuntimeContractEvidence \
  :kadre:contracts:validator:generateAppKitContractEvidence \
  :kadre:contracts:validator:validateContractRegistry \
  --include-build /Users/chaos/.codex/worktrees/cf31/kffi \
  --no-daemon --console=plain
```

Résultat : `BUILD SUCCESSFUL` ; `WIN-005.json` et `APK-010.json` générés,
non vides, scénarios `Passed`.

Le script réel a été lancé avec un wrapper temporaire hors repository qui
ajoutait uniquement le même `--include-build` :

```text
rtk env KADRE_GRADLEW=/tmp/kadre-gradlew-kffi-cf31 \
  ./scripts/test-kadre-appkit-contracts.sh
```

Résultat : phase tests `passed`, phase evidence `passed` ; le wrapper temporaire
a ensuite été supprimé.

```text
rtk ./scripts/test-kadre-appkit-contract-driver.sh
```

Résultat : `Kadre AppKit contract driver behavior: passed`, avec rejets attendus
des fixtures sans `APK-010` et sans `WIN-005`, puis propagation du status `17`.

```text
rtk bash -n scripts/test-kadre-appkit-contracts.sh
rtk bash -n scripts/test-kadre-appkit-contract-driver.sh
rtk bash -n scripts/fixtures/fake-gradlew.sh
rtk git diff --check
```

Résultat : succès, aucune erreur de syntaxe shell ni whitespace.

## Préoccupation externe restante

Sans composite, le snapshot KFFI actuellement résolu par le repository échoue
encore sur les tests AppKit natifs avec le défaut de lookup généré déjà consigné
(`CGWindowLevelForKey`, 28 failures JVM au baseline de cette vague). Avec le
composite local KFFI imposé et revu, toutes les preuves JVM/natives/evidence sont
vertes. Aucun contournement FFI ni override de repository n'a été ajouté ; la
publication du correctif KFFI reste donc une dépendance externe de
reproductibilité hors de cette vague.

Les trois fichiers non suivis préexistants (`.superpowers/plans/`,
`.superpowers/specs/` et
`kadre/implementation-plans/2026-08-25-kffi-objc-foundations.md`) n'ont pas été
modifiés ni ajoutés au commit.
