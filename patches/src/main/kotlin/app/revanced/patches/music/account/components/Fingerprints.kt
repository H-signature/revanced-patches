package app.revanced.patches.music.account.components

import app.revanced.patches.music.utils.resourceid.accountSwitcherAccessibility
import app.revanced.patches.music.utils.resourceid.channelHandle
import app.revanced.patches.music.utils.resourceid.menuEntry
import app.revanced.patches.music.utils.resourceid.namesInactiveAccountThumbnailSize
import app.revanced.patches.music.utils.resourceid.tosFooter
import app.revanced.util.fingerprint.legacyFingerprint

internal val accountSwitcherAccessibilityLabelFingerprint = legacyFingerprint(
    name = "accountSwitcherAccessibilityLabelFingerprint",
    returnType = "V",
    parameters = listOf("L", "Ljava/lang/Object;"),
    literals = listOf(accountSwitcherAccessibility)
)

internal val channelHandleFingerprint = legacyFingerprint(
    name = "channelHandleFingerprint",
    returnType = "V",
    literals = listOf(channelHandle),
)

internal val menuEntryFingerprint = legacyFingerprint(
    name = "menuEntryFingerprint",
    returnType = "V",
    literals = listOf(menuEntry)
)

internal val namesInactiveAccountThumbnailSizeFingerprint = legacyFingerprint(
    name = "namesInactiveAccountThumbnailSizeFingerprint",
    returnType = "V",
    parameters = listOf("L", "Ljava/lang/Object;"),
    literals = listOf(namesInactiveAccountThumbnailSize)
)

internal val termsOfServiceFingerprint = legacyFingerprint(
    name = "termsOfServiceFingerprint",
    returnType = "Landroid/view/View;",
    literals = listOf(tosFooter)
)
