package app.revanced.extension.youtube.utils;

import static app.revanced.extension.shared.utils.ResourceUtils.getStringArray;
import static app.revanced.extension.shared.utils.StringRef.str;
import static app.revanced.extension.shared.utils.Utils.dipToPixels;
import static app.revanced.extension.youtube.patches.video.PlaybackSpeedPatch.userSelectedPlaybackSpeed;
import static app.revanced.extension.youtube.shared.VideoInformation.videoQualityEntries;
import static app.revanced.extension.youtube.shared.VideoInformation.videoQualityEntryValues;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.media.AudioManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import app.revanced.extension.shared.settings.EnumSetting;
import app.revanced.extension.shared.utils.IntentUtils;
import app.revanced.extension.shared.utils.Logger;
import app.revanced.extension.shared.utils.ResourceUtils;
import app.revanced.extension.shared.utils.Utils;
import app.revanced.extension.youtube.patches.shorts.ShortsRepeatStatePatch.ShortsLoopBehavior;
import app.revanced.extension.youtube.patches.video.CustomPlaybackSpeedPatch;
import app.revanced.extension.youtube.patches.video.CustomPlaybackSpeedPatch.PlaybackSpeedMenuType;
import app.revanced.extension.youtube.patches.video.PlaybackSpeedPatch;
import app.revanced.extension.youtube.patches.video.VideoQualityPatch;
import app.revanced.extension.youtube.settings.Settings;
import app.revanced.extension.youtube.settings.preference.ExternalDownloaderPlaylistPreference;
import app.revanced.extension.youtube.settings.preference.ExternalDownloaderVideoPreference;
import app.revanced.extension.youtube.shared.PlaylistIdPrefix;
import app.revanced.extension.youtube.shared.VideoInformation;

@SuppressWarnings("unused")
public class VideoUtils extends IntentUtils {
    /**
     * Scale used to convert user speed to {@link android.widget.ProgressBar#setProgress(int)}.
     */
    private static final float PROGRESS_BAR_VALUE_SCALE = 100;
    /**
     * Formats speeds to UI strings.
     */
    private static final NumberFormat speedFormatter = NumberFormat.getNumberInstance();

    private static final String CHANNEL_URL = "https://www.youtube.com/channel/";
    private static final String PLAYLIST_URL = "https://www.youtube.com/playlist?list=";
    private static final String VIDEO_URL = "https://youtu.be/";
    private static final String VIDEO_SCHEME_INTENT_FORMAT = "vnd.youtube://%s?start=%d";
    private static final String VIDEO_SCHEME_LINK_FORMAT = "https://youtu.be/%s?t=%d";
    private static final AtomicBoolean isExternalDownloaderLaunched = new AtomicBoolean(false);

    static {
        // Cap at 2 decimals (rounds automatically).
        speedFormatter.setMaximumFractionDigits(2);
    }

    private static String getChannelUrl(String channelId) {
        return CHANNEL_URL + channelId;
    }

    private static String getPlaylistUrl(String playlistId) {
        return PLAYLIST_URL + playlistId;
    }

    private static String getVideoUrl(String videoId) {
        return getVideoUrl(videoId, false);
    }

    private static String getVideoUrl(boolean withTimestamp) {
        return getVideoUrl(VideoInformation.getVideoId(), withTimestamp);
    }

    public static String getVideoUrl(String videoId, boolean withTimestamp) {
        StringBuilder builder = new StringBuilder(VIDEO_URL);
        builder.append(videoId);
        final long currentVideoTimeInSeconds = VideoInformation.getVideoTimeInSeconds();
        if (withTimestamp && currentVideoTimeInSeconds > 0) {
            builder.append("?t=");
            builder.append(currentVideoTimeInSeconds);
        }
        return builder.toString();
    }

    public static String getVideoScheme(String videoId, boolean isShorts) {
        return String.format(
                Locale.ENGLISH,
                isShorts ? VIDEO_SCHEME_INTENT_FORMAT : VIDEO_SCHEME_LINK_FORMAT,
                videoId,
                VideoInformation.getVideoTimeInSeconds()
        );
    }

    public static void copyUrl(boolean withTimestamp) {
        copyUrl(getVideoUrl(withTimestamp), withTimestamp);
    }

