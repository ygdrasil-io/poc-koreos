package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSAccessibility
 * Inherits protocols: NSObject
 */
interface NSAccessibility : NSObject {
    fun accessibilityLayoutPointForScreenPoint(point: NSPoint): NSPoint
    
    fun accessibilityLayoutSizeForScreenSize(size: NSSize): NSSize
    
    fun accessibilityScreenPointForLayoutPoint(point: NSPoint): NSPoint
    
    fun accessibilityScreenSizeForLayoutSize(size: NSSize): NSSize
    
    fun accessibilityCellForColumn_row(column: NSInteger, row: NSInteger): MemorySegment
    
    fun accessibilityAttributedStringForRange(range: NSRange): MemorySegment
    
    fun accessibilityRangeForLine(line: NSInteger): NSRange
    
    fun accessibilityStringForRange(range: NSRange): MemorySegment
    
    fun accessibilityRangeForPosition(point: NSPoint): NSRange
    
    fun accessibilityRangeForIndex(index: NSInteger): NSRange
    
    fun accessibilityFrameForRange(range: NSRange): NSRect
    
    fun accessibilityRTFForRange(range: NSRange): MemorySegment
    
    fun accessibilityStyleRangeForIndex(index: NSInteger): NSRange
    
    fun accessibilityLineForIndex(index: NSInteger): NSInteger
    
    fun accessibilityPerformCancel(): BOOL
    
    fun accessibilityPerformConfirm(): BOOL
    
    fun accessibilityPerformDecrement(): BOOL
    
    fun accessibilityPerformDelete(): BOOL
    
    fun accessibilityPerformIncrement(): BOOL
    
    fun accessibilityPerformPick(): BOOL
    
    fun accessibilityPerformPress(): BOOL
    
    fun accessibilityPerformRaise(): BOOL
    
    fun accessibilityPerformShowAlternateUI(): BOOL
    
    fun accessibilityPerformShowDefaultUI(): BOOL
    
    fun accessibilityPerformShowMenu(): BOOL
    
    fun isAccessibilitySelectorAllowed(selector: MemorySegment): BOOL
    
    fun isAccessibilityElement(): BOOL
    
    fun setAccessibilityElement(accessibilityElement: BOOL)
    
    fun accessibilityFrame(): NSRect
    
    fun setAccessibilityFrame(accessibilityFrame: NSRect)
    
    fun isAccessibilityFocused(): BOOL
    
    fun setAccessibilityFocused(accessibilityFocused: BOOL)
    
    fun accessibilityActivationPoint(): NSPoint
    
    fun setAccessibilityActivationPoint(accessibilityActivationPoint: NSPoint)
    
    fun accessibilityTopLevelUIElement(): MemorySegment
    
    fun setAccessibilityTopLevelUIElement(accessibilityTopLevelUIElement: MemorySegment)
    
    fun accessibilityURL(): MemorySegment
    
    fun setAccessibilityURL(accessibilityURL: MemorySegment)
    
    fun accessibilityValue(): MemorySegment
    
    fun setAccessibilityValue(accessibilityValue: MemorySegment)
    
    fun accessibilityValueDescription(): MemorySegment
    
    fun setAccessibilityValueDescription(accessibilityValueDescription: MemorySegment)
    
    fun accessibilityVisibleChildren(): MemorySegment
    
    fun setAccessibilityVisibleChildren(accessibilityVisibleChildren: MemorySegment)
    
    fun accessibilitySubrole(): NSAccessibilitySubrole
    
    fun setAccessibilitySubrole(accessibilitySubrole: NSAccessibilitySubrole)
    
    fun accessibilityTitle(): MemorySegment
    
    fun setAccessibilityTitle(accessibilityTitle: MemorySegment)
    
    fun accessibilityTitleUIElement(): MemorySegment
    
    fun setAccessibilityTitleUIElement(accessibilityTitleUIElement: MemorySegment)
    
    fun accessibilityNextContents(): MemorySegment
    
    fun setAccessibilityNextContents(accessibilityNextContents: MemorySegment)
    
    fun accessibilityOrientation(): NSAccessibilityOrientation
    
    fun setAccessibilityOrientation(accessibilityOrientation: NSAccessibilityOrientation)
    
    fun accessibilityOverflowButton(): MemorySegment
    
    fun setAccessibilityOverflowButton(accessibilityOverflowButton: MemorySegment)
    
    fun accessibilityParent(): MemorySegment
    
    fun setAccessibilityParent(accessibilityParent: MemorySegment)
    
    fun accessibilityPlaceholderValue(): MemorySegment
    
    fun setAccessibilityPlaceholderValue(accessibilityPlaceholderValue: MemorySegment)
    
    fun accessibilityPreviousContents(): MemorySegment
    
    fun setAccessibilityPreviousContents(accessibilityPreviousContents: MemorySegment)
    
    fun accessibilityRole(): NSAccessibilityRole
    
    fun setAccessibilityRole(accessibilityRole: NSAccessibilityRole)
    
    fun accessibilityRoleDescription(): MemorySegment
    
    fun setAccessibilityRoleDescription(accessibilityRoleDescription: MemorySegment)
    
    fun accessibilitySearchButton(): MemorySegment
    
    fun setAccessibilitySearchButton(accessibilitySearchButton: MemorySegment)
    
    fun accessibilitySearchMenu(): MemorySegment
    
    fun setAccessibilitySearchMenu(accessibilitySearchMenu: MemorySegment)
    
