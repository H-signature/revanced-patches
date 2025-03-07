package app.revanced.patches.music.misc.quic

import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.music.utils.compatibility.Constants.COMPATIBLE_PACKAGE
import app.revanced.patches.music.utils.patch.PatchList.DISABLE_QUIC_PROTOCOL
import app.revanced.patches.music.utils.settings.CategoryType
import app.revanced.patches.music.utils.settings.ResourceUtils.updatePatchStatus
import app.revanced.patches.music.utils.settings.addSwitchPreference
import app.revanced.patches.music.utils.settings.settingsPatch
import app.revanced.patches.shared.quic.baseQuicProtocolPatch

@Suppress("unused", "SpellCheckingInspection")
val quicProtocolPatch = bytecodePatch(
    DISABLE_QUIC_PROTOCOL.title,
    DISABLE_QUIC_PROTOCOL.summary,
) {
    compatibleWith(COMPATIBLE_PACKAGE)

    dependsOn(
        settingsPatch,
        baseQuicProtocolPatch(),
    )

    execute {

        addSwitchPreference(
            CategoryType.MISC,
            "revanced_disable_quic_protocol",
            "false"
        )
        updatePatchStatus(DISABLE_QUIC_PROTOCOL)

    }
}
