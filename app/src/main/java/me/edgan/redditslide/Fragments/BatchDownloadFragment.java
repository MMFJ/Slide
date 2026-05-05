package me.edgan.redditslide.Fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
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

import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Adapters.BatchDownloadAdapter;
import me.edgan.redditslide.Adapters.BatchDownloadItem;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Services.BatchDownloadService;

import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;
import net.dean.jraw.paginators.UserProfilePaginator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

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
    private View promptView;
    private View loadingView;
    private TextView loadingText;
    private RecyclerView recyclerView;
    private View actionBar;
    private Button typBtn;        // opens Type filter dialog
    private ImageButton selectAllBtn;
    private Button downloadBtn;
    private Button loadMoreBtn;   // injected into the action bar in updateLoadMoreFooter

    // ---- action-bar filter state ----
    private boolean showImages = true;
    private boolean showVideos = true;

    // ---- fetch state ----
    private String username;
    private BatchDownloadAdapter adapter;
    private UserProfilePaginator paginator; // retained between "Load more" taps
    private FetchMediaPostsTask currentTask;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        username = (args != null) ? args.getString(ARG_USERNAME, "") : "";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_batch_download, container, false);

        promptView   = v.findViewById(R.id.batch_dl_prompt);
        loadingView  = v.findViewById(R.id.batch_dl_loading);
        loadingText  = v.findViewById(R.id.batch_dl_loading_text);
        recyclerView = v.findViewById(R.id.batch_dl_list);
        actionBar    = v.findViewById(R.id.batch_dl_action_bar);
        typBtn       = v.findViewById(R.id.batch_dl_type_btn);
        selectAllBtn = v.findViewById(R.id.batch_dl_select_all);
        downloadBtn  = v.findViewById(R.id.batch_dl_download);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Prompt tap → fetch first page
        promptView.setOnClickListener(view -> startFetch());

        // --- Phase 3: Type filter ---
        typBtn.setOnClickListener(view -> showTypeFilterDialog());

        // --- Phase 3: Select All ---
        selectAllBtn.setOnClickListener(view -> {
            if (adapter != null) adapter.toggleSelectAll();
        });

        // --- Phase 3: scroll-hide on RecyclerView ---
        recyclerView.addOnScrollListener(buildScrollHideListener());

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
                Intent startIntent = new Intent(getContext(), BatchDownloadService.class);
                startIntent.setAction(BatchDownloadService.ACTION_START);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    getContext().startForegroundService(startIntent);
                } else {
                    getContext().startService(startIntent);
                }
                
                downloadBtn.setEnabled(false);
                downloadBtn.setText(getString(R.string.mediaview_downloading));
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
                if (downloadBtn != null) {
                    downloadBtn.setText(getString(R.string.mediaview_downloading) + " (" + progress + "/" + total + ")");
                }
            } else if (BatchDownloadService.BROADCAST_ITEM_DONE.equals(action)) {
                String id = intent.getStringExtra(BatchDownloadService.EXTRA_SUBMISSION_ID);
                if (id != null && adapter != null) {
                    adapter.markDone(id);
                }
            } else if (BatchDownloadService.BROADCAST_ITEM_FAILED.equals(action)) {
                String id = intent.getStringExtra(BatchDownloadService.EXTRA_SUBMISSION_ID);
                if (id != null && adapter != null) {
                    adapter.markFailed(id);
                }
            } else if (BatchDownloadService.BROADCAST_FINISHED.equals(action)) {
                if (downloadBtn != null) {
                    downloadBtn.setEnabled(true);
                    downloadBtn.setText(R.string.batch_dl_download);
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
        if (paginator == null || !paginator.hasNext()) return;
        currentTask = new FetchMediaPostsTask(true);
        currentTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    // -------------------------------------------------------------------------
    // State helpers
    // -------------------------------------------------------------------------

    private enum State { IDLE, LOADING, READY }

    private void showState(State state) {
        promptView.setVisibility(state == State.IDLE ? View.VISIBLE : View.GONE);
        loadingView.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(state == State.READY ? View.VISIBLE : View.GONE);
        // Action bar shown only when there is content
        if (state == State.READY && adapter != null && adapter.getItemCount() > 0) {
            actionBar.setVisibility(View.VISIBLE);
        }
    }

    // -------------------------------------------------------------------------
    // AsyncTask — fetches one page, deduplicates, appends to adapter
    // -------------------------------------------------------------------------

    private class FetchMediaPostsTask extends AsyncTask<Void, String, List<BatchDownloadItem>> {

        private final boolean appendMode; // true = Load More, false = first fetch
        private boolean hasMore = false;

        FetchMediaPostsTask(boolean appendMode) {
            this.appendMode = appendMode;
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

                // Fetch one page (one API call)
                List<Contribution> page = paginator.next();
                hasMore = paginator.hasNext();

                // Build a set of existing dedup keys so we can skip duplicates
                // across Load More appends
                LinkedHashMap<String, BatchDownloadItem> deduped = new LinkedHashMap<>();
                for (Contribution c : page) {
                    if (isCancelled()) break;
                    if (!(c instanceof Submission)) continue;

                    Submission sub = (Submission) c;
                    ContentType.Type type = ContentType.getContentType(sub);
                    if (!isDownloadableType(type)) continue;

                    String key = deriveDedupeKey(sub, type);
                    if (!deduped.containsKey(key)) {
                        deduped.put(key, new BatchDownloadItem(sub, type, key));
                    }
                }
                return new ArrayList<>(deduped.values());

            } catch (Exception e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        @Override
        protected void onPostExecute(List<BatchDownloadItem> results) {
            if (getActivity() == null || getActivity().isFinishing()) return;

            currentTask = null;

            if (!appendMode) {
                // First fetch
                if (results.isEmpty()) {
                    // Show "no media found" in the prompt area
                    TextView promptText = promptView.findViewById(R.id.batch_dl_prompt_text);
                    if (promptText != null) {
                        promptText.setText(R.string.batch_dl_no_media);
                    }
                    showState(State.IDLE);
                    return;
                }
                adapter = new BatchDownloadAdapter(getContext(), results);
                recyclerView.setAdapter(adapter);
            } else {
                // Load More append
                if (adapter != null && !results.isEmpty()) {
                    adapter.appendItems(results);
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
                                getString(R.string.batch_dl_videos)
                        },
                        new boolean[]{ showImages, showVideos },
                        (dialog, which, checked) -> {
                            if (which == 0) showImages = checked;
                            else           showVideos = checked;
                            if (adapter != null) {
                                adapter.setTypeFilter(showImages, showVideos);
                            }
                        })
                .setPositiveButton(R.string.btn_ok, (d, w) -> updateTypeBtnLabel())
                .setOnCancelListener(d -> updateTypeBtnLabel())
                .show();
    }

    /** Updates the Type button label to reflect the active filter. */
    private void updateTypeBtnLabel() {
        if (typBtn == null) return;
        if (showImages && showVideos) {
            typBtn.setText(R.string.batch_dl_type_filter);
        } else if (showImages) {
            typBtn.setText(getString(R.string.batch_dl_type_filter)
                    + ": " + getString(R.string.batch_dl_images));
        } else if (showVideos) {
            typBtn.setText(getString(R.string.batch_dl_type_filter)
                    + ": " + getString(R.string.batch_dl_videos));
        } else {
            typBtn.setText(getString(R.string.batch_dl_type_filter) + ": None");
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Scroll-hide listener for the bottom action bar
    // -------------------------------------------------------------------------

    /**
     * Slides the action bar down (off-screen) when scrolling down,
     * and back up when scrolling up — the inverse of toolbar hide behaviour.
     */
    private RecyclerView.OnScrollListener buildScrollHideListener() {
        return new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (actionBar.getVisibility() != View.VISIBLE) return;
                int barH = actionBar.getHeight();
                if (barH == 0) return;
                float current = actionBar.getTranslationY();
                float next = Math.max(0f, Math.min(barH, current + dy));
                actionBar.setTranslationY(next);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                if (actionBar.getVisibility() != View.VISIBLE) return;
                int barH = actionBar.getHeight();
                boolean hide = actionBar.getTranslationY() > barH * 0.6f;
                actionBar.animate()
                        .translationY(hide ? barH : 0)
                        .setInterpolator(new LinearInterpolator())
                        .setDuration(180);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Load More — proper footer button (replaces Phase 2 type-button hack)
    // -------------------------------------------------------------------------

    private void updateLoadMoreFooter(boolean hasMore) {
        // Re-wire the Type button to its normal job (filter dialog)
        // and manage a separate "Load more" button appended to the action bar.
        if (typBtn != null) {
            typBtn.setOnClickListener(v -> showTypeFilterDialog());
            updateTypeBtnLabel();
        }

        if (actionBar == null) return;

        // Find or create the Load More button inside the action bar
        if (loadMoreBtn == null) {
            loadMoreBtn = new Button(getContext(), null,
                    android.R.attr.borderlessButtonStyle);
            loadMoreBtn.setText("Load more ↓");
            // Insert before the spacer (index 1) so it sits after the Type button
            if (actionBar instanceof android.widget.LinearLayout) {
                ((android.widget.LinearLayout) actionBar).addView(loadMoreBtn, 1);
            }
        }

        if (hasMore) {
            loadMoreBtn.setVisibility(View.VISIBLE);
            loadMoreBtn.setEnabled(true);
            loadMoreBtn.setOnClickListener(v -> {
                loadMoreBtn.setEnabled(false);
                loadMorePage();
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
        if (type == ContentType.Type.REDDIT_GALLERY) {
            return sub.getId();
        }
        String url = sub.getUrl();
        if (url == null) return sub.getId();
        // Strip query string and force lower case
        int q = url.indexOf('?');
        if (q > 0) url = url.substring(0, q);
        return url.toLowerCase(Locale.ENGLISH);
    }
}
