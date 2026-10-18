# Win32 phase 6 — réception manuelle

Statut au 18 octobre 2026 : **non exécutée**. Les tests TSF/COM et l'injection Windows prouvent des chemins natifs automatisés ; ils ne constituent pas une réception d'IME ou de matériel physique. La phase reste en qualification et la PR reste draft.

Environnement automatisé relevé : Windows 25H2 x64, build **26200.9711**, JDK Temurin 25.0.3. Profils utilisateur présents : **fr-FR / 040C:0000040C**, **en-GB / 0809:00000809**. Aucun profil japonais/chinois n'apparaît dans cette liste. Aucun profil ou service de texte n'a été installé par ces travaux. La disponibilité du matériel physique n'a pas été auditée.

## Démarrer la réception

Après publication du nouveau snapshot KFFI et mise à jour du catalogue :

```powershell
./gradlew.bat :kadre:backend:win32:win32RichInputManual '-PkadreKffiLocal=false' --console=plain
```

Pendant le développement conjoint seulement, remplacer la propriété distante par les deux propriétés explicites `'-PkadreKffiLocal=true' '-PkadreKffiWin32Version=1.0.0-win32-rich-input-local-SNAPSHOT'`.

Le harness ouvre deux fenêtres Kadre et un aperçu Swing non activable. **La saisie se fait dans les fenêtres Kadre**, l'aperçu montre localement les documents adoptés et la préédition. Il n'y a aucun contrôle EDIT caché recevant les frappes. Les logs contiennent uniquement catégories, compteurs, révisions, IDs publics, phases, métriques et attributs numériques ; ne pas y ajouter le texte, les sélections ou un identifiant natif de device. Le document initial est `ab`, sélection `[1,1]`, révision 10 ; le harness adopte chaque modification avec une nouvelle révision.

La géométrie TSF communiquée est un curseur fixe au point **client logique (24,24)**, taille **2×24**. L'aperçu séparé ne constitue pas un layout de caractères du HWND. `GetTextExt` n'invente pas les rectangles des autres plages et peut répondre `TS_E_NOLAYOUT`. Vérifier le placement des candidats près du curseur communiqué ; un IME qui ne fonctionne pas avec cette géométrie maintient la réception ouverte. Fermer les deux fenêtres Kadre termine la session et l'aperçu.

## Feuille de réception

Pour chaque ligne, renseigner date, Windows/build, layout/IME/version, appareil (désignation métier, sans identifiant brut), résultat et anomalie. Aucun succès n'est déduit de la compilation du harness.

| Scénario | Résultat actuel | Vérification requise |
| --- | --- | --- |
| IME japonais ou chinois | Non exécuté | Préédition, sélection dans la composition, commit et annulation ; un seul commit public |
| Emoji hors BMP dans l'IME | Non exécuté | Positions UTF-16 cohérentes avant/après adoption 10→11, aucun demi-surrogate |
| Dead key et AltGr, français/autre layout | Non exécuté | Accents/caractères corrects, aucune insertion depuis PhysicalKey ni double traduction |
| IME consommant une touche | Non exécuté | Vraie décision TSF TRUE du service installé ; aucun second WM_CHAR/événement ordinaire. Le test automatisé contrôle seulement la décision du filtre |
| Focus pendant préédition | Non exécuté | Alterner les deux HWND ; suspension conservant la composition jusqu'à fin native, reprise sur le bon document |
| Fermeture pendant préédition | Non exécuté | Aucun callback ancien vers l'autre fenêtre ; aucune fenêtre/processus bloqué |
| Candidats après déplacement | Non exécuté | Origine écran réactualisée sans révision de document inventée |
| Candidats entre écrans à DPI différents | Non exécuté | DPI HWND courant, origine négative si applicable, pas de coordonnée HIMETRIC présentée comme pixel |
| Deux contacts tactiles physiques | Non exécuté | Deux IDs indépendants, mêmes IDs/stamps interaction/input, pressions seulement si connues |
| Souris et stylet simultanés | Non exécuté | Pointeurs distincts, hover, bouton primaire et Barrel ; aucun clic de souris promu en double |
| Gomme physique | Non exécuté | Kind Eraser, attributs conformes aux masks ; pas d'attribut fabriqué |
| Pan, pinch, rotation, double tap physiques | Non exécuté | Débuts/fins exacts, deltas incrémentaux et unités logiques ; aucune inertia/TouchpadPressure annoncée |
| Capture/focus/hide/minimize/cancel | Non exécuté | Annulation des contacts/gestes, aucun contact ressuscité par un ancien historique |

Les cas automatisés correspondants sont recensés dans [la validation](WIN32-PHASE-6-VALIDATION.md). Cette feuille reste ouverte tant que chaque catégorie annoncée n'a pas sa réception réelle.
