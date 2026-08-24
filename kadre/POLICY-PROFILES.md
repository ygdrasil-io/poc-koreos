# New Kadre — Profils de policy intégrés

**Statut :** valeurs normatives fermées.  
**But :** rendre `KadrePolicies.Default`, `Realtime` et `Recording` structurellement comparables et reproductibles sur tous les targets.

## 1. Règles

- Chaque profil est exactement la valeur décrite par les tables ci-dessous. Une cellule absente serait un défaut implicite interdit ; il n’y en a aucune.
- Les capacités d’ingress et de collector sont indépendantes même lorsqu’elles ont la même valeur.
- `Default` est la policy des overloads recevant une application lambda/fun-interface. Les overloads `KadreApplicationFactory` utilisent la même valeur par défaut : le choix factory/lambda ne modifie aucune policy.
- `Default` est le minimum structurel obligatoire de tout adapter officiel déclaré supporté. `Realtime`, `Recording` et une copie custom peuvent échouer à `attach` avec `UnsupportedPolicy` sans dégradation silencieuse.
- Les trois propriétés exposent des values profondément immuables. Un appel à `copy` crée une nouvelle policy ; aucun singleton mutable ni registry globale n’existe.

Dans chaque profil, `KadrePolicy.resources` vaut exactement le `ResourceBudgetPolicy` formé par les quinze champs des sections 6 et 7 ; aucun second budget ni default de constructeur n’est appliqué.

Si plusieurs composants d’une policy sont structurellement impossibles, `attach` retourne l’unique `UnsupportedPolicy` correspondant au premier composant dans l’ordre fermé de `KadrePolicyComponent` : `Execution`, `LifecycleEvents`, `HostSignals`, `WindowEvents`, `DeviceEvents`, `InputEvents`, `DevicePolicy`, `CaptureEvents`, `CaptureFrames`, `Diagnostics`, `Resources`. L’adapter ne dépend donc pas de l’ordre de ses probes pour choisir la failure.

Le mapping de validation est exact :

| Champ de `KadrePolicy` | `KadrePolicyComponent` |
|---|---|
| `execution` | `Execution` |
| `lifecycleEvents` | `LifecycleEvents` |
| `hostSignals` | `HostSignals` |
| `window` | `WindowEvents` |
| `deviceEvents` | `DeviceEvents` |
| `input` | `InputEvents` |
| `devices` | `DevicePolicy` |
| `capture.events` | `CaptureEvents` |
| `capture.frames`, `capture.maxBufferedBytesPerSession` | `CaptureFrames` |
| `diagnostics` | `Diagnostics` |
| `resources` | `Resources` |

## 2. Exécution et devices

| Champ | `Default` | `Realtime` | `Recording` |
|---|---|---|---|
| `execution.priority` | `Balanced` | `LatencyFirst` | `Throughput` |
| `execution.shutdownTimeout` | 5 s | 2 s | 30 s |
| `devices.gamepadRouting` | `ActiveSessionOnly` | `ActiveSessionOnly` | `AllForegroundSessions` |
| `devices.effectOwnership` | `ExclusivePerPhysicalDevice` | `ExclusivePerPhysicalDevice` | `ExclusivePerPhysicalDevice` |

`Recording` ne demande donc jamais implicitement `SharedWhenSupported`. Une application qui accepte l’arbitrage et les divergences backend peut créer une policy custom avec cette valeur ; l’attach doit alors la supporter explicitement.

## 3. Flows discrets

Chaque cellule utilise la notation `ingressCapacity / collectorCapacity / ingressOverflow / collectorOverflow`.

| Champ | `Default` | `Realtime` | `Recording` |
|---|---|---|---|
| `lifecycleEvents` | `256 / 256 / FailSession / CancelSlowCollector` | `64 / 64 / FailSession / CancelSlowCollector` | `8192 / 8192 / FailSession / FailSession` |
| `window.discreteEvents` | `256 / 256 / CloseSource / CancelSlowCollector` | `64 / 64 / CloseSource / CancelSlowCollector` | `8192 / 8192 / FailSession / FailSession` |
| `deviceEvents` | `256 / 256 / CloseSource / CancelSlowCollector` | `64 / 64 / CloseSource / CancelSlowCollector` | `8192 / 8192 / FailSession / FailSession` |
| `input.discreteEvents` | `256 / 256 / CloseSource / CancelSlowCollector` | `64 / 64 / CloseSource / CancelSlowCollector` | `8192 / 8192 / FailSession / FailSession` |
| `capture.events` | `256 / 256 / CloseSource / CancelSlowCollector` | `64 / 64 / CloseSource / CancelSlowCollector` | `8192 / 8192 / FailSession / FailSession` |

Le `FailSession` d’un collector `Recording` signifie qu’un consumer ayant demandé un enregistrement exhaustif ne peut pas transformer son propre retard en capture apparemment complète. Les signaux consultatifs et diagnostics restent hors de cette garantie.

## 4. Flows continus et frames