    fun isAccessibilitySelected(): BOOL
    
    fun setAccessibilitySelected(accessibilitySelected: BOOL)
    
    fun accessibilitySelectedChildren(): MemorySegment
    
    fun setAccessibilitySelectedChildren(accessibilitySelectedChildren: MemorySegment)
    
    fun accessibilityServesAsTitleForUIElements(): MemorySegment
    
    fun setAccessibilityServesAsTitleForUIElements(accessibilityServesAsTitleForUIElements: MemorySegment)
    
    fun accessibilityShownMenu(): MemorySegment
    
    fun setAccessibilityShownMenu(accessibilityShownMenu: MemorySegment)
    
    fun accessibilityMinValue(): MemorySegment
    
    fun setAccessibilityMinValue(accessibilityMinValue: MemorySegment)
    
    fun accessibilityMaxValue(): MemorySegment
    
    fun setAccessibilityMaxValue(accessibilityMaxValue: MemorySegment)
    
    fun accessibilityLinkedUIElements(): MemorySegment
    
    fun setAccessibilityLinkedUIElements(accessibilityLinkedUIElements: MemorySegment)
    
    fun accessibilityWindow(): MemorySegment
    
    fun setAccessibilityWindow(accessibilityWindow: MemorySegment)
    
    fun accessibilityIdentifier(): MemorySegment
    
    fun setAccessibilityIdentifier(accessibilityIdentifier: MemorySegment)
    
    fun accessibilityHelp(): MemorySegment
    
    fun setAccessibilityHelp(accessibilityHelp: MemorySegment)
    
    fun accessibilityFilename(): MemorySegment
    
    fun setAccessibilityFilename(accessibilityFilename: MemorySegment)
    
    fun isAccessibilityExpanded(): BOOL
    
    fun setAccessibilityExpanded(accessibilityExpanded: BOOL)
    
    fun isAccessibilityEdited(): BOOL
    
    fun setAccessibilityEdited(accessibilityEdited: BOOL)
    
    fun isAccessibilityEnabled(): BOOL
    
    fun setAccessibilityEnabled(accessibilityEnabled: BOOL)
    
    fun accessibilityChildren(): MemorySegment
    
    fun setAccessibilityChildren(accessibilityChildren: MemorySegment)
    
    /** @return NSArray<id<NSAccessibilityElement>> * */
    fun accessibilityChildrenInNavigationOrder(): MemorySegment
    
    fun setAccessibilityChildrenInNavigationOrder(accessibilityChildrenInNavigationOrder: MemorySegment)
    
    fun accessibilityClearButton(): MemorySegment
    
    fun setAccessibilityClearButton(accessibilityClearButton: MemorySegment)
    
    fun accessibilityCancelButton(): MemorySegment
    
    fun setAccessibilityCancelButton(accessibilityCancelButton: MemorySegment)
    
    fun isAccessibilityProtectedContent(): BOOL
    
    fun setAccessibilityProtectedContent(accessibilityProtectedContent: BOOL)
    
    fun accessibilityContents(): MemorySegment
    
    fun setAccessibilityContents(accessibilityContents: MemorySegment)
    
    fun accessibilityLabel(): MemorySegment
    
    fun setAccessibilityLabel(accessibilityLabel: MemorySegment)
    
    fun isAccessibilityAlternateUIVisible(): BOOL
    
    fun setAccessibilityAlternateUIVisible(accessibilityAlternateUIVisible: BOOL)
    
    fun accessibilitySharedFocusElements(): MemorySegment
    
    fun setAccessibilitySharedFocusElements(accessibilitySharedFocusElements: MemorySegment)
    
    fun isAccessibilityRequired(): BOOL
    
    fun setAccessibilityRequired(accessibilityRequired: BOOL)
    
    /** @return NSArray<NSAccessibilityCustomRotor *> * */
    fun accessibilityCustomRotors(): MemorySegment
    
    fun setAccessibilityCustomRotors(accessibilityCustomRotors: MemorySegment)
    
    /** @return NSArray<NSString *> * */
    fun accessibilityUserInputLabels(): MemorySegment
    
    fun setAccessibilityUserInputLabels(accessibilityUserInputLabels: MemorySegment)
    
    /** @return NSArray<NSAttributedString *> * */
    fun accessibilityAttributedUserInputLabels(): MemorySegment
    
    fun setAccessibilityAttributedUserInputLabels(accessibilityAttributedUserInputLabels: MemorySegment)
    
    fun accessibilityApplicationFocusedUIElement(): MemorySegment
    
    fun setAccessibilityApplicationFocusedUIElement(accessibilityApplicationFocusedUIElement: MemorySegment)
    
    fun accessibilityMainWindow(): MemorySegment
    
    fun setAccessibilityMainWindow(accessibilityMainWindow: MemorySegment)
    
    fun isAccessibilityHidden(): BOOL
    
    fun setAccessibilityHidden(accessibilityHidden: BOOL)
    
    fun isAccessibilityFrontmost(): BOOL
    
    fun setAccessibilityFrontmost(accessibilityFrontmost: BOOL)
    
    fun accessibilityFocusedWindow(): MemorySegment
    
    fun setAccessibilityFocusedWindow(accessibilityFocusedWindow: MemorySegment)
    
    fun accessibilityWindows(): MemorySegment
    
    fun setAccessibilityWindows(accessibilityWindows: MemorySegment)
    
    fun accessibilityExtrasMenuBar(): MemorySegment
    
