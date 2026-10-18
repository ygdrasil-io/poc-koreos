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

Le watchdog complet a passé au commit `b11a5802ff3060cdb0736bc16bf0040484710cef`, après intégration du master Wayland : **452 runtime, 133 Win32 portables, 42 Win32 natifs, 18 Desktop et 71 validator**, zéro échec/erreur/skip ; **23 JSON** validés au même SHA. KFFI : **110 core, 98 Win32 JVM, 38 natifs**, zéro échec/erreur/skip en exécution fraîche. La génération des **125 fonctions** concorde avec le SDK épinglé. Le harness manuel compile ; il n'a pas été exécuté comme réception.

Quatre mutations temporaires ont rendu rouges leurs sentinelles : accepter une epoch non admise, partager un token entre contacts, ignorer le DPI et poursuivre Translate/Dispatch malgré un filtre TRUE (**3 portables + 1 native**). Les octets source ont été restaurés dans un finally. La vraie gate23 a ensuite refusé un `W32-020.json` manquant et le même fichier portant un SHA périmé ; les octets de la preuve ont également été restaurés. Les preuves sont régénérées au HEAD de livraison après les derniers changements de rapport/CI.

Le fixture d'injection établit explicitement le foreground/focus avant d'installer les collecteurs. Si Windows refuse la demande initiale, une paire DOWN/UP de préparation peut activer la cible, puis le readback est obligatoire. Cette préparation n'est ni un scénario mesuré ni une preuve d'activation par premier contact en arrière-plan. Des essais antérieurs avaient perdu le contact primaire ; cette limite et les corrections de fixture restent consignées dans le journal de validation.

## Reproduction et artefacts

```powershell
scripts/test-kadre-win32-contracts.sh '-PkadreKffiLocal=true' '-PkadreKffiWin32Version=1.0.0-win32-rich-input-local-SNAPSHOT' :kadre:runtime:jvmTest :kadre:platform:desktop:jvmTest :kadre:contracts:validator:jvmTest :kadre:contracts:validator:validateContractRegistry
```

Exécuter ce script depuis Git Bash sous Windows avec JDK25 ; le watchdog externe requiert Perl. Il exige **23 JSON** : BCK-025, SES-002, PMP-001 et W32-001..020, chaque preuve au SHA testé, rapports natifs frais et aucun scénario obligatoire ignoré. Les artifacts CI comprennent preuves, JUnit et diagnostics. La CI n'active pas Maven local.

L'alias `1.0.0-win32-rich-input-local-SNAPSHOT` est exclusivement local. La substitution core root/JVM ne s'applique qu'avec `kadreKffiLocal=true`, pour que le graphe Desktop teste bien le core publié avec Win32. **Ne jamais republier cet alias pendant une compilation/exécution consommatrice.** Les jars source et Maven local ont été comparés octet par octet :

| Source KFFI testée | Artefact | SHA256 |
| --- | --- | --- |
| `e3800bba138806ca498498a9b703616d2bd729b7` | kffi-jvm | `11508EBC6E06DE32FC9DBE3E9E745C9E0B6D66E2ABD177837113F5406A1E1A59` |
| même source | kffi-win32-jvm | `7CFC50339E1C74FE462CB3F539972D37CD63C54B24786F120EA3B215C5719244` |
| même source | classifier native-test-support | `3B415ECF95CDB7ADE8A12018FE7CB05BF76D67BB418D28664992C4AC4A5C97D0` |

Le classifier de test contient le client COM et le fixture d'injection ; aucun injecteur n'entre dans les jars de production. La publication Maven locale ne vaut pas publication Central. Après merge/publication KFFI : vérifier le workflow, épingler le snapshot timestampé Win32 root/JVM, résoudre avec `kadreKffiLocal=false`, puis relancer la même gate au nouveau HEAD. Les exigences KFFI restent ouvertes jusqu'à cette preuve distante.

## Limites et décisions

Android n'a pas de SDK configuré sur cet hôte ; UIKit natif ne compile pas sous Windows. Les compilations Web JS/Wasm et JVM AppKit/Wayland/Win32 ont été vérifiées pour les changements communs précédents. Les vérifications natives des autres plateformes restent à leur CI.

Un sink de touches TSF autonome essayé dans le fixture a été refusé par Windows avec E_INVALIDARG pour deux IDs clients ; il a été retiré. Aucun TIP/IME n'a été enregistré globalement pour contourner ce refus. Le test automatisé de consommation prouve le routage avec une décision contrôlée ; il ne prouve pas la décision d'un IME installé.

GetTextExt ne fournit que le caret connu, pas un layout complet. Les tests de déplacement lisent le DPI HWND effectif sur cet environnement ; un vrai changement d'écran/DPI reste manuel. Les pressions/tilt/twist invalides restent null ; aucun DeviceId public n'est inventé depuis un handle natif. Les fonctionnalités des phases ultérieures (drop, raw input, PTP2, rendu/compositeur) ne sont pas activées ici.
