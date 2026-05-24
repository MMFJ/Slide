package me.edgan.redditslide.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.app.ServiceCompat;
import android.content.pm.ServiceInfo;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import me.edgan.redditslide.Adapters.BatchDownloadItem;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.FileUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.LogUtil;

import net.dean.jraw.models.Submission;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatchDownloadService extends Service {

    public static final String ACTION_START = "me.edgan.redditslide.BATCH_DOWNLOAD_START";
    public static final String ACTION_CANCEL = "me.edgan.redditslide.BATCH_DOWNLOAD_CANCEL";
    
    public static final String BROADCAST_PROGRESS = "me.edgan.redditslide.BATCH_PROGRESS";
    public static final String BROADCAST_ITEM_DONE = "me.edgan.redditslide.BATCH_ITEM_DONE";
    public static final String BROADCAST_ITEM_FAILED = "me.edgan.redditslide.BATCH_ITEM_FAILED";
    public static final String BROADCAST_FINISHED = "me.edgan.redditslide.BATCH_FINISHED";

    public static final String EXTRA_SUBMISSION_ID = "submissionId";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_TOTAL = "total";

    private static final int NOTIFICATION_ID = 54321;
    private static final String CHANNEL_ID = Reddit.CHANNEL_IMG;

    // The queue of items to download. The fragment must populate this before starting the service.
    public static List<BatchDownloadItem> downloadQueue = new ArrayList<>();

    private ExecutorService executorService;
    private NotificationManager notifyManager;
    private NotificationCompat.Builder notificationBuilder;
    private AtomicBoolean isCancelled = new AtomicBoolean(false);

    private int totalItems = 0;
    private int currentItemIndex = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        notifyManager = ContextCompat.getSystemService(this, NotificationManager.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            isCancelled.set(true);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            if (downloadQueue == null || downloadQueue.isEmpty()) {
                stopSelf();
                return START_NOT_STICKY;
            }

            isCancelled.set(false);
            totalItems = downloadQueue.size();
            currentItemIndex = 0;

            startForegroundNotification();

            executorService.submit(this::processQueue);
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not bound
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
        downloadQueue.clear();
    }

    private void startForegroundNotification() {
        Intent cancelIntent = new Intent(this, BatchDownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent pCancelIntent = PendingIntent.getService(
                this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.mediaview_downloading))
                .setContentText("0 / " + totalItems)
                .setSmallIcon(R.drawable.ic_save)
                .setProgress(totalItems, 0, false)
                .addAction(R.drawable.ic_close, getString(R.string.btn_cancel), pCancelIntent)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    private void updateNotification(int progress, int total) {
        if (notificationBuilder != null && notifyManager != null) {
            notificationBuilder.setProgress(total, progress, false);
            notificationBuilder.setContentText(progress + " / " + total);
            notifyManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    private void broadcastProgress() {
        Intent intent = new Intent(BROADCAST_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS, currentItemIndex);
        intent.putExtra(EXTRA_TOTAL, totalItems);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        updateNotification(currentItemIndex, totalItems);
    }

    private void broadcastItemResult(String submissionId, boolean success) {
        Intent intent = new Intent(success ? BROADCAST_ITEM_DONE : BROADCAST_ITEM_FAILED);
        intent.putExtra(EXTRA_SUBMISSION_ID, submissionId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastFinished() {
        Intent intent = new Intent(BROADCAST_FINISHED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void processQueue() {
        for (BatchDownloadItem item : downloadQueue) {
            if (isCancelled.get()) break;

            Submission sub = item.submission;
            ContentType.Type type = item.type;
            boolean success = false;

            try {
                if (type == ContentType.Type.REDDIT_GALLERY) {
                    success = downloadGallery(sub);
                } else if (!item.isImage) {
                    success = downloadVideo(sub);
                } else {
                    success = downloadImage(sub);
                }
            } catch (Exception e) {
                LogUtil.e(e, "Error downloading item: " + sub.getUrl());
            }

            broadcastItemResult(sub.getId(), success);
            currentItemIndex++;
            broadcastProgress();
        }

        if (!isCancelled.get()) {
            Notification doneNotif = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.info_photo_saved))
                    .setContentText("Batch download complete")
                    .setSmallIcon(R.drawable.ic_save)
                    .setAutoCancel(true)
                    .build();
            if (notifyManager != null) {
                notifyManager.notify(NOTIFICATION_ID + 1, doneNotif);
            }
        }

        broadcastFinished();
        stopForeground(true);
        stopSelf();
    }

    // -------------------------------------------------------------------------
    // Download handlers
    // -------------------------------------------------------------------------

    private boolean downloadVideo(Submission sub) {
        String url = sub.getUrl();
        if (url == null || url.isEmpty()) return false;
        
        try {
            // GifUtils handles network/caching on the calling thread when called from a background thread
            // Passing true for 'save' and -1 for index.
            GifUtils.cacheSaveGif(Uri.parse(url), this, sub.getSubredditName(), sub.getTitle(), true, -1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean downloadGallery(Submission sub) {
        boolean allSuccess = true;
        try {
            JsonNode galleryData = sub.getDataNode().get("gallery_data");
            JsonNode mediaMetadata = sub.getDataNode().get("media_metadata");

            if (galleryData != null && mediaMetadata != null && galleryData.has("items")) {
                JsonNode items = galleryData.get("items");
                int idx = 1;
                for (JsonNode item : items) {
                    if (isCancelled.get()) break;
                    String mediaId = item.get("media_id").asText();
                    if (mediaMetadata.has(mediaId)) {
                        JsonNode mediaItem = mediaMetadata.get(mediaId);
                        if (mediaItem != null && mediaItem.has("s")) {
                            JsonNode s = mediaItem.get("s");
                            String url = null;
                            if (s.has("u")) url = s.get("u").asText();
                            else if (s.has("mp4")) url = s.get("mp4").asText();
                            else if (s.has("gif")) url = s.get("gif").asText();

                            if (url != null) {
                                // Fix amp; issues in URLs
                                url = url.replace("&amp;", "&");
                                boolean ok = downloadSingleInline(url, sub.getSubredditName(), sub.getTitle(), idx);
                                if (!ok) allSuccess = false;
                            }
                        }
                    }
                    idx++;
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            allSuccess = false;
        }
        return allSuccess;
    }

    private boolean downloadImage(Submission sub) {
        String url = sub.getUrl();
        if (url == null || url.isEmpty()) return false;
        
        // Fix for imgur urls without extensions
        if (url.contains("imgur.com") && !url.contains(".png") && !url.contains(".jpg")) {
            url = url + ".png";
        }
        
        return downloadSingleInline(url, sub.getSubredditName(), sub.getTitle(), -1);
    }

    /**
     * Inline synchronous download based on ImageDownloadNotificationService.PollTask
     */
    private boolean downloadSingleInline(String url, String subreddit, String title, int index) {
        try {
            Uri baseUri = me.edgan.redditslide.util.StorageUtil.getStorageUri(this);
            if (baseUri == null) return false;

            DocumentFile parentDir = DocumentFile.fromTreeUri(this, baseUri);
            if (parentDir == null || !parentDir.canWrite()) return false;

            if (SettingValues.imageSubfolders && subreddit != null && !subreddit.isEmpty()) {
                String cleanSubredditName = subreddit.replaceAll("[^a-zA-Z0-9.-]", "_");
                DocumentFile subFolder = parentDir.findFile(cleanSubredditName);
                if (subFolder == null) {
                    subFolder = parentDir.createDirectory(cleanSubredditName);
                }
                if (subFolder == null) return false;
                parentDir = subFolder;
            }

            // A blocking way to get the image using UIL:
            Bitmap loadedImage = ((Reddit) getApplication()).getImageLoader().loadImageSync(
                    url,
                    new DisplayImageOptions.Builder()
                            .imageScaleType(ImageScaleType.NONE)
                            .cacheInMemory(false)
                            .cacheOnDisk(true)
                            .build()
            );

            if (loadedImage == null) return false;

            File cachedFile = ((Reddit) getApplicationContext())
                    .getImageLoader()
                    .getDiskCache()
                    .get(url);

            String fileName = getFileName(url, title, index, subreddit);
            String mimeType = getMimeType(fileName);
            DocumentFile outDocFile = parentDir.createFile(mimeType, fileName);

            if (outDocFile != null) {
                OutputStream out = getContentResolver().openOutputStream(outDocFile.getUri());
                if (out != null) {
                    if (cachedFile != null && cachedFile.exists()) {
                        FileUtil.copyFile(cachedFile, out);
                    } else {
                        Bitmap.CompressFormat format = mimeType.contains("png")
                                ? Bitmap.CompressFormat.PNG
                                : Bitmap.CompressFormat.JPEG;
                        loadedImage.compress(format, 100, out);
                    }
                    out.close();
                    return true;
                }
            }

        } catch (Exception e) {
            LogUtil.e(e, "Error downloading " + url);
        }
        return false;
    }

    private String getMimeType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".mp4")) return "video/mp4";
        return "image/png";
    }

    private String getFileName(String url, String submissionTitle, int index, String subreddit) {
        String extension;
        try {
            URL parsedUrl = new URL(url);
            String path = parsedUrl.getPath();
            if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") 
                || path.endsWith(".gif") || path.endsWith(".mp4")) {
                extension = path.substring(path.lastIndexOf("."));
            } else {
                throw new MalformedURLException();
            }
        } catch (MalformedURLException e) {
            extension = ".png";
        }

        String fileIndex = index > -1 ? String.format(Locale.ENGLISH, "_%03d", index) : "";
        String title = (submissionTitle != null && !submissionTitle.trim().isEmpty()) 
                ? submissionTitle : String.valueOf(System.currentTimeMillis());
        String subfolderPath = (subreddit != null && !subreddit.isEmpty()) 
                ? File.separator + subreddit : "/";

        String tempPath = getApplicationContext().getCacheDir().getAbsolutePath();
        File validFile = FileUtil.getValidFile(tempPath, subfolderPath, title, fileIndex, extension);
        return validFile.getName();
    }
}