    fun setAccessibilityExtrasMenuBar(accessibilityExtrasMenuBar: MemorySegment)
    
    fun accessibilityMenuBar(): MemorySegment
    
    fun setAccessibilityMenuBar(accessibilityMenuBar: MemorySegment)
    
    fun accessibilityColumnTitles(): MemorySegment
    
    fun setAccessibilityColumnTitles(accessibilityColumnTitles: MemorySegment)
    
    fun isAccessibilityOrderedByRow(): BOOL
    
    fun setAccessibilityOrderedByRow(accessibilityOrderedByRow: BOOL)
    
    fun accessibilityHorizontalUnits(): NSAccessibilityUnits
    
    fun setAccessibilityHorizontalUnits(accessibilityHorizontalUnits: NSAccessibilityUnits)
    
    fun accessibilityVerticalUnits(): NSAccessibilityUnits
    
    fun setAccessibilityVerticalUnits(accessibilityVerticalUnits: NSAccessibilityUnits)
    
    fun accessibilityHorizontalUnitDescription(): MemorySegment
    
    fun setAccessibilityHorizontalUnitDescription(accessibilityHorizontalUnitDescription: MemorySegment)
    
    fun accessibilityVerticalUnitDescription(): MemorySegment
    
    fun setAccessibilityVerticalUnitDescription(accessibilityVerticalUnitDescription: MemorySegment)
    
    fun accessibilityHandles(): MemorySegment
    
    fun setAccessibilityHandles(accessibilityHandles: MemorySegment)
    
    fun accessibilityWarningValue(): MemorySegment
    
    fun setAccessibilityWarningValue(accessibilityWarningValue: MemorySegment)
    
    fun accessibilityCriticalValue(): MemorySegment
    
    fun setAccessibilityCriticalValue(accessibilityCriticalValue: MemorySegment)
    
    fun isAccessibilityDisclosed(): BOOL
    
    fun setAccessibilityDisclosed(accessibilityDisclosed: BOOL)
    
    fun accessibilityDisclosedByRow(): MemorySegment
    
    fun setAccessibilityDisclosedByRow(accessibilityDisclosedByRow: MemorySegment)
    
    fun accessibilityDisclosedRows(): MemorySegment
    
    fun setAccessibilityDisclosedRows(accessibilityDisclosedRows: MemorySegment)
    
    fun accessibilityDisclosureLevel(): NSInteger
    
    fun setAccessibilityDisclosureLevel(accessibilityDisclosureLevel: NSInteger)
    
    fun accessibilityMarkerUIElements(): MemorySegment
    
    fun setAccessibilityMarkerUIElements(accessibilityMarkerUIElements: MemorySegment)
    
    fun accessibilityMarkerValues(): MemorySegment
    
    fun setAccessibilityMarkerValues(accessibilityMarkerValues: MemorySegment)
    
    fun accessibilityMarkerGroupUIElement(): MemorySegment
    
    fun setAccessibilityMarkerGroupUIElement(accessibilityMarkerGroupUIElement: MemorySegment)
    
    fun accessibilityUnits(): NSAccessibilityUnits
    
    fun setAccessibilityUnits(accessibilityUnits: NSAccessibilityUnits)
    
    fun accessibilityUnitDescription(): MemorySegment
    
    fun setAccessibilityUnitDescription(accessibilityUnitDescription: MemorySegment)
    
    fun accessibilityRulerMarkerType(): NSAccessibilityRulerMarkerType
    
    fun setAccessibilityRulerMarkerType(accessibilityRulerMarkerType: NSAccessibilityRulerMarkerType)
    
    fun accessibilityMarkerTypeDescription(): MemorySegment
    
    fun setAccessibilityMarkerTypeDescription(accessibilityMarkerTypeDescription: MemorySegment)
    
    fun accessibilityHorizontalScrollBar(): MemorySegment
    
    fun setAccessibilityHorizontalScrollBar(accessibilityHorizontalScrollBar: MemorySegment)
    
    fun accessibilityVerticalScrollBar(): MemorySegment
    
    fun setAccessibilityVerticalScrollBar(accessibilityVerticalScrollBar: MemorySegment)
    
    /** @return NSArray<NSNumber *> * */
    fun accessibilityAllowedValues(): MemorySegment
    
    fun setAccessibilityAllowedValues(accessibilityAllowedValues: MemorySegment)
    
    fun accessibilityLabelUIElements(): MemorySegment
    
    fun setAccessibilityLabelUIElements(accessibilityLabelUIElements: MemorySegment)
    
    fun accessibilityLabelValue(): Float
    
    fun setAccessibilityLabelValue(accessibilityLabelValue: Float)
    
    fun accessibilitySplitters(): MemorySegment
    
    fun setAccessibilitySplitters(accessibilitySplitters: MemorySegment)
    
    fun accessibilityDecrementButton(): MemorySegment
    
    fun setAccessibilityDecrementButton(accessibilityDecrementButton: MemorySegment)
    
    fun accessibilityIncrementButton(): MemorySegment
    
    fun setAccessibilityIncrementButton(accessibilityIncrementButton: MemorySegment)
    
    fun accessibilityTabs(): MemorySegment
    
    fun setAccessibilityTabs(accessibilityTabs: MemorySegment)
    
    fun accessibilityHeader(): MemorySegment
    
    fun setAccessibilityHeader(accessibilityHeader: MemorySegment)
    
    fun accessibilityColumnCount(): NSInteger
    
    fun setAccessibilityColumnCount(accessibilityColumnCount: NSInteger)
    
