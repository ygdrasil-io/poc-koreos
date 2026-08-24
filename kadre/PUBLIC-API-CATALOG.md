# New Kadre — Catalogue fermé de l’API publique commune

**Statut :** catalogue normatif fermé pour le snapshot d’architecture.  
**Autorité :** ce document fixe les déclarations et `DESIGN.md` fixe leur sémantique. En cas de conflit, le catalogue gagne pour la forme Kotlin et `DESIGN.md` gagne pour le comportement ; le conflit doit être corrigé avant tout code.

## 1. Règles de lecture

- La surface publique de `kadre` est exactement l’union des déclarations listées ici et des signatures complètes déjà imprimées dans `DESIGN.md`. Aucun autre helper, constructeur, implémentation, bridge, backend concret ou type `Platform*` n’est public par défaut.
- Les types Kotlin standard, Kotlin Time, kotlinx.coroutines et les types SDK présents uniquement dans une extension de plateforme ne sont pas redéclarés ici.
- Toute collection reçue ou publiée est copiée défensivement et exposée comme snapshot immuable. Toute valeur `Double` ou `Float` est finie et `-0.0` est canonicalisé en `0.0`.
- Les textes sémantiques — titres, surrounding text, texte IME et valeur de `LogicalKey.Character` — sont bornés par `maxTextCodeUnitsPerValue`. Les labels, noms de display/device/gamepad/source/item, media types, codes natifs portables et `PixelFormat.Opaque.code` sont bornés par `maxMetadataCodeUnitsPerValue`. Toute `List`, `Set` ou `Map` publique est aussi bornée par `maxCollectionElementsPerValue`, en plus de son budget de ressource plus spécifique ; `ByteArray` et buffers de pixels suivent leurs budgets en octets dédiés, pas ce nombre d’éléments. Une entrée applicative hors budget produit la failure `ResourceLimitExceeded` de son opération ; une métadonnée backend optionnelle devient `null` avec diagnostic ; une valeur ou collection essentielle rend la source/inventaire indisponible avec la failure de budget correspondante. Aucune de ces valeurs n’est tronquée.
- La visibilité Kotlin imprimée est littérale : le constructeur non qualifié d’une classe publique est public. Seuls un `internal constructor` explicitement imprimé et les IDs/révisions/timestamps opaques fermés en section 3.1 sont internes. Il n’existe aucune catégorie implicite « valeur produite par un backend » qui modifierait une signature. Construire ou copier localement un snapshot, event ou outcome ne lui confère aucune autorité : seuls les objets publiés par l’owner dans ses résultats/flows décrivent Kadre.
- Un bloc marqué « variantes fermées » est exhaustif. Ajouter une variante, un membre, une valeur par défaut ou un export constitue une modification préalable de la spec.
- Les APIs marquées `Kotlin-only` sont publiques pour les consumers Kotlin KMP mais ne sont pas promises comme surface source idiomatique Swift ou JavaScript. Leur wrapper est fixé dans `INTEROP-EXPORTS.md`.

## 2. Index fermé par package

| Package | Déclarations publiques |
|---|---|
| `org.graphiks.kadre.application` | `KadreApplication`, `KadreApplicationFactory`, `KadreLaunchContext`, `KadreLaunchReason`, `KadreScope`, `KadreSession`, `SessionState`, `SessionOutcome`, `SessionStopReason`, `RestorationToken`, `KadreHost`, `KadreLifecycle`, `LifecycleState`, `LifecycleCapabilities`, `LifecycleEvent`, `HostSignal`, `MemoryPressureLevel`, `AttachmentState`, `VisibilityState`, `ActivationState`, `SessionId`, `SessionSequence`, `SessionInstant`, `EventStamp`, `EventDeliverySpan` |
| `org.graphiks.kadre.surface` | `HostSurface`, `SurfaceId`, `SurfaceRevision`, `SurfaceState`, `SurfaceAttachmentState`, `SurfaceVisibility`, `SurfaceOcclusion`, `SurfaceFocus`, `SurfaceTheme`, `SurfaceEvent`, `SurfaceUpdate`, `SurfaceUpdateOutcome`, `RejectedSurfaceField`, `SurfaceProperty`, `SurfaceCapabilities`, `CursorStyle`, `CursorIcon`, `CursorImage`, `PointerCaptureMode`, `HitTestingMode`, `InputDefaultBehavior`, `LogicalPoint`, `LogicalDelta`, `LogicalSize`, `LogicalRect`, `LogicalInsets`, `PhysicalPoint`, `PhysicalSize`, `PhysicalRect`, `PixelRounding`, `PropertyChange`, `BinaryImage`, `ImageFormat`, `ImageConstraints` |
| `org.graphiks.kadre.display` | `DisplayManager`, `DisplayManagerState`, `DisplayInventory`, `Display`, `DisplayId`, `DisplayManagerRevision`, `DisplayRevision`, `DisplayState`, `DisplayType`, `DisplayConnectionState`, `DisplayMode`, `DisplayCapabilities`, `DisplayEvent` |
| `org.graphiks.kadre.window` | `WindowManager`, `WindowManagerState`, `WindowManagerCapabilities`, `WindowManagerRevision`, `WindowCreationMode`, `Window`, `WindowId`, `WindowRevision`, `WindowOperationId`, `WindowCloseRequestId`, `WindowRequestId`, `WindowRequest`, `WindowRequestState`, `WindowRequestOutcome`, `WindowCancellationOutcome`, `WindowSpec`, `WindowSpecBuilder`, `WindowState`, `WindowPhase`, `WindowCapabilities`, `LogicalSizeRange`, `FullscreenKind`, `WindowUpdate`, `WindowUpdateOutcome`, `RejectedWindowField`, `WindowProperty`, `WindowCloseOutcome`, `WindowCloseDecision`, `WindowCloseResponseOutcome`, `WindowEvent`, `WindowCloseReason`, `WindowLevel`, `WindowDecorations`, `WindowSystemButtons`, `WindowAttention`, `FullscreenMode`, `ResizeEdge` |
| `org.graphiks.kadre.interaction` | `InteractionHandler`, `InteractionContext`, `InteractionRegistration`, `ArmedInteraction`, `ArmedInteractionState`, `InteractionAction`, `InteractionActionOutcome`, `InteractionArmOptions`, `InteractionTrigger`, `InteractionTriggerKind`, `ArmedInteractionConstraints`, `InteractionEvent`, `InteractionKind`, `InteractionToken`, `InteractionRequestId` |
| `org.graphiks.kadre.input` | `DeviceManager`, `DeviceManagerState`, `DeviceManagerRevision`, `DeviceInventory`, `DeviceLifecycleEvent`, `InputDevice`, `InputDeviceDescriptor`, `InputDeviceKind`, `DeviceId`, `DeviceConnectionState`, `SurfaceInput`, `SurfaceInputState`, `InputStateRevision`, `InputCapabilities`, `InputEvent`, `InputStateResetReason`, `KeyboardState`, `KeyboardModifiers`, `ModifierKey`, `KeyState`, `PhysicalKey`, `LogicalKey`, `NamedKey`, `KeyLocation`, `PointerState`, `PointerId`, `PointerKind`, `PointerButton`, `PointerButtonState`, `PenState`, `TouchState`, `TouchId`, `TouchPhase`, `GestureKind`, `ScrollDelta`, `Gamepad`, `GamepadId`, `GamepadRevision`, `GamepadSnapshot`, `GamepadDescriptor`, `GamepadMapping`, `GamepadRoutingState`, `GamepadCapabilities`, `GamepadEffectConstraints`, `GamepadState`, `GamepadButton`, `GamepadAxis`, `GamepadButtonValue`, `GamepadAxisValue`, `GamepadEvent`, `GamepadEffect`, `GamepadEffectKind`, `GamepadEffectSession`, `GamepadEffectState`, `GamepadEffectOutcome`, `GamepadEffectStopReason`, `TextInputConfig`, `TextInputSession`, `TextInputState`, `TextInputEvent`, `TextInputPurpose`, `TextInputAction`, `TextCapitalization`, `TextRange`, `TextDocumentRevision`, `DropOffer`, `DropOfferId`, `DropOfferState`, `DropOfferTerminationReason`, `DropItemDescriptor`, `DropItemKind`, `DropTransfer`, `DroppedItem`, `DropItemReadMode`, `RawInputAccess`, `RawInputState`, `RawInputEvent`, `RawInputUnit`, `KadrePermission`, `PermissionState` |
| `org.graphiks.kadre.capture` | `CaptureManager`, `CaptureManagerState`, `CaptureManagerRevision`, `CapturePermissionScope`, `CapturePermissionState`, `CaptureCapabilities`, `CaptureTargetConstraints`, `CaptureSources`, `CaptureSource`, `CaptureSourceId`, `CaptureSourceKind`, `CaptureRequest`, `CaptureTarget`, `CaptureSession`, `CaptureSessionState`, `CaptureConfiguration`, `CaptureConfigurationRevision`, `CaptureCadence`, `CaptureOutcome`, `CaptureStopReason`, `CaptureEvent`, `CaptureDiagnostic`, `CaptureFrame`, `CaptureSourceInstant`, `CaptureDiscontinuity`, `CaptureRegion`, `CaptureCursorMode`, `CaptureOrientation`, `PixelFormat`, `PixelPlaneLayout`, `CopiedPixelPlane`, `ColorEncoding`, `ColorPrimaries`, `TransferFunction`, `MatrixCoefficients`, `ColorRange`, `HdrMetadata`, `MasteringDisplayMetadata`, `Chromaticity`, `AlphaMode` |
| `org.graphiks.kadre.policy` | `KadrePolicy`, `KadrePolicies`, `ExecutionPolicy`, `ExecutionPriority`, `EventDeliveryPolicy`, `ResourceBudgetPolicy`, `DevicePolicy`, `GamepadRouting`, `DeviceEffectOwnership`, `DiagnosticPolicy`, `DiagnosticDataExposure`, `DiagnosticOverflowAction`, `ContinuousDelivery`, `FrameDelivery`, `IngressOverflowAction`, `CollectorOverflowAction`, `ContinuousOverflowAction`, `SlowCollectorCancellationException`, `InputDeliveryPolicy`, `WindowDeliveryPolicy`, `CaptureDeliveryPolicy` |
| `org.graphiks.kadre.diagnostics` | `KadreResult`, `KadreException`, `KadreFailure`, `KadreOperation`, `KadrePolicyComponent`, `KadreResourceKind`, `KadrePlatform`, `InteractionFailureReason`, `Capability`, `FeatureAvailability`, `KadreDiagnostics`, `KadreDiagnostic`, `DiagnosticSeverity`, `KadreSubsystem`, `DiagnosticCounters`, `DiagnosticCounter`, `ExperimentalKadreApi`, `KadrePlatformApi`, `DelicateKadreApi` |
| `org.graphiks.kadre.platform.android` | quatre overloads `attachKadre`; callback `withAndroidView` |
| `org.graphiks.kadre.platform.uikit` | `KadreIos`; callback `withUIKitView` |
| `org.graphiks.kadre.platform.web` | `WebAttachmentPolicy`, `WebWindowHost`, `WebWindowProvider`, `KadreApplicationFactoryRef`, `asHostRef`, overloads factory et application de `attachKadre`, `withWebElement` dans chacun des targets JS/Wasm |
| `org.graphiks.kadre.platform.desktop` | `DesktopBackend`, `DesktopIntegration`, `DesktopHostOptions`, `DesktopNativeWindowHandle`, `attachKadreDesktop`, `runKadreApplication`, `withDesktopHandle` |
| `org.graphiks.kadre.test` | `VirtualKadreClock`, `FakeHostOptions`, `FakeCapabilities`, `FakeFeature`, `FakeKadreHost`, `VirtualLifecycleController`, `VirtualSurfaceController`, `VirtualWindowController`, `VirtualDisplayController`, `VirtualInputController`, `VirtualGamepadController`, `VirtualCaptureController`, `FakeDisplayState`, `FakeDropItem`, `FakeCaptureSource`, `FakeCaptureConfiguration`, `FakePixelPlane`, `FakeCaptureFrame` |

