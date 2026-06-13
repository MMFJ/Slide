package me.edgan.redditslide.Adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;

import me.edgan.redditslide.R;
import me.edgan.redditslide.util.ImageLoaderUtils;

import net.dean.jraw.models.Submission;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the Batch DL media list.
 *
 * Maintains a master list ({@code allItems}) and a derived visible list
 * ({@code visibleItems}) controlled by the type filter flags.
 */
public class BatchDownloadAdapter extends RecyclerView.Adapter<BatchDownloadAdapter.ViewHolder> {

    // Thumbnail display options — matches the existing gallery thumbnail style
    private static final DisplayImageOptions THUMB_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cacheOnDisk(true)
                    .resetViewBeforeLoading(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .imageScaleType(ImageScaleType.EXACTLY)
                    .cacheInMemory(false)
                    .displayer(new FadeInBitmapDisplayer(150))
                    .build();

    private final Context context;
    private final List<BatchDownloadItem> allItems;
    private List<BatchDownloadItem> visibleItems;

    private boolean showImages = true;
    private boolean showVideos = true;
    private boolean showVoted = true;
    private boolean showViewed = true;

    public BatchDownloadAdapter(Context context, List<BatchDownloadItem> items) {
        this.context = context;
        this.allItems = new ArrayList<>(items);
        this.visibleItems = new ArrayList<>(items); // all visible initially
    }

    // -------------------------------------------------------------------------
    // RecyclerView.Adapter
    // -------------------------------------------------------------------------

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.batch_download_list_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BatchDownloadItem item = visibleItems.get(position);
        Submission sub = item.submission;

        // Title
        holder.title.setText(sub.getTitle());

        // Score
        holder.score.setText(String.format(Locale.getDefault(), "%d", sub.getScore()));

        // Type
        me.edgan.redditslide.ContentType.Type type = me.edgan.redditslide.ContentType.getContentType(sub);
        int typeResId = me.edgan.redditslide.ContentType.getContentID(type, sub.isNsfw());
        holder.type.setText(context.getString(typeResId));

        // Subreddit
        holder.subreddit.setText("• /r/" + sub.getSubredditName());

        // URL
        String displayUrl = sub.getUrl();
        if (displayUrl != null) {
            displayUrl = displayUrl.replaceFirst("^https?://(www\\.)?", "");
            holder.url.setText(displayUrl);
            holder.url.setVisibility(android.view.View.VISIBLE);
        } else {
            holder.url.setVisibility(android.view.View.GONE);
        }

        // Thumbnail — prefer preview source, fall back to thumbnail URL
        String thumbUrl = null;
        if (sub.getThumbnails() != null && sub.getThumbnails().getSource() != null) {
            thumbUrl = sub.getThumbnails().getSource().getUrl();
        }
        if ((thumbUrl == null || thumbUrl.isEmpty()) && sub.getThumbnail() != null
                && sub.getThumbnailType() == Submission.ThumbnailType.URL) {
            thumbUrl = sub.getThumbnail();
        }

        if (thumbUrl != null && !thumbUrl.isEmpty()) {
            ImageLoader loader = ImageLoaderUtils.imageLoader;
            if (loader != null && loader.isInited()) {
                loader.displayImage(thumbUrl, holder.thumbnail, THUMB_OPTIONS);
            }
        } else {
            holder.thumbnail.setImageDrawable(null);
        }

        // Click listeners
        holder.infoArea.setOnClickListener(v -> {
            if (context instanceof android.app.Activity) {
                me.edgan.redditslide.OpenRedditLink.openUrl(context, "https://reddit.com" + sub.getPermalink(), false);
            }
        });

        holder.thumbnail.setOnClickListener(v -> {
            if (!(context instanceof android.app.Activity)) return;
            android.app.Activity activity = (android.app.Activity) context;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            switch (type) {
                case IMAGE:
                case DEVIANTART:
                case XKCD:
                case IMGUR:
                    me.edgan.redditslide.util.SubmissionThumbnailHelper.openImage(type, activity, sub, null, pos);
                    break;
                case GIF:
                case VREDDIT_DIRECT:
                case VREDDIT_REDIRECT:
                    me.edgan.redditslide.util.SubmissionThumbnailHelper.openGif(activity, sub, pos);
                    break;
                case ALBUM:
                    if (me.edgan.redditslide.SettingValues.albumSwipe) {
                        Intent i = new Intent(activity, me.edgan.redditslide.Activities.AlbumPager.class);
                        i.putExtra(me.edgan.redditslide.Activities.AlbumPager.SUBREDDIT, sub.getSubredditName());
                        i.putExtra(me.edgan.redditslide.Activities.Album.EXTRA_URL, sub.getUrl());
                        i.putExtra(me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE, sub.getTitle());
                        activity.startActivity(i);
                        activity.overridePendingTransition(R.anim.slideright, R.anim.fade_out);
                    } else {
                        Intent i = new Intent(activity, me.edgan.redditslide.Activities.Album.class);
                        i.putExtra(me.edgan.redditslide.Activities.Album.SUBREDDIT, sub.getSubredditName());
                        i.putExtra(me.edgan.redditslide.Activities.Album.EXTRA_URL, sub.getUrl());
                        i.putExtra(me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE, sub.getTitle());
                        activity.startActivity(i);
                        activity.overridePendingTransition(R.anim.slideright, R.anim.fade_out);
                    }
                    break;
                case REDDIT_GALLERY:
                    Intent i;
                    if (me.edgan.redditslide.SettingValues.albumSwipe) {
                        i = new Intent(activity, me.edgan.redditslide.Activities.RedditGalleryPager.class);
                        i.putExtra(me.edgan.redditslide.Activities.AlbumPager.SUBREDDIT, sub.getSubredditName());
                    } else {
                        i = new Intent(activity, me.edgan.redditslide.Activities.RedditGallery.class);
                        i.putExtra(me.edgan.redditslide.Activities.Album.SUBREDDIT, sub.getSubredditName());
                    }
                    i.putExtra(me.edgan.redditslide.Activities.RedditGallery.SUBREDDIT, sub.getSubredditName());
                    i.putExtra(me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE, sub.getTitle());

                    java.util.ArrayList<me.edgan.redditslide.Activities.GalleryImage> urls = new java.util.ArrayList<>();
                    com.fasterxml.jackson.databind.JsonNode dataNode = sub.getDataNode();
                    if (dataNode.has("gallery_data")) {
                        me.edgan.redditslide.util.JsonUtil.getGalleryData(dataNode, urls);
                    } else if (dataNode.has("crosspost_parent_list")) {
                        com.fasterxml.jackson.databind.JsonNode crosspost_parent = dataNode.get("crosspost_parent_list").get(0);
                        if (crosspost_parent.has("gallery_data")) {
                            me.edgan.redditslide.util.JsonUtil.getGalleryData(crosspost_parent, urls);
                        }
                    }
                    Bundle urlsBundle = new Bundle();
                    urlsBundle.putSerializable(me.edgan.redditslide.Activities.RedditGallery.GALLERY_URLS, urls);
                    i.putExtras(urlsBundle);
                    me.edgan.redditslide.DataShare.sharedSubmission = sub;
                    activity.startActivity(i);
                    activity.overridePendingTransition(R.anim.slideright, R.anim.fade_out);
                    break;
                case VIDEO:
                case STREAMABLE:
                case EMBEDDED:
                default:
                    me.edgan.redditslide.util.LinkUtil.openUrl(sub.getUrl(), me.edgan.redditslide.Visuals.Palette.getStatusBarColor(), activity);
                    break;
            }
        });

        // Checkbox — suppress listener while rebinding to avoid state-change side effects
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(item.state == BatchDownloadItem.State.CHECKED
                || item.state == BatchDownloadItem.State.DONE);
        holder.checkbox.setOnCheckedChangeListener((btn, checked) -> {
            item.state = checked ? BatchDownloadItem.State.CHECKED
                                 : BatchDownloadItem.State.UNCHECKED;
        });

        // Error icon (Phase 4)
        holder.errorIcon.setVisibility(
                item.state == BatchDownloadItem.State.FAILED ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Append additional items (from "Load more" pages) and refresh the visible list. */
    public void appendItems(List<BatchDownloadItem> newItems) {
        allItems.addAll(newItems);
        rebuildVisibleItems();
        notifyDataSetChanged();
    }

    /**
     * Set the filter flags.
     * Rebuilds the visible list and notifies the adapter.
     */
    public void setFilter(boolean showImages, boolean showVideos, boolean showVoted, boolean showViewed) {
        this.showImages = showImages;
        this.showVideos = showVideos;
        this.showVoted = showVoted;
        this.showViewed = showViewed;
        rebuildVisibleItems();
        notifyDataSetChanged();
    }

    /**
     * Toggle select-all / deselect-all across currently visible items.
     * If any visible item is UNCHECKED → check all; otherwise → uncheck all.
     */
    public void toggleSelectAll() {
        boolean anyUnchecked = false;
        for (BatchDownloadItem item : visibleItems) {
            if (item.state == BatchDownloadItem.State.UNCHECKED) {
                anyUnchecked = true;
                break;
            }
        }
        BatchDownloadItem.State newState = anyUnchecked
                ? BatchDownloadItem.State.CHECKED
                : BatchDownloadItem.State.UNCHECKED;
        for (BatchDownloadItem item : visibleItems) {
            item.state = newState;
        }
        notifyDataSetChanged();
    }

    /** Returns items that are CHECKED and currently visible. Used by Phase 4 to build the queue. */
    public List<BatchDownloadItem> getCheckedVisibleItems() {
        List<BatchDownloadItem> result = new ArrayList<>();
        for (BatchDownloadItem item : visibleItems) {
            if (item.state == BatchDownloadItem.State.CHECKED) {
                result.add(item);
            }
        }
        return result;
    }

    /** Returns all loaded items (visible or filtered out) for cross-page hash seeding. */
    public List<BatchDownloadItem> getAllItems() {
        return allItems;
    }

    /** Mark an item as failed by submission ID (called from Phase 4 broadcast receiver). */
    public void markFailed(String submissionId) {
        for (int i = 0; i < visibleItems.size(); i++) {
            if (visibleItems.get(i).submission.getId().equals(submissionId)) {
                visibleItems.get(i).state = BatchDownloadItem.State.FAILED;
                notifyItemChanged(i);
                return;
            }
        }
    }

    /** Mark an item as done by submission ID. */
    public void markDone(String submissionId) {
        for (int i = 0; i < visibleItems.size(); i++) {
            if (visibleItems.get(i).submission.getId().equals(submissionId)) {
                visibleItems.get(i).state = BatchDownloadItem.State.DONE;
                notifyItemChanged(i);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void rebuildVisibleItems() {
        visibleItems = new ArrayList<>();
        for (BatchDownloadItem item : allItems) {
            // Type filter
            boolean typeMatch = (item.isImage && showImages) || (!item.isImage && showVideos);
            if (!typeMatch) continue;

            // Voted filter: if showVoted is false, hide voted posts
            if (!showVoted && item.isVoted) continue;

            // Viewed filter: if showViewed is false, hide viewed posts
            if (!showViewed && item.isViewed) continue;

            visibleItems.add(item);
        }
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final ImageView thumbnail;
        final View infoArea;
        final TextView title;
        final TextView score;
        final TextView type;
        final TextView subreddit;
        final TextView url;
        final ImageView errorIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox  = itemView.findViewById(R.id.batch_dl_checkbox);
            thumbnail = itemView.findViewById(R.id.batch_dl_thumbnail);
            infoArea  = itemView.findViewById(R.id.batch_dl_info_area);
            title     = itemView.findViewById(R.id.batch_dl_title);
            score     = itemView.findViewById(R.id.batch_dl_score);
            type      = itemView.findViewById(R.id.batch_dl_type);
            subreddit = itemView.findViewById(R.id.batch_dl_subreddit);
            url       = itemView.findViewById(R.id.batch_dl_url);
            errorIcon = itemView.findViewById(R.id.batch_dl_error_icon);
        }
    }
}