    fun accessibilityRowCount(): NSInteger
    
    fun setAccessibilityRowCount(accessibilityRowCount: NSInteger)
    
    fun accessibilityIndex(): NSInteger
    
    fun setAccessibilityIndex(accessibilityIndex: NSInteger)
    
    fun accessibilityColumns(): MemorySegment
    
    fun setAccessibilityColumns(accessibilityColumns: MemorySegment)
    
    fun accessibilityRows(): MemorySegment
    
    fun setAccessibilityRows(accessibilityRows: MemorySegment)
    
    fun accessibilityVisibleRows(): MemorySegment
    
    fun setAccessibilityVisibleRows(accessibilityVisibleRows: MemorySegment)
    
    fun accessibilitySelectedRows(): MemorySegment
    
    fun setAccessibilitySelectedRows(accessibilitySelectedRows: MemorySegment)
    
    fun accessibilityVisibleColumns(): MemorySegment
    
    fun setAccessibilityVisibleColumns(accessibilityVisibleColumns: MemorySegment)
    
    fun accessibilitySelectedColumns(): MemorySegment
    
    fun setAccessibilitySelectedColumns(accessibilitySelectedColumns: MemorySegment)
    
    fun accessibilitySortDirection(): NSAccessibilitySortDirection
    
    fun setAccessibilitySortDirection(accessibilitySortDirection: NSAccessibilitySortDirection)
    
    fun accessibilityRowHeaderUIElements(): MemorySegment
    
    fun setAccessibilityRowHeaderUIElements(accessibilityRowHeaderUIElements: MemorySegment)
    
    fun accessibilitySelectedCells(): MemorySegment
    
    fun setAccessibilitySelectedCells(accessibilitySelectedCells: MemorySegment)
    
    fun accessibilityVisibleCells(): MemorySegment
    
    fun setAccessibilityVisibleCells(accessibilityVisibleCells: MemorySegment)
    
    fun accessibilityColumnHeaderUIElements(): MemorySegment
    
    fun setAccessibilityColumnHeaderUIElements(accessibilityColumnHeaderUIElements: MemorySegment)
    
    fun accessibilityRowIndexRange(): NSRange
    
    fun setAccessibilityRowIndexRange(accessibilityRowIndexRange: NSRange)
    
    fun accessibilityColumnIndexRange(): NSRange
    
    fun setAccessibilityColumnIndexRange(accessibilityColumnIndexRange: NSRange)
    
    fun accessibilityInsertionPointLineNumber(): NSInteger
    
    fun setAccessibilityInsertionPointLineNumber(accessibilityInsertionPointLineNumber: NSInteger)
    
    fun accessibilitySharedCharacterRange(): NSRange
    
    fun setAccessibilitySharedCharacterRange(accessibilitySharedCharacterRange: NSRange)
    
    fun accessibilitySharedTextUIElements(): MemorySegment
    
    fun setAccessibilitySharedTextUIElements(accessibilitySharedTextUIElements: MemorySegment)
    
    fun accessibilityVisibleCharacterRange(): NSRange
    
    fun setAccessibilityVisibleCharacterRange(accessibilityVisibleCharacterRange: NSRange)
    
    fun accessibilityNumberOfCharacters(): NSInteger
    
    fun setAccessibilityNumberOfCharacters(accessibilityNumberOfCharacters: NSInteger)
    
    fun accessibilitySelectedText(): MemorySegment
    
    fun setAccessibilitySelectedText(accessibilitySelectedText: MemorySegment)
    
    fun accessibilitySelectedTextRange(): NSRange
    
    fun setAccessibilitySelectedTextRange(accessibilitySelectedTextRange: NSRange)
    
    /** @return NSArray<NSValue *> * */
    fun accessibilitySelectedTextRanges(): MemorySegment
    
    fun setAccessibilitySelectedTextRanges(accessibilitySelectedTextRanges: MemorySegment)
    
    fun accessibilityToolbarButton(): MemorySegment
    
    fun setAccessibilityToolbarButton(accessibilityToolbarButton: MemorySegment)
    
    fun isAccessibilityModal(): BOOL
    
    fun setAccessibilityModal(accessibilityModal: BOOL)
    
    fun accessibilityProxy(): MemorySegment
    
    fun setAccessibilityProxy(accessibilityProxy: MemorySegment)
    
    fun isAccessibilityMain(): BOOL
    
    fun setAccessibilityMain(accessibilityMain: BOOL)
    
    fun accessibilityFullScreenButton(): MemorySegment
    
    fun setAccessibilityFullScreenButton(accessibilityFullScreenButton: MemorySegment)
    
    fun accessibilityGrowArea(): MemorySegment
    
    fun setAccessibilityGrowArea(accessibilityGrowArea: MemorySegment)
    
    fun accessibilityDocument(): MemorySegment
    
    fun setAccessibilityDocument(accessibilityDocument: MemorySegment)
    
    fun accessibilityDefaultButton(): MemorySegment
    
    fun setAccessibilityDefaultButton(accessibilityDefaultButton: MemorySegment)
    
    fun accessibilityCloseButton(): MemorySegment
    
    fun setAccessibilityCloseButton(accessibilityCloseButton: MemorySegment)
    
    fun accessibilityZoomButton(): MemorySegment
    
    fun setAccessibilityZoomButton(accessibilityZoomButton: MemorySegment)
    
    fun accessibilityMinimizeButton(): MemorySegment
    