Les overloads, extensions et combinators top-level sont limités à :

- `WindowManager.requestWindow(configure)` ;
- `SurfaceInput.openTextInput(config)` et `SurfaceInput.requestRawInput()` ;
- `HostSurface.installInteractionHandler(handler)` et `HostSurface.armInteraction(action, options)` ;
- les combinators de `KadreResult` listés en section 12 de `DESIGN.md` ;
- `LogicalPoint.toPhysical`, `LogicalSize.toPhysical`, `PhysicalPoint.toLogical` et `PhysicalSize.toLogical` ;
- `KadreFailure.message` et `KadreDiagnostic.message` ;
- les points d’attachement de plateforme de la section 15 de `DESIGN.md` ;
- `KadreApplicationFactory.asHostRef()` et les quatre callbacks target-specific `withAndroidView`, `withUIKitView`, `withWebElement` et `withDesktopHandle`.

Il n’existe aucun typealias public.

Le lifecycle ajoute l’enum fermé `MemoryPressureLevel { Moderate, Critical }`. `Critical` est plus sévère que `Moderate` pour `Latest`/`Coalesced`; aucun niveau natif inconnu n’est abaissé silencieusement.

## 3. Primitives communes

### 3.1 IDs, révisions et temps

Les classes opaques suivantes ont un constructeur `internal`, une égalité/hachage par valeur, un `toString()` redacted et n’exposent pas leur représentation :

```text
SessionId, SurfaceId, WindowId, DisplayId, DeviceId, GamepadId,
CaptureSourceId, WindowCloseRequestId, WindowOperationId,
WindowRequestId, InteractionToken, InteractionRequestId, DropOfferId,
PointerId, TouchId
```

Les révisions suivantes sont des `value class` à constructeur `internal` et exposent `value: Long` positif ou nul :

```text
SurfaceRevision, DisplayManagerRevision, DisplayRevision,
WindowManagerRevision, WindowRevision, DeviceManagerRevision,
InputStateRevision, GamepadRevision, CaptureManagerRevision,
CaptureConfigurationRevision
```

`SessionSequence`, `SessionInstant`, `CaptureSourceInstant` conservent les signatures de `DESIGN.md`; `CaptureSourceInstant.sinceCaptureStart: Duration` est positif ou nul. `TextDocumentRevision(public val value: Long)` est une `value class` construite par l’application et rejette une valeur négative.

### 3.2 Géométrie et patchs

```kotlin
public data class LogicalPoint(public val x: Double, public val y: Double)
public data class LogicalDelta(public val x: Double, public val y: Double)
public data class LogicalSize(public val width: Double, public val height: Double)
public data class LogicalRect(public val origin: LogicalPoint, public val size: LogicalSize)
public data class LogicalInsets(
    public val top: Double,
    public val right: Double,
    public val bottom: Double,
    public val left: Double,
)
public data class PhysicalPoint(public val x: Int, public val y: Int)
public data class PhysicalSize(public val width: Int, public val height: Int)
public data class PhysicalRect(public val origin: PhysicalPoint, public val size: PhysicalSize)

public enum class PixelRounding { Floor, Ceil, NearestTiesToEven, TowardZero }
public fun LogicalPoint.toPhysical(scaleFactor: Double, rounding: PixelRounding = PixelRounding.NearestTiesToEven): PhysicalPoint
public fun LogicalSize.toPhysical(scaleFactor: Double, rounding: PixelRounding = PixelRounding.Ceil): PhysicalSize
public fun PhysicalPoint.toLogical(scaleFactor: Double): LogicalPoint
public fun PhysicalSize.toLogical(scaleFactor: Double): LogicalSize

public sealed interface PropertyChange<out T> {
    public data object Unchanged : PropertyChange<Nothing>
    public data class Set<T>(public val value: T) : PropertyChange<T>
    public data object Clear : PropertyChange<Nothing>
}

public enum class ImageFormat { Png, Jpeg, Webp, Rgba8 }
public data class ImageConstraints(
    public val maximumSize: PhysicalSize,
    public val formats: Set<ImageFormat>,
)
public class BinaryImage(
    bytes: ByteArray,
    public val format: ImageFormat,
    public val pixelSize: PhysicalSize? = null,
) {
    public val bytes: ByteArray
    public override fun equals(other: Any?): Boolean
    public override fun hashCode(): Int
    public override fun toString(): String
}
```

Les tailles logiques sont strictement positives ; les insets sont positifs ou nuls. Une taille physique publiée est strictement positive. Les conversions rejettent un `scaleFactor` non fini ou non strictement positif. `NearestTiesToEven` suit l’arrondi IEEE vers l’entier pair ; les conversions vers `Int` utilisent une arithmétique vérifiée et lèvent `IllegalArgumentException` si le résultat est hors domaine. Les conversions physiques vers logiques ne font aucun arrondi. `PropertyChange.Clear` n’est accepté que pour une propriété déclarée nullable dans ce catalogue. `BinaryImage.bytes` retourne une copie et son constructeur copie l’entrée. `Rgba8` exige `pixelSize` et exactement `width × height × 4` octets en ordre RGBA row-major sans padding ; les formats encodés acceptent `pixelSize = null` ou une taille attendue vérifiée au décodage. `CursorImage` exige toujours `image.pixelSize != null`, `hotspot.x >= 0`, `hotspot.y >= 0`, `hotspot.x < pixelSize.width` et `hotspot.y < pixelSize.height`; son constructeur rejette toute autre combinaison. Lors d’un `SurfaceUpdate`, format, dimensions et octets sont ensuite revalidés contre `SurfaceCapabilities.customCursor` et les budgets courants.

## 4. Surfaces et displays

### 4.1 Surface

```kotlin
public data class SurfaceState(
    public val attachment: SurfaceAttachmentState,
    public val logicalSize: LogicalSize,
    public val physicalSize: PhysicalSize,
    public val scaleFactor: Double,
    public val safeAreaInsets: LogicalInsets,
    public val visibility: SurfaceVisibility,
    public val occlusion: SurfaceOcclusion,
    public val focus: SurfaceFocus,
    public val theme: SurfaceTheme,
    public val cursor: CursorStyle,
    public val pointerCapture: PointerCaptureMode,
    public val hitTesting: HitTestingMode,
    public val inputDefaultBehavior: InputDefaultBehavior,
    public val revision: SurfaceRevision,
)

public enum class SurfaceAttachmentState { Attached, Detached }
public enum class SurfaceVisibility { Visible, Hidden }
public enum class SurfaceOcclusion { Visible, Occluded, Unknown }
public enum class SurfaceFocus { Focused, Unfocused }
public enum class SurfaceTheme { Light, Dark, Unknown }

public sealed interface SurfaceEvent {
    public val stamp: EventStamp
    public val stateRevision: SurfaceRevision
    public data class MetricsChanged(public val state: SurfaceState, override val stamp: EventStamp) : SurfaceEvent { override val stateRevision: SurfaceRevision get() = state.revision }
    public data class FocusChanged(public val state: SurfaceState, override val stamp: EventStamp) : SurfaceEvent { override val stateRevision: SurfaceRevision get() = state.revision }
    public data class VisibilityChanged(public val state: SurfaceState, override val stamp: EventStamp) : SurfaceEvent { override val stateRevision: SurfaceRevision get() = state.revision }
    public data class ThemeChanged(public val state: SurfaceState, override val stamp: EventStamp) : SurfaceEvent { override val stateRevision: SurfaceRevision get() = state.revision }
    public data class RedrawRequested(override val stateRevision: SurfaceRevision, override val stamp: EventStamp) : SurfaceEvent
}

public data class SurfaceUpdate(
    public val cursor: PropertyChange<CursorStyle> = PropertyChange.Unchanged,
    public val pointerCapture: PropertyChange<PointerCaptureMode> = PropertyChange.Unchanged,
    public val hitTesting: PropertyChange<HitTestingMode> = PropertyChange.Unchanged,
    public val inputDefaultBehavior: PropertyChange<InputDefaultBehavior> = PropertyChange.Unchanged,
    public val expectedRevision: SurfaceRevision? = null,
)

public sealed interface SurfaceUpdateOutcome {
    public data class Applied(public val state: SurfaceState) : SurfaceUpdateOutcome
    public data class PartiallyApplied(
        public val state: SurfaceState,
        public val rejected: List<RejectedSurfaceField>,
    ) : SurfaceUpdateOutcome
}

public data class RejectedSurfaceField(public val field: SurfaceProperty, public val failure: KadreFailure)
public enum class SurfaceProperty { Cursor, PointerCapture, HitTesting, InputDefaultBehavior }

public data class SurfaceCapabilities(
    public val cursor: Capability<Set<CursorIcon>>,
    public val customCursor: Capability<ImageConstraints>,
    public val pointerCapture: Capability<Set<PointerCaptureMode>>,
    public val hitTesting: Capability<Set<HitTestingMode>>,
    public val inputDefaultBehavior: Capability<Set<InputDefaultBehavior>>,
    public val handlerInteractions: Capability<Set<InteractionKind>>,
    public val armedInteractions: Capability<ArmedInteractionConstraints>,
    public val platformAccess: Capability<Unit>,
)

public sealed interface CursorStyle {
    public data object Hidden : CursorStyle
    public data class System(public val icon: CursorIcon) : CursorStyle
    public data class Custom(public val image: CursorImage) : CursorStyle
}

public data class CursorImage(public val image: BinaryImage, public val hotspot: PhysicalPoint)
public enum class PointerCaptureMode { None, Confined, Locked }
public enum class HitTestingMode { Enabled, Disabled }
public enum class InputDefaultBehavior { HostDefault, SuppressWhenPossible }
```

