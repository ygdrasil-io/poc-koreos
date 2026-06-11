/**
 * Kotlin/JVM wrapper for Objective-C class: NSSpellChecker
 * Superclass: NSObject
 */
open class NSSpellChecker(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSpellChecker") }
        
        fun uniqueSpellDocumentTag(): NSInteger {
            val sel = ObjCRuntime.sel("uniqueSpellDocumentTag")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, _class, sel) as NSInteger
        }
        
        fun sharedSpellChecker(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedSpellChecker")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun sharedSpellCheckerExists(): BOOL {
            val sel = ObjCRuntime.sel("sharedSpellCheckerExists")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun isAutomaticTextReplacementEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticTextReplacementEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun isAutomaticSpellingCorrectionEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticSpellingCorrectionEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun isAutomaticQuoteSubstitutionEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticQuoteSubstitutionEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun isAutomaticDashSubstitutionEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticDashSubstitutionEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun isAutomaticCapitalizationEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticCapitalizationEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun isAutomaticPeriodSubstitutionEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticPeriodSubstitutionEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun isAutomaticTextCompletionEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticTextCompletionEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
        fun isAutomaticInlinePredictionEnabled(): BOOL {
            val sel = ObjCRuntime.sel("isAutomaticInlinePredictionEnabled")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel) as BOOL
        }
        
    }
    
    fun checkSpellingOfString_startingAt_language_wrap_inSpellDocumentWithTag_wordCount(stringToCheck: MemorySegment, startingOffset: NSInteger, language: MemorySegment, wrapFlag: BOOL, tag: NSInteger, wordCount: MemorySegment): NSRange {
        val sel = ObjCRuntime.sel("checkSpellingOfString:startingAt:language:wrap:inSpellDocumentWithTag:wordCount:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, stringToCheck, startingOffset, language, wrapFlag, tag, wordCount) as NSRange
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun checkSpellingOfString_startingAt_language_wrap_inSpellDocumentWithTag_wordCount(stringToCheck: String, startingOffset: NSInteger, language: String, wrapFlag: BOOL, tag: NSInteger, wordCount: MemorySegment): NSRange = checkSpellingOfString_startingAt_language_wrap_inSpellDocumentWithTag_wordCount(ObjCRuntime.newNSString(Arena.global(), stringToCheck), startingOffset, ObjCRuntime.newNSString(Arena.global(), language), wrapFlag, tag, wordCount)
    
    fun checkSpellingOfString_startingAt(stringToCheck: MemorySegment, startingOffset: NSInteger): NSRange {
        val sel = ObjCRuntime.sel("checkSpellingOfString:startingAt:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, stringToCheck, startingOffset) as NSRange
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun checkSpellingOfString_startingAt(stringToCheck: String, startingOffset: NSInteger): NSRange = checkSpellingOfString_startingAt(ObjCRuntime.newNSString(Arena.global(), stringToCheck), startingOffset)
    
    fun countWordsInString_language(stringToCount: MemorySegment, language: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("countWordsInString:language:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, stringToCount, language) as NSInteger
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun countWordsInString_language(stringToCount: String, language: String): NSInteger = countWordsInString_language(ObjCRuntime.newNSString(Arena.global(), stringToCount), ObjCRuntime.newNSString(Arena.global(), language))
    
    fun checkGrammarOfString_startingAt_language_wrap_inSpellDocumentWithTag_details(stringToCheck: MemorySegment, startingOffset: NSInteger, language: MemorySegment, wrapFlag: BOOL, tag: NSInteger, details: MemorySegment): NSRange {
        val sel = ObjCRuntime.sel("checkGrammarOfString:startingAt:language:wrap:inSpellDocumentWithTag:details:")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel, stringToCheck, startingOffset, language, wrapFlag, tag, details) as NSRange
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun checkGrammarOfString_startingAt_language_wrap_inSpellDocumentWithTag_details(stringToCheck: String, startingOffset: NSInteger, language: String, wrapFlag: BOOL, tag: NSInteger, details: MemorySegment): NSRange = checkGrammarOfString_startingAt_language_wrap_inSpellDocumentWithTag_details(ObjCRuntime.newNSString(Arena.global(), stringToCheck), startingOffset, ObjCRuntime.newNSString(Arena.global(), language), wrapFlag, tag, details)
    
    /** @return NSArray<NSTextCheckingResult *> * */
    fun checkString_range_types_options_inSpellDocumentWithTag_orthography_wordCount(stringToCheck: MemorySegment, range: NSRange, checkingTypes: NSTextCheckingTypes, options: MemorySegment, tag: NSInteger, orthography: MemorySegment, wordCount: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("checkString:range:types:options:inSpellDocumentWithTag:orthography:wordCount:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, stringToCheck, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), checkingTypes, options, tag, orthography, wordCount) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun checkString_range_types_options_inSpellDocumentWithTag_orthography_wordCount(stringToCheck: String, range: NSRange, checkingTypes: NSTextCheckingTypes, options: MemorySegment, tag: NSInteger, orthography: MemorySegment, wordCount: MemorySegment): MemorySegment = checkString_range_types_options_inSpellDocumentWithTag_orthography_wordCount(ObjCRuntime.newNSString(Arena.global(), stringToCheck), range, checkingTypes, options, tag, orthography, wordCount)
    
    fun requestCheckingOfString_range_types_options_inSpellDocumentWithTag_completionHandler(stringToCheck: MemorySegment, range: NSRange, checkingTypes: NSTextCheckingTypes, options: MemorySegment, tag: NSInteger, completionHandler: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("requestCheckingOfString:range:types:options:inSpellDocumentWithTag:completionHandler:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, stringToCheck, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), checkingTypes, options, tag, completionHandler) as NSInteger
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun requestCheckingOfString_range_types_options_inSpellDocumentWithTag_completionHandler(stringToCheck: String, range: NSRange, checkingTypes: NSTextCheckingTypes, options: MemorySegment, tag: NSInteger, completionHandler: MemorySegment): NSInteger = requestCheckingOfString_range_types_options_inSpellDocumentWithTag_completionHandler(ObjCRuntime.newNSString(Arena.global(), stringToCheck), range, checkingTypes, options, tag, completionHandler)
    
    fun requestCandidatesForSelectedRange_inString_types_options_inSpellDocumentWithTag_completionHandler(selectedRange: NSRange, stringToCheck: MemorySegment, checkingTypes: NSTextCheckingTypes, options: MemorySegment, tag: NSInteger, completionHandler: MemorySegment): NSInteger {
        val sel = ObjCRuntime.sel("requestCandidatesForSelectedRange:inString:types:options:inSpellDocumentWithTag:completionHandler:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel, ObjCRuntime.ObjCStructArg(selectedRange, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), stringToCheck, checkingTypes, options, tag, completionHandler) as NSInteger
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun requestCandidatesForSelectedRange_inString_types_options_inSpellDocumentWithTag_completionHandler(selectedRange: NSRange, stringToCheck: String, checkingTypes: NSTextCheckingTypes, options: MemorySegment, tag: NSInteger, completionHandler: MemorySegment): NSInteger = requestCandidatesForSelectedRange_inString_types_options_inSpellDocumentWithTag_completionHandler(selectedRange, ObjCRuntime.newNSString(Arena.global(), stringToCheck), checkingTypes, options, tag, completionHandler)
    
    fun menuForResult_string_options_atLocation_inView(result: MemorySegment, checkedString: MemorySegment, options: MemorySegment, location: NSPoint, view: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("menuForResult:string:options:atLocation:inView:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, result, checkedString, options, ObjCRuntime.ObjCStructArg(location, MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("CGPoint")), view) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun menuForResult_string_options_atLocation_inView(result: MemorySegment, checkedString: String, options: MemorySegment, location: NSPoint, view: MemorySegment): MemorySegment = menuForResult_string_options_atLocation_inView(result, ObjCRuntime.newNSString(Arena.global(), checkedString), options, location, view)
    
    /** @return NSArray<NSString *> * */
    fun userQuotesArrayForLanguage(language: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("userQuotesArrayForLanguage:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, language) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun userQuotesArrayForLanguage(language: String): MemorySegment = userQuotesArrayForLanguage(ObjCRuntime.newNSString(Arena.global(), language))
    
    fun updateSpellingPanelWithMisspelledWord(word: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("updateSpellingPanelWithMisspelledWord:")
        ObjCRuntime.msgSend(null, ptr, sel, word)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun updateSpellingPanelWithMisspelledWord(word: String): Unit = updateSpellingPanelWithMisspelledWord(ObjCRuntime.newNSString(Arena.global(), word))
    
    fun updateSpellingPanelWithGrammarString_detail(string: MemorySegment, detail: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("updateSpellingPanelWithGrammarString:detail:")
        ObjCRuntime.msgSend(null, ptr, sel, string, detail)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun updateSpellingPanelWithGrammarString_detail(string: String, detail: MemorySegment): Unit = updateSpellingPanelWithGrammarString_detail(ObjCRuntime.newNSString(Arena.global(), string), detail)
    
    fun updatePanels(): Unit {
        val sel = ObjCRuntime.sel("updatePanels")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun ignoreWord_inSpellDocumentWithTag(wordToIgnore: MemorySegment, tag: NSInteger): Unit {
        val sel = ObjCRuntime.sel("ignoreWord:inSpellDocumentWithTag:")
        ObjCRuntime.msgSend(null, ptr, sel, wordToIgnore, tag)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun ignoreWord_inSpellDocumentWithTag(wordToIgnore: String, tag: NSInteger): Unit = ignoreWord_inSpellDocumentWithTag(ObjCRuntime.newNSString(Arena.global(), wordToIgnore), tag)
    
    /** @return NSArray<NSString *> * */
    fun ignoredWordsInSpellDocumentWithTag(tag: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("ignoredWordsInSpellDocumentWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, tag) as MemorySegment
    }
    
    fun setIgnoredWords_inSpellDocumentWithTag(words: MemorySegment, tag: NSInteger): Unit {
        val sel = ObjCRuntime.sel("setIgnoredWords:inSpellDocumentWithTag:")
        ObjCRuntime.msgSend(null, ptr, sel, words, tag)
    }
    
    /** @return NSArray<NSString *> * */
    fun guessesForWordRange_inString_language_inSpellDocumentWithTag(range: NSRange, string: MemorySegment, language: MemorySegment, tag: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("guessesForWordRange:inString:language:inSpellDocumentWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), string, language, tag) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun guessesForWordRange_inString_language_inSpellDocumentWithTag(range: NSRange, string: String, language: String, tag: NSInteger): MemorySegment = guessesForWordRange_inString_language_inSpellDocumentWithTag(range, ObjCRuntime.newNSString(Arena.global(), string), ObjCRuntime.newNSString(Arena.global(), language), tag)
    
    fun correctionForWordRange_inString_language_inSpellDocumentWithTag(range: NSRange, string: MemorySegment, language: MemorySegment, tag: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("correctionForWordRange:inString:language:inSpellDocumentWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), string, language, tag) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun correctionForWordRange_inString_language_inSpellDocumentWithTagAsString(range: NSRange, string: MemorySegment, language: MemorySegment, tag: NSInteger): String = ObjCRuntime.toJavaString(correctionForWordRange_inString_language_inSpellDocumentWithTag(range, string, language, tag))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun correctionForWordRange_inString_language_inSpellDocumentWithTag(range: NSRange, string: String, language: String, tag: NSInteger): MemorySegment = correctionForWordRange_inString_language_inSpellDocumentWithTag(range, ObjCRuntime.newNSString(Arena.global(), string), ObjCRuntime.newNSString(Arena.global(), language), tag)
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun correctionForWordRange_inString_language_inSpellDocumentWithTagAsString(range: NSRange, string: String, language: String, tag: NSInteger): String = ObjCRuntime.toJavaString(correctionForWordRange_inString_language_inSpellDocumentWithTag(range, ObjCRuntime.newNSString(Arena.global(), string), ObjCRuntime.newNSString(Arena.global(), language), tag))
    
    /** @return NSArray<NSString *> * */
    fun completionsForPartialWordRange_inString_language_inSpellDocumentWithTag(range: NSRange, string: MemorySegment, language: MemorySegment, tag: NSInteger): MemorySegment {
        val sel = ObjCRuntime.sel("completionsForPartialWordRange:inString:language:inSpellDocumentWithTag:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), string, language, tag) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun completionsForPartialWordRange_inString_language_inSpellDocumentWithTag(range: NSRange, string: String, language: String, tag: NSInteger): MemorySegment = completionsForPartialWordRange_inString_language_inSpellDocumentWithTag(range, ObjCRuntime.newNSString(Arena.global(), string), ObjCRuntime.newNSString(Arena.global(), language), tag)
    
    fun languageForWordRange_inString_orthography(range: NSRange, string: MemorySegment, orthography: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("languageForWordRange:inString:orthography:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, ObjCRuntime.ObjCStructArg(range, MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange")), string, orthography) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun languageForWordRange_inString_orthographyAsString(range: NSRange, string: MemorySegment, orthography: MemorySegment): String = ObjCRuntime.toJavaString(languageForWordRange_inString_orthography(range, string, orthography))
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun languageForWordRange_inString_orthography(range: NSRange, string: String, orthography: MemorySegment): MemorySegment = languageForWordRange_inString_orthography(range, ObjCRuntime.newNSString(Arena.global(), string), orthography)
    
    /** Convenience overload — [String] parameters and [String] return type. */
    fun languageForWordRange_inString_orthographyAsString(range: NSRange, string: String, orthography: MemorySegment): String = ObjCRuntime.toJavaString(languageForWordRange_inString_orthography(range, ObjCRuntime.newNSString(Arena.global(), string), orthography))
    
    fun closeSpellDocumentWithTag(tag: NSInteger): Unit {
        val sel = ObjCRuntime.sel("closeSpellDocumentWithTag:")
        ObjCRuntime.msgSend(null, ptr, sel, tag)
    }
    
    fun recordResponse_toCorrection_forWord_language_inSpellDocumentWithTag(response: NSCorrectionResponse, correction: MemorySegment, word: MemorySegment, language: MemorySegment, tag: NSInteger): Unit {
        val sel = ObjCRuntime.sel("recordResponse:toCorrection:forWord:language:inSpellDocumentWithTag:")
        ObjCRuntime.msgSend(null, ptr, sel, response, correction, word, language, tag)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun recordResponse_toCorrection_forWord_language_inSpellDocumentWithTag(response: NSCorrectionResponse, correction: String, word: String, language: String, tag: NSInteger): Unit = recordResponse_toCorrection_forWord_language_inSpellDocumentWithTag(response, ObjCRuntime.newNSString(Arena.global(), correction), ObjCRuntime.newNSString(Arena.global(), word), ObjCRuntime.newNSString(Arena.global(), language), tag)
    
    fun showCorrectionIndicatorOfType_primaryString_alternativeStrings_forStringInRect_view_completionHandler(type: NSCorrectionIndicatorType, primaryString: MemorySegment, alternativeStrings: MemorySegment, rectOfTypedString: NSRect, view: MemorySegment, completionBlock: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showCorrectionIndicatorOfType:primaryString:alternativeStrings:forStringInRect:view:completionHandler:")
        ObjCRuntime.msgSend(null, ptr, sel, type, primaryString, alternativeStrings, ObjCRuntime.ObjCStructArg(rectOfTypedString, MemoryLayout.structLayout(MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"), ValueLayout.JAVA_DOUBLE.withName("y")).withName("origin"), MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("width"), ValueLayout.JAVA_DOUBLE.withName("height")).withName("size")).withName("CGRect")), view, completionBlock)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun showCorrectionIndicatorOfType_primaryString_alternativeStrings_forStringInRect_view_completionHandler(type: NSCorrectionIndicatorType, primaryString: String, alternativeStrings: MemorySegment, rectOfTypedString: NSRect, view: MemorySegment, completionBlock: MemorySegment): Unit = showCorrectionIndicatorOfType_primaryString_alternativeStrings_forStringInRect_view_completionHandler(type, ObjCRuntime.newNSString(Arena.global(), primaryString), alternativeStrings, rectOfTypedString, view, completionBlock)
    
    fun dismissCorrectionIndicatorForView(view: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("dismissCorrectionIndicatorForView:")
        ObjCRuntime.msgSend(null, ptr, sel, view)
    }
    
    fun showInlinePredictionForCandidates_client(candidates: MemorySegment, client: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("showInlinePredictionForCandidates:client:")
        ObjCRuntime.msgSend(null, ptr, sel, candidates, client)
    }
    
    fun preventsAutocorrectionBeforeString_language(string: MemorySegment, language: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("preventsAutocorrectionBeforeString:language:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string, language) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun preventsAutocorrectionBeforeString_language(string: String, language: String): BOOL = preventsAutocorrectionBeforeString_language(ObjCRuntime.newNSString(Arena.global(), string), ObjCRuntime.newNSString(Arena.global(), language))
    
    fun deletesAutospaceBetweenString_andString_language(precedingString: MemorySegment, followingString: MemorySegment, language: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("deletesAutospaceBetweenString:andString:language:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, precedingString, followingString, language) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun deletesAutospaceBetweenString_andString_language(precedingString: String, followingString: String, language: String): BOOL = deletesAutospaceBetweenString_andString_language(ObjCRuntime.newNSString(Arena.global(), precedingString), ObjCRuntime.newNSString(Arena.global(), followingString), ObjCRuntime.newNSString(Arena.global(), language))
    
    fun setWordFieldStringValue(string: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setWordFieldStringValue:")
        ObjCRuntime.msgSend(null, ptr, sel, string)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setWordFieldStringValue(string: String): Unit = setWordFieldStringValue(ObjCRuntime.newNSString(Arena.global(), string))
    
    fun learnWord(word: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("learnWord:")
        ObjCRuntime.msgSend(null, ptr, sel, word)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun learnWord(word: String): Unit = learnWord(ObjCRuntime.newNSString(Arena.global(), word))
    
    fun hasLearnedWord(word: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("hasLearnedWord:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, word) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun hasLearnedWord(word: String): BOOL = hasLearnedWord(ObjCRuntime.newNSString(Arena.global(), word))
    
    fun unlearnWord(word: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("unlearnWord:")
        ObjCRuntime.msgSend(null, ptr, sel, word)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun unlearnWord(word: String): Unit = unlearnWord(ObjCRuntime.newNSString(Arena.global(), word))
    
    fun language(): MemorySegment {
        val sel = ObjCRuntime.sel("language")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun languageAsString(): String = ObjCRuntime.toJavaString(language())
    
    fun setLanguage(language: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("setLanguage:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, language) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun setLanguage(language: String): BOOL = setLanguage(ObjCRuntime.newNSString(Arena.global(), language))
    
    // @property sharedSpellChecker
    fun sharedSpellChecker(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedSpellChecker")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sharedSpellCheckerExists
    fun sharedSpellCheckerExists(): BOOL {
        val sel = ObjCRuntime.sel("sharedSpellCheckerExists")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property userReplacementsDictionary
    /** @return NSDictionary<NSString *,NSString *> * */
    fun userReplacementsDictionary(): MemorySegment {
        val sel = ObjCRuntime.sel("userReplacementsDictionary")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property spellingPanel
    fun spellingPanel(): MemorySegment {
        val sel = ObjCRuntime.sel("spellingPanel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property accessoryView
    fun accessoryView(): MemorySegment {
        val sel = ObjCRuntime.sel("accessoryView")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAccessoryView(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAccessoryView:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property substitutionsPanel
    fun substitutionsPanel(): MemorySegment {
        val sel = ObjCRuntime.sel("substitutionsPanel")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property substitutionsPanelAccessoryViewController
    fun substitutionsPanelAccessoryViewController(): MemorySegment {
        val sel = ObjCRuntime.sel("substitutionsPanelAccessoryViewController")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSubstitutionsPanelAccessoryViewController(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubstitutionsPanelAccessoryViewController:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property availableLanguages
    /** @return NSArray<NSString *> * */
    fun availableLanguages(): MemorySegment {
        val sel = ObjCRuntime.sel("availableLanguages")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property userPreferredLanguages
    /** @return NSArray<NSString *> * */
    fun userPreferredLanguages(): MemorySegment {
        val sel = ObjCRuntime.sel("userPreferredLanguages")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property automaticallyIdentifiesLanguages
    fun automaticallyIdentifiesLanguages(): BOOL {
        val sel = ObjCRuntime.sel("automaticallyIdentifiesLanguages")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setAutomaticallyIdentifiesLanguages(value: BOOL) {
        val sel = ObjCRuntime.sel("setAutomaticallyIdentifiesLanguages:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property automaticTextReplacementEnabled
    fun isAutomaticTextReplacementEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isAutomaticTextReplacementEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticSpellingCorrectionEnabled
    fun isAutomaticSpellingCorrectionEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isAutomaticSpellingCorrectionEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticQuoteSubstitutionEnabled
    fun isAutomaticQuoteSubstitutionEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isAutomaticQuoteSubstitutionEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticDashSubstitutionEnabled
    fun isAutomaticDashSubstitutionEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isAutomaticDashSubstitutionEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticCapitalizationEnabled
    fun isAutomaticCapitalizationEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isAutomaticCapitalizationEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticPeriodSubstitutionEnabled
    fun isAutomaticPeriodSubstitutionEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isAutomaticPeriodSubstitutionEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticTextCompletionEnabled
    fun isAutomaticTextCompletionEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isAutomaticTextCompletionEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property automaticInlinePredictionEnabled
    fun isAutomaticInlinePredictionEnabled(): BOOL {
        val sel = ObjCRuntime.sel("isAutomaticInlinePredictionEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
}

// ── Category: NSDeprecated on NSSpellChecker ─────────────────────────────────────────

fun NSSpellChecker.guessesForWord(word: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("guessesForWord:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, word) as MemorySegment
}

fun NSSpellChecker.forgetWord(word: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("forgetWord:")
    ObjCRuntime.msgSend(null, ptr, sel, word)
}