    fun setAccessibilityMinimizeButton(accessibilityMinimizeButton: MemorySegment)
    
    fun isAccessibilityMinimized(): BOOL
    
    fun setAccessibilityMinimized(accessibilityMinimized: BOOL)
    
    /** @return NSArray<NSAccessibilityCustomAction *> * */
    fun accessibilityCustomActions(): MemorySegment
    
    fun setAccessibilityCustomActions(accessibilityCustomActions: MemorySegment)
    
    // @property accessibilityElement
    fun isAccessibilityElement(): BOOL
    fun setAccessibilityElement(value: BOOL)
    
    // @property accessibilityFrame
    fun accessibilityFrame(): NSRect
    fun setAccessibilityFrame(value: NSRect)
    
    // @property accessibilityFocused
    fun isAccessibilityFocused(): BOOL
    fun setAccessibilityFocused(value: BOOL)
    
    // @property accessibilityActivationPoint
    fun accessibilityActivationPoint(): NSPoint
    fun setAccessibilityActivationPoint(value: NSPoint)
    
    // @property accessibilityTopLevelUIElement
    fun accessibilityTopLevelUIElement(): MemorySegment
    fun setAccessibilityTopLevelUIElement(value: MemorySegment)
    
    // @property accessibilityURL
    fun accessibilityURL(): MemorySegment
    fun setAccessibilityURL(value: MemorySegment)
    
    // @property accessibilityValue
    fun accessibilityValue(): MemorySegment
    fun setAccessibilityValue(value: MemorySegment)
    
    // @property accessibilityValueDescription
    fun accessibilityValueDescription(): MemorySegment
    fun setAccessibilityValueDescription(value: MemorySegment)
    
    // @property accessibilityVisibleChildren
    fun accessibilityVisibleChildren(): MemorySegment
    fun setAccessibilityVisibleChildren(value: MemorySegment)
    
    // @property accessibilitySubrole
    fun accessibilitySubrole(): NSAccessibilitySubrole
    fun setAccessibilitySubrole(value: NSAccessibilitySubrole)
    
    // @property accessibilityTitle
    fun accessibilityTitle(): MemorySegment
    fun setAccessibilityTitle(value: MemorySegment)
    
    // @property accessibilityTitleUIElement
    fun accessibilityTitleUIElement(): MemorySegment
    fun setAccessibilityTitleUIElement(value: MemorySegment)
    
    // @property accessibilityNextContents
    fun accessibilityNextContents(): MemorySegment
    fun setAccessibilityNextContents(value: MemorySegment)
    
    // @property accessibilityOrientation
    fun accessibilityOrientation(): NSAccessibilityOrientation
    fun setAccessibilityOrientation(value: NSAccessibilityOrientation)
    
    // @property accessibilityOverflowButton
    fun accessibilityOverflowButton(): MemorySegment
    fun setAccessibilityOverflowButton(value: MemorySegment)
    
    // @property accessibilityParent
    fun accessibilityParent(): MemorySegment
    fun setAccessibilityParent(value: MemorySegment)
    
    // @property accessibilityPlaceholderValue
    fun accessibilityPlaceholderValue(): MemorySegment
    fun setAccessibilityPlaceholderValue(value: MemorySegment)
    
    // @property accessibilityPreviousContents
    fun accessibilityPreviousContents(): MemorySegment
    fun setAccessibilityPreviousContents(value: MemorySegment)
    
    // @property accessibilityRole
    fun accessibilityRole(): NSAccessibilityRole
    fun setAccessibilityRole(value: NSAccessibilityRole)
    
    // @property accessibilityRoleDescription
    fun accessibilityRoleDescription(): MemorySegment
    fun setAccessibilityRoleDescription(value: MemorySegment)
    
    // @property accessibilitySearchButton
    fun accessibilitySearchButton(): MemorySegment
    fun setAccessibilitySearchButton(value: MemorySegment)
    
    // @property accessibilitySearchMenu
    fun accessibilitySearchMenu(): MemorySegment
    fun setAccessibilitySearchMenu(value: MemorySegment)
    
    // @property accessibilitySelected
    fun isAccessibilitySelected(): BOOL
    fun setAccessibilitySelected(value: BOOL)
    
    // @property accessibilitySelectedChildren
    fun accessibilitySelectedChildren(): MemorySegment
    fun setAccessibilitySelectedChildren(value: MemorySegment)
    
    // @property accessibilityServesAsTitleForUIElements
    fun accessibilityServesAsTitleForUIElements(): MemorySegment
    fun setAccessibilityServesAsTitleForUIElements(value: MemorySegment)
    
    // @property accessibilityShownMenu
    fun accessibilityShownMenu(): MemorySegment
    fun setAccessibilityShownMenu(value: MemorySegment)
    
    // @property accessibilityMinValue
    fun accessibilityMinValue(): MemorySegment
    fun setAccessibilityMinValue(value: MemorySegment)
    
    // @property accessibilityMaxValue
    fun accessibilityMaxValue(): MemorySegment
    fun setAccessibilityMaxValue(value: MemorySegment)
    
    // @property accessibilityLinkedUIElements
    fun accessibilityLinkedUIElements(): MemorySegment
    fun setAccessibilityLinkedUIElements(value: MemorySegment)
    
    // @property accessibilityWindow
    fun accessibilityWindow(): MemorySegment
    fun setAccessibilityWindow(value: MemorySegment)
    
    // @property accessibilityIdentifier
    fun accessibilityIdentifier(): MemorySegment
    fun setAccessibilityIdentifier(value: MemorySegment)
    