`CursorIcon` est l’enum fermé : `Default`, `Pointer`, `Text`, `Crosshair`, `Move`, `Grab`, `Grabbing`, `NotAllowed`, `Wait`, `Progress`, `Help`, `ResizeHorizontal`, `ResizeVertical`, `ResizeDiagonalNorthWestSouthEast`, `ResizeDiagonalNorthEastSouthWest`, `ResizeColumn`, `ResizeRow`, `ZoomIn`, `ZoomOut`, `ContextMenu`, `Copy`, `Alias`, `Cell`.

`InputDefaultBehavior.SuppressWhenPossible` demande au backend d’empêcher les actions natives concurrentes associées aux événements livrés par Kadre, par exemple scroll/zoom browser. Il ne signifie jamais que le consumer asynchrone a « handled » l’événement. Un backend incapable de supprimer une catégorie la rejette partiellement avec `Unsupported(UpdateSurface)` et publie la capability correspondante ; aucun changement global de page/application n’est implicite.

`SurfaceEvent.MetricsChanged.stateRevision` et les trois événements qui contiennent `state` sont dérivés de `state.revision`; ils ne stockent pas un second membre. Le payload d’un cursor custom est copié lors de l’admission, compté dans les budgets image et payload, puis détenu par la surface jusqu’à remplacement ou fermeture ; il n’existe plus de handle `CustomCursor` public.

Un `SurfaceUpdate` dont les quatre `PropertyChange` valent `Unchanged` est un no-op explicite : après validation de `expectedRevision`, `apply` retourne `Applied(state.value)` sans appel natif, sans événement et sans incrément de `SurfaceRevision`. Une révision attendue stale échoue même pour ce no-op.

Les quatre champs mutables de `SurfaceState` sont les valeurs effectives après commit et sont publiés avant l’outcome/événement correspondant. Ils sont toujours définis, même lorsque leur capability de mutation est `Unsupported` : `System(Default)`, `None`, `Enabled` et `HostDefault` décrivent alors l’absence d’override Kadre, pas une promesse que le host possède matériellement un curseur ou un mécanisme de capture. `PropertyChange.Clear` n’est admis pour aucun champ de `SurfaceUpdate`; revenir au comportement de base utilise ces valeurs explicites.

### 4.2 Display

```kotlin
public enum class DisplayType { Physical, Virtual, HostViewport }
public enum class DisplayConnectionState { Connected, Disconnected }

public data class DisplayMode(
    public val physicalSize: PhysicalSize,
    public val refreshRateHz: Double?,
    public val bitDepth: Int?,
)

public data class DisplayState(
    public val type: DisplayType,
    public val connection: DisplayConnectionState,
    public val name: String?,
    public val bounds: PhysicalRect,
    public val workArea: PhysicalRect?,
    public val scaleFactor: Double,
    public val currentMode: DisplayMode?,
    public val modes: List<DisplayMode>,
    public val revision: DisplayRevision,
)

public data class DisplayCapabilities(
    public val enumeration: Capability<Unit>,
)

public sealed interface DisplayEvent {
    public val stamp: EventStamp
    public val managerRevision: DisplayManagerRevision
    public data class Added(public val display: Display, public val state: DisplayState, override val managerRevision: DisplayManagerRevision, override val stamp: EventStamp) : DisplayEvent
    public data class Changed(public val display: Display, public val state: DisplayState, override val managerRevision: DisplayManagerRevision, override val stamp: EventStamp) : DisplayEvent
    public data class Removed(public val displayId: DisplayId, public val lastState: DisplayState, override val managerRevision: DisplayManagerRevision, override val stamp: EventStamp) : DisplayEvent
}
```

`Added/Changed.state` porte exactement la révision publiée avant l’événement ; `Removed.lastState.connection == Disconnected`. Les événements restent interprétables même si le handle a déjà avancé. La permission éventuelle d’énumération est portée une seule fois par `DisplayCapabilities.enumeration.availability` via `RequiresPermission`; il n’existe aucun champ permission redondant. `DisplayInventory`, `DisplayManagerState`, `DisplayManager` et `Display` restent exactement ceux de la section 9.2 de `DESIGN.md`.

`DisplayMode.refreshRateHz`, lorsqu’il existe, est fini et strictement positif ; `bitDepth`, lorsqu’il existe, est strictement positif. `DisplayState.scaleFactor` est fini et strictement positif, `modes` ne contient aucun doublon et contient `currentMode` lorsqu’il est non vide et que `currentMode != null`. `workArea`, lorsqu’elle existe, est entièrement contenue dans `bounds`. Une métadonnée native invalide n’est ni clampée ni publiée : un champ optionnel devient `null` avec diagnostic, tandis qu’une géométrie ou un scale invalidant le display rend l’inventaire `Unavailable(PlatformFailure)`.

## 5. Fenêtres et interactions

### 5.1 Spécification, état et capabilities

```kotlin
public data class WindowSpec(
    public val title: String = "",
    public val contentSize: LogicalSize = LogicalSize(800.0, 600.0),
    public val minimumSize: LogicalSize? = null,
    public val maximumSize: LogicalSize? = null,
    public val outerPosition: PhysicalPoint? = null,
    public val resizable: Boolean = true,
    public val fullscreen: FullscreenMode = FullscreenMode.Windowed,
    public val decorations: WindowDecorations = WindowDecorations.System,
    public val systemButtons: WindowSystemButtons = WindowSystemButtons.All,
    public val level: WindowLevel = WindowLevel.Normal,
    public val transparent: Boolean = false,
    public val blurBehind: Boolean = false,
    public val icon: BinaryImage? = null,
    public val contentProtection: Boolean = false,
)

public class WindowSpecBuilder internal constructor() {
    public var title: String
    public var contentSize: LogicalSize
    public var minimumSize: LogicalSize?
    public var maximumSize: LogicalSize?
    public var outerPosition: PhysicalPoint?
    public var resizable: Boolean
    public var fullscreen: FullscreenMode
    public var decorations: WindowDecorations
    public var systemButtons: WindowSystemButtons
    public var level: WindowLevel
    public var transparent: Boolean
    public var blurBehind: Boolean
    public var icon: BinaryImage?
    public var contentProtection: Boolean
}

public enum class WindowPhase { Open, Closing, Closed }
public enum class WindowDecorations { System, Borderless }
public enum class WindowSystemButtons { All, CloseOnly, None }
public enum class WindowLevel { Normal, Floating, Modal }
public enum class WindowAttention { None, Informational, Critical }

public sealed interface FullscreenMode {
    public data object Windowed : FullscreenMode
    public data object Borderless : FullscreenMode
    public data class Exclusive(public val displayId: DisplayId, public val mode: DisplayMode) : FullscreenMode
}

public data class WindowState(
    public val phase: WindowPhase,
    public val title: String,
    public val outerBounds: PhysicalRect?,
    public val contentSize: LogicalSize,
    public val minimumSize: LogicalSize?,
    public val maximumSize: LogicalSize?,
    public val resizable: Boolean,
    public val fullscreen: FullscreenMode,
    public val decorations: WindowDecorations,
    public val systemButtons: WindowSystemButtons,
    public val level: WindowLevel,
    public val transparent: Boolean,
    public val blurBehind: Boolean,
    public val icon: BinaryImage?,
    public val contentProtection: Boolean,
    public val revision: WindowRevision,
)

public data class WindowCapabilities(
    public val title: Capability<Unit>,
    public val outerPosition: Capability<Unit>,
    public val contentSize: Capability<LogicalSizeRange>,
    public val minimumSize: Capability<LogicalSizeRange>,
    public val maximumSize: Capability<LogicalSizeRange>,
    public val resizable: Capability<Unit>,
    public val fullscreen: Capability<Set<FullscreenKind>>,
    public val decorations: Capability<Set<WindowDecorations>>,
    public val systemButtons: Capability<Set<WindowSystemButtons>>,
    public val level: Capability<Set<WindowLevel>>,
    public val transparency: Capability<Unit>,
    public val blurBehind: Capability<Unit>,
    public val icon: Capability<ImageConstraints>,
    public val attention: Capability<Set<WindowAttention>>,
    public val contentProtection: Capability<Unit>,
    public val closeInterception: Capability<Unit>,
    public val platformAccess: Capability<Unit>,
)

public data class LogicalSizeRange(public val minimum: LogicalSize?, public val maximum: LogicalSize?, public val increments: LogicalSize?)
public enum class FullscreenKind { Borderless, Exclusive }

public data class WindowManagerCapabilities(
    public val requestWindow: Capability<Set<WindowCreationMode>>,
)
public enum class WindowCreationMode { OpenedHere, OpenedInNewSession }
```

`WindowSpecBuilder` est initialisé avec les valeurs exactes de `WindowSpec()` et sa conversion produit ce snapshot. Les anciens noms génériques `WindowSizeConstraints` sont remplacés par `LogicalSizeRange` pour éviter deux modèles concurrents.

Pour `WindowSpec` et le snapshot résultant d’un `WindowUpdate`, chaque dimension de `minimumSize` est inférieure ou égale à la dimension correspondante de `maximumSize` lorsque les deux existent. La `contentSize` demandée par un `WindowSpec` appartient à cet intervalle. `LogicalSizeRange` applique la même relation et ses `increments`, lorsqu’ils existent, sont strictement positifs sans imposer que `minimum` soit un multiple. Un constructeur direct invalide lève `IllegalArgumentException`; une combinaison invalide produite par le DSL est retournée par `requestWindow` comme `InvalidRequest("sizeConstraints")`. `title` respecte `maxTextCodeUnitsPerValue`; une violation dépendant de la policy produit `Limit(RetainedPayload)` à l’opération, jamais une troncature. Un fullscreen exclusif revalide `displayId` dans la session et `mode` contre les modes/capabilities courants au moment du commit.

