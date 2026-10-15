# Revue finale — phase 5 Win32 complète

Revue indépendante des branches Kadre (515f9c78..1bfe6ef3) et KFFI (71c5b19..de19b55), lecture seule, sans nouvelle délégation. Trois findings Important ont été reproduits avant correction : autorité de move/resize survivant à focus/cancel/release, capture publique périmée après réacquisition refusée, images de curseur attendant le mutex sans budget. Les trois tests ciblés sont verts après correction. La documentation de capacités encore en 5A et le vocabulaire de livraison liée à un consommateur dans KFFI ont été reclassés comme exigences explicites de la demande et corrigés. Aucun minor différé. Une seule passe de corrections, pas de nouvelle revue.

## Décisions et coût si elles se révèlent incorrectes

- Les messages postés ne prouvent pas un bouton physique : payload natif et vraies boucles système sont qualifiés séparément. Souris physique, DPI mixtes et hotplug restent manuels ; coût : ces configurations nécessitent encore une confirmation matérielle.
- Modal, Disabled hit testing, transparence/blur sont des qualifications Unsupported définitives de phase 5 ; Locked/raw, exclusif et embedded restent dans les phases 8/9/12 prévues. Coût : les applications doivent respecter ces contraintes et refus explicites.
- La publication distante complète est un prérequis de sortie du draft. Coût : les succès locaux ne suffisent pas à fusionner la dépendance distante.
- Les écritures ClipCursor étrangères identiques et la course entre read/check/write n’ont pas de jeton d’ownership Windows. La coopération reste nécessaire ; coût : un composant non coopératif peut rendre l’arbitrage ambigu.
- La normalisation ClipCursor hors écran et une défaillance de readback après clipping ne sont pas certifiées par les preuves natives exécutées. Aucun défaut normalisé précis n’est établi par la revue ; coût : ces chemins d’erreur atypiques restent une limite de qualification native.
- Les tests d’images qualifient les handles, slots et durées de vie ; ils ne certifient pas chaque rendu alpha/fallback. Coût : l’apparence des pixels demande une qualification visuelle indépendante.
- DestroyWindow failure dans Win32ManagedWindow.close est hérité et sans nouveau trigger démontré par cette phase. Coût : cette limite de teardown préexistante n’est pas corrigée ici.
- Les anciennes attributions d’import dans l’historique KFFI sont conservées ; aucun nouveau texte ni corps de PR ne cite le dépôt consommateur. Coût : l’historique existant conserve son contexte.
- Les images sont réservées avant l’attente de sérialisation. Une SurfaceUpdate hors budget est refusée en entier à l’admission, comme WindowUpdate. Coût : une application doit réémettre les autres champs d’une commande mixte refusée pour budget.
