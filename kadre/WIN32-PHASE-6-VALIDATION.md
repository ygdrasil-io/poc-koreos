# Win32 phase 6 — qualification conjointe

L'implémentation couvre TSF, composition/caractères, contacts/stylets et gestes Windows. [KFFI #131](https://github.com/Graphiks-org/kffi/pull/131) est fusionnée au commit `69ff1f9f3cf359c052378609322c033ffca024e0` et son [workflow de publication](https://github.com/Graphiks-org/kffi/actions/runs/39169354751) est réussi. Le snapshot distant `1.0.0-20261018.214839-64` est épinglé pour Win32 root et JVM, puis qualifié avec Maven local désactivé. **La phase n'est pas déclarée terminée** : la [réception manuelle](WIN32-PHASE-6-MANUAL.md) reste ouverte.

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

Après adoption distante et intégration de `master` (`184d894c`, phase5 Wayland), la suite complète passe : **452 runtime, 140 Win32 portables, 42 Win32 natifs, 18 Desktop et 71 validator**, zéro échec/erreur/skip. Le watchdog avec `kadreKffiLocal=false` passe au commit `3287695baa4d18bd490c9227a854ae93d39c6475` : tests frais en 4m04s, génération en 1m et **23 JSON** validés au même SHA. Les **4 tests WaylandAdvancedWindowO2Test** passent aussi après la résolution du conflit de listes ; `WIN-013`/`INT-005` et tous les contrats Win32 sont conservés. Un précédent run avait révélé le deadlock worker/UI désormais corrigé par régression déterministe. KFFI : **110 core, 102 Win32 JVM, 40 natifs**, zéro échec/erreur/skip en exécution fraîche ; les 40 cas natifs passent également sur son runner CI. La génération des **125 fonctions** concorde avec le SDK épinglé. Le harness manuel compile ; il n'a pas été exécuté comme réception. La [revue finale](WIN32-PHASE-6-REVIEW.md) décrit les quatre Important corrigés, le deadlock révélé par la gate, le Minor différé et les décisions chronologiques.
Quatre mutations temporaires ont rendu rouges leurs sentinelles : accepter une epoch non admise, partager un token entre contacts, ignorer le DPI et poursuivre Translate/Dispatch malgré un filtre TRUE (**3 portables + 1 native**). Les octets source ont été restaurés dans un finally. La vraie gate23 a ensuite refusé un `W32-020.json` manquant et le même fichier portant un SHA périmé ; les octets de la preuve ont également été restaurés. Les preuves sont régénérées au HEAD de livraison après les derniers changements de rapport/CI.

Le fixture d'injection confirme la réception d'un vrai DOWN/UP sur le HWND ainsi que le foreground/focus avant d'installer les collecteurs, même si le focus correspond déjà. Seules ces sondes de préparation peuvent être répétées ; aucune séquence mesurée n'est rejouée. Après injection, la pompe attend une période sans message de 30ms, avec une borne de 3s, au lieu de traiter un nombre fixe de messages. Cette préparation n'est ni un scénario mesuré ni une preuve d'activation par premier contact en arrière-plan.

Le suivi de CI KFFI #131 a reproduit deux défauts de fixture : retour avant le UP derrière une file de 1000 messages, et absence de sonde native lorsque le focus correspondait déjà. Les deux régressions ont échoué avant leur correction. Au commit KFFI `b297dd3941663b03279d2c350225323cf441d58d`, l'exécution fraîche passe **110 core, 102 Win32 JVM et 40 natifs**, sans échec/erreur/skip ; douze JVM supplémentaires passent les 48 cas de reconnaissance. Après republication locale sérialisée, Kadre `b501d990acfb6bf8505f464efb061dd5860d5e88` passe à nouveau **140 portables et 42 natifs** avec les deux tâches explicitement relancées. La CI conserve les JUnit et une trace bornée des contacts dans les messages d'échec. Les jars de production sont inchangés ; seul le classifier de test a changé.

