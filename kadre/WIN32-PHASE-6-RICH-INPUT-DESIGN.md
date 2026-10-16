# Win32 phase 6 — texte, IME, contacts et gestes natifs

**Statut :** design validé par l’utilisateur le 16 octobre 2026 ; plan d’implémentation conjoint en cours de revue. Aucune capability supplémentaire n’est activée par ce document.
**Date :** 16 octobre 2026.
**Base Kadre :** `853fd1e8af61df51e7319f8f67e273278763ad42`, fusion de #474.
**Base KFFI auditée :** `3b94c9597630401f969ea707a3e2175204d74f99`, snapshot Win32 `1.0.0-20261015.232828-61`.
**Portée :** toute la phase 6 de [la roadmap](WIN32-IMPLEMENTATION-ROADMAP.md), dans la continuité du standalone Windows x64/JVM 25.

## 1. Intention et critères de réussite

La demande est de poursuivre après fusion de la phase 5, avec livraison conjointe des primitives KFFI et de leur consommation Kadre. Les instructions précédentes restent applicables : publier KFFI localement pour développer/tester les deux côtés, livrer la phase entière, puis épingler un snapshot distant publié avant de déclarer la PR Kadre prête. Les changements KFFI, leurs tests, leur documentation et leur PR restent formulés en termes Win32 génériques, sans référence au dépôt consommateur.

Le résultat attendu est une vraie `TextInputSession`, avec composition, surrounding text et révisions conformes, des contacts touch indépendants, des pointeurs souris/stylet qui ne fusionnent pas, et les seuls gestes reconnus par Windows effectivement installés. Les enums et interfaces applicatives du catalogue fermé ne changent pas. Les adaptations nécessaires restent dans le SPI interne et les reducers existants.

Une phase n’est pas terminée par la seule réception de `WM_CHAR`. Elle doit aussi prouver le bridge natif, les plages UTF-16, l’arbitrage de révision, les fins de contact, les pertes de focus/capture, les budgets et le teardown. Les essais matériels non exécutés restent nommés et ne sont pas remplacés par un succès simulé.

## 2. Audit et choix d’architecture

Constats sur les sources examinées :

- `TextInputPort`, `RuntimeTextInputSession` et le factory par surface existent. Win32 ne les branche pas encore ; le router garde actuellement les messages caractères sans publier de texte.
- Les observations texte portent la révision acceptée. Le runtime publie une proposition d’edit sans modifier lui-même le document applicatif ; il rebase la composition au write-back et ferme une mise à jour non réconciliable.
- `SurfaceStimulus.TouchChanged` possède déjà une identité native et des contacts indépendants. Le chemin d’interaction touch ne transporte cependant pas cette identité jusqu’à la publication ordinaire.
- `RuntimeSurfaceInput.pointerStateLocked` utilise actuellement `mousePointerIdLocked()` pour tous les `PointerKind`. Le chemin d’interaction recrée lui aussi un pointeur `Mouse` : souris et stylets simultanés nécessitent une correction commune ciblée.
- KFFI génère certaines fonctions historiques touch/gesture, mais n’a ni owner texte TSF/IMM managé, ni lecture `POINTER_INFO`/pen/touch sûre, ni owner Interaction Context. Aucun `CoInitializeEx`/TSF n’est actuellement installé par le host Win32.

| Approche texte | Avantage | Limite déterminante | Décision |
| --- | --- | --- | --- |
| IMM32 seul | Composition/result strings et candidate window relativement directs | Ces messages ne fournissent pas à eux seuls le protocole de document, sélection et remplacement révisionnés ; l’implémenter en parallèle de TSF créerait deux autorités | Non retenue comme moteur de cette phase |
| Contrôle natif EDIT auxiliaire | TSF/IMM déjà intégrés au contrôle | Introduit un autre receiver, du focus et du layout que Kadre ne possède pas ; cacher/déplacer le contrôle ne prouve pas le bon comportement IME | Non retenue |
| Text store TSF managé | Accès explicite au document UTF-16, sélection, locks, composition et géométrie | Bridge COM révocable et protocole de locks à construire dans KFFI | Retenue |

