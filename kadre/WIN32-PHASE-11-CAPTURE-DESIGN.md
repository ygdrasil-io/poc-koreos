# Win32 phase 11 — capture Windows Graphics Capture, contrôle et frames bornées

## 1. Intention et critères de réussite

Livrer le chemin de capture WGC (Windows Graphics Capture) pour fenêtre de session et moniteur, via la frontière KFFI managée, avec le `CapturePort` runtime : admission honnête, frames BGRA8 copiées sous budget, terminaisons typées. Critères : chaque target annoncée a sa preuve ; la configuration précède les frames ; une frame n'est valide que pendant son emprunt ; tous les owners (device/frame pool/session/delegate) sont fermés sur device loss ou source fermée ; aucune conversion implicite HDR/SDR/format/région ; aucun fallback GDI/DXGI.

## 2. Audit et choix d'architecture

- Runtime : `CapturePort` complet (snapshot/permission/refresh/reserve/start/observer/close) ; projection publique `RuntimeCaptureManager` → `CaptureManager`/`CaptureSession`/`collectFrames`+`copyPlanes` ; backpressure (`Latest`/`Buffered`, `maxBufferedBytesPerSession` passé en `maxFrameBytes` — refuser > budget par `ResourceLimitExceeded(CaptureBuffer)` avant allocation). Patron complet = **AppKit capture port** (seam `AppKitCaptureNative`, registry surface, frames Bgra8, terminaisons Stopped/PermissionRevoked/SourceLost).
- Linux #485 = patron « contrôle honnête » (`HostPickerOnly` + refus typés). Win32 va plus loin : flux réel.
- KFFI audit : génération = fonctions plates seulement (kextract) ; interfaces WinRT/COM = manuscrites par architecture. Primitives existantes réutilisables : `Win32TextComAbi` (downcall vtable), `Win32DropComBridge`/`Win32DropComObject` (délégués upcall + quiescence `Win32ComCallbackGate`), `Win32ComApartment`, `Win32ComResources`, patrons `Win32XInput`/`Win32Displays` (port injectable + fake tests + integration gate).

## 3. Frontière KFFI et owners

- Généré (mécanique) : `combase.yaml` (`RoInitialize`/`RoGetActivationFactory`, helpers HSTRING), `d3d11.yaml` (`D3D11CreateDevice`, `CreateDirect3D11DeviceFromDXGIDevice`), `dxgi.yaml` (`CreateDXGIFactory1`), structs `D3D11_TEXTURE2D_DESC`/`DXGI_...` ; script gen + includes étendus.
- Manuscrit : `Win32GraphicsCaptureAbi` (GUIDs + signatures de slots **IInspectable = IUnknown+3 slots** ; interop `IGraphicsCaptureItemInterop::CreateForWindow` slots 3/4 ; factories ItemStatics/FramePoolStatics ; FramePool/Session/Frame/DxgiInterfaceAccess), délégué `FrameArrived` (IUnknown+Invoke, upcall stub + route map + gate), service `Win32GraphicsCapture` (session AutoCloseable, refs LIFO, révocation avec quiescence).
- Owners : device D3D11 + IDirect3DDevice, frame pool, session, item, délégué — tous référencés dans `Win32ComResources`-style LIFO ; `close()` = stop session → fermer frame pool → révoquer délégué (quiescence) → release refs ; idempotent.
- Décision : **`CreateFreeThreaded`** (pas de DispatcherQueue ; callbacks sur thread-pool OS — copie sous lock court, jamais de travail lourd dans `FrameArrived`).

## 4. Port, admission et sources (Kadre)

- Sources livrées : **moniteurs** (clés `win32-display`, via `Win32Displays.snapshot()`, taille = bounds) ; **Surface = travail ouvert** (registry de session non câblée dans cette tranche — la cible est refusée proprement plutôt que satisfaite à moitié) ; **HostChoice refusé** (`Unsupported(CaptureOpen)`, `hostPicker=Unsupported`) — le picker OS Win32 desktop n'existe pas ; chemin séparé futur.
- Décisions par target : `Source(key)` = moniteurs publiées au snapshot courant (révision exacte exigée par le runtime, clé inconnue → `InvalidRequest("target")`) ; `Surface` = refus honnête.
- Permissions : Windows desktop n'impose pas de gate OS pour WGC → `Granted` publié ; disponibilité **vérifiée à l'exécution** par sonde (activation échouée → `Unavailable` cause typée ; jamais supposée).
- Capabilities : `screen`/`window` = `Supported(constraints)` si sonde OK ; formats annoncés = **Bgra8 seul** ; région `Unsupported`.

