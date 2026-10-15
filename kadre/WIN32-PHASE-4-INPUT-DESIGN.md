# Win32 Phase 4 — Clavier, pointeur et scroll

## Intention et livraison

Rendre les fenêtres Win32 interactives par `Window.surface.input`, avec le reducer et les budgets communs. Le demandeur a autorisé une livraison conjointe KFFI/Kadre et une publication Maven locale pour tester les deux sans attendre une publication Central. La validation porte sur les deux dépôts ensemble ; cette publication locale ne constitue pas une preuve de disponibilité CI distante.

Base Kadre : `09a29b0a` (phase 3 fusionnée). Base KFFI : `67bb7b1`. Windows 11 x64, JVM 25 et toolchains déjà fixées par chaque dépôt.

## Frontière KFFI

KFFI génère `GetKeyboardState`, `GetKeyboardLayout`, `MapVirtualKeyExW`, `ToUnicodeEx`, `SystemParametersInfoW`, ainsi que les fonctions nécessaires aux tests de layout. Les buffers de 256 touches, UTF-16, `TRACKMOUSEEVENT` et `POINT` restent dans KFFI. Kadre reçoit seulement des snapshots Kotlin copiés : aucune nouvelle FFI, aucun layout ni pointeur emprunté dans le mapper Kadre.

`Win32Keyboard` fournit un snapshot de message et une traduction Unicode bornée. `Win32Pointer` possède les appels de suivi leave, conversion écran/client et lecture des réglages wheel. Les échecs Win32 lisent immédiatement `GetLastError` et restent actionnables. Les wrappers refusent un hôte non Windows avant `init`.

## D1 — Messages clavier et texte

La touche physique vient du scan code set 1 et du bit extended, puis d'une table HID explicite. La localisation distingue gauche/droite, Enter du pavé et navigation du pavé ; Pause et PrintScreen ont leurs cas explicites. Une valeur inconnue devient `Unidentified`, jamais une touche US supposée.

