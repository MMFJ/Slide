package me.edgan.redditslide.Fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Adapters.BatchDownloadAdapter;
import me.edgan.redditslide.Adapters.BatchDownloadItem;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Services.BatchDownloadService;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.Vote;
import me.edgan.redditslide.util.ThumbnailDHash;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;
import net.dean.jraw.paginators.UserProfilePaginator;

/**
 * Batch Download tab on the user Profile screen.
 *
 * Phase 1: Tab shell — tappable prompt.
 * Phase 2: Post fetching, deduplication, media list with checkboxes, Load More.
 * Phase 3: Action bar — Type filter, Select All, scroll-hide.
 * Phase 4: BatchDownloadService integration, notifications, error feedback.
 */
public class BatchDownloadFragment extends Fragment {

    private static final String ARG_USERNAME = "username";

    // ---- views ----
    @Nullable private View promptView;
    @Nullable private View loadingView;
    @Nullable private SwipeRefreshLayout swipeRefreshLayout;
    @Nullable private RecyclerView recyclerView;
    @Nullable private View actionBar;
    @Nullable private Button typBtn;        // opens Type filter dialog
    @Nullable private ImageButton selectAllBtn;
    @Nullable private ImageButton downloadBtn;
    @Nullable private TextView downloadProgress;
    @Nullable private Button loadMoreBtn;

    // ---- action-bar filter state ----
    private boolean showImages = true;
    private boolean showVideos = true;
    private boolean showVoted = true;
    private boolean showViewed = true;

    // ---- mark downloads state ----
    private boolean markRead = true;
    private boolean markUpvote = false;