| Champ | `Default` | `Realtime` | `Recording` |
|---|---|---|---|
| `hostSignals` | `Latest` | `Latest` | `Buffered(64, DropOldestAndReport)` |
| `window.geometryChanges` | `Coalesced` | `Coalesced` | `Buffered(8192, FailSession)` |
| `window.redrawRequests` | `Latest` | `Latest` | `Buffered(8192, FailSession)` |
| `input.pointerMotion` | `Coalesced` | `Coalesced` | `Buffered(8192, FailSession)` |
| `input.touchMotion` | `Coalesced` | `Coalesced` | `Buffered(8192, FailSession)` |
| `input.scroll` | `Coalesced` | `Coalesced` | `Buffered(8192, FailSession)` |
| `input.gestureChanges` | `Coalesced` | `Coalesced` | `Buffered(8192, FailSession)` |
| `input.gamepadChanges` | `Latest` | `Latest` | `Buffered(8192, FailSession)` |
| `capture.frames` | `FrameDelivery.Latest` | `FrameDelivery.Latest` | `FrameDelivery.Buffered(3, CloseSource)` |
| `capture.maxBufferedBytesPerSession` | 134 217 728 (128 MiB) | 67 108 864 (64 MiB) | 536 870 912 (512 MiB) |

Pour `Realtime`, « coalesced à chaque tour du host » décrit la cadence d’implémentation visée, mais ne modifie pas la valeur structurelle de la policy : elle reste `ContinuousDelivery.Coalesced` et ne promet aucun nombre de hertz. `Recording` ferme la session ou la source avant de perdre silencieusement une donnée enregistrable ; `CloseSource` sur les frames conserve la failure terminale au lieu de publier un fichier incomplet comme succès.

## 5. Diagnostics

| Champ | `Default` | `Realtime` | `Recording` |
|---|---|---|---|
| `diagnostics.eventBufferCapacity` | 256 | 64 | 8192 |
| `diagnostics.eventOverflow` | `DropOldestEvent` | `DropOldestEvent` | `DropOldestEvent` |
| `diagnostics.dataExposure` | `Redacted` | `Redacted` | `Redacted` |

`eventBufferCapacity` s’applique séparément à l’ingress et à chaque collector. Il n’existe pas de champ collector caché. `IncludePublicMetadata` est uniquement opt-in custom.

## 6. Budgets de cardinalité

| Champ `ResourceBudgetPolicy` | `Default` | `Realtime` | `Recording` |
|---|---:|---:|---:|
| `maxEventCollectorsPerFlow` | 16 | 8 | 16 |
| `maxEventCollectorsPerSession` | 128 | 64 | 128 |
| `maxWindowsPerSession` | 16 | 8 | 32 |
| `maxPendingWindowRequests` | 16 | 8 | 16 |
| `maxPendingInteractionRequests` | 16 | 8 | 16 |
| `maxConcurrentCaptureSessions` | 4 | 2 | 4 |
| `maxConcurrentGamepadEffects` | 16 | 8 | 32 |
| `maxConcurrentDropTransfers` | 4 | 2 | 8 |
| `maxDropChunkBytes` | 262 144 | 65 536 | 1 048 576 |
| `dropTransferClaimTimeout` | 30 s | 5 s | 60 s |

## 7. Budgets de payload

| Champ `ResourceBudgetPolicy` | `Default` | `Realtime` | `Recording` |
|---|---:|---:|---:|
| `maxRetainedPayloadBytesPerSession` | 33 554 432 | 8 388 608 | 134 217 728 |
| `maxTextCodeUnitsPerValue` | 1 048 576 | 262 144 | 4 194 304 |
| `maxMetadataCodeUnitsPerValue` | 4 096 | 2 048 | 16 384 |
| `maxCollectionElementsPerValue` | 4 096 | 2 048 | 16 384 |
| `maxImageBytesPerResource` | 16 777 216 | 4 194 304 | 67 108 864 |

Les valeurs en octets sont binaires exactes, pas des arrondis décimaux.

## 8. Validation fermée

Les constructeurs de policy appliquent exactement ces invariants et lèvent `IllegalArgumentException` s’ils sont violés :

1. toutes les capacités et limites `Int` sont strictement positives ;
2. tous les budgets `Long` sont strictement positifs ;
3. `maxEventCollectorsPerFlow <= maxEventCollectorsPerSession` ;
4. `maxDropChunkBytes <= maxRetainedPayloadBytesPerSession` ;
5. `maxImageBytesPerResource <= maxRetainedPayloadBytesPerSession` ;
6. `capture.maxBufferedBytesPerSession > 0` ;
7. toute `Buffered.capacity > 0` ;
8. `dropTransferClaimTimeout` et `shutdownTimeout` sont finies et strictement positives ;
9. aucun produit ou cumul nécessaire au calcul d’un budget ne peut overflow `Long` ;
10. `DiagnosticPolicy.eventBufferCapacity > 0`.

Il n’existe aucune normalisation, clamp, `Unlimited`, valeur zéro signifiant « désactivé », ni fallback vers un profil intégré.

## 9. Empreinte structurelle et égalité

Deux `KadrePolicy` sont égales si et seulement si tous les champs ci-dessus sont structurellement égaux. Les adapters ne peuvent pas substituer une valeur effective différente : `KadreScope.policy == policy` passée à `attach` après copie défensive des éventuelles collections. Les contraintes natives dynamiques appartiennent aux capabilities et aux résultats d’opération, jamais à une mutation de policy.

## 10. Audit de fermeture

- [x] Chaque propriété de chaque data class de policy possède une valeur pour les trois profils.
- [x] Les capacités ingress et collector sont explicites pour tous les flows discrets.
- [x] Chaque lane continue et chaque stream de frames possède une stratégie exacte.
- [x] Routing et ownership des effets sont exacts pour `Recording`.
- [x] Tous les budgets sont donnés en valeurs entières exactes.
- [x] Tous les invariants de construction sont listés.
