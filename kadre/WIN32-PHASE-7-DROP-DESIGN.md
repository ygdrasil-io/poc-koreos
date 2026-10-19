# Win32 phase 7 — drag-and-drop OLE et transferts bornés

**Statut :** implémentation livrée conjointement avec KFFI #135 (snapshot `1.0.0-20261019.082243-67` épinglé) ; preuves W32-021/022 au gate, réception manuelle inter-applications encore ouverte.
**Date :** 19 octobre 2026.
**Base Kadre :** `91125c2e`, fusion de #476 (phase 6).
**Base KFFI auditée :** snapshot Win32 `1.0.0-20261018.214839-64` (source `69ff1f9`, KFFI #131).
**Portée :** la phase 7 de [la roadmap](WIN32-IMPLEMENTATION-ROADMAP.md) — réception de drops OLE sur les fenêtres Kadre, admission synchrone et transferts bornés. Être source de drag (`DoDragDrop`) reste hors périmètre.

## 1. Intention et critères de réussite

La demande est de poursuivre après la phase 6 avec le même mode de livraison conjointe : publier KFFI localement pour développer les deux côtés, livrer la phase entière, puis épingler un snapshot distant publié avant de déclarer la PR Kadre prête. Les changements KFFI restent formulés en termes Win32 génériques, sans référence au dépôt consommateur.

Le résultat attendu est la vraie chaîne OLE : un `IDropTarget` managé par fenêtre, une décision `AcceptDrop` prise dans la callback native avant son retour, des descriptors portables honnêtes (fichiers, URL, texte, binaire), puis un `DropTransfer` à claim unique et lectures bornées conformes à `OPERATION-CONTRACTS.md` §7. Le catalogue public fermé ne change pas : `DropOffer`, `DropItemDescriptor`, `DroppedItem`, `AcceptDrop` existent déjà et sont consommés tels quels.

Une liste de chemins via `WM_DROPFILES` ne satisfait pas seule le contrat : la phase doit prouver la frontière OLE réelle (vtable, apartment STA, références équilibrées), le refus explicite, la fin de l'emprunt `IDataObject` et la fermeture d'un transfert actif au teardown. Un `IDataObject` sans medium lisible n'est pas une offre partielle : il est rejeté ou publié avec exactement les items réellement disponibles.

## 2. Audit et choix d'architecture

Constats sur les sources examinées :

- Le runtime du drop est complet et backend-agnostique (`RuntimeDropTransfer.kt` : offre à claim unique, timeout, lectures par chunks, budgets ; INP-003 actif). `MinimalWindowSurface.dispatchSynchronousDrop` attend un backend qui crée l'offre puis répond à `InteractionEvent.DropEntered` par `AcceptDrop` dans la frame native.
- AppKit (phase 7 AppKit) fournit le patron de référence : snapshot unique dans la callback d'entrée, admission synchrone, `AcceptDrop` publié dans `handlerInteractions`, sources fermées au rejet/exception, stimuli `DropMoved/DropExited/DropPerformed` portés par l'offer id du runtime.
- KFFI possède déjà les primitives COM nécessaires en interne : `Win32TextComAbi` (GUID, signatures compressées, appels de vtable côté client), `Win32TextComBridge`/`Win32TextComObject` (objet COM côté serveur, quiescence), `Win32ComApartment`/`Win32ComResources`/`Win32ComCallbackGate`. Il ne génère aucune fonction OLE drop : `OleInitialize`, `RegisterDragDrop`, `RevokeDragDrop`, `ReleaseStgMedium` sont absents, tout comme les layouts `FORMATETC`/`STGMEDIUM`.

| Approche | Avantage | Limite déterminante | Décision |
| --- | --- | --- | --- |
| `WM_DROPFILES` seul (shell32) | Trivial, aucun COM | Liste de chemins uniquement : pas d'offre avant drop, pas de formats, pas de décision `AcceptDrop` avant retour natif, pas de texte/URL/binaire | Non retenue comme moteur de la phase |
| `IDropTarget` managé KFFI | Vraie frontière OLE, décision synchrone, formats déclarés, médias HGLOBAL/IStream | Bridge COM et apartment STA à construire dans KFFI | Retenue |
| Les deux en cascade | File drops sans COM quand OLE indisponible | Deux autorités de drop pour le même HWND ; `DragAcceptFiles` modifie le comportement natif global | Non retenue ; OLE seul |

Formats supportés et déclarés précisément, en priorité par item comme AppKit : `CF_HDROP` → `File` (chemins copiés, contenu relu par le système de fichiers), `CFSTR_INETURLW` → `Uri` (`text/uri-list`), `CF_UNICODETEXT` → `Text` (`text/plain` ; `CF_TEXT` ANSI est listé mais non exposé plutôt que décodé avec une page de code devinée), et tout média `TYMED_ISTREAM` → `Binary` (`SingleUse`, lecture chunked sur le thread UI). Les autres formats registered (`HGLOBAL` inconnus, `CF_TEXT`, fichiers virtuels `CFSTR_FILEDESCRIPTORW`/`FILECONTENTS`) sont énumérés mais non exposés dans cette livraison : leur exposition propre reste un travail ouvert nommé, jamais une lecture approximée.

## 3. Frontière KFFI et owners

Les noms décrivent les responsabilités ; les signatures Kotlin seront fixées à partir des headers SDK. Vtables, GUID, layouts, pointeurs, buffers et callbacks restent exclusivement dans KFFI.

| Owner ou valeur KFFI | Responsabilité et durée de vie |
| --- | --- |
| Services drop OLE | `OleInitialize`/`OleUninitialize` STA équilibrés sur le thread UI (`S_OK` et `S_FALSE` consommés chacun une fois, `RPC_E_CHANGED_MODE` en failure typée, jamais de conversion silencieuse MTA→STA) ; fermeture après la dernière révocation |
| `Win32DropTarget` | Objet COM `IDropTarget` complet (IUnknown + DragEnter/DragOver/DragLeave/Drop) par HWND vivant, enregistré par `RegisterDragDrop` sur le thread propriétaire ; callback révocable avec quiescence, `RevokeDragDrop` avant `DestroyWindow` ; `pdwEffect` relu/écrit comme scalaire, `POINTL` copié |
| `Win32DropData` | Lecture bornée de l'`IDataObject` emprunté : énumération `EnumFormatEtc` copiée en valeurs, `QueryGetData` sans medium, `GetData` → copie immédiate `HGLOBAL` (bornée) ou rétention `IStream` référencée ; `ReleaseStgMedium` systématique, chaque référence acquise enregistrée avant l'appel natif suivant |
| `Win32DropStream` | `IStream` retenu par `AddRef` avant la fin de la callback, lecture chunked et `Seek` sur le thread UI, `Release` idempotent ; aucun pointeur n'échappe à KFFI |
| Formats typés | `CF_HDROP`, `CF_UNICODETEXT`, `CF_TEXT` et formats registered résolus une fois ; `TYMED_*`, `DVASPECT` et clés `CFSTR_*` comme scalaires |

Le pont implémente `IUnknown` et les quatre méthodes `IDropTarget` avec signatures vérifiées, références équilibrées et confinement STA. Les callbacks attrapent les exceptions Kotlin, initialisent leurs outputs d'échec (`pdwEffect = DROPEFFECT_NONE`) et transmettent la failure assainie au reporter configuré. Une fermeture demandée depuis un worker révoque immédiatement l'admission puis poste `RevokeDragDrop` et la libération native sur le endpoint UI encore vivant.

Les références `IDataObject`/`IStream` nécessaires au-delà de la callback sont retenues par `AddRef` **avant** le retour natif ; le backend ne déplace jamais une référence COM vers un worker arbitraire. Une adresse `HWND` réutilisée ne réactive jamais un ancien target : la route est désenregistrée avant `DestroyWindow`, comme pour la `WNDPROC`.

## 4. Admission synchrone et offres

`DragEnter` est la seule callback qui construit l'offre :

1. La position écran `POINTL` est convertie en client puis en logique avec le DPI courant (mêmes conversions que les contacts de phase 6), en rejetant les valeurs non finies.
2. Les formats sont énumérés et qualifiés sans acquisition de medium (`QueryGetData` + énumération) ; les chemins `CF_HDROP` sont copiés immédiatement, bornés par `maxCollectionElementsPerValue` et `maxMetadataCodeUnitsPerValue`. L'`IDataObject` est retenu par référence avant le retour.
3. La source snapshot est remise au sink synchrone du driver → `RuntimeWindowManager` → `MinimalWindowSurface.dispatchSynchronousDrop` : le runtime crée l'offre, publie `InputEvent.DropEntered` et dispatche `InteractionEvent.DropEntered` dans la frame native.
4. La réponse `AcceptDrop` est committée par le runtime, puis le sink traduit l'admission en `DROPEFFECT_COPY` ; un rejet, une surface non prête ou une exception ferme la source et retourne `DROPEFFECT_NONE`. Aucun offre partielle n'est publiée : un échec de snapshot est un refus.

`DragOver` publie `SurfaceStimulus.DropMoved` pour l'offer id admis et répond `DROPEFFECT_COPY`/`DROPEFFECT_NONE` selon l'état de l'offre. `DragLeave` termine l'offre `LeftSurface` et publie `DropExited`. `Drop` publie `DropPerformed` : le transfert devient claimable, l'effet retourné reste `DROPEFFECT_COPY`. Une réentrée sans leave, un second enter pendant une offre vivante et les callbacks d'un offer id périmé sont ignorés proprement : le routeur garde au plus une offre, et le runtime termine déjà l'offre précédente comme `LeftSurface`.

La fenêtre fermée pendant un drag publie la fin de l'offre (`OwnerClosed`) et révoque le target avant destruction ; les sources non réclamées sont fermées. La disponibilité est publiée exactement lorsque le target est enregistré : `handlerInteractions` gagne `AcceptDrop` et `InputCapabilities.dragAndDrop` bascule à `Available` ; un échec d'installation OLE garde le drop `Unsupported` avec une cause native explicite, sans casser la session headless ni le clavier/souris existants.

## 5. Transferts bornés

Le backend implémente `DropTransferSource`/`DropItemSource` en Kotlin pur : aucun objet COM ne traverse le SPI runtime.

| Item | ReadMode | Lecture |
| --- | --- | --- |
| `CF_HDROP` (fichier) | `Replayable` | Chemin copié à l'entrée ; le contenu est relu par `Files.newInputStream` à chaque `collectBytes`, par chunks ≤ `maxDropChunkBytes` |
| `CF_UNICODETEXT`/`CF_TEXT` | `Replayable` | Copie `HGLOBAL` bornée par le budget payload au `Drop` (ou au claim) ; le medium est libéré après copie |
| `CFSTR_INETURLW` | `Replayable` | Comme texte ; mime `text/uri-list` |
| Binaire `HGLOBAL` | `Replayable` | Copie bornée ; taille relue avant allocation, dépassement → `ResourceLimitExceeded` |
| `TYMED_ISTREAM` | `SingleUse` | Chunks lus sur le thread UI à la demande du lecteur, par work item borné ; `Seek` non requis, échec de lecture → failure plateforme |

Les lectures de mediums hors thread UI sont interdites : chaque chunk `IStream` (et toute copie `HGLOBAL` différée) est servi par un work item sur la pompe du thread propriétaire, avec budget vérifié avant copie. La cancellation du lecteur arrête la lecture coopérativement et libère le work item. Les fichiers virtuels différés restent dans les limites de ce chemin : le medium demandé après le drop via la référence retenue est rendu par la source, ou la lecture échoue en failure plateforme — aucune valeur inventée.

Les échecs natifs deviennent `KadreFailure.PlatformFailure(KadrePlatform.Win32, "drop", code)` avec code HRESULT assaini ; aucun chemin ni contenu de fichier n'apparaît dans un diagnostic. `claimTransfer` conserve son contrat (`AlreadyInUse` au second claim), `collectBytes` ses limites (`Invalid("maxBytes")`, `Limit(DropItem)`, `Closed`), et le timeout de claim du runtime reste l'autorité d'expiration.

## 6. Intégration, disponibilité et budgets

Le driver crée le routeur drop par fenêtre après l'input observer, le branche au sink synchrone, puis enregistre le target après `onSurfaceReady`. Le routeur drop est séparé des routeurs clavier/souris/contacts/texte ; le handler fenêtre garde une unique décision de dispatch. La révocation participe à la chaîne de cleanup en ordre inverse.

Le teardown respecte l'ordre normatif : admission applicative fermée, puis transferts actifs fermés, puis `RevokeDragDrop` par fenêtre, puis destruction des fenêtres, puis libération des références retenues, puis `OleUninitialize`, avant le endpoint UI et la pompe. Les callbacks nécessaires à la libération restent vivants jusqu'à quiescence. Les ordres de publication (state avant événement) suivent les contrats communs.

Les budgets restent ceux de la politique : `maxConcurrentDropTransfers` (réservé à l'accept), `dropTransferClaimTimeout` (autorisité runtime), `maxDropChunkBytes` (chaque chunk), `maxRetainedPayloadBytesPerSession` (copies texte/binaire et chemins). Aucun nouveau budget n'est introduit ; les copies essentielles qui dépassent rendent l'item ou l'offre inadmissible plutôt que tronquée.

Emplacements attendus :

- KFFI : `generation/ole32.yaml` enrichie (fonctions OLE drop, layouts `FORMATETC`/`STGMEDIUM` si le générateur ne les émet pas), bridge `IDropTarget`, lecture `IDataObject`, services apartment, tests JVM portables et natifs Windows, README/CHANGELOG génériques.
- Kadre Win32 : `Win32DropMapping` (DTO copiés), seam natif + adaptateur KFFI, `Win32DropRouter`, wiring driver/provider, harness manuel.
- Runtime commun : aucun changement attendu ; `dispatchSynchronousDrop` et les stimuli drop existent.
- Preuves : registre (W32-021/022), mappings evidence, workflow Windows existant.

## 7. Matrice de réception et gates

| Contrat proposé | Scénarios essentiels | Sentinelle |
| --- | --- | --- |
| W32-021 — target OLE et admission | Enregistrement réel `RegisterDragDrop` sur HWND, DragEnter → `AcceptDrop` avant retour natif, refus explicite, DragOver/Leave/Drop routés par offer id, retrait avant destruction, callbacks tardifs d'un HWND réutilisé | Accepter sans runtime vivant, ou répondre un effet sans admission, est détecté |
| W32-022 — transferts bornés | Claim unique, timeout, fichiers relu par chunks, texte/URL Replayable, stream SingleUse, budgets taille inconnue, cancellation lecteur, teardown pendant transfert | Un second claim gagnant ou un chunk hors budget est détecté |

Tests portables : machines à états du routeur (admission/rejet/périmé), mapping formats → descriptors, sources fermées au rejet/exception, budgets avant copie, disponibilité et capability, compatibilité des autres domaines (les 9 suites natives existantes restent vertes).

Tests natifs obligatoires : vrai `RegisterDragDrop`/`RevokeDragDrop` sur HWND réel, invocation COM du target enregistré avec un `IDataObject` de test portant des médias `HGLOBAL`/`IStream` réels, lecture via la vraie frontière, ordres de publication publics, teardown en cours de transfert. Un `PostMessage` `WM_DROPFILES` n'est pas une preuve OLE.

Charter manuel (`WIN32-PHASE-7-MANUAL.md`) : drag inter-applications Explorer → fenêtre Kadre (fichiers multiples, texte, URL), source à rendu différé, drop refusé visuellement (`DROPEFFECT_NONE`), fermeture de la fenêtre pendant le drag, dépassement de budget sur gros fichier, stream `SingleUse` consommé deux fois. Consigner Windows/build, source du drag, résultat et limites ; aucun chemin ni contenu dans les journaux.

La livraison conserve les phases 1–6 vertes et n'active W32-021/022 qu'avec preuves fraîches au SHA courant, après publication du snapshot KFFI. Pendant le développement conjoint, l'alias Maven local est explicite (`kadreKffiLocal=true`) ; avant sortie de draft : KFFI fusionné/publié, snapshot timestampé épinglé pour root et JVM, résolution Central avec `-PkadreKffiLocal=false`, mêmes tests et preuves relancés.

## 8. Risques et décisions de sortie

- Le bridge `IDropTarget` est le risque principal : révocation pendant un drag actif, quiescence et réutilisation d'`HWND` doivent être qualifiés ensemble avant tout `Supported`. La sentinelle « accepter sans admission » doit rester rouge sous mutation.
- Les lectures post-drop d'une source à rendu différé peuvent échouer légitimement (source disparue) : la failure plateforme est le comportement attendu, jamais une valeur fabriquée ; le charter le consigne.
- Les fichiers virtuels et les formats spécialisés (email, distributions de navigateur) restent un travail ouvert nommé ; leur absence ne ferme pas artificiellement la roadmap et n'est pas annoncé comme couverte.
- Un manque de bridge ou un comportement non terminé reste ouvert ; il ne devient pas un `Unsupported` permanent pour déclarer la phase complète.

La prochaine étape après revue de ce design est l'implémentation conjointe et les deux PRs (KFFI puis Kadre). Ce document ne revendique aucune exécution native ni activation de contrat.
