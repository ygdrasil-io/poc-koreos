# Desk-tour web — charte manuelle

Complément manuel court au filet e2e déterministe (`../e2e/README.md`). Il couvre
ce que l'automatisation headless ne peut pas prouver honnêtement : le **rendu**
(le canvas Compose est opaque pour le filet — aucun pixel, aucun texte lu dedans),
la **saisie réelle** (vrai clavier, vrai IME, parcours de focus), le **geste
humain** sur une note flottante (déplacement, attention, décoration — les
questions de parité avec la note bureau), et les **moteurs hors chromium**. Il
est informatif ; il ne remplace jamais le filet commis, il n'est câblé nulle
part, et une observation manuelle ne fabrique jamais de preuve de contrat : on
la rapporte À CÔTÉ du résultat du filet, pour qu'elle soit reproductible et,
un jour, automatisable.

## Portée et prérequis

La charte couvre le host web de la démo (`DeskTourWebMain`) : l'attach Kadre sur
le conteneur de la page, l'application Compose, et les notes de la démo (divs
avec leur propre viewport — la couture `NoteWindowHost`, voir le README). Le
bureau (`runJvm`) est le miroir de référence : chaque procédure gagne à être
jouée des deux côtés, la comparaison EST l'observation.

Préparation (mêmes recettes que le README) :

```shell
./gradlew :kadre:samples:desk-tour:wasmJsBrowserDistribution
cd kadre/samples/desk-tour/build/dist/wasmJs/productionExecutable
python3 -m http.server 8080
```

Ouvrir `http://127.0.0.1:8080` dans le navigateur visé — chromium pour la
comparaison avec le filet, puis les autres moteurs pour la procédure 4. Le
serveur statique du filet (`../e2e/serve.mjs`, port 4173 par défaut) convient
aussi ; ne pas partager un port avec un serveur manuel oublié (voir le
dépannage du README e2e).

## Procédures

### 1. La lecture visuelle — ce que le filet ne voit pas

Le filet prouve la structure (un canvas dans le conteneur, un div par note, la
géométrie, zéro erreur) ; il ne prouve pas le RENDU. Sur un vrai écran, lire
réellement la page et vérifier :

- la typographie et les textes français de l'interface (le vocabulaire de
  développeur a déjà atteint l'interface sans que personne ne le remarque —
  lire, pas regarder) ;
- le thème : surface claire pendant l'amorçage, puis Material3 rendu par Skia
  (couleurs, arrondis, ombre portée des notes — la page CSS ne dessine que le
  cadre, le contenu vient du canvas) ;
- les refus honnêtes : « demander l'attention » et « changer la décoration »
  d'une note web désactivés AVEC leur motif lisible, l'espace Écrans dans
  l'état réel de la machine, les affordances désactivées avec le leur ;
- la cascade : ouvrir trois notes et vérifier que l'empilement se lit
  (décalage visible, chaque note recouvre la précédente sans la cacher) ;
- l'opacité assumée du canvas n'excuse pas un rendu cassé : un écran noir, un
  texte tronqué, une mise en page débordée sont des défauts réels que le filet
  laisse passer par construction.

Noter le DPR et la densité de l'écran : le filet tourne à DPR 1 ; un écran
Retina est un terrain que le filet ne couvre pas.

### 2. La vraie saisie — clavier, IME, focus

Le filet clique aux coordonnées du miroir mais ne tape jamais. À la main :

- **La session de saisie publique.** Espace Interactions → « Ouvrir une session
  de saisie », puis taper de vraies touches : lettres simples, accents et touches
  mortes (é, è, ç, ê sous macOS), un IME réel si disponible (pinyin, kana),
  retour arrière, flèches. Vérifier : l'état de la session affiché, le dernier
  événement décrit en français, et la cohérence de ce qui arrive (un « é » composé
  en deux événements ou un seul, selon le host — c'est l'état du host qu'on lit,
  pas une promesse de la démo).
- **Le champ d'une note.** Renommer une note depuis SON champ (le viewport de la
  note reçoit la saisie par les écouteurs du canvas de la note — pas par la
  session publique ; ce sont deux chemins, comme au bureau, limite 2 du README).
  Vérifier : le champ suit, « Appliquer » propage le titre dans la liste de la
  fenêtre principale (la note est relue du store à chaque composition).
- **Le focus.** Cliquer dans le champ d'une note, puis dans la fenêtre
  principale, puis tabuler : où va le caret, quel contrôle reçoit la frappe, le
  canvas reprend-il le focus (il porte `tabindex=0`). Le filet n'affirme rien de
  tout cela.
- **L'espace Interactions en marchant dessus.** Bouger le pointeur, enfoncer
  modificateurs et touches pendant que l'espace est visible : les compteurs
  suivent-ils ce que le host publie, et rien de plus.

