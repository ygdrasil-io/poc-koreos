package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSDecimalNumberBehaviors
 */
interface NSDecimalNumberBehaviors {
    fun roundingMode(): NSRoundingMode
    
    fun scale(): Short
    
    fun exceptionDuringOperation_error_leftOperand_rightOperand(operation: MemorySegment, error: NSCalculationError, leftOperand: MemorySegment, rightOperand: MemorySegment): MemorySegment
    
}

