/**
 * Kotlin/JVM wrapper for Objective-C class: NSMorphology
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSMorphology(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSMorphology") }
        
    }
    
    // @property grammaticalGender
    fun grammaticalGender(): NSGrammaticalGender {
        val sel = ObjCRuntime.sel("grammaticalGender")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGrammaticalGender
    }
    fun setGrammaticalGender(value: NSGrammaticalGender) {
        val sel = ObjCRuntime.sel("setGrammaticalGender:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property partOfSpeech
    fun partOfSpeech(): NSGrammaticalPartOfSpeech {
        val sel = ObjCRuntime.sel("partOfSpeech")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGrammaticalPartOfSpeech
    }
    fun setPartOfSpeech(value: NSGrammaticalPartOfSpeech) {
        val sel = ObjCRuntime.sel("setPartOfSpeech:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property number
    fun number(): NSGrammaticalNumber {
        val sel = ObjCRuntime.sel("number")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGrammaticalNumber
    }
    fun setNumber(value: NSGrammaticalNumber) {
        val sel = ObjCRuntime.sel("setNumber:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property grammaticalCase
    fun grammaticalCase(): NSGrammaticalCase {
        val sel = ObjCRuntime.sel("grammaticalCase")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGrammaticalCase
    }
    fun setGrammaticalCase(value: NSGrammaticalCase) {
        val sel = ObjCRuntime.sel("setGrammaticalCase:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property determination
    fun determination(): NSGrammaticalDetermination {
        val sel = ObjCRuntime.sel("determination")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGrammaticalDetermination
    }
    fun setDetermination(value: NSGrammaticalDetermination) {
        val sel = ObjCRuntime.sel("setDetermination:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property grammaticalPerson
    fun grammaticalPerson(): NSGrammaticalPerson {
        val sel = ObjCRuntime.sel("grammaticalPerson")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGrammaticalPerson
    }
    fun setGrammaticalPerson(value: NSGrammaticalPerson) {
        val sel = ObjCRuntime.sel("setGrammaticalPerson:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property pronounType
    fun pronounType(): NSGrammaticalPronounType {
        val sel = ObjCRuntime.sel("pronounType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGrammaticalPronounType
    }
    fun setPronounType(value: NSGrammaticalPronounType) {
        val sel = ObjCRuntime.sel("setPronounType:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property definiteness
    fun definiteness(): NSGrammaticalDefiniteness {
        val sel = ObjCRuntime.sel("definiteness")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSGrammaticalDefiniteness
    }
    fun setDefiniteness(value: NSGrammaticalDefiniteness) {
        val sel = ObjCRuntime.sel("setDefiniteness:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSCustomPronouns on NSMorphology ─────────────────────────────────────────

fun NSMorphology.customPronounForLanguage(language: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("customPronounForLanguage:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, language) as MemorySegment
}

fun NSMorphology.setCustomPronoun_forLanguage_error(features: MemorySegment, language: MemorySegment, error: MemorySegment): BOOL {
    val sel = ObjCRuntime.sel("setCustomPronoun:forLanguage:error:")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, features, language, error) as BOOL
}

// ── Category: NSMorphologyUserSettings on NSMorphology ─────────────────────────────────────────

fun NSMorphology.isUnspecified(): BOOL {
    val sel = ObjCRuntime.sel("isUnspecified")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// Class method: +[NSMorphology userMorphology]
fun NSMorphology_userMorphology(): MemorySegment {
    val sel = ObjCRuntime.sel("userMorphology")
    val cls = ObjCRuntime.getClass("NSMorphology")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// @property unspecified
fun NSMorphology.isUnspecified(): BOOL {
    val sel = ObjCRuntime.sel("isUnspecified")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// @property userMorphology
fun NSMorphology.userMorphology(): MemorySegment {
    val sel = ObjCRuntime.sel("userMorphology")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

