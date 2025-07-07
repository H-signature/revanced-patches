package app.revanced.extension.youtube.patches.spoof;

import android.app.Activity;
import android.content.Context;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import app.revanced.extension.shared.innertube.client.YouTubeAppClient;
import app.revanced.extension.shared.patches.spoof.requests.StreamingDataRequest;
import app.revanced.extension.shared.settings.AppLanguage;
import app.revanced.extension.shared.settings.BaseSettings;
import app.revanced.extension.shared.settings.EnumSetting;
import app.revanced.extension.shared.utils.Logger;
import app.revanced.extension.shared.utils.ResourceUtils;
import app.revanced.extension.shared.utils.Utils;
import app.revanced.extension.youtube.patches.spoof.requests.AudioTrackRequest;
import app.revanced.extension.youtube.settings.Settings;
import app.revanced.extension.youtube.shared.VideoInformation;
import app.revanced.extension.youtube.utils.AuthUtils;
import app.revanced.extension.youtube.utils.ExtendedUtils;
import app.revanced.extension.youtube.utils.VideoUtils;
import kotlin.Pair;
import kotlin.Triple;

@SuppressWarnings("unused")
public class AudioTrackPatch {
    private static final boolean SPOOF_STREAMING_DATA_AUDIO_TRACK_BUTTON =
            Settings.SPOOF_STREAMING_DATA.get() && Settings.SPOOF_STREAMING_DATA_AUDIO_TRACK_BUTTON.get();

    @NonNull
    private static String audioTrackId = "";
    @NonNull
    private static String playlistId = "";
    @NonNull
    private static String videoId = "";

    /**
     * Injection point.
     */
    @Nullable
    public static String newPlayerResponseParameter(@NonNull String newlyLoadedVideoId, @Nullable String playerParameter,
                                                    @Nullable String newlyLoadedPlaylistId, boolean isShortAndOpeningOrPlaying) {
        if (SPOOF_STREAMING_DATA_AUDIO_TRACK_BUTTON && !VideoInformation.playerParametersAreShort(playerParameter)) {
            if (newlyLoadedPlaylistId == null || newlyLoadedPlaylistId.isEmpty()) {
                playlistId = "";
            } else if (!Objects.equals(playlistId, newlyLoadedPlaylistId)) {
                playlistId = newlyLoadedPlaylistId;
                Logger.printDebug(() -> "newVideoStarted, videoId: " + newlyLoadedVideoId + ", playlistId: " + newlyLoadedPlaylistId);
            }
        }

        return playerParameter; // Return the original value since we are observing and not modifying.
    }

    /**
     * Injection point.
     * Called after {@link StreamingDataRequest}.
     */
    public static void newVideoStarted(@NonNull String newlyLoadedChannelId, @NonNull String newlyLoadedChannelName,
                                       @NonNull String newlyLoadedVideoId, @NonNull String newlyLoadedVideoTitle,
                                       final long newlyLoadedVideoLength, boolean newlyLoadedLiveStreamValue) {
        try {
            if (!SPOOF_STREAMING_DATA_AUDIO_TRACK_BUTTON) {
                return;
            }
            if (Objects.equals(videoId, newlyLoadedVideoId)) {
                return;
            }
            if (!Utils.isNetworkConnected()) {
                Logger.printDebug(() -> "Network not connected, ignoring video");
                return;
            }
            // Only 'Android VR (No auth)' can change the audio track language when fetching.
            // Check if the last spoofed client is 'Android VR (No auth)'.
            if (!isAndroidVRNoAuth()) {
                Logger.printDebug(() -> "Video is not Android VR No Auth");
                return;
            }
            if (AuthUtils.requestHeader == null) {
                Logger.printDebug(() -> "AuthUtils is not initialized");
                return;
            }

            audioTrackId = "";
            videoId = newlyLoadedVideoId;
            Logger.printDebug(() -> "newVideoStarted: " + newlyLoadedVideoId);

            // Use the YouTube API to get a list of audio tracks supported by a video.
            AudioTrackRequest.fetchRequestIfNeeded(videoId, AuthUtils.requestHeader);
        } catch (Exception ex) {
            Logger.printException(() -> "newVideoStarted failure", ex);
        }
    }