    // @property accessibilityHelp
    fun accessibilityHelp(): MemorySegment
    fun setAccessibilityHelp(value: MemorySegment)
    
    // @property accessibilityFilename
    fun accessibilityFilename(): MemorySegment
    fun setAccessibilityFilename(value: MemorySegment)
    
    // @property accessibilityExpanded
    fun isAccessibilityExpanded(): BOOL
    fun setAccessibilityExpanded(value: BOOL)
    
    // @property accessibilityEdited
    fun isAccessibilityEdited(): BOOL
    fun setAccessibilityEdited(value: BOOL)
    
    // @property accessibilityEnabled
    fun isAccessibilityEnabled(): BOOL
    fun setAccessibilityEnabled(value: BOOL)
    
    // @property accessibilityChildren
    fun accessibilityChildren(): MemorySegment
    fun setAccessibilityChildren(value: MemorySegment)
    
    // @property accessibilityChildrenInNavigationOrder
    /** @return NSArray<id<NSAccessibilityElement>> * */
    fun accessibilityChildrenInNavigationOrder(): MemorySegment
    fun setAccessibilityChildrenInNavigationOrder(value: MemorySegment)
    
    // @property accessibilityClearButton
    fun accessibilityClearButton(): MemorySegment
    fun setAccessibilityClearButton(value: MemorySegment)
    
    // @property accessibilityCancelButton
    fun accessibilityCancelButton(): MemorySegment
    fun setAccessibilityCancelButton(value: MemorySegment)
    
    // @property accessibilityProtectedContent
    fun isAccessibilityProtectedContent(): BOOL
    fun setAccessibilityProtectedContent(value: BOOL)
    
    // @property accessibilityContents
    fun accessibilityContents(): MemorySegment
    fun setAccessibilityContents(value: MemorySegment)
    
    // @property accessibilityLabel
    fun accessibilityLabel(): MemorySegment
    fun setAccessibilityLabel(value: MemorySegment)
    
    // @property accessibilityAlternateUIVisible
    fun isAccessibilityAlternateUIVisible(): BOOL
    fun setAccessibilityAlternateUIVisible(value: BOOL)
    
    // @property accessibilitySharedFocusElements
    fun accessibilitySharedFocusElements(): MemorySegment
    fun setAccessibilitySharedFocusElements(value: MemorySegment)
    
    // @property accessibilityRequired
    fun isAccessibilityRequired(): BOOL
    fun setAccessibilityRequired(value: BOOL)
    
    // @property accessibilityCustomRotors
    /** @return NSArray<NSAccessibilityCustomRotor *> * */
    fun accessibilityCustomRotors(): MemorySegment
    fun setAccessibilityCustomRotors(value: MemorySegment)
    
    // @property accessibilityUserInputLabels
    /** @return NSArray<NSString *> * */
    fun accessibilityUserInputLabels(): MemorySegment
    fun setAccessibilityUserInputLabels(value: MemorySegment)
    
    // @property accessibilityAttributedUserInputLabels
    /** @return NSArray<NSAttributedString *> * */
    fun accessibilityAttributedUserInputLabels(): MemorySegment
    fun setAccessibilityAttributedUserInputLabels(value: MemorySegment)
    
    // @property accessibilityApplicationFocusedUIElement
    fun accessibilityApplicationFocusedUIElement(): MemorySegment
    fun setAccessibilityApplicationFocusedUIElement(value: MemorySegment)
    
    // @property accessibilityMainWindow
    fun accessibilityMainWindow(): MemorySegment
    fun setAccessibilityMainWindow(value: MemorySegment)
    
    // @property accessibilityHidden
    fun isAccessibilityHidden(): BOOL
    fun setAccessibilityHidden(value: BOOL)
    
    // @property accessibilityFrontmost
    fun isAccessibilityFrontmost(): BOOL
    fun setAccessibilityFrontmost(value: BOOL)
    
    // @property accessibilityFocusedWindow
    fun accessibilityFocusedWindow(): MemorySegment
    fun setAccessibilityFocusedWindow(value: MemorySegment)
    
    // @property accessibilityWindows
    fun accessibilityWindows(): MemorySegment
    fun setAccessibilityWindows(value: MemorySegment)
    
    // @property accessibilityExtrasMenuBar
    fun accessibilityExtrasMenuBar(): MemorySegment
    fun setAccessibilityExtrasMenuBar(value: MemorySegment)
    
    // @property accessibilityMenuBar
    fun accessibilityMenuBar(): MemorySegment
    fun setAccessibilityMenuBar(value: MemorySegment)
    
    // @property accessibilityColumnTitles
    fun accessibilityColumnTitles(): MemorySegment
    fun setAccessibilityColumnTitles(value: MemorySegment)
    
    // @property accessibilityOrderedByRow
    fun isAccessibilityOrderedByRow(): BOOL
    fun setAccessibilityOrderedByRow(value: BOOL)
    
    // @property accessibilityHorizontalUnits
    fun accessibilityHorizontalUnits(): NSAccessibilityUnits
    fun setAccessibilityHorizontalUnits(value: NSAccessibilityUnits)
    
    // @property accessibilityVerticalUnits
    fun accessibilityVerticalUnits(): NSAccessibilityUnits
    fun setAccessibilityVerticalUnits(value: NSAccessibilityUnits)
    
    // @property accessibilityHorizontalUnitDescription
    fun accessibilityHorizontalUnitDescription(): MemorySegment
    fun setAccessibilityHorizontalUnitDescription(value: MemorySegment)
    