```kotlin
public data class WindowUpdate(
    public val title: PropertyChange<String> = PropertyChange.Unchanged,
    public val outerPosition: PropertyChange<PhysicalPoint> = PropertyChange.Unchanged,
    public val contentSize: PropertyChange<LogicalSize> = PropertyChange.Unchanged,
    public val minimumSize: PropertyChange<LogicalSize> = PropertyChange.Unchanged,
    public val maximumSize: PropertyChange<LogicalSize> = PropertyChange.Unchanged,
    public val resizable: PropertyChange<Boolean> = PropertyChange.Unchanged,
    public val fullscreen: PropertyChange<FullscreenMode> = PropertyChange.Unchanged,
    public val decorations: PropertyChange<WindowDecorations> = PropertyChange.Unchanged,
    public val systemButtons: PropertyChange<WindowSystemButtons> = PropertyChange.Unchanged,
    public val level: PropertyChange<WindowLevel> = PropertyChange.Unchanged,
    public val transparency: PropertyChange<Boolean> = PropertyChange.Unchanged,
    public val blurBehind: PropertyChange<Boolean> = PropertyChange.Unchanged,
    public val icon: PropertyChange<BinaryImage> = PropertyChange.Unchanged,
    public val contentProtection: PropertyChange<Boolean> = PropertyChange.Unchanged,
    public val expectedRevision: WindowRevision? = null,
)
```

`Clear` est accepté uniquement pour `outerPosition`, `minimumSize`, `maximumSize` et `icon`. `WindowProperty` reste l’enum de `DESIGN.md`, complété par `Resizable` entre `MaximumSize` et `Fullscreen`.

Un `WindowUpdate` dont les quatorze `PropertyChange` valent `Unchanged` est un no-op explicite : après validation de `expectedRevision`, `apply` retourne `Applied(operationId, state.value)` avec un nouveau `WindowOperationId`, mais sans appel natif, sans événement et sans incrément de `WindowRevision`. Une révision attendue stale échoue même pour ce no-op. Pour un update non vide, les relations de taille sont validées sur la combinaison du snapshot courant et de toutes les valeurs `Set`/`Clear` avant toute application partielle ; une combinaison intrinsèquement contradictoire retourne `InvalidRequest("sizeConstraints")` comme failure externe et ne committe aucun champ. `WindowState.icon` est la valeur immuable effectivement retenue par la fenêtre et redevient `null` après `Clear`; ses bytes peuvent partager le backing déjà owned par la fenêtre sans nouvelle copie logique à chaque événement. L’attention utilisateur n’est pas un champ de ce patch : `Window.requestAttention` est la commande dédiée et ne fabrique aucun état persistant.

### 5.2 Événements de fenêtre

```kotlin
public enum class WindowCloseReason { User, System, ParentHost, SessionStopping }

public sealed interface WindowEvent {
    public val stamp: EventStamp
    public val stateRevision: WindowRevision
    public val operationId: WindowOperationId?

    public data class GeometryChanged(public val state: WindowState, override val operationId: WindowOperationId?, override val stamp: EventStamp) : WindowEvent { override val stateRevision: WindowRevision get() = state.revision }
    public data class PropertiesChanged(public val state: WindowState, public val changed: Set<WindowProperty>, override val operationId: WindowOperationId?, override val stamp: EventStamp) : WindowEvent { override val stateRevision: WindowRevision get() = state.revision }
    public data class CloseRequested(
        public val requestId: WindowCloseRequestId,
        public val reason: WindowCloseReason,
        public val canReject: Boolean,
        public val deadline: SessionInstant?,
        override val stateRevision: WindowRevision,
        override val stamp: EventStamp,
    ) : WindowEvent { override val operationId: WindowOperationId? = null }
    public data class Closing(public val reason: WindowCloseReason, override val stateRevision: WindowRevision, override val operationId: WindowOperationId?, override val stamp: EventStamp) : WindowEvent
}
```

Pour `GeometryChanged` et `PropertiesChanged`, `stateRevision` est dérivé de `state.revision`. `changed` est non vide et ne contient que les propriétés non géométriques effectivement modifiées dans cette révision. Les outcomes, requêtes et interfaces de fenêtre restent exactement ceux des sections 9.3 et 9.5 de `DESIGN.md` avec les types ci-dessus.

### 5.3 Interactions transitoires

```kotlin
public enum class InteractionKind {
    EnterFullscreen, ExitFullscreen, LockPointer, UnlockPointer,
    BeginWindowMove, BeginWindowResize, AcceptDrop, OpenWindow,
}

public enum class ResizeEdge { North, NorthEast, East, SouthEast, South, SouthWest, West, NorthWest }

public sealed interface InteractionAction {
    public data class EnterFullscreen(public val mode: FullscreenMode) : InteractionAction
    public data object ExitFullscreen : InteractionAction
    public data class LockPointer(public val mode: PointerCaptureMode) : InteractionAction
    public data object UnlockPointer : InteractionAction
    public data object BeginWindowMove : InteractionAction
    public data class BeginWindowResize(public val edge: ResizeEdge) : InteractionAction
    public data class AcceptDrop(public val offerId: DropOfferId) : InteractionAction
    public data class OpenWindow(public val spec: WindowSpec = WindowSpec()) : InteractionAction
}

public sealed interface InteractionActionOutcome {
    public val requestId: InteractionRequestId
    public val stamp: EventStamp
    public data class Committed(override val requestId: InteractionRequestId, public val windowRequestId: WindowRequestId?, override val stamp: EventStamp) : InteractionActionOutcome
    public data class Rejected(override val requestId: InteractionRequestId, public val failure: KadreFailure, override val stamp: EventStamp) : InteractionActionOutcome
    public data class Expired(override val requestId: InteractionRequestId, override val stamp: EventStamp) : InteractionActionOutcome
    public data class OwnerClosed(override val requestId: InteractionRequestId, override val stamp: EventStamp) : InteractionActionOutcome
}

public sealed interface InteractionTrigger {
    public data object NextEligibleActivation : InteractionTrigger
    public data class PointerPressed(public val button: PointerButton?) : InteractionTrigger
    public data class KeyPressed(public val physicalKey: PhysicalKey?) : InteractionTrigger
    public data object TouchStarted : InteractionTrigger
}

public enum class InteractionTriggerKind { AnyActivation, PointerPress, KeyPress, TouchStart }
public data class ArmedInteractionConstraints(
    public val actions: Set<InteractionKind>,
    public val triggers: Set<InteractionTriggerKind>,
)

public data class InteractionArmOptions(
    public val expiresAfter: Duration,
    public val trigger: InteractionTrigger = InteractionTrigger.NextEligibleActivation,
)

public sealed interface ArmedInteractionState {
    public data object Armed : ArmedInteractionState
    public data class Terminated(public val outcome: InteractionActionOutcome) : ArmedInteractionState
}

public sealed interface InteractionEvent {
    public val stamp: EventStamp
    public data class PointerPressed(public val button: PointerButton, public val position: LogicalPoint, override val stamp: EventStamp) : InteractionEvent
    public data class KeyPressed(public val physicalKey: PhysicalKey, override val stamp: EventStamp) : InteractionEvent
    public data class TouchStarted(public val touchId: TouchId, public val position: LogicalPoint, override val stamp: EventStamp) : InteractionEvent
}
```

`InteractionContext.request` n’accepte que l’action dont `InteractionKind` est présent dans `SurfaceCapabilities.handlerInteractions`. `armInteraction` exige l’action dans `armedInteractions.actions` et le kind dérivé du trigger dans `armedInteractions.triggers`; `PointerPressed(null)` et `KeyPressed(null)` restent respectivement `PointerPress` et `KeyPress`. Les deux sets de `ArmedInteractionConstraints` sont non vides. `Committed.windowRequestId` est non-null exactement pour `OpenWindow`.

## 6. Input, IME, drop et raw input

### 6.1 Devices et état de surface

```kotlin
public enum class InputDeviceKind { Keyboard, Mouse, Touchscreen, Touchpad, Pen, Other }
public enum class DeviceConnectionState { Connected, Disconnected }

public data class InputDeviceDescriptor(public val name: String?, public val kind: InputDeviceKind)
public interface InputDevice {
    public val id: DeviceId
    public val descriptor: InputDeviceDescriptor
    public val connection: StateFlow<DeviceConnectionState>
}

public sealed interface DeviceLifecycleEvent {
    public val stamp: EventStamp
    public val managerRevision: DeviceManagerRevision
    public data class DeviceAdded(public val device: InputDevice, override val managerRevision: DeviceManagerRevision, override val stamp: EventStamp) : DeviceLifecycleEvent
    public data class DeviceRemoved(public val deviceId: DeviceId, override val managerRevision: DeviceManagerRevision, override val stamp: EventStamp) : DeviceLifecycleEvent
    public data class GamepadAdded(public val gamepad: Gamepad, override val managerRevision: DeviceManagerRevision, override val stamp: EventStamp) : DeviceLifecycleEvent
    public data class GamepadRemoved(public val gamepadId: GamepadId, override val managerRevision: DeviceManagerRevision, override val stamp: EventStamp) : DeviceLifecycleEvent
}

public enum class ModifierKey { Shift, Control, Alt, Meta, CapsLock, NumLock }
public data class KeyboardModifiers(public val pressed: Set<ModifierKey>)
public enum class KeyState { Pressed, Released }
public enum class KeyLocation { Standard, Left, Right, Numpad }

public sealed interface PhysicalKey {
    public data class Code(public val usagePage: Int, public val usageId: Int) : PhysicalKey
    public data class Unidentified(public val nativeCode: String?) : PhysicalKey
}

public sealed interface LogicalKey {
    public data class Character(public val value: String) : LogicalKey
    public data class Named(public val value: NamedKey) : LogicalKey
    public data class Unidentified(public val nativeCode: String?) : LogicalKey
}

public enum class NamedKey {
    Enter, Tab, Space, Backspace, Escape, Delete, Insert, Home, End,
    PageUp, PageDown, ArrowLeft, ArrowRight, ArrowUp, ArrowDown,
    Shift, Control, Alt, Meta, CapsLock, NumLock, ContextMenu,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    MediaPlayPause, MediaStop, MediaNext, MediaPrevious, VolumeUp, VolumeDown, VolumeMute,
}

public data class KeyboardState(public val pressedKeys: Set<PhysicalKey>)
public enum class PointerKind { Mouse, Touchpad, Pen, Eraser, Unknown }
public sealed interface PointerButton {
    public data object Primary : PointerButton
    public data object Secondary : PointerButton
    public data object Auxiliary : PointerButton
    public data object Back : PointerButton
    public data object Forward : PointerButton
    public data object Barrel : PointerButton
    public data object Eraser : PointerButton
    public data class Other(public val nativeCode: Int) : PointerButton
}
public enum class PointerButtonState { Pressed, Released }

public data class PenState(
    public val tiltXDegrees: Double?,
    public val tiltYDegrees: Double?,
    public val twistRadians: Double?,
    public val tangentialPressure: Double?,
)

public data class PointerState(
    public val id: PointerId,
    public val kind: PointerKind,
    public val position: LogicalPoint?,
    public val pressedButtons: Set<PointerButton>,
    public val pressure: Double?,
    public val pen: PenState?,
)

public data class TouchState(
    public val id: TouchId,
    public val position: LogicalPoint,
    public val pressure: Double?,
)

public data class InputCapabilities(
    public val keyboard: FeatureAvailability,
    public val pointer: FeatureAvailability,
    public val touch: FeatureAvailability,
    public val gestures: FeatureAvailability,
    public val dragAndDrop: FeatureAvailability,
    public val textInput: Capability<Unit>,
    public val rawInput: Capability<Unit>,
)
```