Ce choix est une conclusion de conception fondée sur le contrat Kadre et le modèle [Text Stores de Microsoft](https://learn.microsoft.com/en-us/windows/win32/tsf/text-stores). Il ne prétend pas que toute application IMM32 serait incapable d’éditer du texte. Le prototype natif de réception doit qualifier le text store avant son activation publique.

Pour les contacts, retenir `WM_POINTER` avec lectures typées KFFI. Pour les gestes, retenir **Windows Interaction Context** alimenté par les frames des contacts suivis. Cela conserve les contacts et confie la reconnaissance à Windows. Le couple historique `RegisterTouchWindow`/`WM_GESTURE` n’est pas choisi : Windows route soit le touch enregistré, soit les gestes par défaut selon son [modèle historique](https://learn.microsoft.com/en-us/windows/win32/wintouch/architectural-overview). Aucun recognizer géométrique maison n’est ajouté au runtime.

## 3. Frontière KFFI et owners

Les noms ci-dessous décrivent les responsabilités ; le plan fixera les signatures Kotlin à partir des headers SDK. Les vtables, GUID, layouts, pointeurs, buffers et callbacks restent exclusivement dans KFFI.

| Owner ou valeur KFFI proposée | Responsabilité et durée de vie |
| --- | --- |
| `Win32TextServices` | Admission COM STA équilibrée sur le thread UI, activation du thread manager TSF, keystroke/message services et focus par document ; fermeture après tous les text stores |
| `Win32TextStore` | Un document par HWND vivant : `ITextStoreACP`, sink de composition, input scope, document manager/context, sink conseillé et notifications ; callback révoqué avant detach/release |
| Valeurs texte copiées | Snapshot UTF-16, sélection, préédition, remplacement, numéro de transaction et epoch opaque du caller ; aucun type Kadre ni révision créée par KFFI |
| `Win32PointerPackets` | Lecture bornée des frames `POINTER_INFO`, `POINTER_TOUCH_INFO`, `POINTER_PEN_INFO` pendant le message ; valeurs et masks copiés, aucun pointeur emprunté retourné |
| `Win32InteractionContext` | Recognizer Windows propre à une fenêtre, configuration/readback des gestes, traitement des frames, callback révocable et destruction sur le thread propriétaire |

L’initialisation COM intervient avant la création du premier context TSF. `S_OK` et `S_FALSE` sont équilibrés ; un apartment incompatible n’est pas converti. Cet échec rend le text input indisponible avec une cause native explicite, sans casser une session headless ni la souris/clavier de base. Aucun thread STA secondaire n’est présenté comme propriétaire d’un HWND créé sur le thread UI.

Le bridge implémente `IUnknown` et les méthodes nécessaires des interfaces SDK avec signatures vérifiées, références équilibrées et confinement STA. Il ne retourne jamais `S_OK` pour une méthode non implémentée. Les callbacks attrapent les exceptions Kotlin et initialisent leurs outputs d’échec ; le thread hôte reçoit la failure assainie. Une fermeture demandée depuis un worker révoque immédiatement l’admission puis poste la libération native sur le endpoint UI encore vivant.

Le protocole [RequestLock](https://learn.microsoft.com/en-us/windows/win32/api/textstor/nf-textstor-itextstoreacp-requestlock) est explicite : lecture/écriture vérifiées à chaque méthode, lock valide pendant `OnLockGranted` uniquement, upgrade asynchrone réentrant servi après la lecture, refus synchrone documenté lorsqu’il est indisponible. Les notifications du sink dues au write-back applicatif sont émises hors lock ; aucune notification réfléchie n’est envoyée en réponse à une mutation du manager.

## 4. Document texte et composition

### 4.1 Deux vues, une autorité applicative

Le port conserve le snapshot **accepté** `(text, selection, revision)`, et le text store une vue native provisoire pour la transaction/composition courante. Les observations publiques sont toujours traduites vers le document de la révision acceptée ; un offset dans la préédition reste une sélection relative à son texte. Le text store ne fabrique pas une nouvelle `TextDocumentRevision`.

Une transaction native se déroule ainsi :

1. TSF lit ou édite sa vue sous lock. Les limites sont vérifiées avant copie/allocation et avant publication.
2. À la fin du lock, le bridge produit un batch borné. Les écritures successives du même batch sont réduites à un remplacement cohérent du document accepté, ou à une mise à jour de préédition. Les mutations provisoires ne deviennent pas chacune un edit public contradictoire.
3. Le port publie le batch dans l’admission sérialisée du runtime, avec son epoch de document. Une transaction de commit reste en attente de write-back applicatif ; aucune seconde transaction ne publie une plage calculée dans du texte provisoire comme si elle appartenait au document accepté.
4. Le write-back de révision supérieure écrit le snapshot natif puis engage le snapshot accepté via le chemin existant. Le texte applicatif est autoritaire, y compris s’il corrige ou refuse la proposition. Les notifications TSF suivent le commit natif hors lock.
5. Tant qu’un commit attend sa réponse, un lock d’écriture asynchrone reste différé et les demandes compatibles sont fusionnées dans une seule requête de lock ; les demandes synchrones impossibles reçoivent l’erreur TSF prévue. Les messages caractères en attente sont bornés par la capacité discrète et le budget payload, jamais par une queue illimitée.

Une préédition peut être actualisée plusieurs fois sur une même révision : toutes ses plages restent exprimées dans le même document accepté. Après adoption applicative, son range est rebased. Exemple de réception obligatoire : `ab`, sélection `[1,1]`, préédition `😀` à la révision 10 ; après adoption de `a😀b` à la révision 11, le composing range est `[1,3]`, et la sélection de préédition reste relative à `😀`.

Une observation acceptée pendant un write-back suspendu suit la fermeture non réconciliable déjà imposée par le runtime. On ne masque pas cette course. Une observation refusée, une queue native épuisée ou un mapping de plage impossible arrête la production native concernée et déclenche un reset/une fermeture explicite par le runtime ; aucun `S_OK` fictif ne conserve deux documents divergents. Le pont interne doit transmettre ce signal terminal, absent de `TextInputObservation` aujourd’hui, sans créer un événement public.

### 4.2 Clavier, caractères et absence de doublon

Le routage des touches vers TSF se place avant `TranslateMessage` pour les HWND dont le text store est actif. Les chemins `TestKeyDown/KeyDown` et `TestKeyUp/KeyUp` suivent le résultat natif : une touche mangée n’est ni traduite une seconde fois ni réinjectée comme input ordinaire. Voir [ITfKeystrokeMgr](https://learn.microsoft.com/en-us/windows/win32/api/msctf/nf-msctf-itfkeystrokemgr-testkeyup).

Une touche non mangée conserve le routage physique existant. Son texte, lorsqu’une session est active, vient uniquement du chemin caractères/text store. `WM_CHAR` UTF-16 est assemblé sans couper les paires ; `WM_UNICHAR` respecte le probe `UNICODE_NOCHAR` et les scalaires valides. Les messages dead-char ne commitent rien. Les messages IMM de compatibilité ne sont pas une seconde source de commit : aucun résultat n’est publié à la fois par TSF et `WM_IME_CHAR`/`WM_CHAR`.

Les caractères sans session texte restent au comportement natif existant. Aucun `ToUnicodeEx` supplémentaire ne consomme l’état des dead keys pour fabriquer du texte. Les répétitions, `AltGr`, les shortcuts et les contrôles sont testés distinctement du texte composé.

`action` est publié comme `TextInputObservation.Action` sur la soumission admise ; il ne fabrique pas un caractère. En mode multiline, une entrée de texte de fin de ligne reste du texte selon le résultat natif. Le bridge expose les input scopes TSF ; les variantes purpose/capitalization/autocorrect et leurs hints ou absence d’équivalent sont qualifiées exhaustivement, sans transformer un hint en validation applicative ni modifier les caractères reçus. Password n’introduit aucune promesse de confidentialité système et aucun texte, secret ou non, ne paraît dans les diagnostics.

### 4.3 Géométrie, focus et fermeture

`updateCursor` exige la révision exacte. Le rect logique fourni est converti au DPI effectif et aux coordonnées écran du HWND, y compris position négative et changement d’écran. Les notifications de layout sont réémises après déplacement/DPI/scroll indiqué par l’application.

Le contrat ne fournit pas la géométrie de chaque caractère. `GetTextExt` répond avec le rect de curseur seulement pour la plage qu’il décrit réellement ; toute autre plage sans layout retourne `TS_E_NOLAYOUT`. `GetACPFromPoint` ne devine pas un index dans le texte. Voir [GetTextExt](https://learn.microsoft.com/en-us/windows/win32/api/textstor/nf-textstor-itextstoreacp-gettextext) et [GetACPFromPoint](https://learn.microsoft.com/en-us/windows/win32/api/textstor/nf-textstor-itextstoreacp2-getacpfrompoint). Le test IME réel doit confirmer le placement de candidats avec cette limite ; si ce chemin ne suffit pas, le besoin de layout doit être résolu dans la spécification avant activation, pas caché par un rectangle inventé.

Perdre temporairement le focus suspend la session et révoque son accès clavier TSF, en conservant la composition tant que Windows ne l’a pas terminée. Le regain restaure le document et reprend la session. Une fin native est publiée avant de perdre son owner vivant. Fermer la session, sa surface ou sa fenêtre révoque les observations, détache le focus/context, libère les sinks/références, puis permet `DestroyWindow`. Aucun callback tardif d’un ancien HWND ne peut atteindre une nouvelle fenêtre réutilisant l’adresse.

## 5. Touch, stylets et identité commune

Chaque contact Windows possède un token privé stable pendant sa durée de vie ; la réutilisation d’un pointer ID natif crée un nouveau token. Les frames sont copiées et traitées pendant la validité du message. Coordonnées écran physiques → client physique → logique avec le DPI courant ; le `lParam` signé 16 bits ne remplace pas la position complète disponible dans `POINTER_INFO`.

Le SPI pointeur reçoit un `nativeIdentity` optionnel et des métadonnées dans l’interaction synchrone. Sans token, les adapters existants conservent leur pointeur historique ; Win32 fournit des tokens distincts pour souris/stylets. `RuntimeSurfaceInput` assigne les `PointerId`, conserve boutons/pressure/pen par identité et retire seulement le pointeur terminé. Les publications différées après handler gardent token, kind et attributs, y compris en cas de callback réentrant ou consommé.

Pour touch, le runtime réserve l’identité publique depuis le token natif avant le handler `TouchStarted`, et réutilise cette réservation pour l’input ordinaire avec le même stamp. Le backend ne crée pas un `TouchId` indépendant. Il n’y a ni double Started ni nouveau contact au premier Move. Pression, deviceId et annulation sont conservés dans les métadonnées internes du chemin synchrone.

| Source native | Publication |
| --- | --- |
| Touch | `TouchChanged` Started/Moved/Ended/Cancelled ; jamais un `PointerState` supplémentaire |
| Pen/eraser | `PointerState` séparé, kind exact ; boutons et hover observés, pression/tilt/twist uniquement avec masks valides |
| Souris | Chemin phase 4 préservé ; aucun `EnableMouseInPointer` global ajouté |
| Souris promue d’un contact déjà traité | Filtrée sur la provenance native vérifiable ; aucun second clic ni seconde interaction |

Les valeurs hors domaine ou non finies ne sont pas clampées ; un champ inconnu devient `null`. `POINTER_FLAG_CANCELED`, capture perdue, focus perdu, fenêtre cachée/minimisée et teardown terminent ou reset les contacts concernés sans attente d’un UP qui peut ne jamais arriver. Le [contrat WM_POINTER](https://learn.microsoft.com/en-us/windows/win32/inputmsg/wm-pointerdown) exige de ne pas supposer des paires down/up après perte de capture.

Les tables de contacts, frames et maps d’identités sont bornées par `maxCollectionElementsPerValue`, en plus de la capacité d’ingress. Le débordement applique la politique existante et invalide la source explicitement ; aucun contact essentiel n’est tronqué ou fusionné avec un autre pour rester sous la limite.

## 6. Gestes reconnus par Windows

Le context est créé, configuré et lu avant l’annonce de capability. Ses callbacks synchrones ne suspendent pas ; ils copient des valeurs bornées vers le même ingress runtime que les contacts. Chaque frame native est soumise une seule fois ; le grouping des frames/pointers et leur ordre suivent [ProcessPointerFramesInteractionContext](https://learn.microsoft.com/en-us/windows/win32/api/interactioncontext/nf-interactioncontext-processpointerframesinteractioncontext). L’ajout/retrait d’un contact ne fabrique pas une frame avec un autre ensemble d’identités.

Configurer les unités écran puis les relire via `INTERACTION_CONTEXT_PROPERTY_MEASUREMENT_UNITS` avant de convertir les translations en unités logiques ; on ne traite pas les unités HIMETRIC par défaut comme des pixels. [Propriété des unités](https://learn.microsoft.com/en-us/windows/win32/api/interactioncontext/ne-interactioncontext-interaction_context_property).

| Geste public | Source retenue et mapping |
| --- | --- |
| Pan | Manipulation translation X/Y Windows ; delta incrémental converti en logique |
| Pinch | Manipulation scaling Windows ; facteur incrémental fini strictement positif |
| Rotation | Manipulation rotation Windows ; angle incrémental en radians selon convention SDK qualifiée |
| DoubleTap | Reconnaissance TAP avec count 2 ; événement discret Ended ; jamais un two-finger tap renommé |
| TouchpadPressure | Non annoncé : ni pression tactile ni press-and-hold n’équivalent à ce variant |

Une manipulation combinée peut publier Pan/Pinch/Rotation depuis le même callback, chacun avec ses champs valides et ses phases réelles. Le reset du context termine les gestes suivis avant révocation ; il n’est pas présenté comme une notification native de cancellation que Windows n’a pas émise. L’inertie n’est pas activée dans la configuration de cette phase : aucun timer synthétique ne prolonge un geste après sa fin native.

La capability est exactement le set dont configuration, readback et callback ont réussi. Un échec d’installation retire ce set avant le diagnostic ; il ne coupe pas les contacts bruts déjà disponibles. Les flags de configuration sont vérifiés car Windows n’active [aucune interaction par défaut](https://learn.microsoft.com/en-us/windows/win32/api/interactioncontext/nf-interactioncontext-setinteractionconfigurationinteractioncontext).

Le parcours qualifié est celui du touch/pen `WM_POINTER`. Les extensions Precision Touchpad récentes ne sont pas déduites de cette preuve : leurs payloads nécessitent [les variantes InteractionContext2](https://learn.microsoft.com/en-us/windows/win32/input-precisiontouchpad/processpointerframesinteractioncontext2). Le scroll souris/touchpad déjà observé conserve son contrat ; aucune pression ni gesture Precision Touchpad supplémentaire n’est annoncée sans sa propre réception.

## 7. Intégration, disponibilité et budgets

Le driver crée les ports privés par fenêtre, les branche au factory texte du `RuntimeWindowManager`, puis active les observations après `onSurfaceReady`. Le router texte, le router contacts et le recognizer sont séparés du router clavier/souris existant ; le handler fenêtre garde une unique décision de dispatch/default native.

Une observation interne de capability texte permet de refléter l’installation/retrait réel du service ; son champ absent préserve les adapters existants. Un pont terminal interne permet au port de fermer/reset sa session via le runtime lorsque les observations ne sont plus réconciliables. Ces adaptations n’étendent pas le catalogue public. Les failures respectent `OPERATION-CONTRACTS.md` : `AlreadyInUse` avant second owner, `StaleRevision`, `InvalidRequest`, `Closed`, limites et failures de plateforme assainies.

Le budget vérifie le nombre de code units, les octets retenus et le nombre d’éléments avant copie. Les deux vues texte, batches, messages différés et transactions en attente partagent le budget payload de session avec les autres ressources ; le coût est réservé avant allocation/attente UI et rendu à l’annulation/commit/close. Les opérations arithmétiques sont contrôlées avant taille native. Ni le texte ni les contacts essentiels ne sont tronqués.

Le teardown ferme d’abord admission et callbacks applicatifs, puis texte, contacts et recognizer, puis la fenêtre. Les callbacks natifs nécessaires à la libération restent vivants jusqu’à quiescence. Les services TSF/COM ferment après les derniers documents, avant le endpoint UI et la pompe. La fermeture non bloquante publique ne court-circuite pas cette séquence.

Emplacements attendus :

- KFFI : génération Win32/COM depuis les headers SDK, owners managés texte/pointer/interaction, tests JVM/natifs, README/CHANGELOG génériques.
- Kadre Win32 : ports natifs, nouvelles unités texte/contacts/gestures, composition du driver/host et harness manuel.
- Runtime commun : identité pointeur, interaction touch/pointer avec métadonnées, branchement du port texte et signal terminal ; régressions conservant les chemins sans token des autres adapters.
- Preuves : registre, mappings Win32, producteurs/validator et workflow Windows ; préserver les contrats et producteurs Wayland/AppKit/Web/UIKit déjà fusionnés.

## 8. Matrice de réception et gates

Les IDs suivants sont proposés et ne sont activés qu’avec leurs scénarios, sentinelles et exécutions obligatoires :

| Contrat proposé | Scénarios essentiels | Sentinelle |
| --- | --- | --- |
| W32-017 — document TSF | Vrai context COM/TSF, surrounding text/selection, locks réentrants, stale write-back, reset/close, callbacks tardifs | Supprimer le lock check ou accepter une révision obsolète fait échouer la preuve |
| W32-018 — composition et caractères | Préédition/commit/cancel, exemple hors BMP, dead keys, action, focus suspend/resume, géométrie candidats, absence de doublon TSF/char | Double commit ou plage de préédition dans le mauvais document est détecté |
| W32-019 — contacts et pointeurs | Deux contacts simultanés, souris+pen, masks, réutilisation ID, perte capture, interaction puis input avec même identité/stamp, overflow | Fusionner deux tokens ou perdre la cancellation est détecté |
| W32-020 — gestes Windows | Vrai Interaction Context, readback config/unités, frames à deux contacts, callbacks Pan/Pinch/Rotation/DoubleTap, teardown | Annoncer un geste sans callback/configuration ou traiter HIMETRIC comme pixels est détecté |

Tests portables : machines à états TSF et document, mapping des observations, budgets avant allocation, toutes branches de configuration, identités distinctes et compatibilité sans token, cancellation et callbacks imbriqués. Régressions communes du runtime et suites des adapters affectés exécutées sur leurs plateformes disponibles ; une impossibilité d’exécution est rapportée.

Tests natifs obligatoires : vtables/layouts/références COM sur SDK x64, création/activation et fermeture réelles TSF, appels de test par interfaces natives, real HWND, Interaction Context et frames natives conformes, message pump et worker pendant activité texte. Un `PostMessage` avec faux pointer ID n’est pas une preuve de `GetPointerInfo`. Les probes contrôlées d’injection de contact restent isolées dans le harness Windows ; la bibliothèque ne forge aucune entrée matérielle.

Charter manuel : IME à composition japonais ou chinois installé, layout non US à dead key/AltGr, caractère hors BMP, sélection/remplacement après write-back, perte de focus pendant composition, fermeture active, candidats après déplacement/DPI, deux contacts physiques, stylet/eraser et souris simultanés, pan/pinch/rotation/double-tap. Consigner Windows/build, layout/IME, hardware, résultat et limites ; aucune trace de texte utilisateur. Chaque catégorie de contact annoncée a une réception propre.

La livraison conserve les phases 1–5 vertes, active W32-017..020 seulement avec preuves fraîches au SHA courant et passe le watchdog Windows avec toutes les preuves antérieures. Pendant le développement conjoint, l’alias Maven local est explicite. Avant sortie de draft : KFFI fusionné/publié, snapshot timestampé épinglé pour root et JVM, résolution Central avec `-PkadreKffiLocal=false`, mêmes tests et preuves relancés.

## 9. Risques et décisions de sortie

- Le bridge TSF est le risque principal : locks, edit sessions et write-back applicatif doivent être qualifiés ensemble avant tout `Supported`. Les tests du batch et de la transaction en attente doivent démontrer que les touches suivantes progressent après réponse applicative et restent bornées si elle ne répond pas.
- Le rect de curseur n’est pas un layout complet. Si un IME de référence exige un layout indisponible pour fonctionner correctement, le contrat doit être corrigé avant activation ; une suite de mocks verte ne suffit pas à fermer cette phase.
- Le runtime commun reçoit une correction d’identité limitée au besoin Win32 ; les adapters sans token gardent leur comportement. Leur migration vers plusieurs pointeurs n’est pas revendiquée par cette phase.
- Pression/tilt et gestures sont conditionnés aux données et routes effectivement observées. Un matériel absent est une limite de qualification à documenter, pas une valeur inventée.
- Un manque de bridge ou un comportement non terminé reste ouvert. Il ne devient pas un `Unsupported` permanent pour déclarer artificiellement la phase complète.

La prochaine étape après revue de ce design est le plan d’implémentation conjoint, puis sa mise en œuvre et les deux PRs. Ce document constitue le cadrage ; il ne revendique aucune nouvelle exécution native ni livraison de phase 6.
