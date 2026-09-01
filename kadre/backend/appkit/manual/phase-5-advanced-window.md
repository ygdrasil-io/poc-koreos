# Cahier manuel AppKit — Phase 5 fenêtre avancée

`WIN-006`, `APK-011`, `INT-001` et `APK-012` sont prouvés par les tests
automatisés nommés dans le registre. Ce cahier isole les constats qui demandent
un opérateur dans une session macOS visible ; il ne transforme jamais une
observation manuelle en preuve CI.

Lancer le harness depuis une session macOS visible, avec un opérateur devant la
fenêtre :

    ./gradlew :kadre:backend:appkit:phase5AdvancedWindowHarness \
      --args='--record=kadre/backend/appkit/build/manual/phase-5-advanced-window.tsv --build-id=<commit-ou-artefact>'

Commandes : `snapshot`, `transparent`, `opaque`, `attention informational`,
`attention critical`, `attention none`, `install-move-handler`, `move`,
`result M1..M5 pass|fail|not-applicable <note>`, `close` et `finish`.
Après `move`, l'opérateur presse réellement le bouton principal dans la fenêtre :
le harness ne fabrique ni événement pointeur ni geste. EOF déclenche `finish`.
Les métadonnées machine, snapshots, événements fenêtre/input, outcomes
d'interaction et résultats de commande sont enregistrés ; la fermeture vérifie
ensuite une fenêtre de stabilité de 250 ms.

Un run sans observateur visible, y compris CI ou desktop distant sans
observation fiable, inscrit chaque scénario concerné comme `not-applicable`.
Il ne doit jamais être consigné `pass` et ce statut n'est pas un échec CI.

| ID | Manipulation opérateur | Attendu observable et limite exacte |
| --- | --- | --- |
| M1 | Exécuter `transparent`, puis `opaque`, et relever les snapshots/événements. | Le readback public passe à `transparent=true`, puis `false`. La transparence est l'opacité/readback de la fenêtre native ; une translucidité visible exige un alpha dessiné par l'application. Kadre ne revendique aucun effet de compositor. |
| M2 | Exécuter `attention informational`, puis observer la réaction éventuelle de l'OS. | La commande est admise. Son rendu visuel dépend de la politique macOS, de l'utilisateur et du host ; noter toute absence plutôt que d'en déduire un échec de l'API. |
| M3 | Exécuter `attention critical`, puis `attention none`. | La demande critique puis l'annulation best-effort sont admises. Aucune persistance ni visibilité n'est promise. |
| M4 | Exécuter `install-move-handler`, puis relever le résultat et les capabilities surface. | Un handler synchrone pour `BeginWindowMove` est installé ; les interactions armées restent hors scope. |
| M5 | Exécuter `move`, puis effectuer une vraie pression pointeur dans la fenêtre et déplacer la souris pendant la pression. | Le handler, l'outcome et l'input pointeur sont enregistrés. Le déplacement natif provient du même callback de pression ; ne pas remplacer ce geste par une commande ou un événement synthétique. |

Ne pas utiliser ce harness pour revendiquer blur, icône, resize, position externe,
fullscreen exclusif ou interactions armées : ces comportements restent reportés
ou explicitement non supportés. `contentProtection` demeure
`Unsupported(UpdateWindow)` sur AppKit : `NSWindowSharingNone` est legacy et
inutilisable comme sécurité de capture ; Kadre ne formule aucune promesse
anti-capture.

Une application Kotlin AppKit qui veut installer et posséder elle-même une
`NSVisualEffectView` peut utiliser KFFI dans `Window.withDesktopHandle`, avec
des adresses AppKit garanties non nulles et non zéro, utilisables seulement dans
le callback admis. Une copie du handle ou de ses adresses ne doit pas être
retenue ni utilisée après son retour. La fermeture attend une lease déjà admise
et un appel ultérieur retourne `Closed(Window)`. Kadre ne possède ni cette vue
ni un renderer.
