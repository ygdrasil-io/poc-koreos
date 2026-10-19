# Win32 phase 7 — charter de réception manuelle du drop OLE

**Objet :** réceptions matérielles qui ne sont pas remplaçables par une preuve automatisée : drag inter-applications réel, rendu différé d'une source vivante, apparence du curseur de refus. Aucun verdict automatique : l'opérateur consigne résultat et limites.
**Harness :** `./gradlew :kadre:backend:win32:win32DropManual -PkadreKffiLocal=true -PkadreKffiWin32Version=<alias-local>` (fenêtre Kadre + panneau de comptage ; toggle accept/refus). Le rapport n'affiche que des catégories et compteurs ; les chemins et contenus restent dans l'aperçu local et hors stdout.
**Environnement à consigner par essai :** Windows 11 build, écran(s)/DPI, source du drag (Explorer, navigateur, application Office), résultat, limites.

## M1 — Drag inter-applications Explorer → fenêtre Kadre (fichiers)

Glisser 1 puis N (≥ 5) fichiers depuis l'Explorateur vers la fenêtre Kadre en mode acceptation.

Attendu : curseur copie pendant le survol ; à l'entrée, `Entrée : {File=N}` ; au lâcher, `Drop lu : N item(s)`, octets cumulés cohérents avec les tailles de fichiers. Chaque claim est unique ; un second lâcher reconstruit une nouvelle offre. Les lectures répétées d'un même drop (Replayable) passent.

## M2 — Texte et URL

Glisser une sélection de texte (éditeur de texte → fenêtre) puis une URL (barre d'adresse de navigateur → fenêtre).

Attendu : catégories `Text` puis `Uri` ; contenu vérifié localement dans l'aperçu du destinataire, jamais dans stdout. Un texte hors BMP (emoji) traverse sans corruption. Une URL `UniformResourceLocatorW` est lue sans code page improvisé.

## M3 — Refus explicite

Basculer le toggle sur refus puis refaire M1/M2.

Attendu : curseur `DROPEFFECT_NONE` (cercle interdit) pendant tout le survol ; compteur `refus` incrémenté ; aucune offre transférable ; la source Explorer ne déclenche aucune copie.

## M4 — Fermeture pendant un drag actif

Démarrer un drag depuis Explorer, maintenir le bouton au-dessus de la fenêtre Kadre, et fermer la fenêtre Kadre via Alt+F4 depuis le clavier (sans lâcher).

Attendu : la session termine l'offre (`OwnerClosed`) sans crash ni deadlock ; le drag côté Explorer se termine proprement ; la pompe reste vivante jusqu'à la fin du drag. Toute fuite du target natif (vérifiable en rejouant une ouverture/fermeture) est consignée.

## M5 — Source à rendu différé et gros fichiers

Glisser une sélection depuis une application connue pour le rendu différé (par exemple une pièce jointe non téléchargée dans un client mail, ou une cellule Excel copiée en tant que fichier virtuel si disponible), puis un fichier > 1 Go.

Attendu : la lecture post-drop rend soit le contenu, soit une failure plateforme explicite — jamais de contenu fabriqué ni de blocage permanent. Le dépassement de budget de lecture (`maxBytes`) remonte comme `ResourceLimitExceeded`/failure, sans troncation silencieuse. Les items `TYMED_ISTREAM` éventuels restent single-use : une seconde lecture du même item échoue avec `Closed(DropItem)`.

## Hors périmètre de ce charter

- Drag sortant (`DoDragDrop`, Kadre source) : non couvert par la phase 7.
- Formats spécialisés (pièces jointes Outlook, distributions navigateur, fichiers virtuels `CFSTR_FILEDESCRIPTORW`) : travail ouvert nommé ; leur réception propre fera l'objet d'une tranche dédiée.
- Multi-écrans à DPI mixtes pendant un drag : croise le charter DPI de phase 3 ; consigner si observé.
