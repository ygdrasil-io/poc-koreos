package org.graphiks.kadre.ffi.win32.generated

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

private object kextract_runtime {
    val C_BOOL: ValueLayout = ValueLayout.JAVA_BOOLEAN
    val C_CHAR: ValueLayout = ValueLayout.JAVA_BYTE
    val C_SHORT: ValueLayout = ValueLayout.JAVA_SHORT
    val C_INT: ValueLayout = ValueLayout.JAVA_INT
    val C_LONG: ValueLayout = ValueLayout.JAVA_LONG
    val C_LONG_LONG: ValueLayout = ValueLayout.JAVA_LONG
    val C_FLOAT: ValueLayout = ValueLayout.JAVA_FLOAT
    val C_DOUBLE: ValueLayout = ValueLayout.JAVA_DOUBLE
    val C_POINTER: ValueLayout = ValueLayout.ADDRESS
}

private val _DLL_KERNEL32_DLL: SymbolLookup? = try {
    SymbolLookup.libraryLookup("Kernel32.dll", Arena.global())
} catch (ex: Throwable) {
    null
}

private fun _lookup(symbol: String): SymbolLookup {
    return when (symbol) {
        "GetCurrentThreadId", "GetModuleHandleW", "SetLastError", "GetLastError" -> _DLL_KERNEL32_DLL ?: SymbolLookup.loaderLookup()
        else -> SymbolLookup.loaderLookup()
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong uintptr_t;}
 */
typealias uintptr_t = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong size_t;}
 */
typealias size_t = Long

/**
 * {@snippet lang=c : typedef LongLong ptrdiff_t;}
 */
typealias ptrdiff_t = Long

/**
 * {@snippet lang=c : typedef LongLong intptr_t;}
 */
typealias intptr_t = Long

/**
 * {@snippet lang=c : typedef Bool __vcrt_bool;}
 */
typealias _vcrt_bool = Boolean

/**
 * {@snippet lang=c : typedef UNSIGNED = Short wchar_t;}
 */
typealias wchar_t = Short

/**
 * NS_ENUM: {@snippet lang=c : enum _EXCEPTION_DISPOSITION}
 */
enum class _EXCEPTION_DISPOSITION(val value: Long) {
    ExceptionContinueExecution(0L), ExceptionContinueSearch(1L), ExceptionNestedException(2L), ExceptionCollidedUnwind(3L);
    
    companion object {
        fun fromValue(v: Long): _EXCEPTION_DISPOSITION = entries.firstOrNull { it.value == v }
            ?: error("Unknown _EXCEPTION_DISPOSITION value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long ULONG;}
 */
typealias ULONG = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Short USHORT;}
 */
typealias USHORT = Short

/**
 * {@snippet lang=c : typedef UNSIGNED = Char UCHAR;}
 */
typealias UCHAR = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = Long DWORD;}
 */
typealias DWORD = Long

/**
 * {@snippet lang=c : typedef Int BOOL;}
 */
typealias BOOL = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Char BYTE;}
 */
typealias BYTE = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = Short WORD;}
 */
typealias WORD = Short

/**
 * {@snippet lang=c : typedef Float FLOAT;}
 */
typealias FLOAT = Float

/**
 * {@snippet lang=c : typedef Int INT;}
 */
typealias INT = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Int UINT;}
 */
typealias UINT = Int

/**
 * {@snippet lang=c : typedef Bool __crt_bool;}
 */
typealias _crt_bool = Boolean

/**
 * {@snippet lang=c : typedef Int errno_t;}
 */
typealias errno_t = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Short wint_t;}
 */
typealias wint_t = Short

/**
 * {@snippet lang=c : typedef UNSIGNED = Short wctype_t;}
 */
typealias wctype_t = Short

/**
 * {@snippet lang=c : typedef Long __time32_t;}
 */
typealias _time32_t = Long

/**
 * {@snippet lang=c : typedef LongLong __time64_t;}
 */
typealias _time64_t = Long

/**
 * {@snippet lang=c : typedef LongLong time_t;}
 */
typealias time_t = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong rsize_t;}
 */
typealias rsize_t = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong POINTER_64_INT;}
 */
typealias POINTER_64_INT = Long

/**
 * {@snippet lang=c : typedef SIGNED = Char INT8;}
 */
typealias INT8 = Byte

/**
 * {@snippet lang=c : typedef Short INT16;}
 */
typealias INT16 = Short

/**
 * {@snippet lang=c : typedef Int INT32;}
 */
typealias INT32 = Int

/**
 * {@snippet lang=c : typedef LongLong INT64;}
 */
typealias INT64 = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Char UINT8;}
 */
typealias UINT8 = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = Short UINT16;}
 */
typealias UINT16 = Short

/**
 * {@snippet lang=c : typedef UNSIGNED = Int UINT32;}
 */
typealias UINT32 = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong UINT64;}
 */
typealias UINT64 = Long

/**
 * {@snippet lang=c : typedef Int LONG32;}
 */
typealias LONG32 = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Int ULONG32;}
 */
typealias ULONG32 = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Int DWORD32;}
 */
typealias DWORD32 = Int

/**
 * {@snippet lang=c : typedef LongLong INT_PTR;}
 */
typealias INT_PTR = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong UINT_PTR;}
 */
typealias UINT_PTR = Long

/**
 * {@snippet lang=c : typedef LongLong LONG_PTR;}
 */
typealias LONG_PTR = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong ULONG_PTR;}
 */
typealias ULONG_PTR = Long

/**
 * {@snippet lang=c : typedef LongLong SHANDLE_PTR;}
 */
typealias SHANDLE_PTR = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong HANDLE_PTR;}
 */
typealias HANDLE_PTR = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Int UHALF_PTR;}
 */
typealias UHALF_PTR = Int

/**
 * {@snippet lang=c : typedef Int HALF_PTR;}
 */
typealias HALF_PTR = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong SIZE_T;}
 */
typealias SIZE_T = Long

/**
 * {@snippet lang=c : typedef LongLong SSIZE_T;}
 */
typealias SSIZE_T = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong DWORD_PTR;}
 */
typealias DWORD_PTR = Long

/**
 * {@snippet lang=c : typedef LongLong LONG64;}
 */
typealias LONG64 = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong ULONG64;}
 */
typealias ULONG64 = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong DWORD64;}
 */
typealias DWORD64 = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong KAFFINITY;}
 */
typealias KAFFINITY = Long

/**
 * {@snippet lang=c : typedef Char CHAR;}
 */
typealias CHAR = Byte

/**
 * {@snippet lang=c : typedef Short SHORT;}
 */
typealias SHORT = Short

/**
 * {@snippet lang=c : typedef Long LONG;}
 */
typealias LONG = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Short WCHAR;}
 */
typealias WCHAR = Short

/**
 * {@snippet lang=c : typedef UNSIGNED = Long UCSCHAR;}
 */
typealias UCSCHAR = Long

/**
 * {@snippet lang=c : typedef Char TCHAR;}
 */
typealias TCHAR = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = Char TBYTE;}
 */
typealias TBYTE = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = Char FCHAR;}
 */
typealias FCHAR = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = Short FSHORT;}
 */
typealias FSHORT = Short

/**
 * {@snippet lang=c : typedef UNSIGNED = Long FLONG;}
 */
typealias FLONG = Long

/**
 * {@snippet lang=c : typedef Long HRESULT;}
 */
typealias HRESULT = Long

/**
 * {@snippet lang=c : typedef Char CCHAR;}
 */
typealias CCHAR = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = Long LCID;}
 */
typealias LCID = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Short LANGID;}
 */
typealias LANGID = Short

/**
 * NS_ENUM: {@snippet lang=c : enum COMPARTMENT_ID}
 */
enum class COMPARTMENT_ID(val value: Long) {
    UNSPECIFIED_COMPARTMENT_ID(0L), DEFAULT_COMPARTMENT_ID(1L);
    
    companion object {
        fun fromValue(v: Long): COMPARTMENT_ID = entries.firstOrNull { it.value == v }
            ?: error("Unknown COMPARTMENT_ID value: $v")
    }
}

/**
 * {@snippet lang=c : typedef LongLong LONGLONG;}
 */
typealias LONGLONG = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong ULONGLONG;}
 */
typealias ULONGLONG = Long

/**
 * {@snippet lang=c : typedef LongLong USN;}
 */
typealias USN = Long

/**
 * {@snippet lang=c : typedef LongLong RTL_REFERENCE_COUNT;}
 */
typealias RTL_REFERENCE_COUNT = Long

/**
 * {@snippet lang=c : typedef Long RTL_REFERENCE_COUNT32;}
 */
typealias RTL_REFERENCE_COUNT32 = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong DWORDLONG;}
 */
typealias DWORDLONG = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Char BOOLEAN;}
 */
typealias BOOLEAN = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong KSPIN_LOCK;}
 */
typealias KSPIN_LOCK = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Long ACCESS_MASK;}
 */
typealias ACCESS_MASK = Long

/**
 * NS_ENUM: {@snippet lang=c : enum _SID_NAME_USE}
 */
enum class _SID_NAME_USE(val value: Long) {
    SidTypeUser(1L), SidTypeGroup(2L), SidTypeDomain(3L), SidTypeAlias(4L), SidTypeWellKnownGroup(5L), SidTypeDeletedAccount(6L), SidTypeInvalid(7L), SidTypeUnknown(8L), SidTypeComputer(9L), SidTypeLabel(10L), SidTypeLogonSession(11L);
    
    companion object {
        fun fromValue(v: Long): _SID_NAME_USE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SID_NAME_USE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong SID_HASH_ENTRY;}
 */
typealias SID_HASH_ENTRY = Long

/**
 * NS_ENUM: {@snippet lang=c : enum WELL_KNOWN_SID_TYPE}
 */
enum class WELL_KNOWN_SID_TYPE(val value: Long) {
    WinNullSid(0L), WinWorldSid(1L), WinLocalSid(2L), WinCreatorOwnerSid(3L), WinCreatorGroupSid(4L), WinCreatorOwnerServerSid(5L), WinCreatorGroupServerSid(6L), WinNtAuthoritySid(7L), WinDialupSid(8L), WinNetworkSid(9L), WinBatchSid(10L), WinInteractiveSid(11L), WinServiceSid(12L), WinAnonymousSid(13L), WinProxySid(14L), WinEnterpriseControllersSid(15L), WinSelfSid(16L), WinAuthenticatedUserSid(17L), WinRestrictedCodeSid(18L), WinTerminalServerSid(19L), WinRemoteLogonIdSid(20L), WinLogonIdsSid(21L), WinLocalSystemSid(22L), WinLocalServiceSid(23L), WinNetworkServiceSid(24L), WinBuiltinDomainSid(25L), WinBuiltinAdministratorsSid(26L), WinBuiltinUsersSid(27L), WinBuiltinGuestsSid(28L), WinBuiltinPowerUsersSid(29L), WinBuiltinAccountOperatorsSid(30L), WinBuiltinSystemOperatorsSid(31L), WinBuiltinPrintOperatorsSid(32L), WinBuiltinBackupOperatorsSid(33L), WinBuiltinReplicatorSid(34L), WinBuiltinPreWindows2000CompatibleAccessSid(35L), WinBuiltinRemoteDesktopUsersSid(36L), WinBuiltinNetworkConfigurationOperatorsSid(37L), WinAccountAdministratorSid(38L), WinAccountGuestSid(39L), WinAccountKrbtgtSid(40L), WinAccountDomainAdminsSid(41L), WinAccountDomainUsersSid(42L), WinAccountDomainGuestsSid(43L), WinAccountComputersSid(44L), WinAccountControllersSid(45L), WinAccountCertAdminsSid(46L), WinAccountSchemaAdminsSid(47L), WinAccountEnterpriseAdminsSid(48L), WinAccountPolicyAdminsSid(49L), WinAccountRasAndIasServersSid(50L), WinNTLMAuthenticationSid(51L), WinDigestAuthenticationSid(52L), WinSChannelAuthenticationSid(53L), WinThisOrganizationSid(54L), WinOtherOrganizationSid(55L), WinBuiltinIncomingForestTrustBuildersSid(56L), WinBuiltinPerfMonitoringUsersSid(57L), WinBuiltinPerfLoggingUsersSid(58L), WinBuiltinAuthorizationAccessSid(59L), WinBuiltinTerminalServerLicenseServersSid(60L), WinBuiltinDCOMUsersSid(61L), WinBuiltinIUsersSid(62L), WinIUserSid(63L), WinBuiltinCryptoOperatorsSid(64L), WinUntrustedLabelSid(65L), WinLowLabelSid(66L), WinMediumLabelSid(67L), WinHighLabelSid(68L), WinSystemLabelSid(69L), WinWriteRestrictedCodeSid(70L), WinCreatorOwnerRightsSid(71L), WinCacheablePrincipalsGroupSid(72L), WinNonCacheablePrincipalsGroupSid(73L), WinEnterpriseReadonlyControllersSid(74L), WinAccountReadonlyControllersSid(75L), WinBuiltinEventLogReadersGroup(76L), WinNewEnterpriseReadonlyControllersSid(77L), WinBuiltinCertSvcDComAccessGroup(78L), WinMediumPlusLabelSid(79L), WinLocalLogonSid(80L), WinConsoleLogonSid(81L), WinThisOrganizationCertificateSid(82L), WinApplicationPackageAuthoritySid(83L), WinBuiltinAnyPackageSid(84L), WinCapabilityInternetClientSid(85L), WinCapabilityInternetClientServerSid(86L), WinCapabilityPrivateNetworkClientServerSid(87L), WinCapabilityPicturesLibrarySid(88L), WinCapabilityVideosLibrarySid(89L), WinCapabilityMusicLibrarySid(90L), WinCapabilityDocumentsLibrarySid(91L), WinCapabilitySharedUserCertificatesSid(92L), WinCapabilityEnterpriseAuthenticationSid(93L), WinCapabilityRemovableStorageSid(94L), WinBuiltinRDSRemoteAccessServersSid(95L), WinBuiltinRDSEndpointServersSid(96L), WinBuiltinRDSManagementServersSid(97L), WinUserModeDriversSid(98L), WinBuiltinHyperVAdminsSid(99L), WinAccountCloneableControllersSid(100L), WinBuiltinAccessControlAssistanceOperatorsSid(101L), WinBuiltinRemoteManagementUsersSid(102L), WinAuthenticationAuthorityAssertedSid(103L), WinAuthenticationServiceAssertedSid(104L), WinLocalAccountSid(105L), WinLocalAccountAndAdministratorSid(106L), WinAccountProtectedUsersSid(107L), WinCapabilityAppointmentsSid(108L), WinCapabilityContactsSid(109L), WinAccountDefaultSystemManagedSid(110L), WinBuiltinDefaultSystemManagedGroupSid(111L), WinBuiltinStorageReplicaAdminsSid(112L), WinAccountKeyAdminsSid(113L), WinAccountEnterpriseKeyAdminsSid(114L), WinAuthenticationKeyTrustSid(115L), WinAuthenticationKeyPropertyMFASid(116L), WinAuthenticationKeyPropertyAttestationSid(117L), WinAuthenticationFreshKeyAuthSid(118L), WinBuiltinDeviceOwnersSid(119L), WinBuiltinUserModeHardwareOperatorsSid(120L), WinBuiltinOpenSSHUsersSid(121L);
    