`PhysicalKey.Code.usagePage` et `usageId` sont chacun dans `0..65_535` et désignent ensemble une usage HID USB ; aucun packing signé implicite n’est public. `LogicalKey.Character.value` est non vide et peut contenir plusieurs code points formant la valeur logique fournie par le host. Un `nativeCode` de touche non null est un identifiant ASCII non vide d’au plus 256 unités, jamais un label localisé ou une saisie utilisateur.

`PenState` n’est non-null que pour `PointerKind.Pen` ou `Eraser`. Les tilts sont dans `[-90, 90]`, le twist dans `[0, 2π)` et la pression tangentielle dans `[-1, 1]`; `PointerState.pressure` et `TouchState.pressure` sont dans `[0, 1]`. Chaque champ inconnu est `null` et aucune valeur native invalide n’est clampée silencieusement. Les noms optionnels de `InputDeviceDescriptor` respectent le budget de métadonnée, ne constituent jamais une identité persistante et deviennent `null` plutôt que tronqués.

`SurfaceInput`, `SurfaceInputState`, `InputEvent` et `InputStateResetReason` conservent les signatures exhaustives de `DESIGN.md`, avec `pointers` et `touches` triés par leur ID stable dans la session. Un snapshot neutre possède sets/listes vides et conserve seulement les capabilities terminales.

### 6.2 Événements input — variantes fermées

Les signatures exhaustives sont imprimées en section 10.1 de `DESIGN.md`; aucune autre variante n’existe. Toutes portent `stamp`, `deviceId` nullable et `stateRevision` comme membres communs.

Déclarations associées :

```kotlin
public enum class TouchPhase { Started, Moved, Ended, Cancelled }
public enum class GestureKind { Pan, Pinch, Rotation, DoubleTap, TouchpadPressure }
public sealed interface ScrollDelta {
    public data class Logical(public val x: Double, public val y: Double) : ScrollDelta
    public data class Lines(public val x: Double, public val y: Double) : ScrollDelta
}
```

`DeviceInventory.devices` exclut les gamepads, lesquels apparaissent uniquement dans `gamepads`. Une source physique combinée peut produire plusieurs handles typés, mais leurs IDs restent indépendants et aucune relation persistante n’est exposée.

Pour `Gesture`, `phase` utilise `TouchPhase`; les champs non pertinents pour le `kind` sont `null`. Les combinaisons valides sont fermées : `Pan` utilise `delta`, `Pinch` utilise `scale > 0`, `Rotation` utilise `rotationRadians`, `TouchpadPressure` utilise `pressure` dans `[0,1]`, `DoubleTap` n’utilise aucun de ces quatre champs. Le constructeur public rejette une combinaison différente par `IllegalArgumentException`, et un backend n’en publie jamais.

### 6.3 Gamepad

```kotlin
public enum class GamepadMapping { Standard, Native }
public sealed interface GamepadButton {
    public data object South : GamepadButton
    public data object East : GamepadButton
    public data object West : GamepadButton
    public data object North : GamepadButton
    public data object LeftShoulder : GamepadButton
    public data object RightShoulder : GamepadButton
    public data object LeftTrigger : GamepadButton
    public data object RightTrigger : GamepadButton
    public data object Select : GamepadButton
    public data object Start : GamepadButton
    public data object Mode : GamepadButton
    public data object LeftStick : GamepadButton
    public data object RightStick : GamepadButton
    public data object DpadUp : GamepadButton
    public data object DpadDown : GamepadButton
    public data object DpadLeft : GamepadButton
    public data object DpadRight : GamepadButton
    public data class Other(public val nativeCode: String) : GamepadButton
}
public sealed interface GamepadAxis {
    public data object LeftX : GamepadAxis
    public data object LeftY : GamepadAxis
    public data object RightX : GamepadAxis
    public data object RightY : GamepadAxis
    public data object LeftTrigger : GamepadAxis
    public data object RightTrigger : GamepadAxis
    public data object DpadX : GamepadAxis
    public data object DpadY : GamepadAxis
    public data class Other(public val nativeCode: String) : GamepadAxis
}

public data class GamepadDescriptor(
    public val name: String?,
    public val mapping: GamepadMapping,
    public val buttons: List<GamepadButton>,
    public val axes: List<GamepadAxis>,
)
public data class GamepadButtonValue(public val button: GamepadButton, public val value: Double, public val pressed: Boolean)
public data class GamepadAxisValue(public val axis: GamepadAxis, public val value: Double)
public data class GamepadState(public val buttons: List<GamepadButtonValue>, public val axes: List<GamepadAxisValue>)

public data class GamepadCapabilities(
    public val effects: Capability<GamepadEffectConstraints>,
)
public enum class GamepadEffectKind { DualRumble, TriggerRumble }
public data class GamepadEffectConstraints(
    public val kinds: Set<GamepadEffectKind>,
    public val maximumDuration: Duration?,
)

public sealed interface GamepadEvent {
    public val stamp: EventStamp
    public val revision: GamepadRevision
    public data class ButtonChanged(public val value: GamepadButtonValue, override val revision: GamepadRevision, override val stamp: EventStamp) : GamepadEvent
    public data class AxisChanged(public val value: GamepadAxisValue, override val revision: GamepadRevision, override val stamp: EventStamp) : GamepadEvent
    public data class RoutingSuspended(override val revision: GamepadRevision, override val stamp: EventStamp) : GamepadEvent
    public data class RoutingResumed(override val revision: GamepadRevision, override val stamp: EventStamp) : GamepadEvent
}

public sealed interface GamepadEffect {
    public val duration: Duration
    public data class DualRumble(public val strong: Double, public val weak: Double, override val duration: Duration) : GamepadEffect
    public data class TriggerRumble(public val left: Double, public val right: Double, override val duration: Duration) : GamepadEffect
}

public sealed interface GamepadEffectState {
    public data object Starting : GamepadEffectState
    public data object Playing : GamepadEffectState
    public data object Stopping : GamepadEffectState
    public data class Terminated(public val outcome: GamepadEffectOutcome) : GamepadEffectState
}
public sealed interface GamepadEffectOutcome {
    public data object Completed : GamepadEffectOutcome
    public data class Stopped(public val reason: GamepadEffectStopReason) : GamepadEffectOutcome
    public data class Failed(public val failure: KadreFailure) : GamepadEffectOutcome
}
public enum class GamepadEffectStopReason { Requested, DeviceDisconnected, OwnershipLost, ParentSessionStopping }
```

Les intensités et axes sont dans `[-1,1]` pour les axes et `[0,1]` pour les boutons/effets. Les listes `GamepadDescriptor.buttons/axes` ne contiennent aucun doublon, respectent `maxCollectionElementsPerValue` et déterminent l’ordre canonique des listes de `GamepadState`; chaque snapshot contient exactement une valeur par contrôle du descriptor, dans cet ordre, sans entrée supplémentaire. Un contrôle `Other` conserve un `nativeCode` ASCII non vide borné par `maxMetadataCodeUnitsPerValue`; deux codes différents restent deux contrôles distincts. `GamepadDescriptor.name` suit le même budget et devient `null`, jamais tronqué, lorsqu’il est purement décoratif et hors budget. Toute `GamepadEffect.duration` est finie et strictement positive. `GamepadEffectConstraints.kinds` est non vide et `maximumDuration`, si présent, est fini et strictement positif ; un kind absent retourne `InvalidRequest("effect")` et une durée demandée supérieure retourne `InvalidRequest("effect.duration")`, sans clamp. `GamepadSnapshot`, `Gamepad`, `GamepadEffectSession` et `GamepadRoutingState` restent ceux de `DESIGN.md`.

### 6.4 IME

```kotlin
public enum class TextInputPurpose { Text, Name, Email, Url, Telephone, Number, Decimal, Password }
public enum class TextInputAction { Default, Done, Go, Next, Search, Send }
public enum class TextCapitalization { None, Sentences, Words, Characters }
public data class TextRange(public val startUtf16: Int, public val endExclusiveUtf16: Int)

public data class TextInputConfig(
    public val purpose: TextInputPurpose = TextInputPurpose.Text,
    public val action: TextInputAction = TextInputAction.Default,
    public val capitalization: TextCapitalization = TextCapitalization.None,
    public val autocorrect: Boolean = true,
    public val multiline: Boolean = false,
    public val surroundingText: String = "",
    public val selection: TextRange = TextRange(0, 0),
    public val documentRevision: TextDocumentRevision = TextDocumentRevision(0),
)

public sealed interface TextInputState {
    public data class Active(public val documentRevision: TextDocumentRevision, public val composingRange: TextRange?) : TextInputState
    public data class Suspended(public val documentRevision: TextDocumentRevision, public val composingRange: TextRange?) : TextInputState
    public data object Closed : TextInputState
}

public sealed interface TextInputEvent {
    public val stamp: EventStamp
    public val baseRevision: TextDocumentRevision
    public data class Replace(public val range: TextRange, public val text: String, override val baseRevision: TextDocumentRevision, override val stamp: EventStamp) : TextInputEvent
    public data class SelectionChanged(public val selection: TextRange, override val baseRevision: TextDocumentRevision, override val stamp: EventStamp) : TextInputEvent
    public data class CompositionChanged(public val range: TextRange?, public val text: String, override val baseRevision: TextDocumentRevision, override val stamp: EventStamp) : TextInputEvent
    public data class Action(public val action: TextInputAction, override val baseRevision: TextDocumentRevision, override val stamp: EventStamp) : TextInputEvent
}
```

