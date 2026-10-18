# Win32 phase 6 — qualification conjointe

L'implémentation couvre TSF, composition/caractères, contacts/stylets et gestes Windows dans les deux branches. **La phase n'est pas déclarée terminée** : adoption d'un nouveau snapshot distant et [réception manuelle](WIN32-PHASE-6-MANUAL.md) restent requises. Le catalogue conserve le snapshot distant de phase5 `1.0.0-20261015.232828-61` jusqu'à publication KFFI.

## Preuves automatisées

| Domaine | Comportement exercé | Contrat |
| --- | --- | --- |
| Document TSF | Vrai HWND, ITextStoreACP et client COM indépendant, révisions acceptées, writeback worker sur pompe UI | W32-017 |
| Composition/caractères | Préédition hors BMP, adoption 10→11, fin sans Replace supplémentaire, caractères en attente bornés, retrait du context avant destruction | W32-018 |
| Géométrie | Rectangle client logique converti en écran au DPI courant ; lecture COM du caret après déplacement ; absence de layout pour plage inconnue | W32-018 |
| Filtre clavier | Copie clavier avant filtre ; décision TRUE contrôlée sur vraie queue Windows empêchant Translate/Dispatch. Le producteur TRUE d'un IME installé reste manuel | W32-018 |
| Contacts | Injection Windows réelle, deux IDs publics distincts, même stamp interaction/input, pression 0.5 ; fin de chaque durée de vie | W32-019 |
| Stylet/gomme | Paquets copiés/masks testés dans KFFI, tokens distincts, hover/deltas/Barrel et métadonnées dans le chemin synchrone portable | W32-019 ; aucun reçu matériel physique |
| Gestes | Vrai Interaction Context, configuration/unités relues dans KFFI, callbacks publics Pan/Pinch/Rotation/DoubleTap, pas d'inertia | W32-020 |
| Ownership/budget | Budget de session partagé images/texte/frames, queues bornées, annulation et révocation avant cleanup, historique dédupliqué même après reset | Suites runtime, KFFI et W32-017..020 |

Après la revue et les corrections, la suite complète passe : **452 runtime, 140 Win32 portables, 42 Win32 natifs, 18 Desktop et 71 validator**, zéro échec/erreur/skip. Le watchdog complet de livraison passe au commit de code `b6820a2d9e63205faf78df30ef2867ea55c77d7d` : tests frais en 3m48s, génération en 58s et **23 JSON** validés au même SHA. Un précédent run avait révélé le deadlock worker/UI désormais corrigé par régression déterministe. KFFI : **110 core, 100 Win32 JVM, 38 natifs**, zéro échec/erreur/skip en exécution fraîche. La génération des **125 fonctions** concorde avec le SDK épinglé. Le harness manuel compile ; il n'a pas été exécuté comme réception. La [revue finale](WIN32-PHASE-6-REVIEW.md) décrit les quatre Important corrigés, le deadlock révélé par la gate, le Minor différé et les décisions chronologiques.
Quatre mutations temporaires ont rendu rouges leurs sentinelles : accepter une epoch non admise, partager un token entre contacts, ignorer le DPI et poursuivre Translate/Dispatch malgré un filtre TRUE (**3 portables + 1 native**). Les octets source ont été restaurés dans un finally. La vraie gate23 a ensuite refusé un `W32-020.json` manquant et le même fichier portant un SHA périmé ; les octets de la preuve ont également été restaurés. Les preuves sont régénérées au HEAD de livraison après les derniers changements de rapport/CI.

Le fixture d'injection confirme la réception d'un vrai DOWN/UP sur le HWND ainsi que le foreground/focus avant d'installer les collecteurs, même si le focus correspond déjà. Seules ces sondes de préparation peuvent être répétées ; aucune séquence mesurée n'est rejouée. Après injection, la pompe attend une période sans message de 30ms, avec une borne de 3s, au lieu de traiter un nombre fixe de messages. Cette préparation n'est ni un scénario mesuré ni une preuve d'activation par premier contact en arrière-plan.

