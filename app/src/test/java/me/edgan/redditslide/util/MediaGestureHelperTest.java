package me.edgan.redditslide.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.atomic.AtomicBoolean;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.SettingValues;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class MediaGestureHelperTest {

    private ActivityController<AppCompatActivity> controller;
    private AppCompatActivity activity;
    private boolean originalDownload;
    private boolean originalUpvote;
    private boolean originalMarkRead;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(AppCompatActivity.class).setup();
        activity = controller.get();
        originalDownload = SettingValues.longPressDownload;
        originalUpvote = SettingValues.longPressUpvote;
        originalMarkRead = SettingValues.longPressMarkRead;
    }

    @After
    public void tearDown() {
        SettingValues.longPressDownload = originalDownload;
        SettingValues.longPressUpvote = originalUpvote;
        SettingValues.longPressMarkRead = originalMarkRead;
        controller.destroy();
    }

    @Test
    public void executeLongPressActionsWithNoActionsEnabledReturnsFalse() {
        SettingValues.longPressDownload = false;
        SettingValues.longPressUpvote = false;
        SettingValues.longPressMarkRead = false;

        final AtomicBoolean ran = new AtomicBoolean(false);
        final boolean executed =
                MediaGestureHelper.executeLongPressActions(activity, null, () -> ran.set(true));

        assertFalse(executed);
        assertFalse(ran.get());
    }

    @Test
    public void executeLongPressActionsRunsDownloadWhenEnabled() {
        SettingValues.longPressDownload = true;
        SettingValues.longPressUpvote = false;
        SettingValues.longPressMarkRead = false;

        final AtomicBoolean ran = new AtomicBoolean(false);
        final boolean executed =
                MediaGestureHelper.executeLongPressActions(activity, null, () -> ran.set(true));

        assertTrue(executed);
        assertTrue(ran.get());
    }

    @Test
    public void setUpvotedReturnsFalseWhenTargetIsNull() {
        assertFalse(MediaGestureHelper.setUpvoted(activity, null));
    }
}
