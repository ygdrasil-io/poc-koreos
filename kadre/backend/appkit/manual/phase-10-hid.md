# Cahier manuel AppKit — Phase 10.2 HID

La CI valide les invariants du broker, les inventaires atomiques, les IDs
session-locaux, le rejet des doublons GameController et l'arrêt ordonné des
callbacks. Ce cahier couvre le matériel réel. Le harness ne lit que les
descripteurs détachés et les événements de `DeviceManager` ; il n'ouvre pas de
rapport HID, n'injecte rien et n'envoie aucun output report.

Lancer :

```shell
./gradlew :kadre:backend:appkit:phase10HidHarness \
  --args='--record=kadre/backend/appkit/build/manual/phase-10-hid.tsv --build-id=<commit-ou-artefact>'
```

Commandes : `snapshot`, `result H1..H3 pass|fail|not-applicable <note>`,
`close`, `finish`. Sans matériel approprié, un scénario est `not-applicable`,
jamais `pass`.

Le TSV remplace les `DeviceId` et `GamepadId` redacted par des tokens opaques
locaux au run (`d1`, `d2`, `g1`, …). Ils ne sont ni des handles ni des IDs
natifs ; ils permettent uniquement de constater qu'une reconnexion a reçu une
nouvelle identité publique.

| ID | Manipulation | Attendu |
|---|---|---|
| H1 | Brancher un périphérique HID non exposé par GameController — par exemple un contrôleur PS3 si macOS ne le reconnaît que comme HID — puis `snapshot`. | Le périphérique apparaît uniquement dans `DeviceInventory.Enumerated.devices`, avec un `InputDeviceKind` honnête. Aucun faux `Gamepad`, rapport brut, remapping ou entrée synthétique n'apparaît. |
| H2 | Débrancher puis reconnecter le périphérique de H1, avec un `snapshot` entre les deux étapes. | Le retrait est observé et le snapshot intermédiaire ne contient plus l'appareil. La reconnexion porte un nouveau token opaque, donc une nouvelle identité publique. |
| H3 | Avec un contrôleur réellement reconnu par GameController, consulter `snapshot` avant et après sa connexion. | Ce même contrôleur n'apparaît que dans `gamepads`, jamais simultanément dans `devices`. Un contrôleur HID-only est au contraire uniquement dans `devices`. |

Inclure dans le compte rendu macOS, architecture, modèle, mode de connexion,
build id, état GameController/HID du périphérique et toute restriction de
sandbox. Le fichier TSV doit être conservé comme trace brute : l'absence de
signal matériel ne doit pas être transformée en succès.