    companion object {
        fun fromValue(v: Long): WELL_KNOWN_SID_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown WELL_KNOWN_SID_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _ACL_INFORMATION_CLASS}
 */
enum class _ACL_INFORMATION_CLASS(val value: Long) {
    AclRevisionInformation(1L), AclSizeInformation(2L);
    
    companion object {
        fun fromValue(v: Long): _ACL_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _ACL_INFORMATION_CLASS value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Short SECURITY_DESCRIPTOR_CONTROL;}
 */
typealias SECURITY_DESCRIPTOR_CONTROL = Short

/**
 * NS_ENUM: {@snippet lang=c : enum _AUDIT_EVENT_TYPE}
 */
enum class _AUDIT_EVENT_TYPE(val value: Long) {
    AuditEventObjectAccess(0L), AuditEventDirectoryServiceAccess(1L);
    
    companion object {
        fun fromValue(v: Long): _AUDIT_EVENT_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _AUDIT_EVENT_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _ACCESS_REASON_TYPE}
 */
enum class _ACCESS_REASON_TYPE(val value: Long) {
    AccessReasonNone(0L), AccessReasonAllowedAce(65536L), AccessReasonDeniedAce(131072L), AccessReasonAllowedParentAce(196608L), AccessReasonDeniedParentAce(262144L), AccessReasonNotGrantedByCape(327680L), AccessReasonNotGrantedByParentCape(393216L), AccessReasonNotGrantedToAppContainer(458752L), AccessReasonMissingPrivilege(1048576L), AccessReasonFromPrivilege(2097152L), AccessReasonIntegrityLevel(3145728L), AccessReasonOwnership(4194304L), AccessReasonNullDacl(5242880L), AccessReasonEmptyDacl(6291456L), AccessReasonNoSD(7340032L), AccessReasonNoGrant(8388608L), AccessReasonTrustLabel(9437184L), AccessReasonFilterAce(10485760L);
    
    companion object {
        fun fromValue(v: Long): _ACCESS_REASON_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _ACCESS_REASON_TYPE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long ACCESS_REASON;}
 */
typealias ACCESS_REASON = Long

/**
 * NS_ENUM: {@snippet lang=c : enum _SECURITY_IMPERSONATION_LEVEL}
 */
enum class _SECURITY_IMPERSONATION_LEVEL(val value: Long) {
    SecurityAnonymous(0L), SecurityIdentification(1L), SecurityImpersonation(2L), SecurityDelegation(3L);
    
    companion object {
        fun fromValue(v: Long): _SECURITY_IMPERSONATION_LEVEL = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SECURITY_IMPERSONATION_LEVEL value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _TOKEN_TYPE}
 */
enum class _TOKEN_TYPE(val value: Long) {
    TokenPrimary(1L), TokenImpersonation(2L);
    
    companion object {
        fun fromValue(v: Long): _TOKEN_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _TOKEN_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _TOKEN_ELEVATION_TYPE}
 */
enum class _TOKEN_ELEVATION_TYPE(val value: Long) {
    TokenElevationTypeDefault(1L), TokenElevationTypeFull(2L), TokenElevationTypeLimited(3L);
    
    companion object {
        fun fromValue(v: Long): _TOKEN_ELEVATION_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _TOKEN_ELEVATION_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _TOKEN_INFORMATION_CLASS}
 */
enum class _TOKEN_INFORMATION_CLASS(val value: Long) {
    TokenUser(1L), TokenGroups(2L), TokenPrivileges(3L), TokenOwner(4L), TokenPrimaryGroup(5L), TokenDefaultDacl(6L), TokenSource(7L), TokenType(8L), TokenImpersonationLevel(9L), TokenStatistics(10L), TokenRestrictedSids(11L), TokenSessionId(12L), TokenGroupsAndPrivileges(13L), TokenSessionReference(14L), TokenSandBoxInert(15L), TokenAuditPolicy(16L), TokenOrigin(17L), TokenElevationType(18L), TokenLinkedToken(19L), TokenElevation(20L), TokenHasRestrictions(21L), TokenAccessInformation(22L), TokenVirtualizationAllowed(23L), TokenVirtualizationEnabled(24L), TokenIntegrityLevel(25L), TokenUIAccess(26L), TokenMandatoryPolicy(27L), TokenLogonSid(28L), TokenIsAppContainer(29L), TokenCapabilities(30L), TokenAppContainerSid(31L), TokenAppContainerNumber(32L), TokenUserClaimAttributes(33L), TokenDeviceClaimAttributes(34L), TokenRestrictedUserClaimAttributes(35L), TokenRestrictedDeviceClaimAttributes(36L), TokenDeviceGroups(37L), TokenRestrictedDeviceGroups(38L), TokenSecurityAttributes(39L), TokenIsRestricted(40L), TokenProcessTrustLevel(41L), TokenPrivateNameSpace(42L), TokenSingletonAttributes(43L), TokenBnoIsolation(44L), TokenChildProcessFlags(45L), TokenIsLessPrivilegedAppContainer(46L), TokenIsSandboxed(47L), TokenIsAppSilo(48L), TokenLoggingInformation(49L), TokenLearningMode(50L), MaxTokenInfoClass(51L);
    
    companion object {
        fun fromValue(v: Long): _TOKEN_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _TOKEN_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _MANDATORY_LEVEL}
 */
enum class _MANDATORY_LEVEL(val value: Long) {
    MandatoryLevelUntrusted(0L), MandatoryLevelLow(1L), MandatoryLevelMedium(2L), MandatoryLevelHigh(3L), MandatoryLevelSystem(4L), MandatoryLevelSecureProcess(5L), MandatoryLevelCount(6L);
    
