# Registre contractuel New Kadre

`contracts.tsv` est la source lisible par machine des contrats `planned`, `active` et `retired` définis par `TEST-STRATEGY.md`.

- Une ligne par `contractId` stable.
- Tabulation entre colonnes ; virgule entre valeurs d'une liste.
- `-` représente une liste vide ou une `retirementRef` absente.
- Aucun texte utilisateur ni tabulation n'entre dans une cellule.
- Une entrée devient `active` dans le même commit que ses scénarios, preuves target-specific et sentinelles.
- Une entrée devient `retired` dans le même commit que la suppression de ses scénarios et l'ajout de sa référence de retrait.

Le validateur vérifie uniquement la structure que la CI doit compter. La correspondance sémantique avec les specs et les capabilities reste une responsabilité de code review et de contract suite.