    public static Map<String, Pair<String, Boolean>> getAudioTrackMap() {
        try {
            String videoId = VideoInformation.getVideoId();
            AudioTrackRequest request = AudioTrackRequest.getRequestForVideoId(videoId);
            if (request != null) {
                return request.getStream();
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getAudioTrackMap failure", ex);
        }

        return null;
    }

    public static boolean audioTrackMapIsNotNull() {
        return getAudioTrackMap() != null;
    }

    public static void showAudioTrackDialog(@NonNull Context context) {
        Map<String, Pair<String, Boolean>> map = getAudioTrackMap();

        // This video does not support audio tracks.
        if (map == null) {
            return;
        }

        Triple<String[], String[], Boolean[]> audioTracks = sortByDisplayNames(map);
        String[] displayNames = audioTracks.getFirst();
        String[] ids = audioTracks.getSecond();
        Boolean[] audioIsDefaults = audioTracks.getThird();

        LinearLayout mainLayout = ExtendedUtils.prepareMainLayout(context);
        Map<LinearLayout, Runnable> actionsMap = new LinkedHashMap<>(displayNames.length);
        EnumSetting<AppLanguage> appLanguage = BaseSettings.SPOOF_STREAMING_DATA_LANGUAGE;

        int checkIconId = ResourceUtils.getDrawableIdentifier("quantum_ic_check_white_24");

        // Whether the audio track language is currently used.
        boolean isSelectedAudioTrack;
        // Some audio tracks have multiple audio tracks with the same language code (e.g. en.2, en.3, en.4)
        // Check if the audio track is found to prevent multiple checks.
        boolean currentAudioTrackFound = false;

        Context mContext = Utils.getContext();
        Activity mActivity = Utils.getActivity();
        if (mActivity != null) mContext = mActivity;
        //noinspection deprecation
        String language = mContext.getResources().getConfiguration().locale.getLanguage();

        for (int i = 0; i < displayNames.length; i++) {
            String id = ids[i];
            String displayName = displayNames[i];
            Boolean audioIsDefault = audioIsDefaults[i];

            Runnable action = () -> {
                audioTrackId = id;

                // Save the language code to be changed in the [overrideLanguage] field of the [StreamingDataRequest] class.
                StreamingDataRequest.overrideLanguage(id);

                // Change the audio track language by reloading the same video.
                // Due to structural limitations of the YouTube app, the url of a video that is already playing will not be opened.
                // As a workaround, the video should be forcefully dismissed.
                VideoUtils.dismissPlayer();

                // Open the video.
                if (playlistId.isEmpty()) {
                    VideoUtils.openVideo(videoId);
                } else { // If the video is playing from a playlist, the url must include the playlistId.
                    VideoUtils.openPlaylist(playlistId, videoId, true);
                }

                // If the video has been reloaded, initialize the [overrideLanguage] field of the [StreamingDataRequest] class.
                ExtendedUtils.runOnMainThreadDelayed(() -> StreamingDataRequest.overrideLanguage(""), 3000L);
            };

            if (currentAudioTrackFound) {
                isSelectedAudioTrack = false;
            } else {
                // Checks if the audio track id saved in the field matches.
                if (audioTrackId.equals(id)) {
                    isSelectedAudioTrack = true;
                    currentAudioTrackFound = true;
                } else if (id.startsWith(language)) { // Check if the language applied to MainActivity matches the audio track id.
                    isSelectedAudioTrack = true;
                    currentAudioTrackFound = true;
                } else if (audioIsDefault) { // Check if 'audioIsDefault' in the response is true.
                    isSelectedAudioTrack = true;
                    currentAudioTrackFound = true;
                } else if (!appLanguage.isSetToDefault() &&
                        Objects.equals(appLanguage.get(), AppLanguage.getAppLanguage(id))
                ) { // Check if it matches the 'VR default audio stream language' value.
                    isSelectedAudioTrack = true;
                    currentAudioTrackFound = true;
                } else {
                    isSelectedAudioTrack = false;
                }
            }

            LinearLayout itemLayout =
                    ExtendedUtils.createItemLayout(context, displayName, isSelectedAudioTrack ? checkIconId : 0);
            actionsMap.putIfAbsent(itemLayout, action);
            mainLayout.addView(itemLayout);
        }

        ExtendedUtils.showBottomSheetDialog(context, mainLayout, actionsMap);
    }

    /**
     * Sorts audio tracks by displayName in lexicographical order.
     */
    private static Triple<String[], String[], Boolean[]> sortByDisplayNames(@NonNull Map<String, Pair<String, Boolean>> map) {
        final int firstEntriesToPreserve = 0;
        final int mapSize = map.size();

        List<Triple<String, String, Boolean>> firstTriples = new ArrayList<>(firstEntriesToPreserve);
        List<Triple<String, String, Boolean>> triplesToSort = new ArrayList<>(mapSize);

        int i = 0;
        for (Map.Entry<String, Pair<String, Boolean>> entrySet : map.entrySet()) {
            Pair<String, Boolean> pair = entrySet.getValue();
            String displayName = entrySet.getKey();
            String id = pair.getFirst();
            Boolean audioIsDefault = pair.getSecond();

            Triple<String, String, Boolean> triple = new Triple<>(displayName, id, audioIsDefault);
            if (i < firstEntriesToPreserve) {
                firstTriples.add(triple);
            } else {
                triplesToSort.add(triple);
            }
            i++;
        }

        triplesToSort.sort((triple1, triple2)
                -> triple1.getFirst().compareToIgnoreCase(triple2.getFirst()));

        String[] sortedDisplayNames = new String[mapSize];
        String[] sortedIds = new String[mapSize];
        Boolean[] sortedAudioIsDefaults = new Boolean[mapSize];

        i = 0;
        for (Triple<String, String, Boolean> triple : firstTriples) {
            sortedDisplayNames[i] = triple.getFirst();
            sortedIds[i] = triple.getSecond();
            sortedAudioIsDefaults[i] = triple.getThird();
            i++;
        }

        for (Triple<String, String, Boolean> triple : triplesToSort) {
            sortedDisplayNames[i] = triple.getFirst();
            sortedIds[i] = triple.getSecond();
            sortedAudioIsDefaults[i] = triple.getThird();
            i++;
        }

        return new Triple<>(sortedDisplayNames, sortedIds, sortedAudioIsDefaults);
    }

    /**
     * @return Whether the last spoofed client was Android VR (No auth) or not.
     */
    private static boolean isAndroidVRNoAuth() {
        return YouTubeAppClient.ClientType.ANDROID_VR_NO_AUTH.getFriendlyName()
                .equals(StreamingDataRequest.getLastSpoofedClientName());
    }
}