`TextRange` exige `0 <= startUtf16 <= endExclusiveUtf16`. `TextInputConfig.selection` doit être contenue dans `0..surroundingText.length`; les constructeurs rejettent une violation structurelle. Les limites de texte dépendant de `KadrePolicy` sont revalidées par `openTextInput`/`updateSurroundingText` et produisent la failure de budget de leur ligne d’opération, jamais une troncature. Chaque range d’un `TextInputState` ou `TextInputEvent` est garanti contenu dans le document identifié par sa révision de base ; un backend incapable de satisfaire cette garantie réinitialise l’input au lieu de publier un range invalide.

### 6.5 Drag-and-drop

```kotlin
public enum class DropItemKind { Text, File, Uri, Binary }
public data class DropItemDescriptor(
    public val displayName: String?,
    public val sizeBytes: Long?,
    public val mimeTypes: List<String>,
    public val kind: DropItemKind,
)
```

`sizeBytes` est `null` ou positif ou nul. `mimeTypes` est un snapshot ordonné sans doublon ; chaque entrée est un media type ASCII canonique lowercase `type/subtype`, sans paramètre, ou la liste est vide lorsque le host ne connaît aucun type portable. `displayName` et chaque media type respectent `maxMetadataCodeUnitsPerValue`, tandis que la liste respecte `maxCollectionElementsPerValue`; un item essentiel hors budget rend l’offre inadmissible au lieu d’être tronqué.

Les interfaces, états, outcomes et règles de lecture restent exactement ceux de la section 10.4 de `DESIGN.md`.

### 6.6 Raw input

```kotlin
@DelicateKadreApi
public suspend fun SurfaceInput.requestRawInput(): KadreResult<RawInputAccess>

@DelicateKadreApi
public interface RawInputAccess : AutoCloseable {
    public val state: StateFlow<RawInputState>
    public val events: Flow<RawInputEvent>
    public override fun close()
}

public sealed interface RawInputState {
    public data object Active : RawInputState
    public data class Suspended(public val reason: KadreFailure) : RawInputState
    public data object Closed : RawInputState
}
public enum class RawInputUnit { DeviceCount, LogicalPixel, PhysicalPixel }
public data class RawInputEvent(
    public val deltaX: Double,
    public val deltaY: Double,
    public val unit: RawInputUnit,
    public val deviceId: DeviceId?,
    public val stamp: EventStamp,
)
```

Une surface n’accepte qu’un `RawInputAccess`. `RawInputAccess.events` est mappé sur `input.pointerMotion` dans la table fermée de delivery de `DESIGN.md`. Fermer l’accès neutralise ses deltas sans altérer `SurfaceInput.events`.

### 6.7 Permissions

```kotlin
public enum class KadrePermission {
    DisplayEnumeration, InputMonitoring, RawInput, CaptureScreen, CaptureWindow,
}
```

`PermissionState` conserve les variantes exactes de la section 10.6 de `DESIGN.md`.

## 7. Capture

### 7.1 Sources, requêtes et capabilities

```kotlin
public enum class CaptureSourceKind { Display, Window, HostSurface }

public data class CaptureSource(
    public val id: CaptureSourceId,
    public val kind: CaptureSourceKind,
    public val name: String?,
    public val size: PhysicalSize?,
    public val managerRevision: CaptureManagerRevision,
)

public sealed interface CaptureTarget {
    public data object HostChoice : CaptureTarget
    public data class Source(public val id: CaptureSourceId, public val managerRevision: CaptureManagerRevision) : CaptureTarget
    public data class Surface(public val id: SurfaceId) : CaptureTarget
}

public data class CaptureRequest(
    public val target: CaptureTarget = CaptureTarget.HostChoice,
    public val preferredSize: PhysicalSize? = null,
    public val preferredFormats: List<PixelFormat> = emptyList(),
    public val region: CaptureRegion? = null,
    public val cursorMode: CaptureCursorMode = CaptureCursorMode.EmbeddedWhenAvailable,
    public val minimumFrameInterval: Duration? = null,
)

public enum class CapturePermissionScope { Screen, Window }
public data class CapturePermissionState(
    public val screen: PermissionState,
    public val window: PermissionState,
)

public data class CaptureTargetConstraints(
    public val formats: Set<PixelFormat>,
    public val cursorModes: Set<CaptureCursorMode>,
    public val region: FeatureAvailability,
)

public data class CaptureCapabilities(
    public val screen: Capability<CaptureTargetConstraints>,
    public val window: Capability<CaptureTargetConstraints>,
    public val surface: Capability<CaptureTargetConstraints>,
    public val sourceEnumeration: Capability<Unit>,
    public val hostPicker: FeatureAvailability,
)

public data class CaptureRegion(public val rect: PhysicalRect)
public enum class CaptureCursorMode { Hidden, Embedded, EmbeddedWhenAvailable }
public enum class CaptureOrientation { Upright, Rotated90, Rotated180, Rotated270, MirroredUpright, Mirrored90, Mirrored180, Mirrored270 }
```

`CapturePermissionScope.Screen` correspond à `KadrePermission.CaptureScreen` et `Window` à `CaptureWindow`. `CaptureTarget.Surface` n’utilise aucune troisième permission implicite : sa capability expose la précondition réelle ou `Unsupported`.

`preferredFormats = emptyList()` signifie « choix backend parmi les formats des contraintes de la target » ; sinon la liste est un ordre de préférence sans doublon. `minimumFrameInterval`, lorsqu’il existe, est fini et strictement positif. Une target `Surface` capture uniquement une surface déjà possédée par la même session ; aucun rendu ni compositing n’est ajouté par Kadre. `CaptureTarget.Source` revalide le `CaptureSourceKind` contre la capability `screen` ou `window`; `HostChoice` utilise les kinds réellement présentés par le picker.

`CaptureRegion.rect` est exprimé en pixels de la source dans son orientation naturelle upright, origine en haut à gauche, avant application de `CaptureOrientation`; son origine est positive ou nulle. Il doit être entièrement contenu dans la taille connue de la source ; lorsque cette taille est inconnue, le backend valide après sélection et peut retourner `InvalidRequest("request.region")`. `preferredSize`, `preferredFormats` et `minimumFrameInterval` sont des préférences de négociation, jamais une permission de falsifier `CaptureConfiguration` : l’effective value complète fait autorité.

### 7.2 Frames, couleurs et événements

```kotlin
public sealed interface PixelFormat {
    public data object Rgba8 : PixelFormat
    public data object Bgra8 : PixelFormat
    public data object Bgrx8 : PixelFormat
    public data object Nv12 : PixelFormat
    public data object I420 : PixelFormat
    public data class Opaque(public val code: String, public val planeCount: Int) : PixelFormat
}
public enum class AlphaMode { Opaque, Straight, Premultiplied, Unknown }
public enum class ColorPrimaries { Bt601, Bt709, Bt2020, DisplayP3, Unknown }
public enum class TransferFunction { Linear, Srgb, Bt1886, Pq, Hlg, Unknown }
public enum class MatrixCoefficients { Identity, Bt601, Bt709, Bt2020NonConstant, Unknown }
public enum class ColorRange { Full, Limited, Unknown }

public data class Chromaticity(public val x: Double, public val y: Double)
public data class MasteringDisplayMetadata(
    public val red: Chromaticity,
    public val green: Chromaticity,
    public val blue: Chromaticity,
    public val whitePoint: Chromaticity,
    public val minimumLuminanceNits: Double,
    public val maximumLuminanceNits: Double,
)
public sealed interface HdrMetadata {
    public data object None : HdrMetadata
    public data class Static(
        public val masteringDisplay: MasteringDisplayMetadata?,
        public val maximumContentLightLevelNits: Double?,
        public val maximumFrameAverageLightLevelNits: Double?,
    ) : HdrMetadata
    public data object Unknown : HdrMetadata
}
public data class ColorEncoding(
    public val primaries: ColorPrimaries,
    public val transfer: TransferFunction,
    public val matrix: MatrixCoefficients,
    public val range: ColorRange,
    public val hdr: HdrMetadata,
)

public enum class CaptureDiscontinuity { PauseResume, TimestampReset, DroppedFrames, DuplicateFrame, SourceReconfigured }

public sealed interface CaptureEvent {
    public val stamp: EventStamp
    public data class StreamingStarted(public val configuration: CaptureConfiguration, override val stamp: EventStamp) : CaptureEvent
    public data class Reconfigured(public val configuration: CaptureConfiguration, override val stamp: EventStamp) : CaptureEvent
    public data class Paused(public val reason: KadreFailure?, override val stamp: EventStamp) : CaptureEvent
    public data class Resumed(public val configuration: CaptureConfiguration, override val stamp: EventStamp) : CaptureEvent
}

public sealed interface CaptureDiagnostic {
    public val stamp: EventStamp
    public data class FrameDropped(public val count: Long, override val stamp: EventStamp) : CaptureDiagnostic
    public data class TimestampDiscontinuity(public val discontinuity: CaptureDiscontinuity, override val stamp: EventStamp) : CaptureDiagnostic
    public data class BackendFallback(public val requested: PixelFormat?, public val effective: PixelFormat, override val stamp: EventStamp) : CaptureDiagnostic
}
```

`PixelFormat.Opaque.code` est un identifiant ASCII non vide d’au plus 256 unités et `planeCount > 0`. Chaque coordonnée de `Chromaticity` est finie dans `[0, 1]`. Les luminances HDR sont finies et positives ou nulles ; `MasteringDisplayMetadata.maximumLuminanceNits > minimumLuminanceNits`, et lorsque les deux light levels existent `maximumFrameAverageLightLevelNits <= maximumContentLightLevelNits`. `HdrMetadata.Static` contient au moins une information non nulle ; sinon le backend publie `HdrMetadata.None`. Une valeur native invalide produit `HdrMetadata.Unknown` et un diagnostic plutôt qu’une valeur clampée.

`CaptureDiagnostic.FrameDropped.count` est strictement positif. Les counts/limits des diagnostics génériques sont eux aussi strictement positifs ; les compteurs cumulés restent positifs ou nuls.

Les autres déclarations capture conservent exactement les signatures monomorphes de la section 11 de `DESIGN.md`.

## 8. Diagnostics et capabilities