## 5. Streaming, delivery et terminaison

- Config : taille = frame native (pas de rescale implicite) ; région refusée (`Unsupported(CaptureOpen)`) ; format non-Bgra8 demandé explicitement refusé ; curseur Embedded/EmbeddedWhenAvailable → actif, Hidden → désactivé ; `minimumFrameInterval` non contracté (consigné).
- Frame : `FrameArrived` → `TryGetNextFrame` → QI `DxgiInterfaceAccess` → staging `CopyResource` + `Map` → **copie scalaire immédiate** (`CapturePortFrame(Bgra8, 1 plane)`) ; `> maxFrameBytes` → `ResourceLimitExceeded(CaptureBuffer)` et frame jetée ; timestamps WGC → `sourceTimestamp` ; si la première frame diffère de la taille source → `onReconfigured` (révision +1 exacte).
- Terminaisons typées : `Item.Closed` → `SourceLost` (sans id inventé) ; device loss (RemoveDevice/test de santé) → `Failed(PlatformFailure)` + tout owner fermé ; cancel collector / stop session / teardown parent → `Stopped(...)` ; **aucune frame après terminaison** ; `close` port/réservation/stream idempotents.

## 6. Matrice de réception et gates

| Contrat proposé | Scénarios essentiels | Sentinelle |
| --- | --- | --- |
| W32-027 — contrôle capture win32 | sonde disponibilité typée, sources = moniteurs + surfaces de session seulement, HostChoice refusé, permissions honnêtes, HWND jamais publié | une fenêtre étrangère inventoriée, un HWND croisé dans l'état public, ou un HostChoice satisfait par cible arbitraire |
| W32-028 — frames bornées et terminaisons | start→frames Bgra8 copiées sous budget, reconfigure révision +1, SourceLost sans id, device loss ferme tout, budget refusé avant allocation | une frame hors emprunt vivante, un budget dépassé alloué, ou une frame livrée après terminaison |

Tests portables : port à FakeNative (disponibilité honnête, inventaire moniteurs, refus de cibles, forme des requêtes). Natifs O3 : vraie session runtime — énumération moniteur réelle, flux BGRA8 réel via `collectFrames` (packé, borné, terminaison `Stopped(Requested)`), absence de WGC → `Unavailable` honnête. Charter manuel : multi-moniteur, resize live, contenu protégé (WDA), fermeture fenêtre à chaud.

As-built streaming : frames **tirées par un poller daemon kffi (16 ms)** — la registration `FrameArrived` répond 0x8000001C sur les hôtes observés (consigné) ; `TryGetNextFrame` livrant le même contrat de flux, la voie événementielle reste une optimisation future sans changement de contrat. La taille du pool = taille de l'item (par valeur : `width | height shl 32` en registre x64) ; si la première frame diffère de la taille source → `onReconfigured` (révision +1 exacte).

## 7. Hors périmètre assumé de cette livraison

- **Picker HostChoice** : pas d'API picker pour apps desktop Win32 — chemin séparé futur (owner UI dédié).
- **GDI/DXGI desktop duplication** : aucun fallback sans design/contrat propres.
- **HDR / conversion de format / crop de région** : refus honnête, pas de conversion implicite.
- **Enregistrement audio (WGC 1.0.9+)** : hors tranche.
- **minimumFrameInterval** : non contracté (consigné).

## 8. Risques

- Offsets de slots IInspectable (IUnknown+3) : table manuscrite vérifiée par test d'intégration réel avant toute autre preuve.
- `Map` de texture staging hors thread owner : opéré via device context immédiat thread-agnostique (D3D11 thread-safe si device créé avec multithread protect) — consigné et testé.
- Callbacks FrameArrived thread-pool OS : copie bornée + gate révocable ; quiescence avant release.
- Runner CI sans session interactive/adapter : la sonde retourne `Unavailable` honnête → preuve de contrôle seulement ; le flux réel = tests Windows locaux + charter.