## Reproduction et artefacts

```powershell
scripts/test-kadre-win32-contracts.sh '-PkadreKffiLocal=false' :kadre:runtime:jvmTest :kadre:platform:desktop:jvmTest :kadre:contracts:validator:jvmTest :kadre:contracts:validator:validateContractRegistry
```

Exécuter ce script depuis Git Bash sous Windows avec JDK25 ; le watchdog externe requiert Perl. Il exige **23 JSON** : BCK-025, SES-002, PMP-001 et W32-001..020, chaque preuve au SHA testé, rapports natifs frais et aucun scénario obligatoire ignoré. Les artifacts CI comprennent preuves, JUnit et diagnostics. La CI n'active pas Maven local.

La résolution avec `--refresh-dependencies` et `kadreKffiLocal=false` confirme root et JVM `1.0.0-20261018.214839-64` depuis Central Portal. Une contrainte stricte JVM liée au même pin empêche la redirection `1.0.0-SNAPSHOT` des métadonnées root de suivre une publication ultérieure : le défaut a été observé dans `jvmRuntimeClasspath`, puis corrigé et revérifié. Le classifier de test utilise le même timestamp. Le core JVM distant résolu est `1.0.0-20261018.214839-86`. Les trois jars distants téléchargés sont identiques aux octets qualifiés localement :

| Source KFFI publiée | Artefact | SHA256 |
| --- | --- | --- |
| `69ff1f9f3cf359c052378609322c033ffca024e0` | kffi-jvm, snapshot `86` | `11508EBC6E06DE32FC9DBE3E9E745C9E0B6D66E2ABD177837113F5406A1E1A59` |
| même source | kffi-win32-jvm | `F9106A6E48AFE091C1B8A71267561F25C8260D7CE5A5E97A793BED078DDDA0A9` |
| même source | classifier native-test-support | `5A4C1114DD63D1CECA528FA95EC01EA4F3C0A4873E5496AE1BF4C816FA128B66` |

L'alias `1.0.0-win32-rich-input-local-SNAPSHOT` reste exclusivement destiné au développement. La substitution core root/JVM ne s'applique qu'avec `kadreKffiLocal=true`, pour qualifier la paire locale. **Ne jamais republier cet alias pendant une compilation/exécution consommatrice.** Il n'est utilisé ni par la gate distante ci-dessus, ni par la CI.

Le classifier de test contient le client COM et le fixture d'injection ; aucun injecteur n'entre dans les jars de production. Les besoins KFFI-WIN32-003/004/005 sont clos après cette preuve de publication/adoption distante. Les réceptions IME installé, stylet/gomme, contacts physiques, premier contact en arrière-plan et DPI mixtes restent non exécutées ; elles gardent la PR en draft et la phase en qualification.

## Limites et décisions

Android n'a pas de SDK configuré sur cet hôte ; UIKit natif ne compile pas sous Windows. Les compilations Web JS/Wasm et JVM AppKit/Wayland/Win32 ont été vérifiées pour les changements communs précédents. Les vérifications natives des autres plateformes restent à leur CI.

Un sink de touches TSF autonome essayé dans le fixture a été refusé par Windows avec E_INVALIDARG pour deux IDs clients ; il a été retiré. Aucun TIP/IME n'a été enregistré globalement pour contourner ce refus. Le test automatisé de consommation prouve le routage avec une décision contrôlée ; il ne prouve pas la décision d'un IME installé.

GetTextExt ne fournit que le caret connu, pas un layout complet. Les tests de déplacement lisent le DPI HWND effectif sur cet environnement ; un vrai changement d'écran/DPI reste manuel. Les pressions/tilt/twist invalides restent null ; aucun DeviceId public n'est inventé depuis un handle natif. Les fonctionnalités des phases ultérieures (drop, raw input, PTP2, rendu/compositeur) ne sont pas activées ici.