```kotlin
public enum class KadrePlatform { Android, UIKit, Web, AppKit, Win32, X11, Wayland, Fake }
public enum class DiagnosticSeverity { Info, Warning, Error, Fatal }
public enum class KadreSubsystem { Application, Host, Lifecycle, Surface, Window, Display, Input, Gamepad, Capture, Policy }

public sealed interface KadreDiagnostic {
    public val stamp: EventStamp
    public val severity: DiagnosticSeverity
    public val subsystem: KadreSubsystem
    public data class EventLoss internal constructor(public val count: Long, public val resource: KadreResourceKind, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning }
    public data class SlowConsumer internal constructor(public val resource: KadreResourceKind, public val droppedCount: Long, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning }
    public data class CollectorRejected internal constructor(public val perFlow: Boolean, public val limit: Long, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning }
    public data class ResourceLimitHit internal constructor(public val resource: KadreResourceKind, public val limit: Long, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning }
    public data class InteractionExpired internal constructor(override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Info }
    public data class PermissionRevoked internal constructor(public val permission: KadrePermission, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning }
    public data class CapabilityChanged internal constructor(public val resource: KadreResourceKind, public val operation: KadreOperation?, public val availability: FeatureAvailability, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Info }
    public data class BackendFallback internal constructor(public val operation: KadreOperation, public val platform: KadrePlatform, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Warning }
    public data class PlatformFailureObserved internal constructor(public val failure: KadreFailure.PlatformFailure, public val operation: KadreOperation, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Error }
    public data class SessionFailure internal constructor(public val failure: KadreFailure, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Fatal }
    public data class CleanupFailure internal constructor(public val failure: KadreFailure, override val subsystem: KadreSubsystem, override val stamp: EventStamp) : KadreDiagnostic { override val severity: DiagnosticSeverity get() = DiagnosticSeverity.Error }
}
```

Pour chaque `Capability.Unsupported`, `failure.operation` est fixé par le champ :

| Champ | `KadreOperation` |
|---|---|
| `SurfaceCapabilities.cursor`, `customCursor`, `pointerCapture`, `hitTesting`, `inputDefaultBehavior` | `UpdateSurface` |
| `SurfaceCapabilities.handlerInteractions` | `InstallInteractionHandler` |
| `SurfaceCapabilities.armedInteractions` | `ArmInteraction` |
| `SurfaceCapabilities.platformAccess` | `PlatformSurfaceAccess` |
| `WindowCapabilities.title` à `icon`, puis `contentProtection` | `UpdateWindow` |
| `WindowCapabilities.attention` | `RequestWindowAttention` |
| `WindowCapabilities.closeInterception` | `RespondToCloseRequest` |
| `WindowCapabilities.platformAccess` | `PlatformWindowAccess` |
| `WindowManagerCapabilities.requestWindow` | `RequestWindow` |
| `GamepadCapabilities.effects` | `GamepadEffect` |
| `CaptureCapabilities.screen`, `window`, `surface` | `CaptureOpen` |
| `DisplayCapabilities.enumeration` | `DisplayAccess` |
| `InputCapabilities.textInput` | `TextInput` |
| `InputCapabilities.rawInput` | `RawInputAccess` |
| `CaptureCapabilities.sourceEnumeration` | `CaptureRefreshSources` |

Toute contrainte `Set<T>` d’un `Capability.Supported` est non vide ; un domaine structurellement vide utilise `Capability.Unsupported`. `ImageConstraints.formats`, `CaptureTargetConstraints.formats/cursorModes`, `GamepadEffectConstraints.kinds` et les sets de fenêtre/surface suivent cette règle. Les collections sont copiées avant publication. Les champs directement typés `FeatureAvailability` utilisent `FeatureAvailability.Unsupported` lorsqu’ils n’ont aucune opération propre.

Le couple `resource/operation` de `KadreDiagnostic.CapabilityChanged` est exact :

| Owner ou sous-feature modifié | `resource` | `operation` |
|---|---|---|
| `LifecycleCapabilities.memoryPressure` | `Host` | `null` |
| tout champ de `SurfaceCapabilities` | `Surface` | opération du tableau précédent |
| `DisplayCapabilities.enumeration` | `Display` | `DisplayAccess` |
| `WindowManagerCapabilities.requestWindow` | `WindowRequest` | `RequestWindow` |
| tout champ de `WindowCapabilities` | `Window` | opération du tableau précédent |
| `InputCapabilities.keyboard`, `pointer`, `touch`, `gestures`, `dragAndDrop` | `InputSource` | `null` |
| `InputCapabilities.textInput`, `rawInput` | `InputSource` | `TextInput` ou `RawInputAccess` |
| `GamepadCapabilities.effects` | `Gamepad` | `GamepadEffect` |
| `CaptureCapabilities.screen`, `window`, `surface`, `sourceEnumeration` | `CaptureSource` | `CaptureOpen` ou `CaptureRefreshSources` |
| `CaptureCapabilities.hostPicker` et `CaptureTargetConstraints.region` de chaque target | `CaptureSource` | `null` |

Une transition de `Capability.Unsupported` vers `Supported` ou l’inverse encode respectivement la nouvelle `availability` ou `FeatureAvailability.Unsupported`. Le diagnostic est best-effort et ne remplace jamais le snapshot, publié avant lui.

Les onze variantes sont exhaustives. Leur constructor est `internal`, mais leurs champs et destructuring restent publics pour le pattern matching. `SessionFailure` porte la failure primaire ; `CleanupFailure` porte uniquement une failure secondaire qui ne remplace jamais l’outcome. Chaque compteur détaillable possède une variante correspondante ; `CapabilityChanged`, `SessionFailure` et `CleanupFailure` n’ajoutent pas de compteur. `CapabilityChanged.operation == null` uniquement pour un champ passif sans verbe public propre ; toute capability adossée à une opération renseigne cette opération. `Capability`, `FeatureAvailability`, `KadreDiagnostics`, compteurs, failures, operations et annotations conservent les signatures fermées de `DESIGN.md`, modifiées uniquement par les compléments explicitement inscrits dans `OPERATION-CONTRACTS.md`.

## 9. Déclarations de plateforme

La forme exacte des points d’attachement et options est définie dans `BACKEND-CAPABILITIES.md`; leur décision d’export est définie dans `INTEROP-EXPORTS.md`. Aucun SDK type n’entre dans `commonMain`.

## 10. Artifact `test`

Le package public `org.graphiks.kadre.test` est exactement :

