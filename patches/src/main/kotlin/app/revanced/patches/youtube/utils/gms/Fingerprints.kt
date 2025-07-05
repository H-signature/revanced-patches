package app.revanced.patches.youtube.utils.gms

import app.revanced.patches.youtube.utils.resourceid.icOfflineNoContentUpsideDown
import app.revanced.patches.youtube.utils.resourceid.offlineNoContentBodyTextNotOfflineEligible
import app.revanced.util.fingerprint.legacyFingerprint
import app.revanced.util.or
import com.android.tools.smali.dexlib2.AccessFlags

internal val specificNetworkErrorViewControllerFingerprint = legacyFingerprint(
    name = "specificNetworkErrorViewControllerFingerprint",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    returnType = "V",
    parameters = emptyList(),
    literals = listOf(icOfflineNoContentUpsideDown, offlineNoContentBodyTextNotOfflineEligible),
)

// It's not clear if this second class is ever used and it may be dead code,
// but it the layout image / text is identical to the network error fingerprint above.
internal val loadingFrameLayoutControllerFingerprint = legacyFingerprint(
    name = "loadingFrameLayoutControllerFingerprint",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    returnType = "V",
    parameters = listOf("L"),
    literals = listOf(icOfflineNoContentUpsideDown, offlineNoContentBodyTextNotOfflineEligible),
)
