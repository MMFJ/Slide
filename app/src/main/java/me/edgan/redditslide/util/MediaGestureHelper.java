package me.edgan.redditslide.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.os.Handler;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.DataShare;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Vote;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;
import org.jspecify.annotations.NullMarked;

/**
 * Shared helper for media viewer gestures (upvoting toggle, floating visual feedback,
 * and unified action execution).
 */
@NullMarked
public class MediaGestureHelper {

    /**
     * Toggles upvote for the given submission, showing feedback and updating action state.
     *
     * @param activity Hosting activity
     * @param submission Submission to vote on (falls back to DataShare.sharedSubmission if null)
     * @return true if vote was toggled, false if voting was unavailable or user not logged in
     */
    public static boolean handleUpvote(final Activity activity, @Nullable final Submission submission) {
        if (!Authentication.isLoggedIn || !Authentication.didOnline) {
            Toast.makeText(activity, R.string.vote_err_login, Toast.LENGTH_SHORT).show();
            return false;
        }

        final Submission target = submission != null ? submission : DataShare.sharedSubmission;
        if (target == null) {
            Toast.makeText(activity, "Upvoting not available", Toast.LENGTH_SHORT).show();
            return false;
        }

        final VoteDirection currentVote = ActionStates.getVoteDirection(target);
        final boolean isUpvoting = (currentVote != VoteDirection.UPVOTE);

        if (isUpvoting) {
            new Vote(true, null, activity).execute(target);
            ActionStates.setVoteDirection(target, VoteDirection.UPVOTE);
            showVoteFeedback(activity, true);
        } else {
            new Vote(null, activity).execute(target);
            ActionStates.setVoteDirection(target, VoteDirection.NO_VOTE);
            showVoteFeedback(activity, false);
        }
        return true;
    }

    /**
     * Ensures the submission is upvoted (idempotent; will not undo/reverse an existing upvote).
     *
     * @param activity Hosting activity
     * @param target Submission to upvote
     * @return true if target was valid, false if null or upvoting not available
     */
    public static boolean setUpvoted(final Activity activity, @Nullable final Submission target) {
        if (target == null) {
            return false;
        }
        if (!Authentication.isLoggedIn || !Authentication.didOnline) {
            Toast.makeText(activity, R.string.vote_err_login, Toast.LENGTH_SHORT).show();
            return false;
        }

        final VoteDirection currentVote = ActionStates.getVoteDirection(target);
        if (currentVote != VoteDirection.UPVOTE) {
            new Vote(true, null, activity).execute(target);
            ActionStates.setVoteDirection(target, VoteDirection.UPVOTE);
            showVoteFeedback(activity, true);
        }
        return true;
    }

    /**
     * Shows a brief visual floating icon (check circle for upvote, close for remove)
     * centered over the activity window.
     */
    public static void showVoteFeedback(final Activity activity, final boolean isUpvote) {
        final ImageView feedbackIcon = new ImageView(activity);

        if (isUpvote) {
            feedbackIcon.setImageResource(R.drawable.ic_check_circle);
            BlendModeUtil.tintImageViewAsSrcAtop(
                    feedbackIcon, ContextCompat.getColor(activity, R.color.md_green_500));
        } else {
            feedbackIcon.setImageResource(R.drawable.ic_close);
            BlendModeUtil.tintImageViewAsSrcAtop(
                    feedbackIcon, ContextCompat.getColor(activity, R.color.md_grey_600));
        }

        int iconSize = (int) (72 * activity.getResources().getDisplayMetrics().density); // 72dp
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(iconSize, iconSize);
        feedbackIcon.setLayoutParams(params);
        feedbackIcon.setAlpha(0f);

        View rootView = activity.findViewById(android.R.id.content);
        if (rootView instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) rootView;

            final LinearLayout container = new LinearLayout(activity);
            container.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT));
            container.setGravity(android.view.Gravity.CENTER);
            container.addView(feedbackIcon);
            container.setClickable(false);
            container.setFocusable(false);

            viewGroup.addView(container);

            feedbackIcon
                    .animate()
                    .alpha(1f)
                    .setDuration(150)
                    .setListener(
                            new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    new Handler()
                                            .postDelayed(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            feedbackIcon
                                                                    .animate()
                                                                    .alpha(0f)
                                                                    .setDuration(200)
                                                                    .setListener(
                                                                            new AnimatorListenerAdapter() {
                                                                                @Override
                                                                                public void onAnimationEnd(
                                                                                        Animator animation) {
                                                                                    viewGroup.removeView(
                                                                                            container);
                                                                                }
                                                                            })
                                                                    .start();
                                                        }
                                                    },
                                                    500);
                                }
                            })
                    .start();
        }
    }

    /**
     * Executes the user-configured long-press actions (Download, Upvote toggle, Mark read).
     *
     * @param activity Hosting activity
     * @param submission Associated submission (can be null; falls back to DataShare.sharedSubmission)
     * @param downloadAction Runnable to perform image/media download if enabled
     * @return true if at least one action was executed, false if none were configured or performed
     */
    public static boolean executeLongPressActions(
            final Activity activity,
            @Nullable final Submission submission,
            @Nullable final Runnable downloadAction) {

        boolean anyAction = false;
        final Submission target = submission != null ? submission : DataShare.sharedSubmission;

        if (SettingValues.longPressDownload && downloadAction != null) {
            downloadAction.run();
            anyAction = true;
        }

        if (SettingValues.longPressUpvote) {
            if (setUpvoted(activity, target)) {
                anyAction = true;
            }
        }

        if (SettingValues.longPressMarkRead) {
            if (target != null) {
                HasSeen.addSeen(target.getFullName());
                Toast.makeText(activity, R.string.marked_as_read, Toast.LENGTH_SHORT).show();
                anyAction = true;
            }
        }

        if (anyAction) {
            View decor = activity.getWindow().getDecorView();
            decor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }

        return anyAction;
    }
}
