package me.edgan.redditslide.Adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

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

        // Subreddit
        holder.subreddit.setText("/r/" + sub.getSubredditName());

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
     * Set the type-filter flags. Both true = show all.
     * Rebuilds the visible list and notifies the adapter.
     */
    public void setTypeFilter(boolean showImages, boolean showVideos) {
        this.showImages = showImages;
        this.showVideos = showVideos;
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
            if (item.isImage && showImages) visibleItems.add(item);
            else if (!item.isImage && showVideos) visibleItems.add(item);
        }
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final ImageView thumbnail;
        final TextView title;
        final TextView subreddit;
        final ImageView errorIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox  = itemView.findViewById(R.id.batch_dl_checkbox);
            thumbnail = itemView.findViewById(R.id.batch_dl_thumbnail);
            title     = itemView.findViewById(R.id.batch_dl_title);
            subreddit = itemView.findViewById(R.id.batch_dl_subreddit);
            errorIcon = itemView.findViewById(R.id.batch_dl_error_icon);
        }
    }
}
