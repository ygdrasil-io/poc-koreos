//[kadre-core](../../../index.md)/[org.graphiks.kadre.core](../index.md)/[WindowAttributes](index.md)

# WindowAttributes

[common]\
data class [WindowAttributes](index.md)(val title: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html) = &quot;Kadre&quot;, val size: [PhysicalSize](../-physical-size/index.md)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;? = null, val visible: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = true, val resizable: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = true)

Window creation parameters.

## Constructors

| | |
|---|---|
| [WindowAttributes](-window-attributes.md) | [common]<br>constructor(title: [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html) = &quot;Kadre&quot;, size: [PhysicalSize](../-physical-size/index.md)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;? = null, visible: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = true, resizable: [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html) = true) |

## Properties

| Name | Summary |
|---|---|
| [resizable](resizable.md) | [common]<br>val [resizable](resizable.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>Indicates whether the user can resize the window. |
| [size](size.md) | [common]<br>val [size](size.md): [PhysicalSize](../-physical-size/index.md)&lt;[Int](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-int/index.html)&gt;?<br>Initial size in physical pixels, or null to use the default size. |
| [title](title.md) | [common]<br>val [title](title.md): [String](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-string/index.html)<br>Title shown in the window's title bar. |
| [visible](visible.md) | [common]<br>val [visible](visible.md): [Boolean](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-boolean/index.html)<br>Indicates whether the window is visible at the time of its creation. |