package me.edgan.redditslide.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.graphics.Point;
import android.graphics.PointF;
import android.view.MotionEvent;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class BottomSheetAnchorTest {

    @Test
    public void calculateAnchorPosition_leftSideAlignsNearTouch() {
        final PointF anchor = new PointF(100f, 800f);
        final int dialogWidth = 340;
        final int dialogHeight = 300;
        final int screenWidth = 1600;
        final int screenHeight = 2400;
        final int marginH = 16;
        final int marginV = 16;
        final int offsetY = 24;

        final Point pos =
                BottomSheet.calculateAnchorPosition(
                        anchor, dialogWidth, dialogHeight, screenWidth, screenHeight, marginH, marginV, offsetY);

        // x aligns with anchor.x because anchor is on left half
        assertThat(pos.x, is(100));
        // y aligns with anchor.y - offsetY
        assertThat(pos.y, is(776));
    }

    @Test
    public void calculateAnchorPosition_rightSideOffsetsLeftOfTouch() {
        final PointF anchor = new PointF(1400f, 800f);
        final int dialogWidth = 340;
        final int dialogHeight = 300;
        final int screenWidth = 1600;
        final int screenHeight = 2400;
        final int marginH = 16;
        final int marginV = 16;
        final int offsetY = 24;

        final Point pos =
                BottomSheet.calculateAnchorPosition(
                        anchor, dialogWidth, dialogHeight, screenWidth, screenHeight, marginH, marginV, offsetY);

        // x is offset to the left of touch so dialog stays on-screen
        assertThat(pos.x, is(1400 - 340));
        assertThat(pos.y, is(776));
    }

    @Test
    public void calculateAnchorPosition_clampsToScreenBounds() {
        final int dialogWidth = 340;
        final int dialogHeight = 300;
        final int screenWidth = 1600;
        final int screenHeight = 2400;
        final int marginH = 16;
        final int marginV = 16;
        final int offsetY = 24;

        // Extreme top-left
        final Point topLeft =
                BottomSheet.calculateAnchorPosition(
                        new PointF(2f, 5f),
                        dialogWidth,
                        dialogHeight,
                        screenWidth,
                        screenHeight,
                        marginH,
                        marginV,
                        offsetY);
        assertThat(topLeft.x, is(marginH));
        assertThat(topLeft.y, is(marginV));

        // Extreme bottom-right
        final Point bottomRight =
                BottomSheet.calculateAnchorPosition(
                        new PointF(1599f, 2399f),
                        dialogWidth,
                        dialogHeight,
                        screenWidth,
                        screenHeight,
                        marginH,
                        marginV,
                        offsetY);
        assertThat(bottomRight.x, is(screenWidth - dialogWidth - marginH));
        assertThat(bottomRight.y, is(screenHeight - dialogHeight - marginV));
    }

    @Test
    public void builderAnchorFromMotionEventSetsAnchor() {
        final Application context = ApplicationProvider.getApplicationContext();
        final BottomSheet.Builder builder = new BottomSheet.Builder(context);

        final MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 120f, 350f, 0);
        builder.anchor(event);
        event.recycle();

        // Building the dialog (under Robolectric) initializes the dialog view and attributes cleanly
        builder.sheet(1, null, "Item 1");
        final android.app.Dialog dialog = builder.show();
        assertNotNull(dialog);
        dialog.dismiss();
    }
}
