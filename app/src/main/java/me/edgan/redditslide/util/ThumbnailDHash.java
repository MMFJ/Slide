package me.edgan.redditslide.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.TextUtils;

import me.edgan.redditslide.Reddit;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.InputStream;

/**
 * Perceptual duplicate detection using the Difference Hash (dHash) algorithm.
 *
 * <p>The algorithm:
 * <ol>
 *   <li>Scale the image down to 9×8 pixels.</li>
 *   <li>Convert to grayscale.</li>
 *   <li>For each row, compare each pixel to its right neighbour (8 comparisons × 8 rows = 64 bits).
 *   </li>
 *   <li>Encode the 64-bit result as a 16-character hex string.</li>
 * </ol>
 *
 * <p>Two images are considered perceptual duplicates if their Hamming distance is ≤ the supplied
 * threshold (recommended value: 4).
 */
public class ThumbnailDHash {

    /** Recommended Hamming-distance threshold for "same image" detection. */
    public static final int DEFAULT_THRESHOLD = 4;

    /** Reddit placeholder values that carry no image content — skip hashing these. */
    private static final String[] SKIP_THUMBNAILS = {
        "self", "default", "nsfw", "spoiler", "image", ""
    };

    // Bitmap decode options: sub-sample aggressively; we only need a tiny image.
    private static final BitmapFactory.Options DECODE_OPTS;
    static {
        DECODE_OPTS = new BitmapFactory.Options();
        DECODE_OPTS.inSampleSize = 4; // reduce memory pressure on large thumbnails
    }

    private ThumbnailDHash() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Downloads the thumbnail at {@code url} and computes its dHash.
     *
     * @return 16-char hex hash string, or {@code null} on any failure.
     */
    public static String fetchAndHash(String url) {
        if (TextUtils.isEmpty(url) || isSkippedThumbnail(url)) return null;
        try {
            Request request = new Request.Builder().url(url).build();
            Response response = Reddit.client.newCall(request).execute();
            if (!response.isSuccessful()) return null;
            ResponseBody body = response.body();
            if (body == null) return null;
            try (InputStream is = body.byteStream()) {
                Bitmap raw = BitmapFactory.decodeStream(is, null, DECODE_OPTS);
                if (raw == null) return null;
                String hash = getDHash(raw);
                raw.recycle();
                return hash;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Computes the dHash of an already-decoded bitmap.
     *
     * @return 16-char hex string, or {@code null} if the bitmap is unusable.
     */
    public static String getDHash(Bitmap bitmap) {
        if (bitmap == null) return null;
        // Scale to 9×8 so we get 8 horizontal comparisons per row.
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true);
        long hash = 0L;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int left  = toGray(scaled.getPixel(col,     row));
                int right = toGray(scaled.getPixel(col + 1, row));
                hash = (hash << 1) | (left < right ? 1L : 0L);
            }
        }
        if (scaled != bitmap) scaled.recycle();
        return String.format("%016x", hash);
    }

    /**
     * Returns the Hamming distance (number of differing bits) between two dHash strings.
     *
     * @return distance in [0, 64], or {@link Integer#MAX_VALUE} if either hash is null.
     */
    public static int hammingDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) return Integer.MAX_VALUE;
        if (hash1.length() != 16 || hash2.length() != 16) return Integer.MAX_VALUE;
        try {
            long a = Long.parseUnsignedLong(hash1, 16);
            long b = Long.parseUnsignedLong(hash2, 16);
            return Long.bitCount(a ^ b);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Returns {@code true} if {@code hash1} and {@code hash2} represent perceptually identical
     * images (Hamming distance ≤ {@code threshold}).
     */
    public static boolean isDuplicate(String hash1, String hash2, int threshold) {
        return hammingDistance(hash1, hash2) <= threshold;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Converts an ARGB pixel to its luminance value (0-255). */
    private static int toGray(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        // Standard ITU-R BT.601 luminance formula
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    /** Returns true for Reddit placeholder strings that should not be hashed. */
    private static boolean isSkippedThumbnail(String url) {
        for (String skip : SKIP_THUMBNAILS) {
            if (skip.equalsIgnoreCase(url)) return true;
        }
        return false;
    }
}
