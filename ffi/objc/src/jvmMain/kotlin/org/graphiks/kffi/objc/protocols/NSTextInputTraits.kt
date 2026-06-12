package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextInputTraits
 */
interface NSTextInputTraits {
    // @optional
    fun autocorrectionType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'autocorrectionType' not implemented")
    
    // @optional
    fun setAutocorrectionType(autocorrectionType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setAutocorrectionType:' not implemented")
    
    // @optional
    fun spellCheckingType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'spellCheckingType' not implemented")
    
    // @optional
    fun setSpellCheckingType(spellCheckingType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setSpellCheckingType:' not implemented")
    
    // @optional
    fun grammarCheckingType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'grammarCheckingType' not implemented")
    
    // @optional
    fun setGrammarCheckingType(grammarCheckingType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setGrammarCheckingType:' not implemented")
    
    // @optional
    fun smartQuotesType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'smartQuotesType' not implemented")
    
    // @optional
    fun setSmartQuotesType(smartQuotesType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setSmartQuotesType:' not implemented")
    
    // @optional
    fun smartDashesType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'smartDashesType' not implemented")
    
    // @optional
    fun setSmartDashesType(smartDashesType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setSmartDashesType:' not implemented")
    
    // @optional
    fun smartInsertDeleteType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'smartInsertDeleteType' not implemented")
    
    // @optional
    fun setSmartInsertDeleteType(smartInsertDeleteType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setSmartInsertDeleteType:' not implemented")
    
    // @optional
    fun textReplacementType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'textReplacementType' not implemented")
    
    // @optional
    fun setTextReplacementType(textReplacementType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setTextReplacementType:' not implemented")
    
    // @optional
    fun dataDetectionType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'dataDetectionType' not implemented")
    
    // @optional
    fun setDataDetectionType(dataDetectionType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setDataDetectionType:' not implemented")
    
    // @optional
    fun linkDetectionType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'linkDetectionType' not implemented")
    
    // @optional
    fun setLinkDetectionType(linkDetectionType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setLinkDetectionType:' not implemented")
    
    // @optional
    fun textCompletionType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'textCompletionType' not implemented")
    
    // @optional
    fun setTextCompletionType(textCompletionType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setTextCompletionType:' not implemented")
    
    // @optional
    fun inlinePredictionType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'inlinePredictionType' not implemented")
    
    // @optional
    fun setInlinePredictionType(inlinePredictionType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setInlinePredictionType:' not implemented")
    
    // @optional
    fun mathExpressionCompletionType(): NSTextInputTraitType =
        throw UnsupportedOperationException("Optional ObjC method 'mathExpressionCompletionType' not implemented")
    
    // @optional
    fun setMathExpressionCompletionType(mathExpressionCompletionType: NSTextInputTraitType): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setMathExpressionCompletionType:' not implemented")
    
    // @optional
    fun writingToolsBehavior(): NSWritingToolsBehavior =
        throw UnsupportedOperationException("Optional ObjC method 'writingToolsBehavior' not implemented")
    
    // @optional
    fun setWritingToolsBehavior(writingToolsBehavior: NSWritingToolsBehavior): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setWritingToolsBehavior:' not implemented")
    
    // @optional
    fun allowedWritingToolsResultOptions(): NSWritingToolsResultOptions =
        throw UnsupportedOperationException("Optional ObjC method 'allowedWritingToolsResultOptions' not implemented")
    
    // @optional
    fun setAllowedWritingToolsResultOptions(allowedWritingToolsResultOptions: NSWritingToolsResultOptions): Unit =
        throw UnsupportedOperationException("Optional ObjC method 'setAllowedWritingToolsResultOptions:' not implemented")
    
    // @property autocorrectionType
    // @property spellCheckingType
    // @property grammarCheckingType
    // @property smartQuotesType
    // @property smartDashesType
    // @property smartInsertDeleteType
    // @property textReplacementType
    // @property dataDetectionType
    // @property linkDetectionType
    // @property textCompletionType
    // @property inlinePredictionType
    // @property mathExpressionCompletionType
    // @property writingToolsBehavior
    // @property allowedWritingToolsResultOptions