Le suivi de CI KFFI #131 a reproduit deux défauts de fixture : retour avant le UP derrière une file de 1000 messages, et absence de sonde native lorsque le focus correspondait déjà. Les deux régressions ont échoué avant leur correction. Au commit KFFI `b297dd3941663b03279d2c350225323cf441d58d`, l'exécution fraîche passe **110 core, 102 Win32 JVM et 40 natifs**, sans échec/erreur/skip ; douze JVM supplémentaires passent les 48 cas de reconnaissance. Après republication locale sérialisée, Kadre `b501d990acfb6bf8505f464efb061dd5860d5e88` passe à nouveau **140 portables et 42 natifs** avec les deux tâches explicitement relancées. La CI conserve les JUnit et une trace bornée des contacts dans les messages d'échec. Les jars de production sont inchangés ; seul le classifier de test a changé.

## Reproduction et artefacts

```powershell
scripts/test-kadre-win32-contracts.sh '-PkadreKffiLocal=true' '-PkadreKffiWin32Version=1.0.0-win32-rich-input-local-SNAPSHOT' :kadre:runtime:jvmTest :kadre:platform:desktop:jvmTest :kadre:contracts:validator:jvmTest :kadre:contracts:validator:validateContractRegistry
```

Exécuter ce script depuis Git Bash sous Windows avec JDK25 ; le watchdog externe requiert Perl. Il exige **23 JSON** : BCK-025, SES-002, PMP-001 et W32-001..020, chaque preuve au SHA testé, rapports natifs frais et aucun scénario obligatoire ignoré. Les artifacts CI comprennent preuves, JUnit et diagnostics. La CI n'active pas Maven local.

L'alias `1.0.0-win32-rich-input-local-SNAPSHOT` est exclusivement local. La substitution core root/JVM ne s'applique qu'avec `kadreKffiLocal=true`, pour que le graphe Desktop teste bien le core publié avec Win32. **Ne jamais republier cet alias pendant une compilation/exécution consommatrice.** Les jars source et Maven local ont été comparés octet par octet :

| Source KFFI testée | Artefact | SHA256 |
| --- | --- | --- |
| `b297dd3941663b03279d2c350225323cf441d58d` | kffi-jvm | `11508EBC6E06DE32FC9DBE3E9E745C9E0B6D66E2ABD177837113F5406A1E1A59` |
| même source | kffi-win32-jvm | `F9106A6E48AFE091C1B8A71267561F25C8260D7CE5A5E97A793BED078DDDA0A9` |
| même source | classifier native-test-support | `5A4C1114DD63D1CECA528FA95EC01EA4F3C0A4873E5496AE1BF4C816FA128B66` |

Le premier commit de la PR KFFI #131, `15b5341fa76d325349c5e3630a022ac6444c97f9`, reprend sur historique linéaire l'arbre `432225750dba1549eda277405bd59f643ff29dca` de la source initialement testée `8ea72efd436623dc5f63d6c4c31391d4a76e09b2`. Le suivi CI ajoute les diagnostics et corrections de fixture décrits ci-dessus, sans changer les jars de production.

Le classifier de test contient le client COM et le fixture d'injection ; aucun injecteur n'entre dans les jars de production. La publication Maven locale ne vaut pas publication Central. Après merge/publication KFFI : vérifier le workflow, épingler le snapshot timestampé Win32 root/JVM, résoudre avec `kadreKffiLocal=false`, puis relancer la même gate au nouveau HEAD. Les exigences KFFI restent ouvertes jusqu'à cette preuve distante.

## Limites et décisions

Android n'a pas de SDK configuré sur cet hôte ; UIKit natif ne compile pas sous Windows. Les compilations Web JS/Wasm et JVM AppKit/Wayland/Win32 ont été vérifiées pour les changements communs précédents. Les vérifications natives des autres plateformes restent à leur CI.

Un sink de touches TSF autonome essayé dans le fixture a été refusé par Windows avec E_INVALIDARG pour deux IDs clients ; il a été retiré. Aucun TIP/IME n'a été enregistré globalement pour contourner ce refus. Le test automatisé de consommation prouve le routage avec une décision contrôlée ; il ne prouve pas la décision d'un IME installé.

GetTextExt ne fournit que le caret connu, pas un layout complet. Les tests de déplacement lisent le DPI HWND effectif sur cet environnement ; un vrai changement d'écran/DPI reste manuel. Les pressions/tilt/twist invalides restent null ; aucun DeviceId public n'est inventé depuis un handle natif. Les fonctionnalités des phases ultérieures (drop, raw input, PTP2, rendu/compositeur) ne sont pas activées ici.
