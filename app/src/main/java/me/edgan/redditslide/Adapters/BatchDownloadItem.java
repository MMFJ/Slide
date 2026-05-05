package me.edgan.redditslide.Adapters;

import me.edgan.redditslide.ContentType;

import net.dean.jraw.models.Submission;

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

    /**
     * Canonical key used to deduplicate across multiple subreddit cross-posts.
     * <ul>
     *   <li>REDDIT_GALLERY → submission ID (not URL, which may differ per post)</li>
     *   <li>All others     → lower-cased URL with query string stripped</li>
     * </ul>
     */
    public final String dedupeKey;

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
    }

    private static boolean isImageFamily(ContentType.Type t) {
        switch (t) {
            case IMAGE:
            case GIF:
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