La touche logique nommée vient du virtual key ; les caractères viennent du layout du thread et de `ToUnicodeEx` avec le bit 2 (`0x4`, sans mutation de l'état clavier). Un résultat dead key est identifié comme tel et ne devient pas un commit de texte. Les buffers sont lus selon le nombre de code units renvoyé, y compris les surrogate pairs. Les codes de contrôle des raccourcis sont retraduits avec Control/Alt neutralisés seulement lorsque le premier résultat est un contrôle ; un résultat imprimable AltGr reste intact.

Le snapshot logique est pris **avant** l'unique `TranslateMessage` de la pompe et consommé par la callback correspondante, avec restauration de contexte en cas de pompe réentrante. Un message envoyé directement utilise une lecture locale sans appeler `TranslateMessage`. Aucun `WM_CHAR`, `WM_SYSCHAR` ou `WM_IME_*` ne produit un événement de texte Kadre en phase 4 ; l'IME et `TextInputSession` appartiennent à la phase 6.

Le bit previous-state distingue repeat et première pression ; une release ne répète jamais. Le compte natif de répétition est développé sans dépasser les limites d'admission du runtime. Les modifiers et toggles proviennent du snapshot natif. Un changement de layout affecte les messages suivants, sans changer l'identité physique des touches déjà pressées.

## D2 — Pointeur et wheel

Le premier mouvement client admet `PointerEntered`, puis `PointerMoved`. Les coordonnées `lParam` sont des shorts signés ; les positions et deltas sont convertis en unités logiques avec le DPI relu pour cette fenêtre. Les boutons gauche/droite/milieu/X1/X2 sont mappés explicitement. `WM_MOUSELEAVE` est armé par `TrackMouseEvent` puis réarmé à la prochaine entrée. Aucun pointeur ni pression matérielle n'est inventé.

Les wheel vertical et horizontal conservent les fractions de `WHEEL_DELTA=120`, avec le signe portable bas/droite. Les réglages système sont lus : lignes verticales et incréments de texte horizontaux. `ScrollDelta.Lines` porte ces incréments textuels discrets ; aucun pixel n'est inféré. Zéro signifie aucun scroll. Le réglage page n'a pas d'unité commune fidèle : il est explicitement non mappé, comme `DOM_DELTA_PAGE` dans l'adapter Web. Le cahier manuel nomme cette limite.

Les frontières de coalescence changent lorsque l'axe, les réglages, modifiers, boutons ou le focus changent. Les messages souris promus restent le seul chemin pointeur en phase 4 : le filtrage de duplication avec touch/pointer attend l'installation effective de ces chemins en phase 6.

## D3 — Runtime, focus et terminal

Le backend publie `InputObservationChanged` après admission de la surface et installation de l'observation. Le runtime possède les IDs, révisions, stamps, état avant événement, coalescing, overflow et fermeture. Seuls keyboard et pointer deviennent disponibles ; les autres domaines d'input gardent leurs refus existants.

`WM_SETFOCUS`/`WM_KILLFOCUS` et l'activation sont réconciliés sans doubles transitions ; `WA_INACTIVE` se lit dans le mot bas de `wParam`. La perte d'activation passe par `FocusChanged(Unfocused)` et le reset atomique commun. Un callback terminal est ignoré.

Clarification de la roadmap face à `DESIGN.md` §input : la fermeture termine la lane et gèle son dernier snapshot ; elle ne synthétise ni reset ni releases. La capture du pointeur n'est pas activée ici : `WM_CAPTURECHANGED` ne fabrique pas un `FocusLost`. Capture, confinement et verrouillage restent à la phase 5/8, avec leur décision de réconciliation distincte. Aucun enum public n'est ajouté pour contourner cette frontière.

## D4 — Thread UI et comportement natif

La pompe et le driver partagent le même port natif. Les commandes appelées depuis les coroutines applicatives sont admises dans une queue UI bornée, réveillée par la fenêtre de contrôle ; les opérations natives et leurs callbacks s'exécutent sur le thread créateur. Le shutdown ferme l'admission et termine les commandes restantes avec une erreur explicite. Cette couture est nécessaire à la preuve du chemin public, qui ne doit pas se limiter à un driver créé directement sur le thread du test.

La `WndProc` restitue le véritable retour de `DefWindowProcW` pour les messages non consommés. `HostDefault` conserve le comportement Windows, notamment les system keys. `SuppressWhenPossible` supprime uniquement les défauts des messages d'input livrés à cette fenêtre ; il ne promet pas de bloquer les raccourcis globaux du système.

Les pressions clavier et pointeur passent par `dispatchSynchronousInteraction` avant leur stimulus ordinaire. `OpenWindow`, déjà supporté par le runtime et le backend, est le seul domaine d'action activé ici ; move/resize/fullscreen/lock/drop restent aux phases qui implémentent leurs primitives. Une fermeture réentrante empêche la publication ordinaire qui suivrait.

## Preuves et livraison

O2 : fixtures scan codes et modifiers, inconnus, releases/repeat, changement de layout, coordonnées négatives, DPI fractionnaire, boutons, fractions et signes wheel, réglage page refusé, frontières de coalescence, focus/reset unique, fermeture réentrante, défaut natif et commandes UI.

O3 KFFI : wrappers réellement appelés sous Windows, traduction de layouts connus sans perturbation dead key, suivi leave et conversion client. O3 Kadre : messages postés dans la file Windows vers une vraie fenêtre ; observation par `surface.input`, jamais appel direct du mapper. Une preuve standalone utilise le provider Desktop et les coroutines applicatives.

Les nouveaux contrats `W32-009` (clavier), `W32-010` (pointeur/wheel) et `W32-011` (focus, défauts et interaction) sont activés seulement avec leurs tests/mappings. Le harness manuel couvre clavier réel US/non-US, AltGr, dead keys, repeat, souris, wheel horizontal/vertical, focus perdu, fermeture et réglage page ; une injection de message ne prouve pas la réception matérielle.

La validation locale utilise un repository Maven local filtré sur KFFI et une version de travail explicite, activés par propriétés Gradle. La résolution par défaut reste Central. Les PRs des deux dépôts peuvent être revues ensemble ; la gate distante Kadre attend la publication de son prérequis KFFI.

## Références

- [ToUnicodeEx et ses interactions avec TranslateMessage](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-tounicodeex)
- [GetKeyboardState et la queue du thread](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getkeyboardstate)
- [TrackMouseEvent et réarmement du leave](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-trackmouseevent)
- [WM_MOUSEWHEEL : deltas fractionnaires et coordonnées signées](https://learn.microsoft.com/en-us/windows/win32/inputdev/wm-mousewheel)

