/**
 * Kotlin/JVM interface for Objective-C protocol: NSDecimalNumberBehaviors
 */
interface NSDecimalNumberBehaviors {
    fun roundingMode(): NSRoundingMode
    
    fun scale(): Short
    
    fun exceptionDuringOperation_error_leftOperand_rightOperand(operation: MemorySegment, error: NSCalculationError, leftOperand: MemorySegment, rightOperand: MemorySegment): MemorySegment
    
}