    // @property accessibilityVerticalUnitDescription
    fun accessibilityVerticalUnitDescription(): MemorySegment
    fun setAccessibilityVerticalUnitDescription(value: MemorySegment)
    
    // @property accessibilityHandles
    fun accessibilityHandles(): MemorySegment
    fun setAccessibilityHandles(value: MemorySegment)
    
    // @property accessibilityWarningValue
    fun accessibilityWarningValue(): MemorySegment
    fun setAccessibilityWarningValue(value: MemorySegment)
    
    // @property accessibilityCriticalValue
    fun accessibilityCriticalValue(): MemorySegment
    fun setAccessibilityCriticalValue(value: MemorySegment)
    
    // @property accessibilityDisclosed
    fun isAccessibilityDisclosed(): BOOL
    fun setAccessibilityDisclosed(value: BOOL)
    
    // @property accessibilityDisclosedByRow
    fun accessibilityDisclosedByRow(): MemorySegment
    fun setAccessibilityDisclosedByRow(value: MemorySegment)
    
    // @property accessibilityDisclosedRows
    fun accessibilityDisclosedRows(): MemorySegment
    fun setAccessibilityDisclosedRows(value: MemorySegment)
    
    // @property accessibilityDisclosureLevel
    fun accessibilityDisclosureLevel(): NSInteger
    fun setAccessibilityDisclosureLevel(value: NSInteger)
    
    // @property accessibilityMarkerUIElements
    fun accessibilityMarkerUIElements(): MemorySegment
    fun setAccessibilityMarkerUIElements(value: MemorySegment)
    
    // @property accessibilityMarkerValues
    fun accessibilityMarkerValues(): MemorySegment
    fun setAccessibilityMarkerValues(value: MemorySegment)
    
    // @property accessibilityMarkerGroupUIElement
    fun accessibilityMarkerGroupUIElement(): MemorySegment
    fun setAccessibilityMarkerGroupUIElement(value: MemorySegment)
    
    // @property accessibilityUnits
    fun accessibilityUnits(): NSAccessibilityUnits
    fun setAccessibilityUnits(value: NSAccessibilityUnits)
    
    // @property accessibilityUnitDescription
    fun accessibilityUnitDescription(): MemorySegment
    fun setAccessibilityUnitDescription(value: MemorySegment)
    
    // @property accessibilityRulerMarkerType
    fun accessibilityRulerMarkerType(): NSAccessibilityRulerMarkerType
    fun setAccessibilityRulerMarkerType(value: NSAccessibilityRulerMarkerType)
    
    // @property accessibilityMarkerTypeDescription
    fun accessibilityMarkerTypeDescription(): MemorySegment
    fun setAccessibilityMarkerTypeDescription(value: MemorySegment)
    
    // @property accessibilityHorizontalScrollBar
    fun accessibilityHorizontalScrollBar(): MemorySegment
    fun setAccessibilityHorizontalScrollBar(value: MemorySegment)
    
    // @property accessibilityVerticalScrollBar
    fun accessibilityVerticalScrollBar(): MemorySegment
    fun setAccessibilityVerticalScrollBar(value: MemorySegment)
    
    // @property accessibilityAllowedValues
    /** @return NSArray<NSNumber *> * */
    fun accessibilityAllowedValues(): MemorySegment
    fun setAccessibilityAllowedValues(value: MemorySegment)
    
    // @property accessibilityLabelUIElements
    fun accessibilityLabelUIElements(): MemorySegment
    fun setAccessibilityLabelUIElements(value: MemorySegment)
    
    // @property accessibilityLabelValue
    fun accessibilityLabelValue(): Float
    fun setAccessibilityLabelValue(value: Float)
    
    // @property accessibilitySplitters
    fun accessibilitySplitters(): MemorySegment
    fun setAccessibilitySplitters(value: MemorySegment)
    
    // @property accessibilityDecrementButton
    fun accessibilityDecrementButton(): MemorySegment
    fun setAccessibilityDecrementButton(value: MemorySegment)
    
    // @property accessibilityIncrementButton
    fun accessibilityIncrementButton(): MemorySegment
    fun setAccessibilityIncrementButton(value: MemorySegment)
    
    // @property accessibilityTabs
    fun accessibilityTabs(): MemorySegment
    fun setAccessibilityTabs(value: MemorySegment)
    
    // @property accessibilityHeader
    fun accessibilityHeader(): MemorySegment
    fun setAccessibilityHeader(value: MemorySegment)
    
    // @property accessibilityColumnCount
    fun accessibilityColumnCount(): NSInteger
    fun setAccessibilityColumnCount(value: NSInteger)
    
    // @property accessibilityRowCount
    fun accessibilityRowCount(): NSInteger
    fun setAccessibilityRowCount(value: NSInteger)
    
    // @property accessibilityIndex
    fun accessibilityIndex(): NSInteger
    fun setAccessibilityIndex(value: NSInteger)
    
    // @property accessibilityColumns
    fun accessibilityColumns(): MemorySegment
    fun setAccessibilityColumns(value: MemorySegment)
    
    // @property accessibilityRows
    fun accessibilityRows(): MemorySegment
    fun setAccessibilityRows(value: MemorySegment)
    
    // @property accessibilityVisibleRows
    fun accessibilityVisibleRows(): MemorySegment
    fun setAccessibilityVisibleRows(value: MemorySegment)
    
