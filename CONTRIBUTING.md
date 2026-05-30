# Contribuer à Koreos

## Build & tests

```bash
./gradlew build          # compile + tests de toutes les cibles
./gradlew check          # tests + validations (dont la stabilité ABI)
```

## Stabilité de l'API publique (ABI)

Les modules publiés (`koreos-core`, `koreos-appkit`, `koreos-uikit`,
`koreos-android`, `koreos`) sont protégés par la validation ABI intégrée au plugin
Kotlin. Si une PR modifie l'API publique, `checkKotlinAbi` (exécutée par `check`)
**échoue** tant que le dump de référence n'est pas mis à jour.

En cas de changement d'API **intentionnel** :

```bash
./gradlew updateKotlinAbi          # régénère les dumps <module>/api/
git add **/api/                     # commiter les dumps dans la même PR
```

Voir [docs/koreos/api-stability.md](docs/koreos/api-stability.md) pour le détail.

## Tests d'`ApplicationHandler` sans backend natif

Le module `koreos-test` fournit `ScriptedEventLoop` et le DSL `scriptedTest { … }`
pour tester un handler de façon déterministe. Voir
[docs/koreos/testing.md](docs/koreos/testing.md).