### 3. Le vrai flux des notes — les questions de parité bureau

Sur le bureau, une note est une fenêtre Kadre : l'OS la déplace, la décore,
lui donne le focus, et les contrôles attention/décoration parlent au gestionnaire
de fenêtres. Sur le web, la note est un div de la démo. À la main, sur les deux
hôtes côte à côte :

- **Déplacer ?** Au bureau, la note se déplace par sa barre de titre. Sur le
  web, vérifier le comportement HONNÊTE : la note est posée par la cascade du
  host et rien ne la déplace — pas de glisser-déposer, et c'est voulu (la
  position est l'affaire du host démo, il n'y a pas de gestionnaire de fenêtres
  sous la page). Vérifier qu'un clic-glissé sur la note ne fait rien de cassé
  (pas d'erreur console, la note reste fonctionnelle).
- **Attention et décoration ?** Au bureau, les actions s'appliquent à la
  fenêtre. Sur le web, elles restent désactivées avec leur motif — vérifier que
  le motif se LIT et dit la vraie raison (une note web n'est pas une fenêtre).
- **Le clic et les deux piles.** Sur le web, un clic dans la fenêtre principale
  traverse les DEUX piles (Compose, puis Kadre par bouillonnement — la sonde,
  Q2-c). Vérifier à la main : l'espace Activité (le journal de Kadre) et la
  réaction Compose répondent au même clic. Un clic sur une NOTE, elle, ne
  concerne que le viewport de la note : le div de note vit dans la couche à
  notes, HORS du conteneur attaché — Kadre n'en voit rien. Vérifier (journal +
  console) que c'est bien le comportement observé.
- **Fermer puis rouvrir.** Fermer une note, en rouvrir : la nouvelle note ne se
  pose PAS sur la survivante (la place suit la clé monotone — le filet
  l'épingle en géométrie ; à l'œil, vérifier que ça se lit pareil).
- **La fenêtre du navigateur.** Redimensionner la fenêtre réelle : le canvas
  principal suit, les notes gardent 420 × 240 (le filet le prouve en
  géométrie ; à la main, vérifier qu'il n'y a pas d'état intermédiaire cassé —
  liseré, flash, canvas décroché de son div).

### 4. Firefox et WebKit — informel, hors périmètre du filet

Le filet est chromium seul (décision documentée : c'est le filet d'un sample ;
la matrice de moteurs est l'affaire du driver de contrats). La procédure ici
est strictement informelle : servir la distribution, ouvrir dans Firefox et
Safari (WebKit) récents — le binaire Kotlin/Wasm exige wasm GC, présent dans
les versions courantes des deux moteurs — et vérifier le grossier : la page
s'amorce, le canvas apparaît, « Créer une note flottante » pose une note, la
saisie arrive, zéro erreur console. Noter TOUT écart (amorce qui pend, entrée
absente, géométrie fausse) avec le nom et la version du moteur ; ne pas en
tirer un verdict de compatibilité — un écart se transforme en question pour le
driver de contrats, pas en contrat du sample.

## Ce que le filet e2e ne prouve pas

La liste honnête, pour garder le filet à sa place :

- le **rendu** : le canvas est opaque — un pixel, un texte, un thème cassés
  passent verts (procédure 1) ;
- la **saisie réelle** : aucun caractère n'est tapé par le filet — IME, touches
  mortes, parcours de focus sont hors de sa portée (procédure 2) ;
- le **geste humain** : déplacement d'une note, feel du pointeur, survol,
  double-clic, sélection (procédure 3) ;
- les **moteurs non chromium** (procédure 4) ;
- les **écrans réels** : DPR ≠ 1, vraie densité, vraie GAM (procédure 1) ;
- la **performance** : cadence, latence de l'amorçage, mémoire des résidus de
  notes fermées (bornés et documentés, mais jamais chronométrés).

## Limites et relevé des observations

Noter chaque anomalie avec : le navigateur, sa version, l'OS, le DPR, la
distribution servie (le hash du répertoire suffit), les étapes exactes, tout
message console, et une capture d'écran quand le défaut est visuel. Ne pas
inférer une capacité d'une seule exécution manuelle, ne pas modifier le filet
pour faire passer une observation, et ne pas câbler cette charte où que ce
soit : une observation manuelle se rapporte À CÔTÉ du résultat du filet, pour
qu'elle soit reproduite, puis automatisée quand le moteur le permet.

## Références

- [Le filet e2e](../e2e/README.md) et son spec (`../e2e/desk-tour-web.spec.mjs`)
- [Les notes de la sonde](../PROBE-NOTES.md) — le mécanisme (viewports, bouillonnement, resize, résidu)
- [Le README du sample](../README.md) — recettes et limites
- Style et épistémologie : les chartes manuelles du driver
  (`kadre/contracts/driver/web/manual/phase-1-lifecycle.md`)