    // @property accessibilitySelectedRows
    fun accessibilitySelectedRows(): MemorySegment
    fun setAccessibilitySelectedRows(value: MemorySegment)
    
    // @property accessibilityVisibleColumns
    fun accessibilityVisibleColumns(): MemorySegment
    fun setAccessibilityVisibleColumns(value: MemorySegment)
    
    // @property accessibilitySelectedColumns
    fun accessibilitySelectedColumns(): MemorySegment
    fun setAccessibilitySelectedColumns(value: MemorySegment)
    
    // @property accessibilitySortDirection
    fun accessibilitySortDirection(): NSAccessibilitySortDirection
    fun setAccessibilitySortDirection(value: NSAccessibilitySortDirection)
    
    // @property accessibilityRowHeaderUIElements
    fun accessibilityRowHeaderUIElements(): MemorySegment
    fun setAccessibilityRowHeaderUIElements(value: MemorySegment)
    
    // @property accessibilitySelectedCells
    fun accessibilitySelectedCells(): MemorySegment
    fun setAccessibilitySelectedCells(value: MemorySegment)
    
    // @property accessibilityVisibleCells
    fun accessibilityVisibleCells(): MemorySegment
    fun setAccessibilityVisibleCells(value: MemorySegment)
    
    // @property accessibilityColumnHeaderUIElements
    fun accessibilityColumnHeaderUIElements(): MemorySegment
    fun setAccessibilityColumnHeaderUIElements(value: MemorySegment)
    
    // @property accessibilityRowIndexRange
    fun accessibilityRowIndexRange(): NSRange
    fun setAccessibilityRowIndexRange(value: NSRange)
    
    // @property accessibilityColumnIndexRange
    fun accessibilityColumnIndexRange(): NSRange
    fun setAccessibilityColumnIndexRange(value: NSRange)
    
    // @property accessibilityInsertionPointLineNumber
    fun accessibilityInsertionPointLineNumber(): NSInteger
    fun setAccessibilityInsertionPointLineNumber(value: NSInteger)
    
    // @property accessibilitySharedCharacterRange
    fun accessibilitySharedCharacterRange(): NSRange
    fun setAccessibilitySharedCharacterRange(value: NSRange)
    
    // @property accessibilitySharedTextUIElements
    fun accessibilitySharedTextUIElements(): MemorySegment
    fun setAccessibilitySharedTextUIElements(value: MemorySegment)
    
    // @property accessibilityVisibleCharacterRange
    fun accessibilityVisibleCharacterRange(): NSRange
    fun setAccessibilityVisibleCharacterRange(value: NSRange)
    
    // @property accessibilityNumberOfCharacters
    fun accessibilityNumberOfCharacters(): NSInteger
    fun setAccessibilityNumberOfCharacters(value: NSInteger)
    
    // @property accessibilitySelectedText
    fun accessibilitySelectedText(): MemorySegment
    fun setAccessibilitySelectedText(value: MemorySegment)
    
    // @property accessibilitySelectedTextRange
    fun accessibilitySelectedTextRange(): NSRange
    fun setAccessibilitySelectedTextRange(value: NSRange)
    
    // @property accessibilitySelectedTextRanges
    /** @return NSArray<NSValue *> * */
    fun accessibilitySelectedTextRanges(): MemorySegment
    fun setAccessibilitySelectedTextRanges(value: MemorySegment)
    
    // @property accessibilityToolbarButton
    fun accessibilityToolbarButton(): MemorySegment
    fun setAccessibilityToolbarButton(value: MemorySegment)
    
    // @property accessibilityModal
    fun isAccessibilityModal(): BOOL
    fun setAccessibilityModal(value: BOOL)
    
    // @property accessibilityProxy
    fun accessibilityProxy(): MemorySegment
    fun setAccessibilityProxy(value: MemorySegment)
    
    // @property accessibilityMain
    fun isAccessibilityMain(): BOOL
    fun setAccessibilityMain(value: BOOL)
    
    // @property accessibilityFullScreenButton
    fun accessibilityFullScreenButton(): MemorySegment
    fun setAccessibilityFullScreenButton(value: MemorySegment)
    
    // @property accessibilityGrowArea
    fun accessibilityGrowArea(): MemorySegment
    fun setAccessibilityGrowArea(value: MemorySegment)
    
    // @property accessibilityDocument
    fun accessibilityDocument(): MemorySegment
    fun setAccessibilityDocument(value: MemorySegment)
    
    // @property accessibilityDefaultButton
    fun accessibilityDefaultButton(): MemorySegment
    fun setAccessibilityDefaultButton(value: MemorySegment)
    
    // @property accessibilityCloseButton
    fun accessibilityCloseButton(): MemorySegment
    fun setAccessibilityCloseButton(value: MemorySegment)
    
    // @property accessibilityZoomButton
    fun accessibilityZoomButton(): MemorySegment
    fun setAccessibilityZoomButton(value: MemorySegment)
    
    // @property accessibilityMinimizeButton
    fun accessibilityMinimizeButton(): MemorySegment
    fun setAccessibilityMinimizeButton(value: MemorySegment)
    
    // @property accessibilityMinimized
    fun isAccessibilityMinimized(): BOOL
    fun setAccessibilityMinimized(value: BOOL)
    
    // @property accessibilityCustomActions
    /** @return NSArray<NSAccessibilityCustomAction *> * */
    fun accessibilityCustomActions(): MemorySegment
    fun setAccessibilityCustomActions(value: MemorySegment)
    
}