    // ---- fetch state ----
    private String username = "";
    @Nullable private BatchDownloadAdapter adapter;
    @Nullable private UserProfilePaginator paginator; // retained between "Load more" taps
    @Nullable private FetchMediaPostsTask currentTask;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        username = (args != null) ? args.getString(ARG_USERNAME, "") : "";
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_batch_download, container, false);

        promptView   = v.findViewById(R.id.batch_dl_prompt);
        loadingView  = v.findViewById(R.id.batch_dl_loading);
        swipeRefreshLayout = v.findViewById(R.id.batch_dl_swipe_refresh);
        recyclerView = v.findViewById(R.id.batch_dl_list);
        actionBar    = v.findViewById(R.id.batch_dl_action_bar);

        if (SettingValues.prefs != null) {
            showImages = SettingValues.prefs.getBoolean("batch_dl_show_images", true);
            showVideos = SettingValues.prefs.getBoolean("batch_dl_show_videos", true);
            showVoted = SettingValues.prefs.getBoolean("batch_dl_show_voted", true);
            showViewed = SettingValues.prefs.getBoolean("batch_dl_show_viewed", true);
            markRead = SettingValues.prefs.getBoolean("batch_dl_mark_read", true);
            markUpvote = SettingValues.prefs.getBoolean("batch_dl_mark_upvote", false);
        }

        Context ctx = v.getContext();
        swipeRefreshLayout.setColorSchemeColors(Palette.getColors(username, ctx));
        swipeRefreshLayout.setProgressViewOffset(
                false,
                Constants.TAB_HEADER_VIEW_OFFSET - Constants.PTR_OFFSET_TOP,
                Constants.TAB_HEADER_VIEW_OFFSET + Constants.PTR_OFFSET_BOTTOM);
        swipeRefreshLayout.setOnRefreshListener(this::startFetch);
        typBtn       = v.findViewById(R.id.batch_dl_type_btn);
        selectAllBtn = v.findViewById(R.id.batch_dl_select_all);
        downloadBtn  = v.findViewById(R.id.batch_dl_download);
        downloadProgress = v.findViewById(R.id.batch_dl_download_progress);
        loadMoreBtn  = v.findViewById(R.id.batch_dl_load_more);

        ImageButton markBtn = v.findViewById(R.id.batch_dl_mark_btn);
        markBtn.setOnClickListener(view -> {
            if (getContext() == null) return;
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(getContext(), view);
            android.view.Menu menu = popup.getMenu();

            android.view.MenuItem titleItem = menu.add(android.view.Menu.NONE, 0, 0, R.string.batch_dl_mark_downloads);
            titleItem.setEnabled(false);

            android.view.MenuItem readItem = menu.add(android.view.Menu.NONE, 1, 1, R.string.batch_dl_read);
            readItem.setCheckable(true);
            readItem.setChecked(markRead);

            android.view.MenuItem upvoteItem = menu.add(android.view.Menu.NONE, 2, 2, R.string.batch_dl_upvote);
            upvoteItem.setCheckable(true);
            upvoteItem.setChecked(markUpvote);

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) {
                    markRead = !markRead;
                    item.setChecked(markRead);
                    if (SettingValues.prefs != null) {
                        SettingValues.prefs.edit().putBoolean("batch_dl_mark_read", markRead).apply();
                    }
                } else if (id == 2) {
                    markUpvote = !markUpvote;
                    item.setChecked(markUpvote);
                    if (SettingValues.prefs != null) {
                        SettingValues.prefs.edit().putBoolean("batch_dl_mark_upvote", markUpvote).apply();
                    }
                }
                return true;
            });
            popup.show();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Prompt tap → fetch first page
        promptView.setOnClickListener(view -> startFetch());

        // --- Phase 3: Type filter ---
        typBtn.setOnClickListener(view -> showTypeFilterDialog());

        // --- Phase 3: Select All ---
        selectAllBtn.setOnClickListener(view -> {
            if (adapter != null) adapter.toggleSelectAll();
        });

        // Action bar remains always visible; no scroll-hide listener needed

        // --- Phase 4: Start download service ---
        downloadBtn.setOnClickListener(view -> {
            if (adapter != null) {
                List<BatchDownloadItem> queue = adapter.getCheckedVisibleItems();
                if (queue.isEmpty()) {
                    Toast.makeText(getContext(), R.string.batch_dl_no_media, Toast.LENGTH_SHORT).show();
                    return;
                }

                // Populate the static queue
                BatchDownloadService.downloadQueue = queue;

                // Start the service
                Context context = v.getContext();
                Intent startIntent = new Intent(context, BatchDownloadService.class);
                startIntent.setAction(BatchDownloadService.ACTION_START);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent);
                } else {
                    context.startService(startIntent);
                }

                if (downloadBtn != null) {
                    downloadBtn.setEnabled(false);
                }
                if (downloadProgress != null) {
                    downloadProgress.setText("0/" + queue.size());
                    downloadProgress.setVisibility(View.VISIBLE);
                }
            }
        });

        return v;
    }

    // -------------------------------------------------------------------------
    // Phase 4 - Broadcast Receiver for Service Updates
    // -------------------------------------------------------------------------

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            String action = intent.getAction();
            if (BatchDownloadService.BROADCAST_PROGRESS.equals(action)) {
                int progress = intent.getIntExtra(BatchDownloadService.EXTRA_PROGRESS, 0);
                int total = intent.getIntExtra(BatchDownloadService.EXTRA_TOTAL, 0);
                if (downloadProgress != null) {
                    downloadProgress.setText(progress + "/" + total);
                    downloadProgress.setVisibility(View.VISIBLE);
                }
            } else if (BatchDownloadService.BROADCAST_ITEM_DONE.equals(action)) {
                String id = intent.getStringExtra(BatchDownloadService.EXTRA_SUBMISSION_ID);
                if (id != null && adapter != null) {
                    adapter.markDone(id);
                    for (BatchDownloadItem item : adapter.getAllItems()) {
                        if (id.equals(item.submission.getId())) {
                            if (markRead) {
                                HasSeen.addSeen(item.submission.getFullName());
                            }
                            if (markUpvote && getActivity() != null) {
                                ActionStates.setVoteDirection(item.submission, VoteDirection.UPVOTE);
                                new Vote(true, null, getActivity()).execute(item.submission);
                            }
                            break;
                        }
                    }
                }
            } else if (BatchDownloadService.BROADCAST_ITEM_FAILED.equals(action)) {
                String id = intent.getStringExtra(BatchDownloadService.EXTRA_SUBMISSION_ID);
                if (id != null && adapter != null) {
                    adapter.markFailed(id);
                }
            } else if (BatchDownloadService.BROADCAST_FINISHED.equals(action)) {
                if (downloadBtn != null) {
                    downloadBtn.setEnabled(true);
                }
                if (downloadProgress != null) {
                    downloadProgress.setVisibility(View.GONE);
                }
                if (getContext() != null) {
                    Toast.makeText(getContext(), R.string.info_photo_saved, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BatchDownloadService.BROADCAST_PROGRESS);
            filter.addAction(BatchDownloadService.BROADCAST_ITEM_DONE);
            filter.addAction(BatchDownloadService.BROADCAST_ITEM_FAILED);
            filter.addAction(BatchDownloadService.BROADCAST_FINISHED);
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(downloadReceiver, filter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(downloadReceiver);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
    }

    // -------------------------------------------------------------------------
    // Fetch logic
    // -------------------------------------------------------------------------

    private void startFetch() {
        showState(State.LOADING);
        paginator = null; // reset paginator on a fresh fetch
        currentTask = new FetchMediaPostsTask(false);
        currentTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void loadMorePage() {
        loadMorePages(1);
    }

    private void loadMorePages(int count) {
        if (currentTask != null) return; // already fetching
        if (paginator == null || !paginator.hasNext()) return;
        currentTask = new FetchMediaPostsTask(true, count);
        currentTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    // -------------------------------------------------------------------------
    // State helpers
    // -------------------------------------------------------------------------

    private enum State { IDLE, LOADING, READY }

    private void showState(State state) {
        if (promptView != null) {
            promptView.setVisibility(state == State.IDLE ? View.VISIBLE : View.GONE);
        }
        if (loadingView != null) {
            loadingView.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setVisibility(state == State.READY ? View.VISIBLE : View.GONE);
        }
        // Action bar shown only when there is content
        if (state == State.READY && adapter != null && adapter.getItemCount() > 0) {
            if (actionBar != null) {
                actionBar.setVisibility(View.VISIBLE);
            }
        }
    }

    // -------------------------------------------------------------------------
    // AsyncTask — fetches one page, deduplicates, appends to adapter
    // -------------------------------------------------------------------------

    private class FetchMediaPostsTask extends AsyncTask<Void, String, List<BatchDownloadItem>> {

        private final boolean appendMode; // true = Load More, false = first fetch
        private final int pagesToFetch;   // how many pages to pull in one task
        private boolean hasMore = false;

        FetchMediaPostsTask(boolean appendMode) {
            this(appendMode, 1);
        }

        FetchMediaPostsTask(boolean appendMode, int pagesToFetch) {
            this.appendMode = appendMode;
            this.pagesToFetch = pagesToFetch;
        }

        @Override
        protected void onPreExecute() {
            if (appendMode) {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(true);
                }
                if (loadMoreBtn != null) {
                    loadMoreBtn.setEnabled(true);
                    loadMoreBtn.setText("Loading 1/" + pagesToFetch + " ✕");
                }
            }
        }

        @Override
        protected List<BatchDownloadItem> doInBackground(Void... params) {
            try {
                if (!appendMode || paginator == null) {
                    // Create a fresh paginator honoring the current profile sort
                    paginator = new UserProfilePaginator(
                            Authentication.reddit, "submitted", username);
                    paginator.setSorting(
                            Profile.profSort != null ? Profile.profSort : Sorting.NEW);
                    paginator.setTimePeriod(
                            Profile.profTime != null ? Profile.profTime : TimePeriod.ALL);
                }

                if (!paginator.hasNext()) return new ArrayList<>();

                // Seed list of already loaded items (from prior pages) to support
                // cross-page URL/visual duplicate checks with read/voted state promotion
                List<BatchDownloadItem> existingItems = new ArrayList<>();
                if (appendMode && adapter != null) {
                    existingItems.addAll(adapter.getAllItems());
                }

                // Fetch up to pagesToFetch pages in a single background task
                LinkedHashMap<String, BatchDownloadItem> deduped = new LinkedHashMap<>();
                for (int p = 0; p < pagesToFetch && paginator.hasNext() && !isCancelled(); p++) {
                    publishProgress(String.valueOf(p + 1), String.valueOf(pagesToFetch));
                    List<Contribution> page = paginator.next();
                    hasMore = paginator.hasNext();

                    for (Contribution c : page) {
                        if (isCancelled()) break;
                        if (!(c instanceof Submission)) continue;

                        Submission sub = (Submission) c;
                        ContentType.Type type = ContentType.getContentType(sub);
                        if (!isDownloadableType(type)) continue;

                        // --- URL-based deduplication (fast, no network) ---
                        String key = deriveDedupeKey(sub, type);

                        // 1. URL duplicate within the same batch (current page's fetch results)
                        BatchDownloadItem existingInBatch = deduped.get(key);
                        if (existingInBatch != null) {
                            if (!existingInBatch.isVoted && ActionStates.getVoteDirection(sub) != VoteDirection.NO_VOTE) {
                                existingInBatch.isVoted = true;
                            }
                            if (!existingInBatch.isViewed && HasSeen.getSeen(sub)) {
                                existingInBatch.isViewed = true;
                            }
                            continue;
                        }

                        // 2. URL duplicate across different batches (already in the adapter)
                        BatchDownloadItem existingInAdapter = null;
                        for (BatchDownloadItem item : existingItems) {
                            if (item.dedupeKey.equals(key)) {
                                existingInAdapter = item;
                                break;
                            }
                        }
                        if (existingInAdapter != null) {
                            if (!existingInAdapter.isVoted && ActionStates.getVoteDirection(sub) != VoteDirection.NO_VOTE) {
                                existingInAdapter.isVoted = true;
                            }
                            if (!existingInAdapter.isViewed && HasSeen.getSeen(sub)) {
                                existingInAdapter.isViewed = true;
                            }
                            continue;
                        }

                        // --- Thumbnail perceptual-hash deduplication ---
                        // Prefer the full preview source — the same image displayed
                        // in the list — over the small getThumbnail() fallback.
                        // Reddit CDN preview URLs may use HTML-encoded ampersands; decode them.
                        String thumbUrl = null;
                        if (sub.getThumbnails() != null
                                && sub.getThumbnails().getSource() != null) {
                            thumbUrl = sub.getThumbnails().getSource().getUrl();
                            if (thumbUrl != null) thumbUrl = thumbUrl.replace("&amp;", "&");
                        }
                        if (thumbUrl == null || thumbUrl.isEmpty()) {
                            // Falls through to placeholder detection inside fetchAndHash
                            thumbUrl = sub.getThumbnail();
                        }
                        String hash = ThumbnailDHash.fetchAndHash(thumbUrl);

                        // Hard-skip items whose thumbnail is Imgur's "image not found" page
                        if (ThumbnailDHash.isImgurRemovedImage(hash)) {
                            continue;
                        }

                        // 3. Visual duplicate within the same batch
                        boolean visualDupInBatch = false;
                        if (hash != null) {
                            for (BatchDownloadItem item : deduped.values()) {
                                if (item.thumbnailHash != null && ThumbnailDHash.isDuplicate(hash, item.thumbnailHash, ThumbnailDHash.DEFAULT_THRESHOLD)) {
                                    if (!item.isVoted && ActionStates.getVoteDirection(sub) != VoteDirection.NO_VOTE) {
                                        item.isVoted = true;
                                    }
                                    if (!item.isViewed && HasSeen.getSeen(sub)) {
                                        item.isViewed = true;
                                    }
                                    visualDupInBatch = true;
                                    break;
                                }
                            }
                        }
                        if (visualDupInBatch) continue;

                        // 4. Visual duplicate across different batches (already in the adapter)
                        boolean visualDupInAdapter = false;
                        if (hash != null) {
                            for (BatchDownloadItem item : existingItems) {
                                if (item.thumbnailHash != null && ThumbnailDHash.isDuplicate(hash, item.thumbnailHash, ThumbnailDHash.DEFAULT_THRESHOLD)) {
                                    if (!item.isVoted && ActionStates.getVoteDirection(sub) != VoteDirection.NO_VOTE) {
                                        item.isVoted = true;
                                    }
                                    if (!item.isViewed && HasSeen.getSeen(sub)) {
                                        item.isViewed = true;
                                    }
                                    visualDupInAdapter = true;
                                    break;
                                }
                            }
                        }
                        if (visualDupInAdapter) continue;

                        // If not a duplicate at all, add to current batch
                        BatchDownloadItem item = new BatchDownloadItem(sub, type, key);
                        item.thumbnailHash = hash;
                        deduped.put(key, item);
                    }
                }
                return new ArrayList<>(deduped.values());

            } catch (Exception e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (loadMoreBtn != null && values != null && values.length >= 2) {
                loadMoreBtn.setText("Loading " + values[0] + "/" + values[1] + " ✕");
            }
        }

        @Override
        protected void onCancelled(List<BatchDownloadItem> results) {
            currentTask = null;
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            if (adapter != null && results != null && !results.isEmpty()) {
                adapter.appendItems(results);
            }
            showState(State.READY);
            updateLoadMoreFooter(hasMore);
        }

        @Override
        protected void onPostExecute(List<BatchDownloadItem> results) {
            if (getActivity() == null || getActivity().isFinishing()) return;

            currentTask = null;

            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }

            if (!appendMode) {
                // First fetch
                if (results.isEmpty()) {
                    // Show "no media found" in the prompt area
                    if (promptView != null) {
                        TextView promptText = promptView.findViewById(R.id.batch_dl_prompt_text);
                        if (promptText != null) {
                            promptText.setText(R.string.batch_dl_no_media);
                        }
                    }
                    showState(State.IDLE);
                    return;
                }
                Context ctx = getContext();
                if (ctx != null) {
                    adapter = new BatchDownloadAdapter(ctx, results);
                    adapter.setFilter(showImages, showVideos, showVoted, showViewed);
                    if (recyclerView != null) {
                        recyclerView.setAdapter(adapter);
                    }
                }
            } else {
                // Load More append
                if (adapter != null && !results.isEmpty()) {
                    adapter.appendItems(results);
                    showActionBar();
                }
            }

            showState(State.READY);
            updateLoadMoreFooter(hasMore);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Type filter dialog
    // -------------------------------------------------------------------------

    private void showTypeFilterDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.batch_dl_type_filter)
                .setMultiChoiceItems(
                        new CharSequence[]{
                                getString(R.string.batch_dl_images),
                                getString(R.string.batch_dl_videos),
                                getString(R.string.batch_dl_voted),
                                getString(R.string.batch_dl_viewed)
                        },
                        new boolean[]{ showImages, showVideos, showVoted, showViewed },
                        (dialog, which, checked) -> {
                            if (which == 0) {
                                showImages = checked;
                                if (SettingValues.prefs != null) SettingValues.prefs.edit().putBoolean("batch_dl_show_images", checked).apply();
                            } else if (which == 1) {
                                showVideos = checked;
                                if (SettingValues.prefs != null) SettingValues.prefs.edit().putBoolean("batch_dl_show_videos", checked).apply();
                            } else if (which == 2) {
                                showVoted = checked;
                                if (SettingValues.prefs != null) SettingValues.prefs.edit().putBoolean("batch_dl_show_voted", checked).apply();
                            } else if (which == 3) {
                                showViewed = checked;
                                if (SettingValues.prefs != null) SettingValues.prefs.edit().putBoolean("batch_dl_show_viewed", checked).apply();
                            }

                            if (adapter != null) {
                                adapter.setFilter(showImages, showVideos, showVoted, showViewed);
                            }
                        })
                .setPositiveButton(R.string.btn_ok, (d, w) -> updateTypeBtnLabel())
                .setOnCancelListener(d -> updateTypeBtnLabel())
                .show();
    }

    /** Updates the Type button label to reflect the active filter. */
    private void updateTypeBtnLabel() {
        if (typBtn == null) return;
        if (showImages && showVideos && showVoted && showViewed) {
            typBtn.setText(R.string.batch_dl_type_filter);
        } else {
            List<String> active = new ArrayList<>();
            if (!showImages) active.add("No Img");
            if (!showVideos) active.add("No Vid");
            if (!showVoted)  active.add("No Voted");
            if (!showViewed) active.add("No Viewed");

            if (active.isEmpty()) {
                typBtn.setText(R.string.batch_dl_type_filter);
            } else {
                typBtn.setText(getString(R.string.batch_dl_type_filter) + ": " + active.size() + " hidden");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Scroll-hide listener for the bottom action bar
    // -------------------------------------------------------------------------

    /**
     * Slides the action bar down (off-screen) when scrolling down,
     * and back up when scrolling up — the inverse of toolbar hide behaviour.
     */
    /**
     * Force-shows the action bar (resets translation to 0).
     */
    private void showActionBar() {
        if (actionBar != null) {
            actionBar.setVisibility(View.VISIBLE);
            actionBar.setTranslationY(0);
        }
    }

    private void setLoadMoreButtonDefaultText() {
        if (loadMoreBtn == null) return;
        SpannableString text = new SpannableString("Load 4 pages ↓\n(hold for all)");
        text.setSpan(new RelativeSizeSpan(0.7f), 15, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        loadMoreBtn.setText(text);
    }

    private void updateLoadMoreFooter(boolean hasMore) {
        if (typBtn != null) {
            typBtn.setOnClickListener(v -> showTypeFilterDialog());
            updateTypeBtnLabel();
        }

        if (loadMoreBtn == null) return;

        if (hasMore) {
            loadMoreBtn.setVisibility(View.VISIBLE);
            loadMoreBtn.setEnabled(true);
            setLoadMoreButtonDefaultText();
            loadMoreBtn.setOnClickListener(v -> {
                if (currentTask != null) {
                    currentTask.cancel(true);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Loading cancelled", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    loadMorePages(4);
                }
            });
            loadMoreBtn.setOnLongClickListener(v -> {
                if (currentTask != null) {
                    return true;
                }
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Loading all remaining pages (up to 25)…", Toast.LENGTH_SHORT).show();
                }
                loadMorePages(25);
                return true;
            });
        } else {
            loadMoreBtn.setVisibility(View.GONE);
        }
    }

    // -------------------------------------------------------------------------
    // Deduplication helpers
    // -------------------------------------------------------------------------

    private static boolean isDownloadableType(ContentType.Type type) {
        switch (type) {
            case IMAGE:
            case GIF:
            case IMGUR:
            case ALBUM:
            case REDDIT_GALLERY:
            case DEVIANTART:
            case XKCD:
            case TUMBLR:
            case VREDDIT_DIRECT:
            case VREDDIT_REDIRECT:
            case STREAMABLE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Derives a canonical deduplication key for a submission.
     * <ul>
     *   <li>REDDIT_GALLERY → submission ID</li>
     *   <li>All others     → lowercase URL, query string stripped</li>
     * </ul>
     */
    private static String deriveDedupeKey(Submission sub, ContentType.Type type) {
        String id = sub.getId();
        if (type == ContentType.Type.REDDIT_GALLERY || id == null) {
            return id != null ? id : "";
        }
        String url = sub.getUrl();
        if (url == null) return id;
        // Strip query string and force lower case
        int q = url.indexOf('?');
        if (q > 0) url = url.substring(0, q);
        url = url.toLowerCase(Locale.ENGLISH);
        // Strip common prefixes to ensure different permutations of the same host match
        url = url.replaceFirst("^https?://", "");
        url = url.replaceFirst("^(www\\.|m\\.|i\\.)", "");
        return url;
    }
}
