package me.edgan.redditslide.Adapters;

import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.HasSeen;

import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;

/**
 * Represents a single deduplicated media post in the Batch DL list.
 */
public class BatchDownloadItem {

    /** The original Reddit submission this item came from. */
    public final Submission submission;

    /** ContentType resolved for this submission's URL. */
    public final ContentType.Type type;

    /**
     * True  = image-family (IMAGE, GIF, IMGUR, ALBUM, REDDIT_GALLERY, DEVIANTART, XKCD, TUMBLR)
     * False = video-family (VREDDIT_DIRECT, VREDDIT_REDIRECT, STREAMABLE)
     */
    public final boolean isImage;

    /** True if the user has upvoted or downvoted this submission. */
    public boolean isVoted;

    /** True if the user has viewed this submission. */
    public boolean isViewed;

    /**
     * Canonical key used to deduplicate across multiple subreddit cross-posts.
     * <ul>
     *   <li>REDDIT_GALLERY → submission ID (not URL, which may differ per post)</li>
     *   <li>All others     → lower-cased URL with query string stripped</li>
     * </ul>
     */
    public final String dedupeKey;

    /**
     * Perceptual hash of the submission's thumbnail, computed during the fetch task.
     * Used for visual duplicate detection across different upload URLs.
     * May be null if the thumbnail was unavailable or hashing failed.
     */
    public String thumbnailHash;

    public enum State {
        CHECKED,     // visible, selected for download (default)
        UNCHECKED,   // visible, not selected
        DOWNLOADING, // download in progress (Phase 4)
        DONE,        // downloaded successfully (Phase 4)
        FAILED       // download failed (Phase 4)
    }

    public State state = State.CHECKED;

    public BatchDownloadItem(Submission submission, ContentType.Type type, String dedupeKey) {
        this.submission = submission;
        this.type = type;
        this.dedupeKey = dedupeKey;
        this.isImage = isImageFamily(type);
        this.isVoted = ActionStates.getVoteDirection(submission) != VoteDirection.NO_VOTE;
        this.isViewed = HasSeen.getSeen(submission);
    }

    private static boolean isImageFamily(ContentType.Type t) {
        switch (t) {
            case IMAGE:
            case IMGUR:
            case ALBUM:
            case REDDIT_GALLERY:
            case DEVIANTART:
            case XKCD:
            case TUMBLR:
                return true;
            default:
                return false;
        }
    }
}