    public static void copyUrl(String videoUrl, boolean withTimestamp) {
        setClipboard(videoUrl, withTimestamp
                ? str("revanced_share_copy_url_timestamp_success")
                : str("revanced_share_copy_url_success")
        );
    }

    public static void copyTimeStamp() {
        final String timeStamp = getTimeStamp(VideoInformation.getVideoTime());
        setClipboard(timeStamp, str("revanced_share_copy_timestamp_success", timeStamp));
    }

    public static void launchVideoExternalDownloader() {
        launchVideoExternalDownloader(VideoInformation.getVideoId());
    }

    public static void launchVideoExternalDownloader(@NonNull String videoId) {
        try {
            final String downloaderPackageName = ExternalDownloaderVideoPreference.getExternalDownloaderPackageName();
            if (ExternalDownloaderVideoPreference.checkPackageIsDisabled()) {
                return;
            }

            isExternalDownloaderLaunched.compareAndSet(false, true);
            launchExternalDownloader(getVideoUrl(videoId), downloaderPackageName);
        } catch (Exception ex) {
            Logger.printException(() -> "launchExternalDownloader failure", ex);
        } finally {
            runOnMainThreadDelayed(() -> isExternalDownloaderLaunched.compareAndSet(true, false), 500);
        }
    }

    public static void launchPlaylistExternalDownloader(@NonNull String playlistId) {
        try {
            final String downloaderPackageName = ExternalDownloaderPlaylistPreference.getExternalDownloaderPackageName();
            if (ExternalDownloaderPlaylistPreference.checkPackageIsDisabled()) {
                return;
            }

            isExternalDownloaderLaunched.compareAndSet(false, true);
            launchExternalDownloader(getPlaylistUrl(playlistId), downloaderPackageName);
        } catch (Exception ex) {
            Logger.printException(() -> "launchPlaylistExternalDownloader failure", ex);
        } finally {
            runOnMainThreadDelayed(() -> isExternalDownloaderLaunched.compareAndSet(true, false), 500);
        }
    }

    public static void openChannel(@NonNull String channelId) {
        launchView(getChannelUrl(channelId), getContext().getPackageName());
    }

    public static void openPlaylist(@NonNull String playlistId) {
        openPlaylist(playlistId, "");
    }

    public static void openPlaylist(@NonNull String playlistId, @NonNull String videoId) {
        openPlaylist(playlistId, videoId, false);
    }

    public static void openPlaylist(@NonNull String playlistId, @NonNull String videoId, boolean withTimestamp) {
        final StringBuilder sb = new StringBuilder();
        if (videoId.isEmpty()) {
            sb.append(getPlaylistUrl(playlistId));
        } else {
            sb.append(VIDEO_URL);
            sb.append(videoId);
            sb.append("?list=");
            sb.append(playlistId);
            if (withTimestamp) {
                final long currentVideoTimeInSeconds = VideoInformation.getVideoTimeInSeconds();
                if (currentVideoTimeInSeconds > 0) {
                    sb.append("&t=");
                    sb.append(currentVideoTimeInSeconds);
                }
            }
        }
        launchView(sb.toString(), getContext().getPackageName());
    }

    public static void openVideo() {
        openVideo(VideoInformation.getVideoId());
    }

    public static void openVideo(@NonNull String videoId) {
        openVideo(videoId, false, null);
    }

    public static void openVideo(@NonNull String videoId, boolean isShorts) {
        openVideo(videoId, isShorts, null);
    }

    public static void openVideo(@NonNull PlaylistIdPrefix playlistIdPrefix) {
        openVideo(VideoInformation.getVideoId(), false, playlistIdPrefix);
    }

    public static void openVideo(@NonNull String videoId, boolean isShorts, @Nullable PlaylistIdPrefix playlistIdPrefix) {
        final StringBuilder sb = new StringBuilder(getVideoScheme(videoId, isShorts));
        // Create playlist with all channel videos.
        if (playlistIdPrefix != null) {
            sb.append("&list=");
            sb.append(playlistIdPrefix.prefixId);
            if (playlistIdPrefix.useChannelId) {
                final String channelId = VideoInformation.getChannelId();
                // Channel id always starts with `UC` prefix
                if (!channelId.startsWith("UC")) {
                    showToastShort(str("revanced_overlay_button_play_all_not_available_toast"));
                    return;
                }
                sb.append(channelId.substring(2));
            } else {
                sb.append(videoId);
            }
        }

        launchView(sb.toString(), getContext().getPackageName());
    }

