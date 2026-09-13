# Cahier manuel AppKit — Phase 10.1 GameController

La CI prouve le routage, les snapshots, l’isolation des sessions, l’arbitrage des
effets et le bridge KFFI ouvert en lecture seule. Ce cahier couvre le matériel
réel. Il n’injecte aucun input et ne joue aucun effet sans la commande explicite
de l’opérateur. Sans contrôleur compatible, un scénario est `not-applicable`,
jamais `pass`.

Lancer :

```shell
./gradlew :kadre:backend:appkit:phase10GameControllerHarness \
  --args='--record=kadre/backend/appkit/build/manual/phase-10-gamecontroller.tsv --build-id=<commit-ou-artefact>'
```

Commandes : `snapshot`, `effect <index> <locality>`,
`result M1..M4 pass|fail|not-applicable <note>`, `close`, `finish`.
`effect` est volontairement opt-in et joue une pulsation d’une seconde seulement
si le gamepad sélectionné annonce la locality demandée.

Les modifications de contrôles sont consignées automatiquement dans
`GAMEPAD_SNAPSHOT` et `GAMEPAD_EVENT` ; il n'est pas nécessaire de synthétiser
une entrée ni de relancer un `snapshot` pour les voir.

Le TSV remplace les `DeviceId` et `GamepadId` redacted par des tokens opaques
locaux au run (`d1`, `g1`, …). Ils ne sont ni des handles ni des IDs natifs ;
ils permettent seulement de suivre une identité publique pendant l'essai.

| ID | Manipulation | Attendu |
|---|---|---|
| M1 | Brancher ou allumer un contrôleur GameController pris en charge, puis `snapshot`. | Un `Gamepad` apparaît ; `devices` reste vide ; les capacités ne listent que les localities réelles. |
| M2 | Bouger sticks, d-pad et boutons puis consulter les snapshots et événements. | Les contrôles changent sous le mapping `Standard` ou `Native`, sans crash ni entrée clavier synthétique. |
| M3 | Débrancher puis reconnecter le contrôleur, avec un `snapshot` entre les deux. | Retrait terminal et événement de lifecycle ; la reconnexion crée une nouvelle identité publique. |
| M4 | Après avoir inspecté les localities de M1, lancer `effect <index> <locality>`. | Effet uniquement si annoncé ; sinon failure typée. Noter la réponse physique sans essayer d’en déduire une correspondance rumble. |

Inclure dans le compte rendu macOS, architecture, modèle du contrôleur, mode de
connexion, build id et toute restriction de sandbox. Le contrôleur PS3 peut
apparaître comme GameController ou seulement comme HID : le second cas est hors
de cette sous-tranche et sera couvert par le MVP HID.
