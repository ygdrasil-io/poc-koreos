/**
 * Kotlin/JVM interface for Objective-C protocol: NSPrintPanelAccessorizing
 */
interface NSPrintPanelAccessorizing {
    /** @return NSArray<NSDictionary<NSPrintPanelAccessorySummaryKey,NSString *> *> * */
    fun localizedSummaryItems(): MemorySegment
    
    /** @return NSSet<NSString *> * */
    // @optional
    fun keyPathsForValuesAffectingPreview(): MemorySegment =
        throw UnsupportedOperationException("Optional ObjC method 'keyPathsForValuesAffectingPreview' not implemented")
    
}