    /**
     * Pause the media by changing audio focus.
     */
    public static void pauseMedia() {
        Context mContext = getContext();
        if (mContext != null && mContext.getApplicationContext().getSystemService(Context.AUDIO_SERVICE) instanceof AudioManager audioManager) {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    public static void showCustomNoThemePlaybackSpeedDialog(@NonNull Context context) {
        final String[] playbackSpeedEntries = CustomPlaybackSpeedPatch.getTrimmedEntries();
        final String[] playbackSpeedEntryValues = CustomPlaybackSpeedPatch.getTrimmedEntryValues();

        final float playbackSpeed = VideoInformation.getPlaybackSpeed();
        final int index = Arrays.binarySearch(playbackSpeedEntryValues, String.valueOf(playbackSpeed));

        new AlertDialog.Builder(context)
                .setSingleChoiceItems(playbackSpeedEntries, index, (mDialog, mIndex) -> {
                    final float selectedPlaybackSpeed = Float.parseFloat(playbackSpeedEntryValues[mIndex] + "f");
                    VideoInformation.setPlaybackSpeed(selectedPlaybackSpeed);
                    VideoInformation.overridePlaybackSpeed(selectedPlaybackSpeed);
                    userSelectedPlaybackSpeed(selectedPlaybackSpeed);
                    mDialog.dismiss();
                })
                .show();
    }

    public static void showCustomLegacyPlaybackSpeedDialog(@NonNull Context context) {
        final String[] playbackSpeedEntries = CustomPlaybackSpeedPatch.getTrimmedEntries();
        final String[] playbackSpeedEntryValues = CustomPlaybackSpeedPatch.getTrimmedEntryValues();

        final float playbackSpeed = VideoInformation.getPlaybackSpeed();
        final int selectedIndex = Arrays.binarySearch(playbackSpeedEntryValues, String.valueOf(playbackSpeed));

        LinearLayout mainLayout = ExtendedUtils.prepareMainLayout(context);
        Map<LinearLayout, Runnable> actionsMap = new LinkedHashMap<>(playbackSpeedEntryValues.length);
        int checkIconId = ResourceUtils.getDrawableIdentifier("quantum_ic_check_white_24");

        int i = 0;
        for (String entryValue: playbackSpeedEntryValues) {
            final float selectedPlaybackSpeed = Float.parseFloat(playbackSpeedEntryValues[i] + "f");
            Runnable action = () -> {
                VideoInformation.setPlaybackSpeed(selectedPlaybackSpeed);
                VideoInformation.overridePlaybackSpeed(selectedPlaybackSpeed);
                userSelectedPlaybackSpeed(selectedPlaybackSpeed);
            };
            LinearLayout itemLayout =
                    ExtendedUtils.createItemLayout(context, playbackSpeedEntries[i], selectedIndex == i ? checkIconId : 0);
            actionsMap.putIfAbsent(itemLayout, action);
            mainLayout.addView(itemLayout);
            i++;
        }

        ExtendedUtils.showBottomSheetDialog(context, mainLayout, actionsMap);
    }

    public static void showPlaybackSpeedDialog(@NonNull Context context,
                                               EnumSetting<PlaybackSpeedMenuType> type) {
        switch (type.get()) {
            case YOUTUBE_LEGACY -> showYouTubeLegacyPlaybackSpeedFlyoutMenu();
            case CUSTOM_NO_THEME -> showCustomNoThemePlaybackSpeedDialog(context);
            case CUSTOM_LEGACY -> showCustomLegacyPlaybackSpeedDialog(context);
            case CUSTOM_MODERN -> showCustomModernPlaybackSpeedDialog(context);
        }
    }

    private static int mClickedDialogEntryIndex;

    public static void showShortsRepeatDialog(@NonNull Context context) {
        final EnumSetting<ShortsLoopBehavior> setting = Settings.CHANGE_SHORTS_REPEAT_STATE;
        final String settingsKey = setting.key;

        final String entryKey = settingsKey + "_entries";
        final String entryValueKey = settingsKey + "_entry_values";
        final String[] mEntries = getStringArray(entryKey);
        final String[] mEntryValues = getStringArray(entryValueKey);

        final int findIndex = Arrays.binarySearch(mEntryValues, String.valueOf(setting.get()));
        mClickedDialogEntryIndex = findIndex >= 0 ? findIndex : setting.defaultValue.ordinal();

        new AlertDialog.Builder(context)
                .setTitle(str(settingsKey + "_title"))
                .setSingleChoiceItems(mEntries, mClickedDialogEntryIndex, (dialog, id) -> {
                    mClickedDialogEntryIndex = id;
                    for (ShortsLoopBehavior behavior : ShortsLoopBehavior.values()) {
                        if (behavior.ordinal() == id) setting.save(behavior);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static String getFormattedQualityString(@Nullable String prefix) {
        final String qualityString = VideoInformation.getVideoQualitySimplifiedString();

        return prefix == null ? qualityString : String.format("%s\u2009•\u2009%s", prefix, qualityString);
    }

    public static String getFormattedSpeedString(@Nullable String prefix) {
        final float playbackSpeed = VideoInformation.getPlaybackSpeed();

        final String playbackSpeedString = isRightToLeftTextLayout()
                ? "\u2066x\u2069" + playbackSpeed
                : playbackSpeed + "x";

        return prefix == null ? playbackSpeedString : String.format("%s\u2009•\u2009%s", prefix, playbackSpeedString);
    }

    /**
     * Injection point.
     * Disable PiP mode when an external downloader Intent is started.
     */
    public static boolean getExternalDownloaderLaunchedState(boolean original) {
        return !isExternalDownloaderLaunched.get() && original;
    }

    /**
     * Rest of the implementation added by patch.
     */
    public static void dismissPlayer() {
        Logger.printDebug(() -> "Dismiss player");
    }

    /**
     * Rest of the implementation added by patch.
     */
    public static void enterFullscreenMode() {
        Logger.printDebug(() -> "Enter fullscreen mode");
    }

    /**
     * Rest of the implementation added by patch.
     */
    public static void exitFullscreenMode() {
        Logger.printDebug(() -> "Exit fullscreen mode");
    }

    /**
     * Rest of the implementation added by patch.
     */
    public static void showYouTubeLegacyPlaybackSpeedFlyoutMenu() {
        Logger.printDebug(() -> "Playback speed flyout menu opened");
    }

    /**
     * Rest of the implementation added by patch.
     */
    public static void showYouTubeLegacyVideoQualityFlyoutMenu() {
        // These instructions are ignored by patch.
        Log.d("Extended: VideoUtils", "Video quality flyout menu opened");
    }

    public static void showCustomVideoQualityFlyoutMenu(Context context) {
        if (videoQualityEntries != null && videoQualityEntryValues != null) {
            List<String> entries = Objects.requireNonNull(videoQualityEntries);
            List<Integer> entryValues = Objects.requireNonNull(videoQualityEntryValues);

            int videoQualityEntrySize = entries.size();
            if (videoQualityEntrySize > 1 && videoQualityEntrySize == entryValues.size()) {
                LinearLayout mainLayout = ExtendedUtils.prepareMainLayout(context);
                Map<LinearLayout, Runnable> actionsMap = new LinkedHashMap<>(videoQualityEntrySize - 1);
                int checkIconId = ResourceUtils.getDrawableIdentifier("quantum_ic_check_white_24");

                // Sometimes there can be two qualities, such as '1080p Premium' and '1080p'
                boolean foundQuality = false;
                // Index 0 being the 'automatic' value of -2
                for (int i = 1; i < videoQualityEntrySize; i++) {
                    String qualityLabel = entries.get(i);
                    int qualityValue = entryValues.get(i);

                    Runnable action = () -> {
                        VideoInformation.overrideVideoQuality(qualityValue);
                        VideoQualityPatch.userSelectedVideoQuality(qualityValue);
                    };
                    LinearLayout itemLayout = ExtendedUtils.createItemLayout(context, entries.get(i));
                    if (VideoInformation.getVideoQuality() == qualityValue && !foundQuality) {
                        itemLayout = ExtendedUtils.createItemLayout(context, entries.get(i), checkIconId);
                        foundQuality = true;
                    }
                    actionsMap.putIfAbsent(itemLayout, action);
                    mainLayout.addView(itemLayout);
                }
                ExtendedUtils.showBottomSheetDialog(context, mainLayout, actionsMap);
                return;
            } else {
                Logger.printDebug(() -> "invalid entry size");
            }
        } else {
            Logger.printDebug(() -> "videoQualityEntries is null");
        }
        showYouTubeLegacyVideoQualityFlyoutMenu();
    }

    /**
     * Displays a modern custom dialog for adjusting video playback speed.
     * <p>
     * This method creates a dialog with a slider, plus/minus buttons, and preset speed buttons
     * to allow the user to modify the video playback speed. The dialog is styled with rounded
     * corners and themed colors, positioned at the bottom of the screen. The playback speed
     * can be adjusted in 0.05 increments using the slider or buttons, or set directly to preset
     * values. The dialog updates the displayed speed in real-time and applies changes to the
     * video playback. The dialog is dismissed if the player enters Picture-in-Picture (PiP) mode.
     */
    public static void showCustomModernPlaybackSpeedDialog(Context context) {
        LinearLayout mainLayout = ExtendedUtils.prepareMainLayout(context, true);

        // Preset size constants.
        final int dip5 = dipToPixels(5);
        final int dip32 = dipToPixels(32); // Height for in-rows speed buttons.
        final int dip36 = dipToPixels(36); // Height for minus and plus buttons.
        final int dip60 = dipToPixels(60); // Height for speed button container.

        // Display current playback speed.
        TextView currentSpeedText = new TextView(context);
        float currentSpeed = VideoInformation.getPlaybackSpeed();
        // Initially show with only 0 minimum digits, so 1.0 shows as 1x
        currentSpeedText.setText(formatSpeedStringX(currentSpeed, 0));
        currentSpeedText.setTextColor(ThemeUtils.getForegroundColor());
        currentSpeedText.setTextSize(16);
        currentSpeedText.setTypeface(Typeface.DEFAULT_BOLD);
        currentSpeedText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.setMargins(0, 0, 0, 0);
        currentSpeedText.setLayoutParams(textParams);
        // Add current speed text view to main layout.
        mainLayout.addView(currentSpeedText);

        // Create horizontal layout for slider and +/- buttons.
        LinearLayout sliderLayout = new LinearLayout(context);
        sliderLayout.setOrientation(LinearLayout.HORIZONTAL);
        sliderLayout.setGravity(Gravity.CENTER_VERTICAL);
        sliderLayout.setPadding(dip5, dip5, dip5, dip5); // 5dp padding.

        // Create minus button.
        Button minusButton = new Button(context, null, 0); // Disable default theme style.
        minusButton.setText(""); // No text on button.
        ShapeDrawable minusBackground = new ShapeDrawable(new RoundRectShape(
                Utils.createCornerRadii(20), null, null));
        minusBackground.getPaint().setColor(ThemeUtils.getAdjustedBackgroundColor(false));
        minusButton.setBackground(minusBackground);
        OutlineSymbolDrawable minusDrawable = new OutlineSymbolDrawable(false); // Minus symbol.
        minusButton.setForeground(minusDrawable);
        LinearLayout.LayoutParams minusParams = new LinearLayout.LayoutParams(dip36, dip36);
        minusParams.setMargins(0, 0, dip5, 0); // 5dp to slider.
        minusButton.setLayoutParams(minusParams);

        // Create slider for speed adjustment.
        SeekBar speedSlider = new SeekBar(context);
        speedSlider.setMax(speedToProgressValue(CustomPlaybackSpeedPatch.getPlaybackSpeedMaximum()));
        speedSlider.setProgress(speedToProgressValue(currentSpeed));
        speedSlider.getProgressDrawable().setColorFilter(
                ThemeUtils.getForegroundColor(), PorterDuff.Mode.SRC_IN); // Theme progress bar.
        speedSlider.getThumb().setColorFilter(
                ThemeUtils.getForegroundColor(), PorterDuff.Mode.SRC_IN); // Theme slider thumb.
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sliderParams.setMargins(dip5, 0, dip5, 0); // 5dp to -/+ buttons.
        speedSlider.setLayoutParams(sliderParams);

        // Create plus button.
        Button plusButton = new Button(context, null, 0); // Disable default theme style.
        plusButton.setText(""); // No text on button.
        ShapeDrawable plusBackground = new ShapeDrawable(new RoundRectShape(
                Utils.createCornerRadii(20), null, null));
        plusBackground.getPaint().setColor(ThemeUtils.getAdjustedBackgroundColor(false));
        plusButton.setBackground(plusBackground);
        OutlineSymbolDrawable plusDrawable = new OutlineSymbolDrawable(true); // Plus symbol.
        plusButton.setForeground(plusDrawable);
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dip36, dip36);
        plusParams.setMargins(dip5, 0, 0, 0); // 5dp to slider.
        plusButton.setLayoutParams(plusParams);

        // Add -/+ and slider views to slider layout.
        sliderLayout.addView(minusButton);
        sliderLayout.addView(speedSlider);
        sliderLayout.addView(plusButton);

        LinearLayout.LayoutParams sliderLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sliderLayoutParams.setMargins(0, 0, 0, dip5); // 5dp bottom margin.
        sliderLayout.setLayoutParams(sliderLayoutParams);

        // Add slider layout to main layout.
        mainLayout.addView(sliderLayout);

        Function<Float, Void> userSelectedSpeed = newSpeed -> {
            final float roundedSpeed = roundSpeedToNearestIncrement(newSpeed);
            if (VideoInformation.getPlaybackSpeed() == roundedSpeed) {
                // Nothing has changed. New speed rounds to the current speed.
                return null;
            }

            VideoInformation.overridePlaybackSpeed(roundedSpeed);
            PlaybackSpeedPatch.userSelectedPlaybackSpeed(roundedSpeed);
            currentSpeedText.setText(formatSpeedStringX(roundedSpeed, 2)); // Update display.
            speedSlider.setProgress(speedToProgressValue(roundedSpeed)); // Update slider.
            return null;
        };

        // Set listener for slider to update playback speed.
        speedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Convert from progress value to video playback speed.
                    userSelectedSpeed.apply(CustomPlaybackSpeedPatch.getPlaybackSpeedMinimum() + (progress / PROGRESS_BAR_VALUE_SCALE));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        minusButton.setOnClickListener(v -> userSelectedSpeed.apply(
                VideoInformation.getPlaybackSpeed() - 0.05f));
        plusButton.setOnClickListener(v -> userSelectedSpeed.apply(
                VideoInformation.getPlaybackSpeed() + 0.05f));

        // Create GridLayout for preset speed buttons.
        GridLayout gridLayout = new GridLayout(context);
        gridLayout.setColumnCount(5); // 5 columns for speed buttons.
        gridLayout.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        gridLayout.setRowCount((int) Math.ceil(CustomPlaybackSpeedPatch.getLength() / 5.0));
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.setMargins(0, 0, 0, 0); // No margins around GridLayout.
        gridLayout.setLayoutParams(gridParams);

        // For all buttons show at least 1 zero in decimal (2 -> "2.0").
        speedFormatter.setMinimumFractionDigits(1);

        // Add buttons for each preset playback speed.
        for (float speed : CustomPlaybackSpeedPatch.getPlaybackSpeeds()) {
            // Container for button and optional label.
            FrameLayout buttonContainer = new FrameLayout(context);

            // Set layout parameters for each grid cell.
            GridLayout.LayoutParams containerParams = new GridLayout.LayoutParams();
            containerParams.width = 0; // Equal width for columns.
            containerParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            containerParams.setMargins(dip5, 0, dip5, 0); // Button margins.
            containerParams.height = dip60; // Fixed height for button and label.
            buttonContainer.setLayoutParams(containerParams);

            // Create speed button.
            Button speedButton = new Button(context, null, 0);
            speedButton.setText(speedFormatter.format(speed)); // Do not use 'x' speed format.
            speedButton.setTextColor(ThemeUtils.getForegroundColor());
            speedButton.setTextSize(12);
            speedButton.setAllCaps(false);
            speedButton.setGravity(Gravity.CENTER);

            ShapeDrawable buttonBackground = new ShapeDrawable(new RoundRectShape(
                    Utils.createCornerRadii(20), null, null));
            buttonBackground.getPaint().setColor(ThemeUtils.getAdjustedBackgroundColor(false));
            speedButton.setBackground(buttonBackground);
            speedButton.setPadding(dip5, dip5, dip5, dip5);

            // Center button vertically and stretch horizontally in container.
            FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, dip32, Gravity.CENTER);
            speedButton.setLayoutParams(buttonParams);

            // Add speed buttons view to buttons container layout.
            buttonContainer.addView(speedButton);

            // Add "Normal" label for 1.0x speed.
            if (speed == 1.0f) {
                TextView normalLabel = new TextView(context);
                // Use same 'Normal' string as stock YouTube.
                normalLabel.setText(str("revanced_playback_speed_normal"));
                normalLabel.setTextColor(ThemeUtils.getForegroundColor());
                normalLabel.setTextSize(10);
                normalLabel.setGravity(Gravity.CENTER);

                FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                labelParams.bottomMargin = 0; // Position label below button.
                normalLabel.setLayoutParams(labelParams);

                buttonContainer.addView(normalLabel);
            }

            speedButton.setOnClickListener(v -> userSelectedSpeed.apply(speed));

            gridLayout.addView(buttonContainer);
        }

        // Add in-rows speed buttons layout to main layout.
        mainLayout.addView(gridLayout);

        ExtendedUtils.showBottomSheetDialog(context, mainLayout);
    }

    /**
     * @param speed The playback speed value to format.
     * @return A string representation of the speed with 'x' (e.g. "1.25x" or "1.00x").
     */
    private static String formatSpeedStringX(float speed, int minimumFractionDigits) {
        speedFormatter.setMinimumFractionDigits(minimumFractionDigits);
        return speedFormatter.format(speed) + 'x';
    }

    /**
     * @return user speed converted to a value for {@link SeekBar#setProgress(int)}.
     */
    private static int speedToProgressValue(float speed) {
        return (int) ((speed - CustomPlaybackSpeedPatch.getPlaybackSpeedMinimum()) * PROGRESS_BAR_VALUE_SCALE);
    }

    /**
     * Rounds the given playback speed to the nearest 0.05 increment and ensures it is within valid bounds.
     *
     * @param speed The playback speed to round.
     * @return The rounded speed, constrained to the specified bounds.
     */
    private static float roundSpeedToNearestIncrement(float speed) {
        // Round to nearest 0.05 speed.  Must use double precision otherwise rounding error can occur.
        final double roundedSpeed = Math.round(speed / 0.05) * 0.05;
        return Utils.clamp((float) roundedSpeed, 0.05f, CustomPlaybackSpeedPatch.PLAYBACK_SPEED_MAXIMUM);
    }
}

/**
 * Custom Drawable for rendering outlined plus and minus symbols on buttons.
 */
class OutlineSymbolDrawable extends Drawable {
    private final boolean isPlus; // Determines if the symbol is a plus or minus.
    private final Paint paint;

    OutlineSymbolDrawable(boolean isPlus) {
        this.isPlus = isPlus;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG); // Enable anti-aliasing for smooth rendering.
        paint.setColor(ThemeUtils.getForegroundColor());
        paint.setStyle(Paint.Style.STROKE); // Use stroke style for outline.
        paint.setStrokeWidth(dipToPixels(1)); // 1dp stroke width.
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        final int width = bounds.width();
        final int height = bounds.height();
        final float centerX = width / 2f; // Center X coordinate.
        final float centerY = height / 2f; // Center Y coordinate.
        final float size = Math.min(width, height) * 0.25f; // Symbol size is 25% of button dimensions.

        // Draw horizontal line for both plus and minus symbols.
        canvas.drawLine(centerX - size, centerY, centerX + size, centerY, paint);
        if (isPlus) {
            // Draw vertical line for plus symbol.
            canvas.drawLine(centerX, centerY - size, centerX, centerY + size, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
