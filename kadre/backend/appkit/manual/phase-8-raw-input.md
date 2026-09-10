# Cahier manuel AppKit — Phase 8 raw input

La CI prouve l’admission avant l’appel natif, le partage du prompt, le fan-out
par accès, la révocation, les budgets, le teardown, le câblage public et le
preflight KFFI typé. Elle ne demande jamais Input Monitoring et n’installe
jamais de tap global réel. Ce cahier couvre seulement les observations qui
exigent un périphérique réel et le réglage macOS **Input Monitoring**. Il ne
consigne que les deltas et états publics : ni touches, ni coordonnées, ni
pointeurs natifs, ni contenu saisi.

Lancer le harness visible :

```shell
./gradlew :kadre:backend:appkit:phase8RawInputHarness \
  --args='--record=kadre/backend/appkit/build/manual/phase-8-raw-input.tsv --build-id=<commit-ou-artefact>'
```

Commandes : `snapshot`, `request`, `close-access <id>`,
`result M1..M7 pass|fail|not-applicable <note>`, `close`, `finish`. La commande
`request` peut être utilisée plusieurs fois : deux accès actifs doivent recevoir
les mêmes mouvements. Un run sans opérateur, périphérique ou permission est
`not-applicable`, jamais `pass`.

| ID | Manipulation | Attendu |
|---|---|---|
| M1 | Lancer sans permission Input Monitoring puis `request`. | Capability `RequiresPermission(InputMonitoring)` avant demande ; failure publique `PermissionDenied(RawInput)` si le système refuse. |
| M2 | Autoriser Kadre dans Réglages Système, relancer si macOS le demande, puis `request`. | Access `Active` et deltas `DeviceCount` issus d’une souris ou d’un trackpad réel. |
| M3 | Exécuter `request` deux fois, déplacer le périphérique, puis `close-access 1`. | Chaque accès reçoit les mêmes deltas ; fermer le premier ne ferme pas le second. |
| M4 | Avec un accès actif, saisir au clavier et déplacer la souris hors de la fenêtre Kadre. | Seuls les `RAW_INPUT_EVENT` portent les deltas globaux ; aucun événement raw n’est injecté comme `ORDINARY_INPUT_EVENT`. |
| M5 | Révoquer Input Monitoring pendant qu’un accès est actif, puis ramener l’application au premier plan et `snapshot`. | Capability devient indisponible avant la suspension de l’accès ; aucun nouveau delta n’est livré tant que l’accès n’est pas réautorisé. |
| M6 | Réautoriser Input Monitoring, ramener l’application au premier plan et faire une nouvelle demande si nécessaire. | Un nouveau source peut être admis sans laisser l’ancien actif ; l’état final observé est cohérent avec la capability. |
| M7 | Laisser au moins un accès actif, puis `close`. | `TERMINAL_STABILITY` confirme que tous les accès sont `Closed` et qu’aucun événement input ordinaire n’arrive après le detach. |

Inclure dans le compte rendu macOS, architecture, matériel, périphérique utilisé,
état initial de la permission, build id et toute restriction d’administration ou
sandbox. Le réglage de permission est une action explicite de l’opérateur : le
harness ne le modifie jamais.
