# Win32 phase 5A — validation conjointe

## Périmètre

La première sous-tranche de la phase 5 livre les mutations de titre, taille cliente logique, contraintes min/max, position physique, resizabilité, décorations/boutons et fullscreen borderless réversible. La phase 5 entière reste ouverte : niveaux, icône, attention, curseurs, hit testing, confinement et actions transitoires demandent leurs propres qualifications. Aucun changement de l'API publique n'est effectué.

## Reproduction locale

Dans la branche KFFI associée, sur Windows/JDK 25 :

```powershell
$env:KFFI_WIN32_INTEGRATION = '1'
./scripts/publish-win32-local.ps1 -Version 1.0.0-win32-phase5-local-SNAPSHOT
```

Dans Kadre :

```powershell
./gradlew.bat :kadre:backend:win32:win32ContractTests :kadre:runtime:jvmTest :kadre:platform:desktop:jvmTest :kadre:contracts:validator:jvmTest :kadre:contracts:validator:validateContractRegistry :kadre:contracts:validator:generateWin32ContractEvidence '-PkadreKffiLocal=true' '-PkadreKffiWin32Version=1.0.0-win32-phase5-local-SNAPSHOT' --console=plain
```

Le snapshot distant de phase 4 reste épinglé dans le catalogue jusqu'à la publication des nouveaux wrappers. Cette branche Kadre doit rester draft tant que la dépendance de phase 5 n'est pas publiée puis épinglée. Les succès locaux avec Maven local ne sont pas des succès de résolution distante.

## Preuves

- Gate Kadre complète : 621 tests (89 portables Win32, 26 natifs Win32, 418 runtime, 18 Desktop, 70 validator), zéro failure/error/skip. Avec KFFI : 757 tests. Les 16 JSON de contrats sont produits et validés ; W32-012/013 s'ajoutent aux 14 preuves précédentes.
- KFFI : 110 tests core et 26 Win32, sans échec ni skip, exécutés avec le gate natif activé. Publication locale des jars effectivement testés sous une version distincte.
- Génération de 85 fonctions User32/Kernel32/GDI/DWM, quatre ajouts, `--check` identique. Limite d'outillage conservée : le parseur Clang signale `excpt.h` absent dans le SDK installé ; aucune génération sans ce diagnostic n'est revendiquée. Les wrappers ajoutés sont exécutés contre Windows réel.
- Cycle RED→GREEN des mutations publiques puis du fullscreen, en portable et natif. Les tests natifs relisent RECT/styles/DPI via les fonctions User32, puis comparent avec l'outcome public et les métriques de surface.
- W32-012 : propriétés et limites natives, application Desktop depuis un worker, readback substitué dans le modèle indépendant, événements corrélés ou externes, cancellation avant claim.
- W32-013 : round-trip natif styles/rectangle, contraintes suspendues et restaurées, rollback après échec d'entrée injecté.
- Le test de fermeture réentrante a révélé le verrou d'admission runtime : la queue native diffère maintenant la mutation même lorsqu'elle est admise sur le thread UI. Le setter et ses callbacks exécutent après le retour du SPI. L'annulation après claim conserve la publication du readback réellement commis.

## Qualification manuelle et limites

Non exécutés : redimensionnement physique dans le menu système, écrans à DPI mixtes, retrait/reconnexion de moniteur, et déplacement dans une boucle modale système. Les tests de queue et les setters automatisés ne valent pas qualification matérielle de ces chemins. Aucune frappe injectée ni modification de résolution n'est utilisée.

Les contraintes sont des tailles clientes logiques converties en track limits au DPI courant. Une mutation native étrangère peut les contourner : Kadre publie alors la taille réelle et retire la contrainte devenue incompatible, plutôt que construire un WindowState invalide. La minimisation conserve les dernières métriques positives. L'entrée fullscreen depuis une fenêtre minimisée/maximisée échoue explicitement ; il faut d'abord la restaurer par le système.

Une mutation de géométrie/chrome pendant le fullscreen est refusée champ par champ avec TemporarilyUnavailable. Un titre reste modifiable. Un échec de restauration fullscreen qui empêche d'établir un état cohérent échoue la session ; aucune réussite n'est fabriquée.
