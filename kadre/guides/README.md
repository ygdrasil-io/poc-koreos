# Guides d'intégration Kadre iOS/tvOS

Ces guides sont les premiers guides de ce dépôt. Leur registre est **des décisions, pas
des promesses** : chaque page décrit ce qui est livré et prouvé, jamais ce qui est
envisagé. Ce qu'un guide ne dit pas n'est pas une promesse en attente — c'est hors
périmètre jusqu'à décision contraire.

**La vérité par feature vit ailleurs.** Chaque guide renvoie à
[`kadre/capabilities/uikit.md`](../capabilities/uikit.md), le registre d'implémentation
à sept colonnes (`feature`, `target`, `minimum déclaré`, `compile gate`, `runtime gate`,
`état absent`, `tests`) : c'est LÀ que se lit, ligne par ligne, ce qui est livré, sur
quelle famille, prouvé par quel gate, et ce qui ne l'est pas. Les guides donnent le
chemin d'intégration ; le registre donne l'état exact de chaque feature. En cas de
divergence perçue, le registre fait foi — et les guides citent la ligne qui les porte.
Les vérités **matérielles** (ce que seul un appareil physique peut prouver) renvoient
aux charters manuels : l'index tenu par la phase 10 (`kadre/DEFERRED-AUDIT-uikit.md`,
nommé par la §4 du registre) et la colonne `état absent` du registre, ligne par ligne.

## Les sept guides

| Guide | Portée |
|---|---|
| [`uikit.md`](uikit.md) | Attacher une session : `KadreIos.attach`, la session, la possession de la surface, le cycle de vie |
| [`swiftui.md`](swiftui.md) | SwiftUI : `KadreHostViewController`, le wrapper `UIViewControllerRepresentable` app-owned, le `updateUIViewController` contractuellement vide, le gate |
| [`multi-scene.md`](multi-scene.md) | Multi-fenêtre : `requestWindow`, le provider app-owned, la corrélation de scènes, le refus mono-scène tvOS |
| [`installation.md`](installation.md) | Distribution : XCFramework statique, publications contractTest, `-PkadreRepository`/`-PkadreVersion`, lien statique et contrainte anti-duplication, préfixe Obj-C |
| [`conditional-capabilities.md`](conditional-capabilities.md) | Lire `capabilities/uikit.md` : unavailable vs unsupported, frontières de klib, charters manuels |
| [`migration-from-kadre-old.md`](migration-from-kadre-old.md) | Migrer depuis le kadre-old : le mapping `API-MIGRATION.md` et les ports livrés |
| [`tvos.md`](tvos.md) | tvOS : mono-scène, honnêteté télécommande (focus système), saisie système, Simulator vs Apple TV physique |

## Deux règles qui traversent tous les guides

1. **Le double-tap est le tap livré.** Là où un guide nomme l'ensemble des gestes, il
   dit **double-tap** : le recognizer livré est un `UITapGestureRecognizer` avec
   `numberOfTapsRequired = 2`, qui livre `Gesture(DoubleTap)` — jamais un tap simple.
   Ensembles exacts livrés : **{double-tap, pan, pinch, rotation}** sur iOS,
   **{double-tap, pan}** sur tvOS (pinch/rotation/survol absents du klib tvOS).
2. **Une session par `UIWindowScene`, la primaire imposée par le host.** Kadre ne
   choisit jamais de fenêtre, ne crée jamais de hiérarchie native et ne possède jamais
   le chrome — le host pose la `UIWindow`, la `UIView` de contenu et le delegate de scène.