```kotlin
public class VirtualKadreClock {
    public val now: SessionInstant
    public fun advanceBy(duration: Duration)
    public fun runCurrent()
}

public data class FakeHostOptions(
    public val primarySurface: Boolean = true,
    public val primaryWindow: Boolean = true,
    public val capabilities: FakeCapabilities = FakeCapabilities.All,
)

public class FakeKadreHost(
    public val clock: VirtualKadreClock = VirtualKadreClock(),
    public val options: FakeHostOptions = FakeHostOptions(),
) : KadreHost {
    public val lifecycle: VirtualLifecycleController
    public val surfaces: VirtualSurfaceController
    public val windows: VirtualWindowController
    public val displays: VirtualDisplayController
    public val input: VirtualInputController
    public val gamepads: VirtualGamepadController
    public val capture: VirtualCaptureController
    public fun detach()
}

public sealed interface FakeCapabilities {
    public data object All : FakeCapabilities
    public data object Minimal : FakeCapabilities
    public data class Custom(
        public val unavailableOperations: Set<KadreOperation> = emptySet(),
        public val unavailableFeatures: Set<FakeFeature> = emptySet(),
    ) : FakeCapabilities
}

public enum class FakeFeature {
    MemoryPressure,
    DeviceInventory,
    Keyboard,
    Pointer,
    Touch,
    Gestures,
    DragAndDrop,
    CaptureHostPicker,
    CaptureScreenTarget,
    CaptureWindowTarget,
    CaptureSurfaceTarget,
    CaptureScreenRegion,
    CaptureWindowRegion,
    CaptureSurfaceRegion,
}

public interface VirtualLifecycleController {
    public fun setCapabilities(capabilities: LifecycleCapabilities)
    public fun setVisibility(visibility: VisibilityState): LifecycleState
    public fun setActivation(activation: ActivationState): LifecycleState
    public fun memoryPressure(level: MemoryPressureLevel)
}

public interface VirtualSurfaceController {
    public fun setMetrics(
        surfaceId: SurfaceId,
        logicalSize: LogicalSize,
        physicalSize: PhysicalSize,
        scaleFactor: Double,
        safeAreaInsets: LogicalInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    ): SurfaceState
    public fun setFocus(surfaceId: SurfaceId, focus: SurfaceFocus): SurfaceState
    public fun setVisibility(surfaceId: SurfaceId, visibility: SurfaceVisibility, occlusion: SurfaceOcclusion): SurfaceState
    public fun setTheme(surfaceId: SurfaceId, theme: SurfaceTheme): SurfaceState
    public fun setCapabilities(surfaceId: SurfaceId, capabilities: SurfaceCapabilities)
    public fun requestRedraw(surfaceId: SurfaceId)
    public fun detach(surfaceId: SurfaceId)
}

public interface VirtualWindowController {
    public val pendingRequests: List<WindowRequestId>
    public fun openHere(requestId: WindowRequestId): Window
    public fun openInNewSession(requestId: WindowRequestId, sessionId: SessionId)
    public fun reject(requestId: WindowRequestId, failure: KadreFailure)
    public fun cancel(requestId: WindowRequestId)
    public fun externalApply(windowId: WindowId, update: WindowUpdate): WindowState
    public fun setCapabilities(windowId: WindowId, capabilities: WindowCapabilities)
    public fun requestClose(
        windowId: WindowId,
        reason: WindowCloseReason = WindowCloseReason.User,
        canReject: Boolean = true,
        expiresAfter: Duration? = null,
    ): WindowCloseRequestId
    public fun forceClose(windowId: WindowId, reason: WindowCloseReason = WindowCloseReason.System)
}

public data class FakeDisplayState(
    public val bounds: PhysicalRect,
    public val type: DisplayType = DisplayType.Physical,
    public val name: String? = null,
    public val workArea: PhysicalRect? = null,
    public val scaleFactor: Double = 1.0,
    public val currentMode: DisplayMode? = null,
    public val modes: List<DisplayMode> = emptyList(),
)

public interface VirtualDisplayController {
    public fun setCapabilities(capabilities: DisplayCapabilities)
    public fun requirePermission()
    public fun denyPermission(canRequestAgain: Boolean)
    public fun fail(failure: KadreFailure)
    public fun connect(state: FakeDisplayState, primary: Boolean = false): Display
    public fun update(displayId: DisplayId, state: FakeDisplayState): DisplayState
    public fun disconnect(displayId: DisplayId)
}

public interface VirtualInputController {
    public fun setCapabilities(surfaceId: SurfaceId, capabilities: InputCapabilities)
    public fun connectDevice(descriptor: InputDeviceDescriptor): InputDevice
    public fun disconnectDevice(deviceId: DeviceId)
    public fun key(
        surfaceId: SurfaceId,
        physicalKey: PhysicalKey,
        logicalKey: LogicalKey,
        location: KeyLocation,
        state: KeyState,
        repeat: Boolean = false,
        modifiers: KeyboardModifiers = KeyboardModifiers(emptySet()),
        deviceId: DeviceId? = null,
    )
    public fun pointerEnter(
        surfaceId: SurfaceId,
        kind: PointerKind,
        position: LogicalPoint,
        deviceId: DeviceId? = null,
    ): PointerId
    public fun pointerMove(
        surfaceId: SurfaceId,
        pointerId: PointerId,
        kind: PointerKind,
        position: LogicalPoint,
        delta: LogicalDelta,
        pressure: Double? = null,
        pen: PenState? = null,
        deviceId: DeviceId? = null,
    )
    public fun pointerButton(
        surfaceId: SurfaceId,
        pointerId: PointerId,
        kind: PointerKind,
        button: PointerButton,
        state: PointerButtonState,
        position: LogicalPoint,
        pressure: Double? = null,
        pen: PenState? = null,
        deviceId: DeviceId? = null,
    )
    public fun pointerLeave(surfaceId: SurfaceId, pointerId: PointerId, deviceId: DeviceId? = null)
    public fun scroll(surfaceId: SurfaceId, delta: ScrollDelta, deviceId: DeviceId? = null)
    public fun touchStart(surfaceId: SurfaceId, position: LogicalPoint, pressure: Double? = null, deviceId: DeviceId? = null): TouchId
    public fun touchMove(surfaceId: SurfaceId, touchId: TouchId, position: LogicalPoint, pressure: Double? = null, deviceId: DeviceId? = null)
    public fun touchEnd(surfaceId: SurfaceId, touchId: TouchId, cancelled: Boolean = false, deviceId: DeviceId? = null)
    public fun gesture(surfaceId: SurfaceId, kind: GestureKind, phase: TouchPhase, delta: LogicalDelta? = null, scale: Double? = null, rotationRadians: Double? = null, pressure: Double? = null, deviceId: DeviceId? = null)
    public fun reset(surfaceId: SurfaceId, reason: InputStateResetReason, deviceId: DeviceId? = null)
    public fun offerDrop(surfaceId: SurfaceId, items: List<FakeDropItem>, position: LogicalPoint): DropOffer
    public fun moveDrop(surfaceId: SurfaceId, offerId: DropOfferId, position: LogicalPoint)
    public fun leaveDrop(surfaceId: SurfaceId, offerId: DropOfferId)
    public fun performDrop(surfaceId: SurfaceId, offerId: DropOfferId, position: LogicalPoint)
    public fun rawDelta(surfaceId: SurfaceId, deltaX: Double, deltaY: Double, unit: RawInputUnit, deviceId: DeviceId? = null)
}

public class FakeDropItem(
    public val descriptor: DropItemDescriptor,
    bytes: ByteArray,
    public val readMode: DropItemReadMode = DropItemReadMode.Replayable,
) {
    public val bytes: ByteArray
}

public interface VirtualGamepadController {
    public fun connect(descriptor: GamepadDescriptor, capabilities: GamepadCapabilities): Gamepad
    public fun button(gamepadId: GamepadId, value: GamepadButtonValue)
    public fun axis(gamepadId: GamepadId, value: GamepadAxisValue)
    public fun suspendRouting(gamepadId: GamepadId)
    public fun resumeRouting(gamepadId: GamepadId)
    public fun completeEffect(effect: GamepadEffectSession)
    public fun failEffect(effect: GamepadEffectSession, failure: KadreFailure)
    public fun disconnect(gamepadId: GamepadId)
}

public data class FakeCaptureSource(
    public val kind: CaptureSourceKind,
    public val name: String? = null,
    public val size: PhysicalSize? = null,
)

public data class FakeCaptureConfiguration(
    public val size: PhysicalSize,
    public val format: PixelFormat,
    public val colorEncoding: ColorEncoding,
    public val alphaMode: AlphaMode,
    public val orientation: CaptureOrientation = CaptureOrientation.Upright,
    public val cadence: CaptureCadence = CaptureCadence.Unknown,
    public val region: CaptureRegion? = null,
    public val cursorMode: CaptureCursorMode = CaptureCursorMode.EmbeddedWhenAvailable,
)

public interface VirtualCaptureController {
    public fun setCapabilities(capabilities: CaptureCapabilities)
    public fun setPermission(scope: CapturePermissionScope, state: PermissionState)
    public fun setSources(sources: List<FakeCaptureSource>): List<CaptureSource>
    public fun requirePermission(required: Set<KadrePermission>)
    public fun useHostPicker()
    public fun failInventory(failure: KadreFailure)
    public fun enqueueOpen(configuration: FakeCaptureConfiguration)
    public fun enqueueOpenFailure(failure: KadreFailure)
    public fun reconfigure(session: CaptureSession, configuration: FakeCaptureConfiguration)
    public suspend fun emitFrame(session: CaptureSession, frame: FakeCaptureFrame)
    public fun pause(session: CaptureSession, reason: KadreFailure? = null)
    public fun resume(session: CaptureSession)
    public fun complete(session: CaptureSession)
    public fun fail(session: CaptureSession, failure: KadreFailure)
}

public class FakePixelPlane(public val layout: PixelPlaneLayout, bytes: ByteArray) {
    public val bytes: ByteArray
}

public class FakeCaptureFrame(
    planes: List<FakePixelPlane>,
    public val sourceTimestamp: Duration? = null,
    public val duration: Duration? = null,
    public val discontinuity: CaptureDiscontinuity? = null,
) {
    public val planes: List<FakePixelPlane>
}
```

Les contrôleurs attribuent eux-mêmes IDs, revisions, stamps et operation IDs ; aucun test ne forge une valeur à constructeur interne. `pointerEnter`, `touchStart`, `connectDevice`, `connect`, `connect(display)` et `setSources` constituent les factories d’identités fake. Ils refusent par `IllegalArgumentException` un ID étranger, un ordre impossible ou une valeur hors bornes. `enqueueOpen*` est FIFO et chaque entrée est consommée par le prochain `CaptureManager.open`; une queue vide laisse l’appel suspendu. `FakeDropItem`, `FakePixelPlane` et `FakeCaptureFrame` copient profondément leurs entrées. Chaque lecture d’un getter `bytes` retourne une nouvelle copie ; `FakeCaptureFrame.planes` retourne un snapshot immuable de `FakePixelPlane` défensifs. Leur `equals`/`hashCode` est structurel et compare le contenu des octets, tandis que leur `toString` n’imprime jamais les octets. Une source timestamp `Duration` est convertie par le fake en `CaptureSourceInstant`. Les contrôleurs n’exposent aucun objet d’implémentation et exécutent les mêmes budgets, policies, state/event ordering et transitions que les adapters réels.

`VirtualLifecycleController.setVisibility(Background)` publie atomiquement `Background + Inactive`; repasser `Foreground` conserve `Inactive` jusqu’à un `setActivation(Active)` explicite. `setActivation(Active)` exige `Attached + Foreground`. Le detach terminal reste possédé par `FakeKadreHost.detach`; tout appel ultérieur du contrôleur lève `IllegalArgumentException`. `setCapabilities` accepte pour `memoryPressure` uniquement le domaine fermé de `DESIGN.md`, et `memoryPressure` exige la valeur courante `Available` avant d’admettre le signal.

`VirtualKadreClock` démarre à zéro. `advanceBy` rejette une durée négative/non finie, avance l’horloge sans exécuter de task puis `runCurrent` draine dans l’ordre FIFO toutes les tasks maintenant éligibles, y compris celles planifiées récursivement au même instant. `FakeCapabilities.All` supporte chaque capability portable que le fake peut exécuter sémantiquement ; `SurfaceCapabilities.platformAccess` reste toujours `Unsupported(PlatformSurfaceAccess)` et `WindowCapabilities.platformAccess` reste toujours `Unsupported(PlatformWindowAccess)`, car le fake ne forge aucun objet SDK ni handle natif. `Minimal` ne garantit que les axes du lifecycle, les métriques/redraw de la surface primaire et `KadrePolicies.Default`; sa pression mémoire est `Unsupported`. `Custom` vaut `All` moins les opérations et features listées. `unavailableOperations` accepte exactement `HostAttach`, `DisplayAccess`, `RequestWindow`, `UpdateWindow`, `RequestWindowAttention`, `RespondToCloseRequest`, `UpdateSurface`, `InstallInteractionHandler`, `ArmInteraction`, `GamepadEffect`, `TextInput`, `CapturePermission`, `CaptureRefreshSources`, `CaptureOpen` et `RawInputAccess`; toute autre valeur est rejetée au constructeur. Une opération mappée force ses champs `Capability` vers `Capability.Unsupported` ou son champ/state direct vers l’absence structurelle correspondante. Un élément de `unavailableFeatures` force le champ passif ou la sous-feature correspondante vers `FeatureAvailability.Unsupported`; `FakeFeature.MemoryPressure` force `LifecycleCapabilities.memoryPressure`, `FakeFeature.DeviceInventory` publie `DeviceInventory.Unsupported` et une feature `Capture*Target` force le `Capability` de cette target vers `Unsupported(CaptureOpen)`. Les sets sont copiés et une même feature ne possède qu’une clé. `FakeHostOptions(primaryWindow = true, primarySurface = false)` est invalide. Les objets initiaux utilisent 800×600 logical/physical, scale 1, insets zéro, light theme, visible/focused, surface `System(Default) + None + Enabled + HostDefault`, lifecycle `Attached + Foreground + Active` et `WindowSpec()` lorsque présents.

`FakeKadreHost.platform` vaut toujours `KadrePlatform.Fake`. Le fake ne produit spontanément aucune `PlatformFailure`; une telle failure ne peut apparaître que si le test l’a fournie explicitement à une méthode `fail*`/`reject`/`enqueueOpenFailure`.

## 11. Checklist de fermeture

- [x] Tous les packages publics sont listés.
- [x] Tous les handles, snapshots, events, outcomes, capabilities, IDs et révisions cités dans `DESIGN.md` ont une forme fermée.
- [x] Les anciennes formes génériques de géométrie sont remplacées par huit value objects monomorphes exportables.
- [x] Le raw input et l’artifact `test`, auparavant seulement nommés, ont une surface exacte.
- [x] Aucun `Any`, type FFI, renderer, widget, layout, event loop globale ou ressource closeable dans un `Flow` multicast n’entre dans le catalogue.
- [x] Les exports non-Kotlin sont délégués à un registre fermé, pas à des suppositions du compilateur.
