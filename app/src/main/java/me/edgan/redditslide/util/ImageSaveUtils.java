package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.gson.JsonObject;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.ImgurAlbum.AlbumUtils;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.Notifications.ImageDownloadNotificationService;
import me.edgan.redditslide.R;
import java.util.List;
import net.dean.jraw.models.Submission;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ImageSaveUtils {
    private static final String TAG = "ImageSaveUtils";
    public static final String EXTRA_SUBMISSION_TITLE = ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

    /**
     * Downloads all media for a submission: if the submission is a Reddit gallery or Imgur album,
     * downloads all images in the gallery/album. Otherwise, downloads the single media file.
     */
    public static void saveSubmissionMedia(
            final Activity activity,
            final Submission submission,
            @Nullable final String fallbackUrl) {
        final ContentType.Type type = ContentType.getContentType(submission);
        if (type == ContentType.Type.REDDIT_GALLERY) {
            final List<GalleryImage> images = GalleryTiles.imagesFor(submission.getDataNode());
            if (images != null && !images.isEmpty()) {
                int index = 0;
                for (final GalleryImage elem : images) {
                    final String elemUrl = elem.url;
                    if (elemUrl != null) {
                        if (elem.isAnimated()) {
                            final String subName =
                                    submission.getSubredditName() != null
                                            ? submission.getSubredditName()
                                            : "";
                            final String title =
                                    submission.getTitle() != null ? submission.getTitle() : "";
                            GifUtils.cacheSaveGif(
                                    Uri.parse(elemUrl),
                                    activity,
                                    subName,
                                    title,
                                    true);
                        } else {
                            doImageSave(
                                    activity,
                                    false,
                                    elemUrl,
                                    index,
                                    submission.getSubredditName(),
                                    submission.getTitle(),
                                    () -> StorageUtil.showDirectoryChooser(activity));
                        }
                    }
                    index++;
                }
                return;
            }
        } else if (type == ContentType.Type.ALBUM) {
            final String albumUrl = fallbackUrl != null ? fallbackUrl : submission.getUrl();
            if (albumUrl != null) {
                new AlbumUtils.GetAlbumWithCallback(albumUrl, activity) {
                    @Override
                    public boolean doWithData(@Nullable List<Image> jsonElements) {
                        if (!super.doWithData(jsonElements) || jsonElements == null) {
                            return false;
                        }
                        int index = 0;
                        for (final Image elem : jsonElements) {
                            if (elem.getImageUrl() != null) {
                                doImageSave(
                                        activity,
                                        elem.animated(),
                                        elem.getImageUrl(),
                                        index,
                                        submission.getSubredditName(),
                                        submission.getTitle(),
                                        () -> StorageUtil.showDirectoryChooser(activity));
                            }
                            index++;
                        }
                        return true;
                    }
                }.execute();
                return;
            }
        }

        final boolean isGif =
                type == ContentType.Type.GIF
                        || type == ContentType.Type.VIDEO
                        || type == ContentType.Type.STREAMABLE
                        || type == ContentType.Type.VREDDIT_REDIRECT
                        || type == ContentType.Type.VREDDIT_DIRECT;
        final String url = fallbackUrl != null ? fallbackUrl : submission.getUrl();
        doImageSave(
                activity,
                isGif,
                url,
                -1,
                submission.getSubredditName(),
                submission.getTitle(),
                () -> StorageUtil.showDirectoryChooser(activity));
    }

    /**
     * Saves an image or gif to the device storage
     *
     * @param activity The activity context
     * @param isGif Whether the content is a gif/video
     * @param contentUrl The URL of the content to save
     * @param index The index of the image in a gallery
     * @param subreddit The subreddit the content is from
     * @param submissionTitle The title of the submission
     * @param showFirstDialogCallback Callback to show the storage access dialog
     */
    public static void doImageSave(
            Activity activity,
            boolean isGif,
            @Nullable String contentUrl,
            int index,
            @Nullable String subreddit,
            @Nullable String submissionTitle,
            @Nullable Runnable showFirstDialogCallback) {

        Uri storageUri = StorageUtil.getStorageUri(activity);
        if (storageUri == null || !StorageUtil.hasStorageAccess(activity)) {
            Log.e(TAG, "No valid storage URI found.");
            if (showFirstDialogCallback != null) {
                showFirstDialogCallback.run();
            }
            return;
        } else {
            if (isGif) {
                // Handle video/gif save asynchronously
                new ResolveAndSaveGifTask(
                        activity,
                        contentUrl == null ? "" : contentUrl,
                        subreddit == null ? "" : subreddit,
                        submissionTitle == null ? "" : submissionTitle,
                        index
                ).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                // Handle image save (remains synchronous start of service)
                Intent i = new Intent(activity, ImageDownloadNotificationService.class);
                i.putExtra("actuallyLoaded", contentUrl);
                i.putExtra("downloadUri", storageUri.toString());

                if (subreddit != null && !subreddit.isEmpty()) {
                    i.putExtra("subreddit", subreddit);
                }

                Log.d(TAG, "ImageSaveUtils - Saving with submissionTitle: " + (submissionTitle != null ? "'" + submissionTitle + "'" : "null"));
                if (submissionTitle != null) {
                    i.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
                }
                i.putExtra("index", index); // Index is not used for GIFs, but keep for images

                activity.startService(i);
            }
        }
    }

    // Inner AsyncTask to resolve GIF/Video URI and then save
    private static class ResolveAndSaveGifTask extends AsyncTask<Void, Void, Uri> {
        private final Activity activity;
        private final String initialUrl;
        private final String subreddit;
        private final String submissionTitle;
        private final int index;
        @Nullable private Exception error = null;

        ResolveAndSaveGifTask(Activity activity, String initialUrl, String subreddit, String submissionTitle) {
            this(activity, initialUrl, subreddit, submissionTitle, -1);
        }

        ResolveAndSaveGifTask(Activity activity, String initialUrl, String subreddit, String submissionTitle, int index) {
            this.activity = activity;
            this.initialUrl = initialUrl;
            this.subreddit = subreddit;
            this.submissionTitle = submissionTitle;
            this.index = index;
        }

        @Override
        protected @Nullable Uri doInBackground(Void... params) {
            // Logic adapted from GifUtils.AsyncLoadGif.doInBackground
            final String url = GifUtils.AsyncLoadGif.formatUrl(initialUrl);
            GifUtils.AsyncLoadGif.VideoType videoType = GifUtils.AsyncLoadGif.getVideoType(url);
            LogUtil.v("ResolveAndSaveGifTask: " + url + ", VideoType: " + videoType);

            try {
                switch (videoType) {
                    case REDGIFS:
                        String id = url.substring(url.lastIndexOf("/"));
                        return GifUtils.AsyncLoadGif.loadRedGifs(id, url, null, null, false);
                    case VREDDIT:
                        return Uri.parse(url);
                    case GFYCAT:
                        String name = url.substring(url.lastIndexOf("/"));
                        Uri gfyUri = GifUtils.AsyncLoadGif.loadGfycat(name, url, null, null, false);
                        if (GifUtils.AsyncLoadGif.isHandoff(gfyUri)) {
                            // A marker, not a url — see HANDOFF_SCHEME. There is nothing to
                            // download, and returning it would have the saver try to fetch it.
                            Log.w(TAG, "Gfycat resolved to gifdeliverynetwork with no media: " + url);
                            error = new IllegalArgumentException("No media behind this gfycat link.");
                            return null;
                        }
                        return gfyUri;
                    case REDDIT_GALLERY: // Galleries aren't single videos, saving doesn't make sense here
                        Log.w(TAG, "Attempted to save a Reddit Gallery link as a single GIF: " + url);
                        error = new IllegalArgumentException("Cannot save Reddit Gallery as single video.");
                        return null;
                    case DIRECT:
                    case IMGUR:
                    case TUMBLR:
                        return Uri.parse(url);
                    case STREAMABLE:
                        String hash = url.substring(url.lastIndexOf("/") + 1);
                        String streamableUrl = "https://api.streamable.com/videos/" + hash;
                        JsonObject result = HttpUtil.getJsonObject(GifUtils.AsyncLoadGif.client, GifUtils.AsyncLoadGif.gson, streamableUrl);
                        final JsonObject files = GsonUtil.obj(result, "files");
                        if (result == null
                                || !(files.has("mp4") || files.has("mp4-mobile"))) {
                            error = new Exception("Streamable API response invalid for: " + url);
                            return null;
                        } else {
                            String obj;
                            obj = GsonUtil.string(GsonUtil.obj(files, "mp4-mobile"), "url", "");
                            if (obj.isEmpty()) {
                                obj = GsonUtil.string(GsonUtil.obj(files, "mp4"), "url", "");
                            }
                            if (obj.isEmpty()) {
                                error = new Exception("Streamable API response invalid for: " + url);
                                return null;
                            }
                            return Uri.parse(obj);
                        }
                    case OTHER:
                        error = new IllegalArgumentException("Cannot resolve video URL for type OTHER: " + url);
                        return null; // Cannot resolve
                }
            } catch (Exception e) {
                LogUtil.e(e, "Error resolving GIF/Video URI for saving: " + initialUrl);
                error = e;
                return null;
            }
            // Add default return statement outside try-catch to satisfy compiler
            return null;
        }

        @Override
        protected void onPostExecute(@Nullable Uri resolvedUri) {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return; // Activity gone
            }

            if (resolvedUri != null) {
                // MMFJ: no toast here -- GifUtils.cacheSaveGif shows the same one, which made
                // video saves toast twice.

                // Proceed with saving using the resolved URI
                GifUtils.cacheSaveGif(resolvedUri, activity, subreddit != null ? subreddit : "", submissionTitle != null ? submissionTitle : "", true, index);
            } else {
                // Handle errors during resolution
                Log.e(TAG, "Failed to resolve URI for " + initialUrl + (error != null ? ": " + error.getMessage() : ""));
                // Show error dialog or toast
                if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                    DialogUtil.showErrorDialog((androidx.appcompat.app.AppCompatActivity) activity);
                } else {
                     // Fallback or log error if casting is not possible (shouldn't happen in practice)
                    Log.e(TAG, "Activity is not an AppCompatActivity, cannot show error dialog.");
                }
            }
        }
    }

}