    companion object {
        fun fromValue(v: Long): _MANDATORY_LEVEL = entries.firstOrNull { it.value == v }
            ?: error("Unknown _MANDATORY_LEVEL value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Char SECURITY_CONTEXT_TRACKING_MODE;}
 */
typealias SECURITY_CONTEXT_TRACKING_MODE = Byte

/**
 * {@snippet lang=c : typedef UNSIGNED = Long SECURITY_INFORMATION;}
 */
typealias SECURITY_INFORMATION = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Char SE_SIGNING_LEVEL;}
 */
typealias SE_SIGNING_LEVEL = Byte

/**
 * NS_ENUM: {@snippet lang=c : enum _SE_IMAGE_SIGNATURE_TYPE}
 */
enum class _SE_IMAGE_SIGNATURE_TYPE(val value: Long) {
    SeImageSignatureNone(0L), SeImageSignatureEmbedded(1L), SeImageSignatureCache(2L), SeImageSignatureCatalogCached(3L), SeImageSignatureCatalogNotCached(4L), SeImageSignatureCatalogHint(5L), SeImageSignaturePackageCatalog(6L), SeImageSignaturePplMitigated(7L);
    
    companion object {
        fun fromValue(v: Long): _SE_IMAGE_SIGNATURE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SE_IMAGE_SIGNATURE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _HARDWARE_COUNTER_TYPE}
 */
enum class _HARDWARE_COUNTER_TYPE(val value: Long) {
    PMCCounter(0L), MaxHardwareCounterType(1L);
    
    companion object {
        fun fromValue(v: Long): _HARDWARE_COUNTER_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _HARDWARE_COUNTER_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _PROCESS_MITIGATION_POLICY}
 */
enum class _PROCESS_MITIGATION_POLICY(val value: Long) {
    ProcessDEPPolicy(0L), ProcessASLRPolicy(1L), ProcessDynamicCodePolicy(2L), ProcessStrictHandleCheckPolicy(3L), ProcessSystemCallDisablePolicy(4L), ProcessMitigationOptionsMask(5L), ProcessExtensionPointDisablePolicy(6L), ProcessControlFlowGuardPolicy(7L), ProcessSignaturePolicy(8L), ProcessFontDisablePolicy(9L), ProcessImageLoadPolicy(10L), ProcessSystemCallFilterPolicy(11L), ProcessPayloadRestrictionPolicy(12L), ProcessChildProcessPolicy(13L), ProcessSideChannelIsolationPolicy(14L), ProcessUserShadowStackPolicy(15L), ProcessRedirectionTrustPolicy(16L), ProcessUserPointerAuthPolicy(17L), ProcessSEHOPPolicy(18L), MaxProcessMitigationPolicy(19L);
    
    companion object {
        fun fromValue(v: Long): _PROCESS_MITIGATION_POLICY = entries.firstOrNull { it.value == v }
            ?: error("Unknown _PROCESS_MITIGATION_POLICY value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _JOBOBJECT_RATE_CONTROL_TOLERANCE}
 */
enum class _JOBOBJECT_RATE_CONTROL_TOLERANCE(val value: Long) {
    ToleranceLow(1L), ToleranceMedium(2L), ToleranceHigh(3L);
    
    companion object {
        fun fromValue(v: Long): _JOBOBJECT_RATE_CONTROL_TOLERANCE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _JOBOBJECT_RATE_CONTROL_TOLERANCE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _JOBOBJECT_RATE_CONTROL_TOLERANCE_INTERVAL}
 */
enum class _JOBOBJECT_RATE_CONTROL_TOLERANCE_INTERVAL(val value: Long) {
    ToleranceIntervalShort(1L), ToleranceIntervalMedium(2L), ToleranceIntervalLong(3L);
    
    companion object {
        fun fromValue(v: Long): _JOBOBJECT_RATE_CONTROL_TOLERANCE_INTERVAL = entries.firstOrNull { it.value == v }
            ?: error("Unknown _JOBOBJECT_RATE_CONTROL_TOLERANCE_INTERVAL value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum JOB_OBJECT_NET_RATE_CONTROL_FLAGS}
 */
enum class JOB_OBJECT_NET_RATE_CONTROL_FLAGS(val value: Long) {
    JOB_OBJECT_NET_RATE_CONTROL_ENABLE(1L), JOB_OBJECT_NET_RATE_CONTROL_MAX_BANDWIDTH(2L), JOB_OBJECT_NET_RATE_CONTROL_DSCP_TAG(4L), JOB_OBJECT_NET_RATE_CONTROL_VALID_FLAGS(7L);
    
    companion object {
        fun fromValue(v: Long): JOB_OBJECT_NET_RATE_CONTROL_FLAGS = entries.firstOrNull { it.value == v }
            ?: error("Unknown JOB_OBJECT_NET_RATE_CONTROL_FLAGS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum JOB_OBJECT_IO_RATE_CONTROL_FLAGS}
 */
enum class JOB_OBJECT_IO_RATE_CONTROL_FLAGS(val value: Long) {
    JOB_OBJECT_IO_RATE_CONTROL_ENABLE(1L), JOB_OBJECT_IO_RATE_CONTROL_STANDALONE_VOLUME(2L), JOB_OBJECT_IO_RATE_CONTROL_FORCE_UNIT_ACCESS_ALL(4L), JOB_OBJECT_IO_RATE_CONTROL_FORCE_UNIT_ACCESS_ON_SOFT_CAP(8L), JOB_OBJECT_IO_RATE_CONTROL_VALID_FLAGS(15L);
    
    companion object {
        fun fromValue(v: Long): JOB_OBJECT_IO_RATE_CONTROL_FLAGS = entries.firstOrNull { it.value == v }
            ?: error("Unknown JOB_OBJECT_IO_RATE_CONTROL_FLAGS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum JOBOBJECT_IO_ATTRIBUTION_CONTROL_FLAGS}
 */
enum class JOBOBJECT_IO_ATTRIBUTION_CONTROL_FLAGS(val value: Long) {
    JOBOBJECT_IO_ATTRIBUTION_CONTROL_ENABLE(1L), JOBOBJECT_IO_ATTRIBUTION_CONTROL_DISABLE(2L), JOBOBJECT_IO_ATTRIBUTION_CONTROL_VALID_FLAGS(3L);
    
    companion object {
        fun fromValue(v: Long): JOBOBJECT_IO_ATTRIBUTION_CONTROL_FLAGS = entries.firstOrNull { it.value == v }
            ?: error("Unknown JOBOBJECT_IO_ATTRIBUTION_CONTROL_FLAGS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _JOBOBJECTINFOCLASS}
 */
enum class _JOBOBJECTINFOCLASS(val value: Long) {
    JobObjectBasicAccountingInformation(1L), JobObjectBasicLimitInformation(2L), JobObjectBasicProcessIdList(3L), JobObjectBasicUIRestrictions(4L), JobObjectSecurityLimitInformation(5L), JobObjectEndOfJobTimeInformation(6L), JobObjectAssociateCompletionPortInformation(7L), JobObjectBasicAndIoAccountingInformation(8L), JobObjectExtendedLimitInformation(9L), JobObjectJobSetInformation(10L), JobObjectGroupInformation(11L), JobObjectNotificationLimitInformation(12L), JobObjectLimitViolationInformation(13L), JobObjectGroupInformationEx(14L), JobObjectCpuRateControlInformation(15L), JobObjectCompletionFilter(16L), JobObjectCompletionCounter(17L), JobObjectReserved1Information(18L), JobObjectReserved2Information(19L), JobObjectReserved3Information(20L), JobObjectReserved4Information(21L), JobObjectReserved5Information(22L), JobObjectReserved6Information(23L), JobObjectReserved7Information(24L), JobObjectReserved8Information(25L), JobObjectReserved9Information(26L), JobObjectReserved10Information(27L), JobObjectReserved11Information(28L), JobObjectReserved12Information(29L), JobObjectReserved13Information(30L), JobObjectReserved14Information(31L), JobObjectNetRateControlInformation(32L), JobObjectNotificationLimitInformation2(33L), JobObjectLimitViolationInformation2(34L), JobObjectCreateSilo(35L), JobObjectSiloBasicInformation(36L), JobObjectReserved15Information(37L), JobObjectReserved16Information(38L), JobObjectReserved17Information(39L), JobObjectReserved18Information(40L), JobObjectReserved19Information(41L), JobObjectReserved20Information(42L), JobObjectReserved21Information(43L), JobObjectReserved22Information(44L), JobObjectReserved23Information(45L), JobObjectReserved24Information(46L), JobObjectReserved25Information(47L), JobObjectReserved26Information(48L), JobObjectReserved27Information(49L), JobObjectReserved28Information(50L), JobObjectNetworkAccountingInformation(51L), MaxJobObjectInfoClass(52L);
    
    companion object {
        fun fromValue(v: Long): _JOBOBJECTINFOCLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _JOBOBJECTINFOCLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _SERVERSILO_STATE}
 */
enum class _SERVERSILO_STATE(val value: Long) {
    SERVERSILO_INITING(0L), SERVERSILO_STARTED(1L), SERVERSILO_SHUTTING_DOWN(2L), SERVERSILO_TERMINATING(3L), SERVERSILO_TERMINATED(4L);
    
    companion object {
        fun fromValue(v: Long): _SERVERSILO_STATE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SERVERSILO_STATE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _FIRMWARE_TYPE}
 */
enum class _FIRMWARE_TYPE(val value: Long) {
    FirmwareTypeUnknown(0L), FirmwareTypeBios(1L), FirmwareTypeUefi(2L), FirmwareTypeMax(3L);
    
    companion object {
        fun fromValue(v: Long): _FIRMWARE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _FIRMWARE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _LOGICAL_PROCESSOR_RELATIONSHIP}
 */
enum class _LOGICAL_PROCESSOR_RELATIONSHIP(val value: Long) {
    RelationProcessorCore(0L), RelationNumaNode(1L), RelationCache(2L), RelationProcessorPackage(3L), RelationGroup(4L), RelationProcessorDie(5L), RelationNumaNodeEx(6L), RelationProcessorModule(7L), RelationAll(65535L);
    
    companion object {
        fun fromValue(v: Long): _LOGICAL_PROCESSOR_RELATIONSHIP = entries.firstOrNull { it.value == v }
            ?: error("Unknown _LOGICAL_PROCESSOR_RELATIONSHIP value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _PROCESSOR_CACHE_TYPE}
 */
enum class _PROCESSOR_CACHE_TYPE(val value: Long) {
    CacheUnified(0L), CacheInstruction(1L), CacheData(2L), CacheTrace(3L), CacheUnknown(4L);
    
    companion object {
        fun fromValue(v: Long): _PROCESSOR_CACHE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _PROCESSOR_CACHE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _CPU_SET_INFORMATION_TYPE}
 */
enum class _CPU_SET_INFORMATION_TYPE(val value: Long) {
    CpuSetInformation(0L);
    
    companion object {
        fun fromValue(v: Long): _CPU_SET_INFORMATION_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _CPU_SET_INFORMATION_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _RUNTIME_REPORT_TYPE}
 */
enum class _RUNTIME_REPORT_TYPE(val value: Long) {
    RuntimeReportTypeDriver(0L), RuntimeReportTypeMax(1L);
    
    companion object {
        fun fromValue(v: Long): _RUNTIME_REPORT_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _RUNTIME_REPORT_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum MEM_EXTENDED_PARAMETER_TYPE}
 */
enum class MEM_EXTENDED_PARAMETER_TYPE(val value: Long) {
    MemExtendedParameterInvalidType(0L), MemExtendedParameterAddressRequirements(1L), MemExtendedParameterNumaNode(2L), MemExtendedParameterPartitionHandle(3L), MemExtendedParameterUserPhysicalHandle(4L), MemExtendedParameterAttributeFlags(5L), MemExtendedParameterImageMachine(6L), MemExtendedParameterMax(7L);
    
    companion object {
        fun fromValue(v: Long): MEM_EXTENDED_PARAMETER_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown MEM_EXTENDED_PARAMETER_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _MEM_DEDICATED_ATTRIBUTE_TYPE}
 */
enum class _MEM_DEDICATED_ATTRIBUTE_TYPE(val value: Long) {
    MemDedicatedAttributeReadBandwidth(0L), MemDedicatedAttributeReadLatency(1L), MemDedicatedAttributeWriteBandwidth(2L), MemDedicatedAttributeWriteLatency(3L), MemDedicatedAttributeMax(4L);
    
    companion object {
        fun fromValue(v: Long): _MEM_DEDICATED_ATTRIBUTE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _MEM_DEDICATED_ATTRIBUTE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum MEM_SECTION_EXTENDED_PARAMETER_TYPE}
 */
enum class MEM_SECTION_EXTENDED_PARAMETER_TYPE(val value: Long) {
    MemSectionExtendedParameterInvalidType(0L), MemSectionExtendedParameterUserPhysicalFlags(1L), MemSectionExtendedParameterNumaNode(2L), MemSectionExtendedParameterSigningLevel(3L), MemSectionExtendedParameterMax(4L);
    
    companion object {
        fun fromValue(v: Long): MEM_SECTION_EXTENDED_PARAMETER_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown MEM_SECTION_EXTENDED_PARAMETER_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _SharedVirtualDiskSupportType}
 */
enum class _SharedVirtualDiskSupportType(val value: Long) {
    SharedVirtualDisksUnsupported(0L), SharedVirtualDisksSupported(1L), SharedVirtualDiskSnapshotsSupported(3L), SharedVirtualDiskCDPSnapshotsSupported(7L);
    
    companion object {
        fun fromValue(v: Long): _SharedVirtualDiskSupportType = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SharedVirtualDiskSupportType value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _SharedVirtualDiskHandleState}
 */
enum class _SharedVirtualDiskHandleState(val value: Long) {
    SharedVirtualDiskHandleStateNone(0L), SharedVirtualDiskHandleStateFileShared(1L), SharedVirtualDiskHandleStateHandleShared(3L);
    
    companion object {
        fun fromValue(v: Long): _SharedVirtualDiskHandleState = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SharedVirtualDiskHandleState value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _SYSTEM_POWER_STATE}
 */
enum class _SYSTEM_POWER_STATE(val value: Long) {
    PowerSystemUnspecified(0L), PowerSystemWorking(1L), PowerSystemSleeping1(2L), PowerSystemSleeping2(3L), PowerSystemSleeping3(4L), PowerSystemHibernate(5L), PowerSystemShutdown(6L), PowerSystemMaximum(7L);
    
    companion object {
        fun fromValue(v: Long): _SYSTEM_POWER_STATE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SYSTEM_POWER_STATE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum POWER_ACTION}
 */
enum class POWER_ACTION(val value: Long) {
    PowerActionNone(0L), PowerActionReserved(1L), PowerActionSleep(2L), PowerActionHibernate(3L), PowerActionShutdown(4L), PowerActionShutdownReset(5L), PowerActionShutdownOff(6L), PowerActionWarmEject(7L), PowerActionDisplayOff(8L);
    
    companion object {
        fun fromValue(v: Long): POWER_ACTION = entries.firstOrNull { it.value == v }
            ?: error("Unknown POWER_ACTION value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _DEVICE_POWER_STATE}
 */
enum class _DEVICE_POWER_STATE(val value: Long) {
    PowerDeviceUnspecified(0L), PowerDeviceD0(1L), PowerDeviceD1(2L), PowerDeviceD2(3L), PowerDeviceD3(4L), PowerDeviceMaximum(5L);
    
    companion object {
        fun fromValue(v: Long): _DEVICE_POWER_STATE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _DEVICE_POWER_STATE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _MONITOR_DISPLAY_STATE}
 */
enum class _MONITOR_DISPLAY_STATE(val value: Long) {
    PowerMonitorOff(0L), PowerMonitorOn(1L), PowerMonitorDim(2L);
    
    companion object {
        fun fromValue(v: Long): _MONITOR_DISPLAY_STATE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _MONITOR_DISPLAY_STATE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _USER_ACTIVITY_PRESENCE}
 */
enum class _USER_ACTIVITY_PRESENCE(val value: Long) {
    PowerUserPresent(0L), PowerUserNotPresent(1L), PowerUserInactive(2L), PowerUserMaximum(3L), PowerUserInvalid(3L);
    
    companion object {
        fun fromValue(v: Long): _USER_ACTIVITY_PRESENCE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _USER_ACTIVITY_PRESENCE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _ENERGY_SAVER_STATUS}
 */
enum class _ENERGY_SAVER_STATUS(val value: Long) {
    ENERGY_SAVER_OFF(0L), ENERGY_SAVER_STANDARD(1L), ENERGY_SAVER_HIGH_SAVINGS(2L);
    
    companion object {
        fun fromValue(v: Long): _ENERGY_SAVER_STATUS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _ENERGY_SAVER_STATUS value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long EXECUTION_STATE;}
 */
typealias EXECUTION_STATE = Long

/**
 * NS_ENUM: {@snippet lang=c : enum LATENCY_TIME}
 */
enum class LATENCY_TIME(val value: Long) {
    LT_DONT_CARE(0L), LT_LOWEST_LATENCY(1L);
    
    companion object {
        fun fromValue(v: Long): LATENCY_TIME = entries.firstOrNull { it.value == v }
            ?: error("Unknown LATENCY_TIME value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _POWER_REQUEST_TYPE}
 */
enum class _POWER_REQUEST_TYPE(val value: Long) {
    PowerRequestDisplayRequired(0L), PowerRequestSystemRequired(1L), PowerRequestAwayModeRequired(2L), PowerRequestExecutionRequired(3L);
    
    companion object {
        fun fromValue(v: Long): _POWER_REQUEST_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _POWER_REQUEST_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum POWER_INFORMATION_LEVEL}
 */
enum class POWER_INFORMATION_LEVEL(val value: Long) {
    SystemPowerPolicyAc(0L), SystemPowerPolicyDc(1L), VerifySystemPolicyAc(2L), VerifySystemPolicyDc(3L), SystemPowerCapabilities(4L), SystemBatteryState(5L), SystemPowerStateHandler(6L), ProcessorStateHandler(7L), SystemPowerPolicyCurrent(8L), AdministratorPowerPolicy(9L), SystemReserveHiberFile(10L), ProcessorInformation(11L), SystemPowerInformation(12L), ProcessorStateHandler2(13L), LastWakeTime(14L), LastSleepTime(15L), SystemExecutionState(16L), SystemPowerStateNotifyHandler(17L), ProcessorPowerPolicyAc(18L), ProcessorPowerPolicyDc(19L), VerifyProcessorPowerPolicyAc(20L), VerifyProcessorPowerPolicyDc(21L), ProcessorPowerPolicyCurrent(22L), SystemPowerStateLogging(23L), SystemPowerLoggingEntry(24L), SetPowerSettingValue(25L), NotifyUserPowerSetting(26L), PowerInformationLevelUnused0(27L), SystemMonitorHiberBootPowerOff(28L), SystemVideoState(29L), TraceApplicationPowerMessage(30L), TraceApplicationPowerMessageEnd(31L), ProcessorPerfStates(32L), ProcessorIdleStates(33L), ProcessorCap(34L), SystemWakeSource(35L), SystemHiberFileInformation(36L), TraceServicePowerMessage(37L), ProcessorLoad(38L), PowerShutdownNotification(39L), MonitorCapabilities(40L), SessionPowerInit(41L), SessionDisplayState(42L), PowerRequestCreate(43L), PowerRequestAction(44L), GetPowerRequestList(45L), ProcessorInformationEx(46L), NotifyUserModeLegacyPowerEvent(47L), GroupPark(48L), ProcessorIdleDomains(49L), WakeTimerList(50L), SystemHiberFileSize(51L), ProcessorIdleStatesHv(52L), ProcessorPerfStatesHv(53L), ProcessorPerfCapHv(54L), ProcessorSetIdle(55L), LogicalProcessorIdling(56L), UserPresence(57L), PowerSettingNotificationName(58L), GetPowerSettingValue(59L), IdleResiliency(60L), SessionRITState(61L), SessionConnectNotification(62L), SessionPowerCleanup(63L), SessionLockState(64L), SystemHiberbootState(65L), PlatformInformation(66L), PdcInvocation(67L), MonitorInvocation(68L), FirmwareTableInformationRegistered(69L), SetShutdownSelectedTime(70L), SuspendResumeInvocation(71L), PlmPowerRequestCreate(72L), ScreenOff(73L), CsDeviceNotification(74L), PlatformRole(75L), LastResumePerformance(76L), DisplayBurst(77L), ExitLatencySamplingPercentage(78L), RegisterSpmPowerSettings(79L), PlatformIdleStates(80L), ProcessorIdleVeto(81L), PlatformIdleVeto(82L), SystemBatteryStatePrecise(83L), ThermalEvent(84L), PowerRequestActionInternal(85L), BatteryDeviceState(86L), PowerInformationInternal(87L), ThermalStandby(88L), SystemHiberFileType(89L), PhysicalPowerButtonPress(90L), QueryPotentialDripsConstraint(91L), EnergyTrackerCreate(92L), EnergyTrackerQuery(93L), UpdateBlackBoxRecorder(94L), SessionAllowExternalDmaDevices(95L), SendSuspendResumeNotification(96L), BlackBoxRecorderDirectAccessBuffer(97L), SystemPowerSourceState(98L), PowerInformationLevelMaximum(99L);
    
    companion object {
        fun fromValue(v: Long): POWER_INFORMATION_LEVEL = entries.firstOrNull { it.value == v }
            ?: error("Unknown POWER_INFORMATION_LEVEL value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum POWER_USER_PRESENCE_TYPE}
 */
enum class POWER_USER_PRESENCE_TYPE(val value: Long) {
    UserNotPresent(0L), UserPresent(1L), UserUnknown(255L);
    
    companion object {
        fun fromValue(v: Long): POWER_USER_PRESENCE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown POWER_USER_PRESENCE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum POWER_MONITOR_REQUEST_REASON}
 */
enum class POWER_MONITOR_REQUEST_REASON(val value: Long) {
    MonitorRequestReasonUnknown(0L), MonitorRequestReasonPowerButton(1L), MonitorRequestReasonRemoteConnection(2L), MonitorRequestReasonScMonitorpower(3L), MonitorRequestReasonUserInput(4L), MonitorRequestReasonAcDcDisplayBurst(5L), MonitorRequestReasonUserDisplayBurst(6L), MonitorRequestReasonPoSetSystemState(7L), MonitorRequestReasonSetThreadExecutionState(8L), MonitorRequestReasonFullWake(9L), MonitorRequestReasonSessionUnlock(10L), MonitorRequestReasonScreenOffRequest(11L), MonitorRequestReasonIdleTimeout(12L), MonitorRequestReasonPolicyChange(13L), MonitorRequestReasonSleepButton(14L), MonitorRequestReasonLid(15L), MonitorRequestReasonBatteryCountChange(16L), MonitorRequestReasonGracePeriod(17L), MonitorRequestReasonPnP(18L), MonitorRequestReasonDP(19L), MonitorRequestReasonSxTransition(20L), MonitorRequestReasonSystemIdle(21L), MonitorRequestReasonNearProximity(22L), MonitorRequestReasonThermalStandby(23L), MonitorRequestReasonResumePdc(24L), MonitorRequestReasonResumeS4(25L), MonitorRequestReasonTerminal(26L), MonitorRequestReasonPdcSignal(27L), MonitorRequestReasonAcDcDisplayBurstSuppressed(28L), MonitorRequestReasonSystemStateEntered(29L), MonitorRequestReasonWinrt(30L), MonitorRequestReasonUserInputKeyboard(31L), MonitorRequestReasonUserInputMouse(32L), MonitorRequestReasonUserInputTouchpad(33L), MonitorRequestReasonUserInputPen(34L), MonitorRequestReasonUserInputAccelerometer(35L), MonitorRequestReasonUserInputHid(36L), MonitorRequestReasonUserInputPoUserPresent(37L), MonitorRequestReasonUserInputSessionSwitch(38L), MonitorRequestReasonUserInputInitialization(39L), MonitorRequestReasonPdcSignalWindowsMobilePwrNotif(40L), MonitorRequestReasonPdcSignalWindowsMobileShell(41L), MonitorRequestReasonPdcSignalHeyCortana(42L), MonitorRequestReasonPdcSignalHolographicShell(43L), MonitorRequestReasonPdcSignalFingerprint(44L), MonitorRequestReasonDirectedDrips(45L), MonitorRequestReasonDim(46L), MonitorRequestReasonBuiltinPanel(47L), MonitorRequestReasonDisplayRequiredUnDim(48L), MonitorRequestReasonBatteryCountChangeSuppressed(49L), MonitorRequestReasonResumeModernStandby(50L), MonitorRequestReasonTerminalInit(51L), MonitorRequestReasonPdcSignalSensorsHumanPresence(52L), MonitorRequestReasonBatteryPreCritical(53L), MonitorRequestReasonUserInputTouch(54L), MonitorRequestReasonAusterityBatteryDrain(55L), MonitorRequestReasonDozeRestrictedStandby(56L), MonitorRequestReasonSmartRestrictedStandby(57L), MonitorRequestReasonMax(58L);
    
    companion object {
        fun fromValue(v: Long): POWER_MONITOR_REQUEST_REASON = entries.firstOrNull { it.value == v }
            ?: error("Unknown POWER_MONITOR_REQUEST_REASON value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _POWER_MONITOR_REQUEST_TYPE}
 */
enum class _POWER_MONITOR_REQUEST_TYPE(val value: Long) {
    MonitorRequestTypeOff(0L), MonitorRequestTypeOnAndPresent(1L), MonitorRequestTypeToggleOn(2L);
    
    companion object {
        fun fromValue(v: Long): _POWER_MONITOR_REQUEST_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _POWER_MONITOR_REQUEST_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _POWER_LIMIT_TYPES}
 */
enum class _POWER_LIMIT_TYPES(val value: Long) {
    PowerLimitContinuous(0L), PowerLimitType1(0L), PowerLimitBurst(1L), PowerLimitType2(1L), PowerLimitRapid(2L), PowerLimitType3(2L), PowerLimitPreemptive(3L), PowerLimitType4(3L), PowerLimitPreemptiveOffset(4L), PowerLimitTypeMax(5L);
    
    companion object {
        fun fromValue(v: Long): _POWER_LIMIT_TYPES = entries.firstOrNull { it.value == v }
            ?: error("Unknown _POWER_LIMIT_TYPES value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum SYSTEM_POWER_CONDITION}
 */
enum class SYSTEM_POWER_CONDITION(val value: Long) {
    PoAc(0L), PoDc(1L), PoHot(2L), PoConditionMaximum(3L);
    
    companion object {
        fun fromValue(v: Long): SYSTEM_POWER_CONDITION = entries.firstOrNull { it.value == v }
            ?: error("Unknown SYSTEM_POWER_CONDITION value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _POWER_PLATFORM_ROLE}
 */
enum class _POWER_PLATFORM_ROLE(val value: Long) {
    PlatformRoleUnspecified(0L), PlatformRoleDesktop(1L), PlatformRoleMobile(2L), PlatformRoleWorkstation(3L), PlatformRoleEnterpriseServer(4L), PlatformRoleSOHOServer(5L), PlatformRoleAppliancePC(6L), PlatformRolePerformanceServer(7L), PlatformRoleSlate(8L), PlatformRoleMaximum(9L);
    
    companion object {
        fun fromValue(v: Long): _POWER_PLATFORM_ROLE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _POWER_PLATFORM_ROLE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum POWER_SETTING_ALTITUDE}
 */
enum class POWER_SETTING_ALTITUDE(val value: Long) {
    ALTITUDE_GROUP_POLICY(0L), ALTITUDE_USER(1L), ALTITUDE_RUNTIME_OVERRIDE(2L), ALTITUDE_PROVISIONING(3L), ALTITUDE_OEM_CUSTOMIZATION(4L), ALTITUDE_INTERNAL_OVERRIDE(5L), ALTITUDE_OS_DEFAULT(6L);
    
    companion object {
        fun fromValue(v: Long): POWER_SETTING_ALTITUDE = entries.firstOrNull { it.value == v }
            ?: error("Unknown POWER_SETTING_ALTITUDE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _HIBERFILE_BUCKET_SIZE}
 */
enum class _HIBERFILE_BUCKET_SIZE(val value: Long) {
    HiberFileBucket1GB(0L), HiberFileBucket2GB(1L), HiberFileBucket4GB(2L), HiberFileBucket8GB(3L), HiberFileBucket16GB(4L), HiberFileBucket32GB(5L), HiberFileBucketUnlimited(6L), HiberFileBucketMax(7L);
    
    companion object {
        fun fromValue(v: Long): _HIBERFILE_BUCKET_SIZE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _HIBERFILE_BUCKET_SIZE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum IMAGE_AUX_SYMBOL_TYPE}
 */
enum class IMAGE_AUX_SYMBOL_TYPE(val value: Long) {
    IMAGE_AUX_SYMBOL_TYPE_TOKEN_DEF(1L);
    
    companion object {
        fun fromValue(v: Long): IMAGE_AUX_SYMBOL_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown IMAGE_AUX_SYMBOL_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum ARM64_FNPDATA_FLAGS}
 */
enum class ARM64_FNPDATA_FLAGS(val value: Long) {
    PdataRefToFullXdata(0L), PdataPackedUnwindFunction(1L), PdataPackedUnwindFragment(2L);
    
    companion object {
        fun fromValue(v: Long): ARM64_FNPDATA_FLAGS = entries.firstOrNull { it.value == v }
            ?: error("Unknown ARM64_FNPDATA_FLAGS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum ARM64_FNPDATA_CR}
 */
enum class ARM64_FNPDATA_CR(val value: Long) {
    PdataCrUnchained(0L), PdataCrUnchainedSavedLr(1L), PdataCrChainedWithPac(2L), PdataCrChained(3L);
    
    companion object {
        fun fromValue(v: Long): ARM64_FNPDATA_CR = entries.firstOrNull { it.value == v }
            ?: error("Unknown ARM64_FNPDATA_CR value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum IMPORT_OBJECT_TYPE}
 */
enum class IMPORT_OBJECT_TYPE(val value: Long) {
    IMPORT_OBJECT_CODE(0L), IMPORT_OBJECT_DATA(1L), IMPORT_OBJECT_CONST(2L);
    
    companion object {
        fun fromValue(v: Long): IMPORT_OBJECT_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown IMPORT_OBJECT_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum IMPORT_OBJECT_NAME_TYPE}
 */
enum class IMPORT_OBJECT_NAME_TYPE(val value: Long) {
    IMPORT_OBJECT_ORDINAL(0L), IMPORT_OBJECT_NAME(1L), IMPORT_OBJECT_NAME_NO_PREFIX(2L), IMPORT_OBJECT_NAME_UNDECORATE(3L), IMPORT_OBJECT_NAME_EXPORTAS(4L);
    
    companion object {
        fun fromValue(v: Long): IMPORT_OBJECT_NAME_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown IMPORT_OBJECT_NAME_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum ReplacesCorHdrNumericDefines}
 */
enum class ReplacesCorHdrNumericDefines(val value: Long) {
    COMIMAGE_FLAGS_ILONLY(1L), COMIMAGE_FLAGS_32BITREQUIRED(2L), COMIMAGE_FLAGS_IL_LIBRARY(4L), COMIMAGE_FLAGS_STRONGNAMESIGNED(8L), COMIMAGE_FLAGS_NATIVE_ENTRYPOINT(16L), COMIMAGE_FLAGS_TRACKDEBUGDATA(65536L), COMIMAGE_FLAGS_32BITPREFERRED(131072L), COR_VERSION_MAJOR_V2(2L), COR_VERSION_MAJOR(2L), COR_VERSION_MINOR(5L), COR_DELETED_NAME_LENGTH(8L), COR_VTABLEGAP_NAME_LENGTH(8L), NATIVE_TYPE_MAX_CB(1L), COR_ILMETHOD_SECT_SMALL_MAX_DATASIZE(255L), IMAGE_COR_MIH_METHODRVA(1L), IMAGE_COR_MIH_EHRVA(2L), IMAGE_COR_MIH_BASICBLOCK(8L), COR_VTABLE_32BIT(1L), COR_VTABLE_64BIT(2L), COR_VTABLE_FROM_UNMANAGED(4L), COR_VTABLE_FROM_UNMANAGED_RETAIN_APPDOMAIN(8L), COR_VTABLE_CALL_MOST_DERIVED(16L), IMAGE_COR_EATJ_THUNK_SIZE(32L), MAX_CLASS_NAME(1024L), MAX_PACKAGE_NAME(1024L);
    
    companion object {
        fun fromValue(v: Long): ReplacesCorHdrNumericDefines = entries.firstOrNull { it.value == v }
            ?: error("Unknown ReplacesCorHdrNumericDefines value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _RTL_UMS_THREAD_INFO_CLASS}
 */
enum class _RTL_UMS_THREAD_INFO_CLASS(val value: Long) {
    UmsThreadInvalidInfoClass(0L), UmsThreadUserContext(1L), UmsThreadPriority(2L), UmsThreadAffinity(3L), UmsThreadTeb(4L), UmsThreadIsSuspended(5L), UmsThreadIsTerminated(6L), UmsThreadMaxInfoClass(7L);
    
    companion object {
        fun fromValue(v: Long): _RTL_UMS_THREAD_INFO_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _RTL_UMS_THREAD_INFO_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _RTL_UMS_SCHEDULER_REASON}
 */
enum class _RTL_UMS_SCHEDULER_REASON(val value: Long) {
    UmsSchedulerStartup(0L), UmsSchedulerThreadBlocked(1L), UmsSchedulerThreadYield(2L);
    
    companion object {
        fun fromValue(v: Long): _RTL_UMS_SCHEDULER_REASON = entries.firstOrNull { it.value == v }
            ?: error("Unknown _RTL_UMS_SCHEDULER_REASON value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _OS_DEPLOYEMENT_STATE_VALUES}
 */
enum class _OS_DEPLOYEMENT_STATE_VALUES(val value: Long) {
    OS_DEPLOYMENT_STANDARD(1L), OS_DEPLOYMENT_COMPACT(2L);
    
    companion object {
        fun fromValue(v: Long): _OS_DEPLOYEMENT_STATE_VALUES = entries.firstOrNull { it.value == v }
            ?: error("Unknown _OS_DEPLOYEMENT_STATE_VALUES value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _IMAGE_POLICY_ENTRY_TYPE}
 */
enum class _IMAGE_POLICY_ENTRY_TYPE(val value: Long) {
    ImagePolicyEntryTypeNone(0L), ImagePolicyEntryTypeBool(1L), ImagePolicyEntryTypeInt8(2L), ImagePolicyEntryTypeUInt8(3L), ImagePolicyEntryTypeInt16(4L), ImagePolicyEntryTypeUInt16(5L), ImagePolicyEntryTypeInt32(6L), ImagePolicyEntryTypeUInt32(7L), ImagePolicyEntryTypeInt64(8L), ImagePolicyEntryTypeUInt64(9L), ImagePolicyEntryTypeAnsiString(10L), ImagePolicyEntryTypeUnicodeString(11L), ImagePolicyEntryTypeOverride(12L), ImagePolicyEntryTypeMaximum(13L);
    
    companion object {
        fun fromValue(v: Long): _IMAGE_POLICY_ENTRY_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _IMAGE_POLICY_ENTRY_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _IMAGE_POLICY_ID}
 */
enum class _IMAGE_POLICY_ID(val value: Long) {
    ImagePolicyIdNone(0L), ImagePolicyIdEtw(1L), ImagePolicyIdDebug(2L), ImagePolicyIdCrashDump(3L), ImagePolicyIdCrashDumpKey(4L), ImagePolicyIdCrashDumpKeyGuid(5L), ImagePolicyIdParentSd(6L), ImagePolicyIdParentSdRev(7L), ImagePolicyIdSvn(8L), ImagePolicyIdDeviceId(9L), ImagePolicyIdCapability(10L), ImagePolicyIdScenarioId(11L), ImagePolicyIdCapabilityOverridable(12L), ImagePolicyIdTrustletIdOverridable(13L), ImagePolicyIdMaximum(14L);
    
    companion object {
        fun fromValue(v: Long): _IMAGE_POLICY_ID = entries.firstOrNull { it.value == v }
            ?: error("Unknown _IMAGE_POLICY_ID value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _RTL_SYSTEM_GLOBAL_DATA_ID}
 */
enum class _RTL_SYSTEM_GLOBAL_DATA_ID(val value: Long) {
    GlobalDataIdUnknown(0L), GlobalDataIdRngSeedVersion(1L), GlobalDataIdInterruptTime(2L), GlobalDataIdTimeZoneBias(3L), GlobalDataIdImageNumberLow(4L), GlobalDataIdImageNumberHigh(5L), GlobalDataIdTimeZoneId(6L), GlobalDataIdNtMajorVersion(7L), GlobalDataIdNtMinorVersion(8L), GlobalDataIdSystemExpirationDate(9L), GlobalDataIdKdDebuggerEnabled(10L), GlobalDataIdCyclesPerYield(11L), GlobalDataIdSafeBootMode(12L), GlobalDataIdLastSystemRITEventTickCount(13L), GlobalDataIdConsoleSharedDataFlags(14L), GlobalDataIdNtSystemRootDrive(15L), GlobalDataIdQpcBypassEnabled(16L), GlobalDataIdQpcData(17L), GlobalDataIdQpcBias(18L);
    
    companion object {
        fun fromValue(v: Long): _RTL_SYSTEM_GLOBAL_DATA_ID = entries.firstOrNull { it.value == v }
            ?: error("Unknown _RTL_SYSTEM_GLOBAL_DATA_ID value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _HEAP_INFORMATION_CLASS}
 */
enum class _HEAP_INFORMATION_CLASS(val value: Long) {
    HeapCompatibilityInformation(0L), HeapEnableTerminationOnCorruption(1L), HeapOptimizeResources(3L), HeapTag(7L);
    
    companion object {
        fun fromValue(v: Long): _HEAP_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _HEAP_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _ACTIVATION_CONTEXT_INFO_CLASS}
 */
enum class _ACTIVATION_CONTEXT_INFO_CLASS(val value: Long) {
    ActivationContextBasicInformation(1L), ActivationContextDetailedInformation(2L), AssemblyDetailedInformationInActivationContext(3L), FileInformationInAssemblyOfAssemblyInActivationContext(4L), RunlevelInformationInActivationContext(5L), CompatibilityInformationInActivationContext(6L), ActivationContextManifestResourceName(7L), MaxActivationContextInfoClass(8L), AssemblyDetailedInformationInActivationContxt(3L), FileInformationInAssemblyOfAssemblyInActivationContxt(4L);
    
    companion object {
        fun fromValue(v: Long): _ACTIVATION_CONTEXT_INFO_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _ACTIVATION_CONTEXT_INFO_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum ACTCTX_REQUESTED_RUN_LEVEL}
 */
enum class ACTCTX_REQUESTED_RUN_LEVEL(val value: Long) {
    ACTCTX_RUN_LEVEL_UNSPECIFIED(0L), ACTCTX_RUN_LEVEL_AS_INVOKER(1L), ACTCTX_RUN_LEVEL_HIGHEST_AVAILABLE(2L), ACTCTX_RUN_LEVEL_REQUIRE_ADMIN(3L), ACTCTX_RUN_LEVEL_NUMBERS(4L);
    
    companion object {
        fun fromValue(v: Long): ACTCTX_REQUESTED_RUN_LEVEL = entries.firstOrNull { it.value == v }
            ?: error("Unknown ACTCTX_REQUESTED_RUN_LEVEL value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum ACTCTX_COMPATIBILITY_ELEMENT_TYPE}
 */
enum class ACTCTX_COMPATIBILITY_ELEMENT_TYPE(val value: Long) {
    ACTCTX_COMPATIBILITY_ELEMENT_TYPE_UNKNOWN(0L), ACTCTX_COMPATIBILITY_ELEMENT_TYPE_OS(1L), ACTCTX_COMPATIBILITY_ELEMENT_TYPE_MITIGATION(2L), ACTCTX_COMPATIBILITY_ELEMENT_TYPE_MAXVERSIONTESTED(3L);
    
    companion object {
        fun fromValue(v: Long): ACTCTX_COMPATIBILITY_ELEMENT_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown ACTCTX_COMPATIBILITY_ELEMENT_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _CM_SERVICE_NODE_TYPE}
 */
enum class _CM_SERVICE_NODE_TYPE(val value: Long) {
    DriverType(1L), FileSystemType(2L), Win32ServiceOwnProcess(16L), Win32ServiceShareProcess(32L), AdapterType(4L), RecognizerType(8L);
    
    companion object {
        fun fromValue(v: Long): _CM_SERVICE_NODE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _CM_SERVICE_NODE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _CM_SERVICE_LOAD_TYPE}
 */
enum class _CM_SERVICE_LOAD_TYPE(val value: Long) {
    BootLoad(0L), SystemLoad(1L), AutoLoad(2L), DemandLoad(3L), DisableLoad(4L);
    
    companion object {
        fun fromValue(v: Long): _CM_SERVICE_LOAD_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _CM_SERVICE_LOAD_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _CM_ERROR_CONTROL_TYPE}
 */
enum class _CM_ERROR_CONTROL_TYPE(val value: Long) {
    IgnoreError(0L), NormalError(1L), SevereError(2L), CriticalError(3L);
    
    companion object {
        fun fromValue(v: Long): _CM_ERROR_CONTROL_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _CM_ERROR_CONTROL_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _TAPE_DRIVE_PROBLEM_TYPE}
 */
enum class _TAPE_DRIVE_PROBLEM_TYPE(val value: Long) {
    TapeDriveProblemNone(0L), TapeDriveReadWriteWarning(1L), TapeDriveReadWriteError(2L), TapeDriveReadWarning(3L), TapeDriveWriteWarning(4L), TapeDriveReadError(5L), TapeDriveWriteError(6L), TapeDriveHardwareError(7L), TapeDriveUnsupportedMedia(8L), TapeDriveScsiConnectionError(9L), TapeDriveTimetoClean(10L), TapeDriveCleanDriveNow(11L), TapeDriveMediaLifeExpired(12L), TapeDriveSnappedTape(13L);
    
    companion object {
        fun fromValue(v: Long): _TAPE_DRIVE_PROBLEM_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _TAPE_DRIVE_PROBLEM_TYPE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long NOTIFICATION_MASK;}
 */
typealias NOTIFICATION_MASK = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Long SAVEPOINT_ID;}
 */
typealias SAVEPOINT_ID = Long

/**
 * NS_ENUM: {@snippet lang=c : enum _TRANSACTION_OUTCOME}
 */
enum class _TRANSACTION_OUTCOME(val value: Long) {
    TransactionOutcomeUndetermined(1L), TransactionOutcomeCommitted(2L), TransactionOutcomeAborted(3L);
    
    companion object {
        fun fromValue(v: Long): _TRANSACTION_OUTCOME = entries.firstOrNull { it.value == v }
            ?: error("Unknown _TRANSACTION_OUTCOME value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _TRANSACTION_STATE}
 */
enum class _TRANSACTION_STATE(val value: Long) {
    TransactionStateNormal(1L), TransactionStateIndoubt(2L), TransactionStateCommittedNotify(3L);
    
    companion object {
        fun fromValue(v: Long): _TRANSACTION_STATE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _TRANSACTION_STATE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _TRANSACTION_INFORMATION_CLASS}
 */
enum class _TRANSACTION_INFORMATION_CLASS(val value: Long) {
    TransactionBasicInformation(0L), TransactionPropertiesInformation(1L), TransactionEnlistmentInformation(2L), TransactionSuperiorEnlistmentInformation(3L), TransactionBindInformation(4L), TransactionDTCPrivateInformation(5L);
    
    companion object {
        fun fromValue(v: Long): _TRANSACTION_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _TRANSACTION_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _TRANSACTIONMANAGER_INFORMATION_CLASS}
 */
enum class _TRANSACTIONMANAGER_INFORMATION_CLASS(val value: Long) {
    TransactionManagerBasicInformation(0L), TransactionManagerLogInformation(1L), TransactionManagerLogPathInformation(2L), TransactionManagerRecoveryInformation(4L), TransactionManagerOnlineProbeInformation(3L), TransactionManagerOldestTransactionInformation(5L);
    
    companion object {
        fun fromValue(v: Long): _TRANSACTIONMANAGER_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _TRANSACTIONMANAGER_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _RESOURCEMANAGER_INFORMATION_CLASS}
 */
enum class _RESOURCEMANAGER_INFORMATION_CLASS(val value: Long) {
    ResourceManagerBasicInformation(0L), ResourceManagerCompletionInformation(1L);
    
    companion object {
        fun fromValue(v: Long): _RESOURCEMANAGER_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _RESOURCEMANAGER_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _ENLISTMENT_INFORMATION_CLASS}
 */
enum class _ENLISTMENT_INFORMATION_CLASS(val value: Long) {
    EnlistmentBasicInformation(0L), EnlistmentRecoveryInformation(1L), EnlistmentCrmInformation(2L);
    
    companion object {
        fun fromValue(v: Long): _ENLISTMENT_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _ENLISTMENT_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _KTMOBJECT_TYPE}
 */
enum class _KTMOBJECT_TYPE(val value: Long) {
    KTMOBJECT_TRANSACTION(0L), KTMOBJECT_TRANSACTION_MANAGER(1L), KTMOBJECT_RESOURCE_MANAGER(2L), KTMOBJECT_ENLISTMENT(3L), KTMOBJECT_INVALID(4L);
    
    companion object {
        fun fromValue(v: Long): _KTMOBJECT_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _KTMOBJECT_TYPE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long TP_VERSION;}
 */
typealias TP_VERSION = Long

/**
 * NS_ENUM: {@snippet lang=c : enum _TP_CALLBACK_PRIORITY}
 */
enum class _TP_CALLBACK_PRIORITY(val value: Long) {
    TP_CALLBACK_PRIORITY_HIGH(0L), TP_CALLBACK_PRIORITY_NORMAL(1L), TP_CALLBACK_PRIORITY_LOW(2L), TP_CALLBACK_PRIORITY_INVALID(3L), TP_CALLBACK_PRIORITY_COUNT(3L);
    
    companion object {
        fun fromValue(v: Long): _TP_CALLBACK_PRIORITY = entries.firstOrNull { it.value == v }
            ?: error("Unknown _TP_CALLBACK_PRIORITY value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long TP_WAIT_RESULT;}
 */
typealias TP_WAIT_RESULT = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = LongLong WPARAM;}
 */
typealias WPARAM = Long

/**
 * {@snippet lang=c : typedef LongLong LPARAM;}
 */
typealias LPARAM = Long

/**
 * {@snippet lang=c : typedef LongLong LRESULT;}
 */
typealias LRESULT = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Short ATOM;}
 */
typealias ATOM = Short

/**
 * {@snippet lang=c : typedef Int HFILE;}
 */
typealias HFILE = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Long COLORREF;}
 */
typealias COLORREF = Long

/**
 * NS_ENUM: {@snippet lang=c : enum DPI_AWARENESS}
 */
enum class DPI_AWARENESS(val value: Long) {
    DPI_AWARENESS_INVALID(-1L), DPI_AWARENESS_UNAWARE(0L), DPI_AWARENESS_SYSTEM_AWARE(1L), DPI_AWARENESS_PER_MONITOR_AWARE(2L);
    
    companion object {
        fun fromValue(v: Long): DPI_AWARENESS = entries.firstOrNull { it.value == v }
            ?: error("Unknown DPI_AWARENESS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DPI_HOSTING_BEHAVIOR}
 */
enum class DPI_HOSTING_BEHAVIOR(val value: Long) {
    DPI_HOSTING_BEHAVIOR_INVALID(-1L), DPI_HOSTING_BEHAVIOR_DEFAULT(0L), DPI_HOSTING_BEHAVIOR_MIXED(1L);
    
    companion object {
        fun fromValue(v: Long): DPI_HOSTING_BEHAVIOR = entries.firstOrNull { it.value == v }
            ?: error("Unknown DPI_HOSTING_BEHAVIOR value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _FINDEX_INFO_LEVELS}
 */
enum class _FINDEX_INFO_LEVELS(val value: Long) {
    FindExInfoStandard(0L), FindExInfoBasic(1L), FindExInfoMaxInfoLevel(2L);
    
    companion object {
        fun fromValue(v: Long): _FINDEX_INFO_LEVELS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _FINDEX_INFO_LEVELS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _FINDEX_SEARCH_OPS}
 */
enum class _FINDEX_SEARCH_OPS(val value: Long) {
    FindExSearchNameMatch(0L), FindExSearchLimitToDirectories(1L), FindExSearchLimitToDevices(2L), FindExSearchMaxSearchOp(3L);
    
    companion object {
        fun fromValue(v: Long): _FINDEX_SEARCH_OPS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _FINDEX_SEARCH_OPS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _READ_DIRECTORY_NOTIFY_INFORMATION_CLASS}
 */
enum class _READ_DIRECTORY_NOTIFY_INFORMATION_CLASS(val value: Long) {
    ReadDirectoryNotifyInformation(1L), ReadDirectoryNotifyExtendedInformation(2L), ReadDirectoryNotifyFullInformation(3L), ReadDirectoryNotifyMaximumInformation(4L);
    
    companion object {
        fun fromValue(v: Long): _READ_DIRECTORY_NOTIFY_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _READ_DIRECTORY_NOTIFY_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _GET_FILEEX_INFO_LEVELS}
 */
enum class _GET_FILEEX_INFO_LEVELS(val value: Long) {
    GetFileExInfoStandard(0L), GetFileExMaxInfoLevel(1L);
    
    companion object {
        fun fromValue(v: Long): _GET_FILEEX_INFO_LEVELS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _GET_FILEEX_INFO_LEVELS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _FILE_INFO_BY_HANDLE_CLASS}
 */
enum class _FILE_INFO_BY_HANDLE_CLASS(val value: Long) {
    FileBasicInfo(0L), FileStandardInfo(1L), FileNameInfo(2L), FileRenameInfo(3L), FileDispositionInfo(4L), FileAllocationInfo(5L), FileEndOfFileInfo(6L), FileStreamInfo(7L), FileCompressionInfo(8L), FileAttributeTagInfo(9L), FileIdBothDirectoryInfo(10L), FileIdBothDirectoryRestartInfo(11L), FileIoPriorityHintInfo(12L), FileRemoteProtocolInfo(13L), FileFullDirectoryInfo(14L), FileFullDirectoryRestartInfo(15L), FileStorageInfo(16L), FileAlignmentInfo(17L), FileIdInfo(18L), FileIdExtdDirectoryInfo(19L), FileIdExtdDirectoryRestartInfo(20L), FileDispositionInfoEx(21L), FileRenameInfoEx(22L), FileCaseSensitiveInfo(23L), FileNormalizedNameInfo(24L), MaximumFileInfoByHandleClass(25L);
    
    companion object {
        fun fromValue(v: Long): _FILE_INFO_BY_HANDLE_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _FILE_INFO_BY_HANDLE_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _FILE_INFO_BY_NAME_CLASS}
 */
enum class _FILE_INFO_BY_NAME_CLASS(val value: Long) {
    FileStatByNameInfo(0L), FileStatLxByNameInfo(1L), FileCaseSensitiveByNameInfo(2L), FileStatBasicByNameInfo(3L), MaximumFileInfoByNameClass(4L);
    
    companion object {
        fun fromValue(v: Long): _FILE_INFO_BY_NAME_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _FILE_INFO_BY_NAME_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _STREAM_INFO_LEVELS}
 */
enum class _STREAM_INFO_LEVELS(val value: Long) {
    FindStreamInfoStandard(0L), FindStreamInfoMaxInfoLevel(1L);
    
    companion object {
        fun fromValue(v: Long): _STREAM_INFO_LEVELS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _STREAM_INFO_LEVELS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DIRECTORY_FLAGS}
 */
enum class DIRECTORY_FLAGS(val value: Long) {
    DIRECTORY_FLAGS_NONE(0L), DIRECTORY_FLAGS_DISALLOW_PATH_REDIRECTS(1L);
    
    companion object {
        fun fromValue(v: Long): DIRECTORY_FLAGS = entries.firstOrNull { it.value == v }
            ?: error("Unknown DIRECTORY_FLAGS value: $v")
    }
}

/**
 * {@snippet lang=c : GetLastError typedef DWORD = UNSIGNED = Long()
 */
private val GetLastError_DESC: FunctionDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_LONG)
private val GetLastError_ADDR: MemorySegment = _lookup("GetLastError").find("GetLastError").orElseThrow()
private val GetLastError_HANDLE: MethodHandle = Linker.nativeLinker().downcallHandle(GetLastError_ADDR, GetLastError_DESC)

fun GetLastError(): Long {
    try {
        return GetLastError_HANDLE.invokeExact() as Long
    } catch (ex: Error) {
        throw ex
    } catch (ex: RuntimeException) {
        throw ex
    } catch (ex: Throwable) {
        throw AssertionError("should not reach here", ex)
    }
}

/**
 * {@snippet lang=c : SetLastError Void(typedef DWORD = UNSIGNED = Long)
 */
private val SetLastError_DESC: FunctionDescriptor = FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG)
private val SetLastError_ADDR: MemorySegment = _lookup("SetLastError").find("SetLastError").orElseThrow()
private val SetLastError_HANDLE: MethodHandle = Linker.nativeLinker().downcallHandle(SetLastError_ADDR, SetLastError_DESC)

fun SetLastError(arg0: Long): Unit {
    try {
        SetLastError_HANDLE.invokeExact(arg0)
    } catch (ex: Error) {
        throw ex
    } catch (ex: RuntimeException) {
        throw ex
    } catch (ex: Throwable) {
        throw AssertionError("should not reach here", ex)
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _QUEUE_USER_APC_FLAGS}
 */
enum class _QUEUE_USER_APC_FLAGS(val value: Long) {
    QUEUE_USER_APC_FLAGS_NONE(0L), QUEUE_USER_APC_FLAGS_SPECIAL_USER_APC(1L), QUEUE_USER_APC_CALLBACK_DATA_CONTEXT(65536L);
    
    companion object {
        fun fromValue(v: Long): _QUEUE_USER_APC_FLAGS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _QUEUE_USER_APC_FLAGS value: $v")
    }
}

/**
 * {@snippet lang=c : GetCurrentThreadId typedef DWORD = UNSIGNED = Long()
 */
private val GetCurrentThreadId_DESC: FunctionDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_LONG)
private val GetCurrentThreadId_ADDR: MemorySegment = _lookup("GetCurrentThreadId").find("GetCurrentThreadId").orElseThrow()
private val GetCurrentThreadId_HANDLE: MethodHandle = Linker.nativeLinker().downcallHandle(GetCurrentThreadId_ADDR, GetCurrentThreadId_DESC)

fun GetCurrentThreadId(): Long {
    try {
        return GetCurrentThreadId_HANDLE.invokeExact() as Long
    } catch (ex: Error) {
        throw ex
    } catch (ex: RuntimeException) {
        throw ex
    } catch (ex: Throwable) {
        throw AssertionError("should not reach here", ex)
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _THREAD_INFORMATION_CLASS}
 */
enum class _THREAD_INFORMATION_CLASS(val value: Long) {
    ThreadMemoryPriority(0L), ThreadAbsoluteCpuPriority(1L), ThreadDynamicCodePolicy(2L), ThreadPowerThrottling(3L), ThreadInformationClassMax(4L);
    
    companion object {
        fun fromValue(v: Long): _THREAD_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _THREAD_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _PROCESS_INFORMATION_CLASS}
 */
enum class _PROCESS_INFORMATION_CLASS(val value: Long) {
    ProcessMemoryPriority(0L), ProcessMemoryExhaustionInfo(1L), ProcessAppMemoryInfo(2L), ProcessInPrivateInfo(3L), ProcessPowerThrottling(4L), ProcessReservedValue1(5L), ProcessTelemetryCoverageInfo(6L), ProcessProtectionLevelInfo(7L), ProcessLeapSecondInfo(8L), ProcessMachineTypeInfo(9L), ProcessOverrideSubsequentPrefetchParameter(10L), ProcessMaxOverridePrefetchParameter(11L), ProcessInformationClassMax(12L);
    
    companion object {
        fun fromValue(v: Long): _PROCESS_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _PROCESS_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _MACHINE_ATTRIBUTES}
 */
enum class _MACHINE_ATTRIBUTES(val value: Long) {
    UserEnabled(1L), KernelEnabled(2L), Wow64Container(4L);
    
    companion object {
        fun fromValue(v: Long): _MACHINE_ATTRIBUTES = entries.firstOrNull { it.value == v }
            ?: error("Unknown _MACHINE_ATTRIBUTES value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _PROCESS_MEMORY_EXHAUSTION_TYPE}
 */
enum class _PROCESS_MEMORY_EXHAUSTION_TYPE(val value: Long) {
    PMETypeFailFastOnCommitFailure(0L), PMETypeMax(1L);
    
    companion object {
        fun fromValue(v: Long): _PROCESS_MEMORY_EXHAUSTION_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _PROCESS_MEMORY_EXHAUSTION_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _COMPUTER_NAME_FORMAT}
 */
enum class _COMPUTER_NAME_FORMAT(val value: Long) {
    ComputerNameNetBIOS(0L), ComputerNameDnsHostname(1L), ComputerNameDnsDomain(2L), ComputerNameDnsFullyQualified(3L), ComputerNamePhysicalNetBIOS(4L), ComputerNamePhysicalDnsHostname(5L), ComputerNamePhysicalDnsDomain(6L), ComputerNamePhysicalDnsFullyQualified(7L), ComputerNameMax(8L);
    
    companion object {
        fun fromValue(v: Long): _COMPUTER_NAME_FORMAT = entries.firstOrNull { it.value == v }
            ?: error("Unknown _COMPUTER_NAME_FORMAT value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DEVELOPER_DRIVE_ENABLEMENT_STATE}
 */
enum class DEVELOPER_DRIVE_ENABLEMENT_STATE(val value: Long) {
    DeveloperDriveEnablementStateError(0L), DeveloperDriveEnabled(1L), DeveloperDriveDisabledBySystemPolicy(2L), DeveloperDriveDisabledByGroupPolicy(3L);
    
    companion object {
        fun fromValue(v: Long): DEVELOPER_DRIVE_ENABLEMENT_STATE = entries.firstOrNull { it.value == v }
            ?: error("Unknown DEVELOPER_DRIVE_ENABLEMENT_STATE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _MEMORY_RESOURCE_NOTIFICATION_TYPE}
 */
enum class _MEMORY_RESOURCE_NOTIFICATION_TYPE(val value: Long) {
    LowMemoryResourceNotification(0L), HighMemoryResourceNotification(1L);
    
    companion object {
        fun fromValue(v: Long): _MEMORY_RESOURCE_NOTIFICATION_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _MEMORY_RESOURCE_NOTIFICATION_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum OFFER_PRIORITY}
 */
enum class OFFER_PRIORITY(val value: Long) {
    VmOfferPriorityVeryLow(1L), VmOfferPriorityLow(2L), VmOfferPriorityBelowNormal(3L), VmOfferPriorityNormal(4L);
    
    companion object {
        fun fromValue(v: Long): OFFER_PRIORITY = entries.firstOrNull { it.value == v }
            ?: error("Unknown OFFER_PRIORITY value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum WIN32_MEMORY_INFORMATION_CLASS}
 */
enum class WIN32_MEMORY_INFORMATION_CLASS(val value: Long) {
    MemoryRegionInfo(0L);
    
    companion object {
        fun fromValue(v: Long): WIN32_MEMORY_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown WIN32_MEMORY_INFORMATION_CLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum WIN32_MEMORY_PARTITION_INFORMATION_CLASS}
 */
enum class WIN32_MEMORY_PARTITION_INFORMATION_CLASS(val value: Long) {
    MemoryPartitionInfo(0L), MemoryPartitionDedicatedMemoryInfo(1L);
    
    companion object {
        fun fromValue(v: Long): WIN32_MEMORY_PARTITION_INFORMATION_CLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown WIN32_MEMORY_PARTITION_INFORMATION_CLASS value: $v")
    }
}

/**
 * {@snippet lang=c : GetModuleHandleW typedef HMODULE = (Declared(HINSTANCE__))*(typedef LPCWSTR = (UNSIGNED = Short)*)
 */
private val GetModuleHandleW_DESC: FunctionDescriptor = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
private val GetModuleHandleW_ADDR: MemorySegment = _lookup("GetModuleHandleW").find("GetModuleHandleW").orElseThrow()
private val GetModuleHandleW_HANDLE: MethodHandle = Linker.nativeLinker().downcallHandle(GetModuleHandleW_ADDR, GetModuleHandleW_DESC)

fun GetModuleHandleW(arg0: MemorySegment): MemorySegment {
    try {
        return GetModuleHandleW_HANDLE.invokeExact(arg0) as MemorySegment
    } catch (ex: Error) {
        throw ex
    } catch (ex: RuntimeException) {
        throw ex
    } catch (ex: Throwable) {
        throw AssertionError("should not reach here", ex)
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum FILE_WRITE_FLAGS}
 */
enum class FILE_WRITE_FLAGS(val value: Long) {
    FILE_WRITE_FLAGS_NONE(0L), FILE_WRITE_FLAGS_WRITE_THROUGH(1L);
    
    companion object {
        fun fromValue(v: Long): FILE_WRITE_FLAGS = entries.firstOrNull { it.value == v }
            ?: error("Unknown FILE_WRITE_FLAGS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum FILE_FLUSH_MODE}
 */
enum class FILE_FLUSH_MODE(val value: Long) {
    FILE_FLUSH_DEFAULT(0L), FILE_FLUSH_DATA(1L), FILE_FLUSH_MIN_METADATA(2L), FILE_FLUSH_NO_SYNC(3L);
    
    companion object {
        fun fromValue(v: Long): FILE_FLUSH_MODE = entries.firstOrNull { it.value == v }
            ?: error("Unknown FILE_FLUSH_MODE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _DEP_SYSTEM_POLICY_TYPE}
 */
enum class _DEP_SYSTEM_POLICY_TYPE(val value: Long) {
    DEPPolicyAlwaysOff(0L), DEPPolicyAlwaysOn(1L), DEPPolicyOptIn(2L), DEPPolicyOptOut(3L), DEPTotalPolicyCount(4L);
    
    companion object {
        fun fromValue(v: Long): _DEP_SYSTEM_POLICY_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _DEP_SYSTEM_POLICY_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _PROC_THREAD_ATTRIBUTE_NUM}
 */
enum class _PROC_THREAD_ATTRIBUTE_NUM(val value: Long) {
    ProcThreadAttributeParentProcess(0L), ProcThreadAttributeHandleList(2L), ProcThreadAttributeGroupAffinity(3L), ProcThreadAttributePreferredNode(4L), ProcThreadAttributeIdealProcessor(5L), ProcThreadAttributeUmsThread(6L), ProcThreadAttributeMitigationPolicy(7L), ProcThreadAttributeSecurityCapabilities(9L), ProcThreadAttributeProtectionLevel(11L), ProcThreadAttributeJobList(13L), ProcThreadAttributeChildProcessPolicy(14L), ProcThreadAttributeAllApplicationPackagesPolicy(15L), ProcThreadAttributeWin32kFilter(16L), ProcThreadAttributeSafeOpenPromptOriginClaim(17L), ProcThreadAttributeDesktopAppPolicy(18L), ProcThreadAttributePseudoConsole(22L), ProcThreadAttributeMitigationAuditPolicy(24L), ProcThreadAttributeMachineType(25L), ProcThreadAttributeComponentFilter(26L), ProcThreadAttributeEnableOptionalXStateFeatures(27L), ProcThreadAttributeTrustedApp(29L), ProcThreadAttributeSveVectorLength(30L);
    
    companion object {
        fun fromValue(v: Long): _PROC_THREAD_ATTRIBUTE_NUM = entries.firstOrNull { it.value == v }
            ?: error("Unknown _PROC_THREAD_ATTRIBUTE_NUM value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _COPYFILE2_MESSAGE_TYPE}
 */
enum class _COPYFILE2_MESSAGE_TYPE(val value: Long) {
    COPYFILE2_CALLBACK_NONE(0L), COPYFILE2_CALLBACK_CHUNK_STARTED(1L), COPYFILE2_CALLBACK_CHUNK_FINISHED(2L), COPYFILE2_CALLBACK_STREAM_STARTED(3L), COPYFILE2_CALLBACK_STREAM_FINISHED(4L), COPYFILE2_CALLBACK_POLL_CONTINUE(5L), COPYFILE2_CALLBACK_ERROR(6L), COPYFILE2_CALLBACK_MAX(7L);
    
    companion object {
        fun fromValue(v: Long): _COPYFILE2_MESSAGE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _COPYFILE2_MESSAGE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _COPYFILE2_MESSAGE_ACTION}
 */
enum class _COPYFILE2_MESSAGE_ACTION(val value: Long) {
    COPYFILE2_PROGRESS_CONTINUE(0L), COPYFILE2_PROGRESS_CANCEL(1L), COPYFILE2_PROGRESS_STOP(2L), COPYFILE2_PROGRESS_QUIET(3L), COPYFILE2_PROGRESS_PAUSE(4L);
    
    companion object {
        fun fromValue(v: Long): _COPYFILE2_MESSAGE_ACTION = entries.firstOrNull { it.value == v }
            ?: error("Unknown _COPYFILE2_MESSAGE_ACTION value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _COPYFILE2_COPY_PHASE}
 */
enum class _COPYFILE2_COPY_PHASE(val value: Long) {
    COPYFILE2_PHASE_NONE(0L), COPYFILE2_PHASE_PREPARE_SOURCE(1L), COPYFILE2_PHASE_PREPARE_DEST(2L), COPYFILE2_PHASE_READ_SOURCE(3L), COPYFILE2_PHASE_WRITE_DESTINATION(4L), COPYFILE2_PHASE_SERVER_COPY(5L), COPYFILE2_PHASE_NAMEGRAFT_COPY(6L), COPYFILE2_PHASE_MAX(7L);
    
    companion object {
        fun fromValue(v: Long): _COPYFILE2_COPY_PHASE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _COPYFILE2_COPY_PHASE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long OPERATION_ID;}
 */
typealias OPERATION_ID = Long

/**
 * NS_ENUM: {@snippet lang=c : enum _PRIORITY_HINT}
 */
enum class _PRIORITY_HINT(val value: Long) {
    IoPriorityHintVeryLow(0L), IoPriorityHintLow(1L), IoPriorityHintNormal(2L), MaximumIoPriorityHintType(3L);
    
    companion object {
        fun fromValue(v: Long): _PRIORITY_HINT = entries.firstOrNull { it.value == v }
            ?: error("Unknown _PRIORITY_HINT value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _FILE_ID_TYPE}
 */
enum class _FILE_ID_TYPE(val value: Long) {
    FileIdType(0L), ObjectIdType(1L), ExtendedFileIdType(2L), MaximumFileIdType(3L);
    
    companion object {
        fun fromValue(v: Long): _FILE_ID_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _FILE_ID_TYPE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef Long LCSCSTYPE;}
 */
typealias LCSCSTYPE = Long

/**
 * {@snippet lang=c : typedef Long LCSGAMUTMATCH;}
 */
typealias LCSGAMUTMATCH = Long

/**
 * {@snippet lang=c : typedef Long FXPT16DOT16;}
 */
typealias FXPT16DOT16 = Long

/**
 * {@snippet lang=c : typedef Long FXPT2DOT30;}
 */
typealias FXPT2DOT30 = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Char BCHAR;}
 */
typealias BCHAR = Byte

/**
 * NS_ENUM: {@snippet lang=c : enum DISPLAYCONFIG_VIDEO_OUTPUT_TECHNOLOGY}
 */
enum class DISPLAYCONFIG_VIDEO_OUTPUT_TECHNOLOGY(val value: Long) {
    DISPLAYCONFIG_OUTPUT_TECHNOLOGY_OTHER(-1L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_HD15(0L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_SVIDEO(1L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_COMPOSITE_VIDEO(2L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_COMPONENT_VIDEO(3L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_DVI(4L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_HDMI(5L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_LVDS(6L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_D_JPN(8L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_SDI(9L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_DISPLAYPORT_EXTERNAL(10L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_DISPLAYPORT_EMBEDDED(11L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_UDI_EXTERNAL(12L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_UDI_EMBEDDED(13L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_SDTVDONGLE(14L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_MIRACAST(15L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_INDIRECT_WIRED(16L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_INDIRECT_VIRTUAL(17L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_DISPLAYPORT_USB_TUNNEL(18L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_INTERNAL(-2147483648L), DISPLAYCONFIG_OUTPUT_TECHNOLOGY_FORCE_UINT32(-1L);
    
    companion object {
        fun fromValue(v: Long): DISPLAYCONFIG_VIDEO_OUTPUT_TECHNOLOGY = entries.firstOrNull { it.value == v }
            ?: error("Unknown DISPLAYCONFIG_VIDEO_OUTPUT_TECHNOLOGY value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DISPLAYCONFIG_SCANLINE_ORDERING}
 */
enum class DISPLAYCONFIG_SCANLINE_ORDERING(val value: Long) {
    DISPLAYCONFIG_SCANLINE_ORDERING_UNSPECIFIED(0L), DISPLAYCONFIG_SCANLINE_ORDERING_PROGRESSIVE(1L), DISPLAYCONFIG_SCANLINE_ORDERING_INTERLACED(2L), DISPLAYCONFIG_SCANLINE_ORDERING_INTERLACED_UPPERFIELDFIRST(2L), DISPLAYCONFIG_SCANLINE_ORDERING_INTERLACED_LOWERFIELDFIRST(3L), DISPLAYCONFIG_SCANLINE_ORDERING_FORCE_UINT32(-1L);
    
    companion object {
        fun fromValue(v: Long): DISPLAYCONFIG_SCANLINE_ORDERING = entries.firstOrNull { it.value == v }
            ?: error("Unknown DISPLAYCONFIG_SCANLINE_ORDERING value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DISPLAYCONFIG_SCALING}
 */
enum class DISPLAYCONFIG_SCALING(val value: Long) {
    DISPLAYCONFIG_SCALING_IDENTITY(1L), DISPLAYCONFIG_SCALING_CENTERED(2L), DISPLAYCONFIG_SCALING_STRETCHED(3L), DISPLAYCONFIG_SCALING_ASPECTRATIOCENTEREDMAX(4L), DISPLAYCONFIG_SCALING_CUSTOM(5L), DISPLAYCONFIG_SCALING_PREFERRED(128L), DISPLAYCONFIG_SCALING_FORCE_UINT32(-1L);
    
    companion object {
        fun fromValue(v: Long): DISPLAYCONFIG_SCALING = entries.firstOrNull { it.value == v }
            ?: error("Unknown DISPLAYCONFIG_SCALING value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DISPLAYCONFIG_ROTATION}
 */
enum class DISPLAYCONFIG_ROTATION(val value: Long) {
    DISPLAYCONFIG_ROTATION_IDENTITY(1L), DISPLAYCONFIG_ROTATION_ROTATE90(2L), DISPLAYCONFIG_ROTATION_ROTATE180(3L), DISPLAYCONFIG_ROTATION_ROTATE270(4L), DISPLAYCONFIG_ROTATION_FORCE_UINT32(-1L);
    
    companion object {
        fun fromValue(v: Long): DISPLAYCONFIG_ROTATION = entries.firstOrNull { it.value == v }
            ?: error("Unknown DISPLAYCONFIG_ROTATION value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DISPLAYCONFIG_MODE_INFO_TYPE}
 */
enum class DISPLAYCONFIG_MODE_INFO_TYPE(val value: Long) {
    DISPLAYCONFIG_MODE_INFO_TYPE_SOURCE(1L), DISPLAYCONFIG_MODE_INFO_TYPE_TARGET(2L), DISPLAYCONFIG_MODE_INFO_TYPE_DESKTOP_IMAGE(3L), DISPLAYCONFIG_MODE_INFO_TYPE_FORCE_UINT32(-1L);
    
    companion object {
        fun fromValue(v: Long): DISPLAYCONFIG_MODE_INFO_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown DISPLAYCONFIG_MODE_INFO_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DISPLAYCONFIG_PIXELFORMAT}
 */
enum class DISPLAYCONFIG_PIXELFORMAT(val value: Long) {
    DISPLAYCONFIG_PIXELFORMAT_8BPP(1L), DISPLAYCONFIG_PIXELFORMAT_16BPP(2L), DISPLAYCONFIG_PIXELFORMAT_24BPP(3L), DISPLAYCONFIG_PIXELFORMAT_32BPP(4L), DISPLAYCONFIG_PIXELFORMAT_NONGDI(5L), DISPLAYCONFIG_PIXELFORMAT_FORCE_UINT32(-1L);
    
    companion object {
        fun fromValue(v: Long): DISPLAYCONFIG_PIXELFORMAT = entries.firstOrNull { it.value == v }
            ?: error("Unknown DISPLAYCONFIG_PIXELFORMAT value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DISPLAYCONFIG_TOPOLOGY_ID}
 */
enum class DISPLAYCONFIG_TOPOLOGY_ID(val value: Long) {
    DISPLAYCONFIG_TOPOLOGY_INTERNAL(1L), DISPLAYCONFIG_TOPOLOGY_CLONE(2L), DISPLAYCONFIG_TOPOLOGY_EXTEND(4L), DISPLAYCONFIG_TOPOLOGY_EXTERNAL(8L), DISPLAYCONFIG_TOPOLOGY_FORCE_UINT32(-1L);
    
    companion object {
        fun fromValue(v: Long): DISPLAYCONFIG_TOPOLOGY_ID = entries.firstOrNull { it.value == v }
            ?: error("Unknown DISPLAYCONFIG_TOPOLOGY_ID value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DISPLAYCONFIG_DEVICE_INFO_TYPE}
 */
enum class DISPLAYCONFIG_DEVICE_INFO_TYPE(val value: Long) {
    DISPLAYCONFIG_DEVICE_INFO_GET_SOURCE_NAME(1L), DISPLAYCONFIG_DEVICE_INFO_GET_TARGET_NAME(2L), DISPLAYCONFIG_DEVICE_INFO_GET_TARGET_PREFERRED_MODE(3L), DISPLAYCONFIG_DEVICE_INFO_GET_ADAPTER_NAME(4L), DISPLAYCONFIG_DEVICE_INFO_SET_TARGET_PERSISTENCE(5L), DISPLAYCONFIG_DEVICE_INFO_GET_TARGET_BASE_TYPE(6L), DISPLAYCONFIG_DEVICE_INFO_GET_SUPPORT_VIRTUAL_RESOLUTION(7L), DISPLAYCONFIG_DEVICE_INFO_SET_SUPPORT_VIRTUAL_RESOLUTION(8L), DISPLAYCONFIG_DEVICE_INFO_GET_ADVANCED_COLOR_INFO(9L), DISPLAYCONFIG_DEVICE_INFO_SET_ADVANCED_COLOR_STATE(10L), DISPLAYCONFIG_DEVICE_INFO_GET_SDR_WHITE_LEVEL(11L), DISPLAYCONFIG_DEVICE_INFO_GET_MONITOR_SPECIALIZATION(12L), DISPLAYCONFIG_DEVICE_INFO_SET_MONITOR_SPECIALIZATION(13L), DISPLAYCONFIG_DEVICE_INFO_SET_RESERVED1(14L), DISPLAYCONFIG_DEVICE_INFO_GET_ADVANCED_COLOR_INFO_2(15L), DISPLAYCONFIG_DEVICE_INFO_SET_HDR_STATE(16L), DISPLAYCONFIG_DEVICE_INFO_SET_WCG_STATE(17L), DISPLAYCONFIG_DEVICE_INFO_FORCE_UINT32(-1L);
    
    companion object {
        fun fromValue(v: Long): DISPLAYCONFIG_DEVICE_INFO_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown DISPLAYCONFIG_DEVICE_INFO_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _DISPLAYCONFIG_COLOR_ENCODING}
 */
enum class _DISPLAYCONFIG_COLOR_ENCODING(val value: Long) {
    DISPLAYCONFIG_COLOR_ENCODING_RGB(0L), DISPLAYCONFIG_COLOR_ENCODING_YCBCR444(1L), DISPLAYCONFIG_COLOR_ENCODING_YCBCR422(2L), DISPLAYCONFIG_COLOR_ENCODING_YCBCR420(3L), DISPLAYCONFIG_COLOR_ENCODING_INTENSITY(4L), DISPLAYCONFIG_COLOR_ENCODING_FORCE_UINT32(-1L);
    
    companion object {
        fun fromValue(v: Long): _DISPLAYCONFIG_COLOR_ENCODING = entries.firstOrNull { it.value == v }
            ?: error("Unknown _DISPLAYCONFIG_COLOR_ENCODING value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _DISPLAYCONFIG_ADVANCED_COLOR_MODE}
 */
enum class _DISPLAYCONFIG_ADVANCED_COLOR_MODE(val value: Long) {
    DISPLAYCONFIG_ADVANCED_COLOR_MODE_SDR(0L), DISPLAYCONFIG_ADVANCED_COLOR_MODE_WCG(1L), DISPLAYCONFIG_ADVANCED_COLOR_MODE_HDR(2L);
    
    companion object {
        fun fromValue(v: Long): _DISPLAYCONFIG_ADVANCED_COLOR_MODE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _DISPLAYCONFIG_ADVANCED_COLOR_MODE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Short COLOR16;}
 */
typealias COLOR16 = Short

/**
 * NS_ENUM: {@snippet lang=c : enum DIALOG_CONTROL_DPI_CHANGE_BEHAVIORS}
 */
enum class DIALOG_CONTROL_DPI_CHANGE_BEHAVIORS(val value: Long) {
    DCDC_DEFAULT(0L), DCDC_DISABLE_FONT_UPDATE(1L), DCDC_DISABLE_RELAYOUT(2L);
    
    companion object {
        fun fromValue(v: Long): DIALOG_CONTROL_DPI_CHANGE_BEHAVIORS = entries.firstOrNull { it.value == v }
            ?: error("Unknown DIALOG_CONTROL_DPI_CHANGE_BEHAVIORS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum DIALOG_DPI_CHANGE_BEHAVIORS}
 */
enum class DIALOG_DPI_CHANGE_BEHAVIORS(val value: Long) {
    DDC_DEFAULT(0L), DDC_DISABLE_ALL(1L), DDC_DISABLE_RESIZE(2L), DDC_DISABLE_CONTROL_RELAYOUT(4L);
    
    companion object {
        fun fromValue(v: Long): DIALOG_DPI_CHANGE_BEHAVIORS = entries.firstOrNull { it.value == v }
            ?: error("Unknown DIALOG_DPI_CHANGE_BEHAVIORS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum tagPOINTER_INPUT_TYPE}
 */
enum class tagPOINTER_INPUT_TYPE(val value: Long) {
    PT_POINTER(1L), PT_TOUCH(2L), PT_PEN(3L), PT_MOUSE(4L), PT_TOUCHPAD(5L);
    
    companion object {
        fun fromValue(v: Long): tagPOINTER_INPUT_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown tagPOINTER_INPUT_TYPE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long POINTER_INPUT_TYPE;}
 */
typealias POINTER_INPUT_TYPE = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Int POINTER_FLAGS;}
 */
typealias POINTER_FLAGS = Int

/**
 * NS_ENUM: {@snippet lang=c : enum tagPOINTER_BUTTON_CHANGE_TYPE}
 */
enum class tagPOINTER_BUTTON_CHANGE_TYPE(val value: Long) {
    POINTER_CHANGE_NONE(0L), POINTER_CHANGE_FIRSTBUTTON_DOWN(1L), POINTER_CHANGE_FIRSTBUTTON_UP(2L), POINTER_CHANGE_SECONDBUTTON_DOWN(3L), POINTER_CHANGE_SECONDBUTTON_UP(4L), POINTER_CHANGE_THIRDBUTTON_DOWN(5L), POINTER_CHANGE_THIRDBUTTON_UP(6L), POINTER_CHANGE_FOURTHBUTTON_DOWN(7L), POINTER_CHANGE_FOURTHBUTTON_UP(8L), POINTER_CHANGE_FIFTHBUTTON_DOWN(9L), POINTER_CHANGE_FIFTHBUTTON_UP(10L);
    
    companion object {
        fun fromValue(v: Long): tagPOINTER_BUTTON_CHANGE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown tagPOINTER_BUTTON_CHANGE_TYPE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Int TOUCH_FLAGS;}
 */
typealias TOUCH_FLAGS = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Int TOUCH_MASK;}
 */
typealias TOUCH_MASK = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Int PEN_FLAGS;}
 */
typealias PEN_FLAGS = Int

/**
 * {@snippet lang=c : typedef UNSIGNED = Int PEN_MASK;}
 */
typealias PEN_MASK = Int

/**
 * NS_ENUM: {@snippet lang=c : enum POINTER_FEEDBACK_MODE}
 */
enum class POINTER_FEEDBACK_MODE(val value: Long) {
    POINTER_FEEDBACK_DEFAULT(1L), POINTER_FEEDBACK_INDIRECT(2L), POINTER_FEEDBACK_NONE(3L);
    
    companion object {
        fun fromValue(v: Long): POINTER_FEEDBACK_MODE = entries.firstOrNull { it.value == v }
            ?: error("Unknown POINTER_FEEDBACK_MODE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum tagFEEDBACK_TYPE}
 */
enum class tagFEEDBACK_TYPE(val value: Long) {
    FEEDBACK_TOUCH_CONTACTVISUALIZATION(1L), FEEDBACK_PEN_BARRELVISUALIZATION(2L), FEEDBACK_PEN_TAP(3L), FEEDBACK_PEN_DOUBLETAP(4L), FEEDBACK_PEN_PRESSANDHOLD(5L), FEEDBACK_PEN_RIGHTTAP(6L), FEEDBACK_TOUCH_TAP(7L), FEEDBACK_TOUCH_DOUBLETAP(8L), FEEDBACK_TOUCH_PRESSANDHOLD(9L), FEEDBACK_TOUCH_RIGHTTAP(10L), FEEDBACK_GESTURE_PRESSANDTAP(11L), FEEDBACK_MAX(-1L);
    
    companion object {
        fun fromValue(v: Long): tagFEEDBACK_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown tagFEEDBACK_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum EDIT_CONTROL_FEATURE}
 */
enum class EDIT_CONTROL_FEATURE(val value: Long) {
    EDIT_CONTROL_FEATURE_ENTERPRISE_DATA_PROTECTION_PASTE_SUPPORT(0L), EDIT_CONTROL_FEATURE_PASTE_NOTIFICATIONS(1L);
    
    companion object {
        fun fromValue(v: Long): EDIT_CONTROL_FEATURE = entries.firstOrNull { it.value == v }
            ?: error("Unknown EDIT_CONTROL_FEATURE value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long HELPPOLY;}
 */
typealias HELPPOLY = Long

/**
 * NS_ENUM: {@snippet lang=c : enum tagHANDEDNESS}
 */
enum class tagHANDEDNESS(val value: Long) {
    HANDEDNESS_LEFT(0L), HANDEDNESS_RIGHT(1L);
    
    companion object {
        fun fromValue(v: Long): tagHANDEDNESS = entries.firstOrNull { it.value == v }
            ?: error("Unknown tagHANDEDNESS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum tagPOINTER_DEVICE_TYPE}
 */
enum class tagPOINTER_DEVICE_TYPE(val value: Long) {
    POINTER_DEVICE_TYPE_INTEGRATED_PEN(1L), POINTER_DEVICE_TYPE_EXTERNAL_PEN(2L), POINTER_DEVICE_TYPE_TOUCH(3L), POINTER_DEVICE_TYPE_TOUCH_PAD(4L), POINTER_DEVICE_TYPE_MAX(-1L);
    
    companion object {
        fun fromValue(v: Long): tagPOINTER_DEVICE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown tagPOINTER_DEVICE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum tagPOINTER_DEVICE_CURSOR_TYPE}
 */
enum class tagPOINTER_DEVICE_CURSOR_TYPE(val value: Long) {
    POINTER_DEVICE_CURSOR_TYPE_UNKNOWN(0L), POINTER_DEVICE_CURSOR_TYPE_TIP(1L), POINTER_DEVICE_CURSOR_TYPE_ERASER(2L), POINTER_DEVICE_CURSOR_TYPE_MAX(-1L);
    
    companion object {
        fun fromValue(v: Long): tagPOINTER_DEVICE_CURSOR_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown tagPOINTER_DEVICE_CURSOR_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum LEGACY_TOUCHPAD_FEATURES}
 */
enum class LEGACY_TOUCHPAD_FEATURES(val value: Long) {
    LEGACY_TOUCHPAD_FEATURE_NONE(0L), LEGACY_TOUCHPAD_FEATURE_ENABLE_DISABLE(1L), LEGACY_TOUCHPAD_FEATURE_REVERSE_SCROLL_DIRECTION(4L);
    
    companion object {
        fun fromValue(v: Long): LEGACY_TOUCHPAD_FEATURES = entries.firstOrNull { it.value == v }
            ?: error("Unknown LEGACY_TOUCHPAD_FEATURES value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum TOUCHPAD_SENSITIVITY_LEVEL}
 */
enum class TOUCHPAD_SENSITIVITY_LEVEL(val value: Long) {
    TOUCHPAD_SENSITIVITY_LEVEL_MOST_SENSITIVE(0L), TOUCHPAD_SENSITIVITY_LEVEL_HIGH_SENSITIVITY(1L), TOUCHPAD_SENSITIVITY_LEVEL_MEDIUM_SENSITIVITY(2L), TOUCHPAD_SENSITIVITY_LEVEL_LOW_SENSITIVITY(3L), TOUCHPAD_SENSITIVITY_LEVEL_LEAST_SENSITIVE(4L);
    
    companion object {
        fun fromValue(v: Long): TOUCHPAD_SENSITIVITY_LEVEL = entries.firstOrNull { it.value == v }
            ?: error("Unknown TOUCHPAD_SENSITIVITY_LEVEL value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum tagINPUT_MESSAGE_DEVICE_TYPE}
 */
enum class tagINPUT_MESSAGE_DEVICE_TYPE(val value: Long) {
    IMDT_UNAVAILABLE(0L), IMDT_KEYBOARD(1L), IMDT_MOUSE(2L), IMDT_TOUCH(4L), IMDT_PEN(8L), IMDT_TOUCHPAD(16L);
    
    companion object {
        fun fromValue(v: Long): tagINPUT_MESSAGE_DEVICE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown tagINPUT_MESSAGE_DEVICE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum tagINPUT_MESSAGE_ORIGIN_ID}
 */
enum class tagINPUT_MESSAGE_ORIGIN_ID(val value: Long) {
    IMO_UNAVAILABLE(0L), IMO_HARDWARE(1L), IMO_INJECTED(2L), IMO_SYSTEM(4L);
    
    companion object {
        fun fromValue(v: Long): tagINPUT_MESSAGE_ORIGIN_ID = entries.firstOrNull { it.value == v }
            ?: error("Unknown tagINPUT_MESSAGE_ORIGIN_ID value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum tagAR_STATE}
 */
enum class tagAR_STATE(val value: Long) {
    AR_ENABLED(0L), AR_DISABLED(1L), AR_SUPPRESSED(2L), AR_REMOTESESSION(4L), AR_MULTIMON(8L), AR_NOSENSOR(16L), AR_NOT_SUPPORTED(32L), AR_DOCKED(64L), AR_LAPTOP(128L);
    
    companion object {
        fun fromValue(v: Long): tagAR_STATE = entries.firstOrNull { it.value == v }
            ?: error("Unknown tagAR_STATE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum ORIENTATION_PREFERENCE}
 */
enum class ORIENTATION_PREFERENCE(val value: Long) {
    ORIENTATION_PREFERENCE_NONE(0L), ORIENTATION_PREFERENCE_LANDSCAPE(1L), ORIENTATION_PREFERENCE_PORTRAIT(2L), ORIENTATION_PREFERENCE_LANDSCAPE_FLIPPED(4L), ORIENTATION_PREFERENCE_PORTRAIT_FLIPPED(8L);
    
    companion object {
        fun fromValue(v: Long): ORIENTATION_PREFERENCE = entries.firstOrNull { it.value == v }
            ?: error("Unknown ORIENTATION_PREFERENCE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _WINDOW_PLACEMENT_STATE}
 */
enum class _WINDOW_PLACEMENT_STATE(val value: Long) {
    WPS_NORMAL(0L), WPS_MAXIMIZED(1L), WPS_MINIMIZED(2L), WPS_ARRANGED(3L);
    
    companion object {
        fun fromValue(v: Long): _WINDOW_PLACEMENT_STATE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _WINDOW_PLACEMENT_STATE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _WINDOW_ACTION_KINDS}
 */
enum class _WINDOW_ACTION_KINDS(val value: Long) {
    WAK_NONE(0L), WAK_VISIBILITY(1L), WAK_POSITION(2L), WAK_SIZE(4L), WAK_INSERT_AFTER(8L), WAK_ACTIVATE(16L), WAK_PLACEMENT_STATE(32L), WAK_NORMAL_RECT(64L), WAK_MOVE_TO_MONITOR(128L), WAK_FIT_TO_MONITOR(256L), WAK_DISPLAY_CHANGE(512L), WAK_SYSTEM_OPERATION(1024L), WAK_COALESCEABLE(31L);
    
    companion object {
        fun fromValue(v: Long): _WINDOW_ACTION_KINDS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _WINDOW_ACTION_KINDS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _WINDOW_ACTION_MODIFIERS}
 */
enum class _WINDOW_ACTION_MODIFIERS(val value: Long) {
    WAM_NONE(0L), WAM_FRAME_BOUNDS(1L), WAM_ACTIVATE_FOREGROUND(2L), WAM_ACTIVATE_INPUT(4L), WAM_ACTIVATE_NO_ZORDER(8L), WAM_INSERT_AFTER_NO_OWNER(16L), WAM_RESTORE_TO_NORMAL(32L), WAM_RESTORE_TO_MAXIMIZED(64L), WAM_RESTORE_TO_ARRANGED(128L), WAM_WORK_AREA(256L), WAM_DPI(512L), WAM_SCALED_TO_MONITOR(1024L);
    
    companion object {
        fun fromValue(v: Long): _WINDOW_ACTION_MODIFIERS = entries.firstOrNull { it.value == v }
            ?: error("Unknown _WINDOW_ACTION_MODIFIERS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum TOOLTIP_DISMISS_FLAGS}
 */
enum class TOOLTIP_DISMISS_FLAGS(val value: Long) {
    TDF_REGISTER(1L), TDF_UNREGISTER(2L);
    
    companion object {
        fun fromValue(v: Long): TOOLTIP_DISMISS_FLAGS = entries.firstOrNull { it.value == v }
            ?: error("Unknown TOOLTIP_DISMISS_FLAGS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _MOVESIZE_OPERATION}
 */
enum class _MOVESIZE_OPERATION(val value: Long) {
    MSO_SIZE_LEFT(1L), MSO_SIZE_RIGHT(2L), MSO_SIZE_TOP(3L), MSO_SIZE_TOPLEFT(4L), MSO_SIZE_TOPRIGHT(5L), MSO_SIZE_BOTTOM(6L), MSO_SIZE_BOTTOMLEFT(7L), MSO_SIZE_BOTTOMRIGHT(8L), MSO_MOVE(9L);
    
    companion object {
        fun fromValue(v: Long): _MOVESIZE_OPERATION = entries.firstOrNull { it.value == v }
            ?: error("Unknown _MOVESIZE_OPERATION value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long LGRPID;}
 */
typealias LGRPID = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Long LCTYPE;}
 */
typealias LCTYPE = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Long CALTYPE;}
 */
typealias CALTYPE = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Long CALID;}
 */
typealias CALID = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Long GEOTYPE;}
 */
typealias GEOTYPE = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Long GEOCLASS;}
 */
typealias GEOCLASS = Long

/**
 * {@snippet lang=c : typedef Long GEOID;}
 */
typealias GEOID = Long

/**
 * NS_ENUM: {@snippet lang=c : enum SYSNLS_FUNCTION}
 */
enum class SYSNLS_FUNCTION(val value: Long) {
    COMPARE_STRING(1L);
    
    companion object {
        fun fromValue(v: Long): SYSNLS_FUNCTION = entries.firstOrNull { it.value == v }
            ?: error("Unknown SYSNLS_FUNCTION value: $v")
    }
}

/**
 * {@snippet lang=c : typedef UNSIGNED = Long NLS_FUNCTION;}
 */
typealias NLS_FUNCTION = Long

/**
 * NS_ENUM: {@snippet lang=c : enum SYSGEOTYPE}
 */
enum class SYSGEOTYPE(val value: Long) {
    GEO_NATION(1L), GEO_LATITUDE(2L), GEO_LONGITUDE(3L), GEO_ISO2(4L), GEO_ISO3(5L), GEO_RFC1766(6L), GEO_LCID(7L), GEO_FRIENDLYNAME(8L), GEO_OFFICIALNAME(9L), GEO_TIMEZONES(10L), GEO_OFFICIALLANGUAGES(11L), GEO_ISO_UN_NUMBER(12L), GEO_PARENT(13L), GEO_DIALINGCODE(14L), GEO_CURRENCYCODE(15L), GEO_CURRENCYSYMBOL(16L), GEO_NAME(17L), GEO_ID(18L);
    
    companion object {
        fun fromValue(v: Long): SYSGEOTYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown SYSGEOTYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum SYSGEOCLASS}
 */
enum class SYSGEOCLASS(val value: Long) {
    GEOCLASS_NATION(16L), GEOCLASS_REGION(14L), GEOCLASS_ALL(0L);
    
    companion object {
        fun fromValue(v: Long): SYSGEOCLASS = entries.firstOrNull { it.value == v }
            ?: error("Unknown SYSGEOCLASS value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _NORM_FORM}
 */
enum class _NORM_FORM(val value: Long) {
    NormalizationOther(0L), NormalizationC(1L), NormalizationD(2L), NormalizationKC(5L), NormalizationKD(6L);
    
    companion object {
        fun fromValue(v: Long): _NORM_FORM = entries.firstOrNull { it.value == v }
            ?: error("Unknown _NORM_FORM value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum ALLOC_CONSOLE_MODE}
 */
enum class ALLOC_CONSOLE_MODE(val value: Long) {
    ALLOC_CONSOLE_MODE_DEFAULT(0L), ALLOC_CONSOLE_MODE_NEW_WINDOW(1L), ALLOC_CONSOLE_MODE_NO_WINDOW(2L);
    
    companion object {
        fun fromValue(v: Long): ALLOC_CONSOLE_MODE = entries.firstOrNull { it.value == v }
            ?: error("Unknown ALLOC_CONSOLE_MODE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum ALLOC_CONSOLE_RESULT}
 */
enum class ALLOC_CONSOLE_RESULT(val value: Long) {
    ALLOC_CONSOLE_RESULT_NO_CONSOLE(0L), ALLOC_CONSOLE_RESULT_NEW_CONSOLE(1L), ALLOC_CONSOLE_RESULT_EXISTING_CONSOLE(2L);
    
    companion object {
        fun fromValue(v: Long): ALLOC_CONSOLE_RESULT = entries.firstOrNull { it.value == v }
            ?: error("Unknown ALLOC_CONSOLE_RESULT value: $v")
    }
}

/**
 * {@snippet lang=c : typedef Long LSTATUS;}
 */
typealias LSTATUS = Long

/**
 * {@snippet lang=c : typedef UNSIGNED = Long REGSAM;}
 */
typealias REGSAM = Long

/**
 * NS_ENUM: {@snippet lang=c : enum _SC_ACTION_TYPE}
 */
enum class _SC_ACTION_TYPE(val value: Long) {
    SC_ACTION_NONE(0L), SC_ACTION_RESTART(1L), SC_ACTION_REBOOT(2L), SC_ACTION_RUN_COMMAND(3L), SC_ACTION_OWN_RESTART(4L);
    
    companion object {
        fun fromValue(v: Long): _SC_ACTION_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SC_ACTION_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _SC_STATUS_TYPE}
 */
enum class _SC_STATUS_TYPE(val value: Long) {
    SC_STATUS_PROCESS_INFO(0L);
    
    companion object {
        fun fromValue(v: Long): _SC_STATUS_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SC_STATUS_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _SC_ENUM_TYPE}
 */
enum class _SC_ENUM_TYPE(val value: Long) {
    SC_ENUM_PROCESS_INFO(0L);
    
    companion object {
        fun fromValue(v: Long): _SC_ENUM_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SC_ENUM_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum _SC_EVENT_TYPE}
 */
enum class _SC_EVENT_TYPE(val value: Long) {
    SC_EVENT_DATABASE_CHANGE(0L), SC_EVENT_PROPERTY_CHANGE(1L), SC_EVENT_STATUS_CHANGE(2L);
    
    companion object {
        fun fromValue(v: Long): _SC_EVENT_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown _SC_EVENT_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum SERVICE_REGISTRY_STATE_TYPE}
 */
enum class SERVICE_REGISTRY_STATE_TYPE(val value: Long) {
    ServiceRegistryStateParameters(0L), ServiceRegistryStatePersistent(1L), MaxServiceRegistryStateType(2L);
    
    companion object {
        fun fromValue(v: Long): SERVICE_REGISTRY_STATE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown SERVICE_REGISTRY_STATE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum SERVICE_DIRECTORY_TYPE}
 */
enum class SERVICE_DIRECTORY_TYPE(val value: Long) {
    ServiceDirectoryPersistentState(0L), ServiceDirectoryTypeMax(1L);
    
    companion object {
        fun fromValue(v: Long): SERVICE_DIRECTORY_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown SERVICE_DIRECTORY_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum SERVICE_SHARED_REGISTRY_STATE_TYPE}
 */
enum class SERVICE_SHARED_REGISTRY_STATE_TYPE(val value: Long) {
    ServiceSharedRegistryPersistentState(0L);
    
    companion object {
        fun fromValue(v: Long): SERVICE_SHARED_REGISTRY_STATE_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown SERVICE_SHARED_REGISTRY_STATE_TYPE value: $v")
    }
}

/**
 * NS_ENUM: {@snippet lang=c : enum SERVICE_SHARED_DIRECTORY_TYPE}
 */
enum class SERVICE_SHARED_DIRECTORY_TYPE(val value: Long) {
    ServiceSharedDirectoryPersistentState(0L);
    
    companion object {
        fun fromValue(v: Long): SERVICE_SHARED_DIRECTORY_TYPE = entries.firstOrNull { it.value == v }
            ?: error("Unknown SERVICE_SHARED_DIRECTORY_TYPE value: $v")
    }
}

