# CHANGELOG

The old changelog can be read in the [CHANGELOG.md](https://github.com/Haptic-Apps/Slide/blob/master/CHANGELOG.md).

---

## MMFJ Fork Changes

7.4.8.11 / 2026-05-23
===================
* Batch DL: Compact download progress indicator — button now shows only "X/Y" (e.g. "3/47") during download, keeping it visible on small screens
* Batch DL: "Load more" button now loads 4 pages at a time instead of 1
* Batch DL: Infinite scroll — list automatically loads the next page when the user scrolls within 5 items of the bottom (matches Submissions tab behaviour)
* Batch DL: Sort button now appears on the Batch DL tab (same as Submissions/Overview tabs); changing sort resets and re-fetches the list
* Batch DL: Perceptual duplicate detection using the Difference Hash (dHash) algorithm — visually identical thumbnails are filtered out across pages even when URLs differ

7.4.8.10 / 2026-05-05
===================
* Added "Voted posts" and "Viewed posts" filters to the Batch DL tab
* Smart media deduplication: if any instance of a media item has been voted on or viewed, the deduplicated item is marked as such and respects active filters
* Both new filters are checked by default, ensuring all content remains visible until manually hidden
* Updated filter button label to show a summary of hidden items for better visibility

7.4.8.9 / 2026-05-05
===================
* Improved UX when loading more items: the action bar now automatically reappears after the list scrolls to newly added content
* GIFs are now correctly categorized as "Videos" in the Type Filter for easier bulk selection

7.4.8.8 / 2026-05-05
===================
* Improved media deduplication filtering in the Batch DL tab by canonicalizing subdomain prefixes (`m.`, `i.`, `www.`)
* Added abbreviated source URLs to the Batch DL item info bar for better transparency

7.4.8.7 / 2026-05-05
===================
* Added upvote count and media type indicators to Batch DL list items
* Implemented interactive "info bar" to view the associated Reddit post directly from the Batch DL tab
* Integrated app-standard media viewers when clicking Batch DL thumbnails (supports Images, GIFs, Videos, Albums, and Galleries)
* Added smooth scrolling to newly loaded items when clicking "Load more"
* Fixed build and compatibility issues with image viewers inside the list adapter
* Fixed immediate crash on Android 14+ when clicking Download due to missing Foreground Service permissions

7.4.8.6 / 2026-05-05
===================
* Added "Batch DL" tab to Reddit user profile screens (own profile and other users)
* Batch DL tab fetches submitted posts and finds all media (images, videos, and galleries)
* Deduplicates media across multiple subreddit cross-posts using URL/ID canonical keys
* Displays deduplicated media in a scrollable thumbnail list with checkboxes
* Type filter (Images / Videos) dialog for narrowing the media list
* Select All / deselect all toggle for bulk selection
* Paginated "Load more" support to conserve API calls — fetches one page at a time
* Fetch order follows the same sort/time preferences as the Overview and Submitted tabs
* Bottom action bar auto-hides when scrolling down and reappears when scrolling up
* Download button launches a foreground background service (BatchDownloadService)
* Downloads run sequentially to avoid rate-limit abuse
* Progress is tracked in a persistent notification with a Cancel action
* Per-item success/failure feedback: checkmark on success, error icon on failure
* Gallery posts expand to individual images/videos, each saved with sequential index
* Saves media to the configured storage location respecting the subfolder-by-subreddit setting

7.4.8.5 / 2026-05-05
===================
* Fixed a crash when interacting with the info bar in certain view modes (e.g. Fullscreen)
* Added safety checks for upvote toggle logic

7.4.8.4 / 2026-05-05
===================
* Improved post interaction in Card and List views:
    * The upvote icon and score in the info bar are now clickable to toggle upvotes
    * The upvote icon in the info bar now correctly tints orange when a post is upvoted

7.4.8.3 / 2026-05-05
===================
* Improved video player accessibility for tablets:
    * Added a large, semi-transparent (10% black) circular background behind the play/pause button
    * Dynamically resizes the play/pause touch target to 50% of the screen's short side
    * Makes play/pause much easier to reach one-handed on large devices

7.4.8.2 / 2026-05-05
===================
* Improved video player progress bar usability:
    * Increased bar thickness for better visibility
    * Increased touch target height for easier seeking
    * Larger scrubber ("dot") for better grab-ability

7.4.8.1 / 2026-05-05
===================
* Merged upstream 7.4.8
* Updated versioning base from 7.4.0 to 7.4.8

7.4.0.1 / 2026-02-23
===================
* Merged upstream 7.4.0: switched to stock image picker on Android 11+
* Changed app icon to hot pink to visually distinguish MMFJ build from upstream
* Updated versioning base from 7.3.9 to 7.4.0, reset MMFJ build number to 1

7.3.9.6 / 2026-02-15
===================
* Fixed image upvoting by adding DataShare.sharedSubmission in SubmissionThumbnailHelper
* Implemented zoom-level check for upvote gesture (only works when image fully zoomed out)
* Improved download gesture to require 2x more vertical movement (reduces back gesture conflicts)
* Added upstream remote to cygnusx-1-org/Slide repository
* Adopted 4-part versioning scheme (7.3.9.X) matching upstream
* Fixed changelog link in About screen to point to MMFJ/Slide
* Created VERSIONING.md documentation

7.3.8.742 / 2026-02-15
===================
* Fixed MediaView swipe-up gesture logic for images
* Fixed double toast notification when downloading videos
* Added UI refresh in SubmissionsView.onResume() to sync vote states
* Updated DataShare.sharedSubmission in PopulateNewsViewHolder

---

## Upstream (cygnusx-1-org/Slide) Changes

7.4.8 / 2026-4-20
============
* Fixed issues with missing/phathom "Load X more" comments
* Changed "Load X more" to "Load more comments"
* Added whitespace trimming for the client ID and redirect URI
* Added leading and trailing only whitespace trimming to the user agent
* Retitled "Settings | General | Overrides" to "Settings | General | App details"
* Removed HLS suppport for better DASH support for the edge case of a user posting videos in self posts

7.4.7 / 2026-4-15
============
* Added support for overriding the redirect URI and user agent
* Changed the font used to display the the client ID to avoid typoes
* Added HLS video support, including in self posts

7.4.6 / 2026-4-9
===========
* Fixed the Reddit account's interface language being anything but English at login issue in the WebView case

7.4.5 / 2026-4-2
===========
* Fixed /r/all not loading by fixing it in JRAW and updating it

7.4.4 / 2026-3-31
============
* Fixed WebView login
* Fixed App crashes when opening a thread in the last subreddit in the sub list #281

7.4.3 / 2026-2-28
============
* Added button to the Login screen to open a Chrome custom tab via the default browser as a login alternative
* Fixed Some gifs in comments fail to embed

7.4.2 / 2026-2-25
============
* Removed READ_EXTERNAL_STORAGE, READ_MEDIA_IMAGES, and READ_MEDIA_VIDEO permissions

7.4.1 / 2026-2-25
============
* Completely removed TedImagePicker for the stock image picker

7.4.0 / 2026-2-21
============
* Switched to the stock image picker on Android 11+
* Added two retries on 500 errors while trying to load posts from a subreddit

7.3.8 / 2025-12-31
=============
* Added a search button to You/Profile tabs
* Improved caching for "Go to profile" tabs and in Saved posts
* Added "Filter posts older than 30 days" setting in Filters
* Fixed issue with some post's URLs causing them to be marked as Title/None instead of Link

7.3.7 / 2025-11-6
============
* Fixed Slideshow #271
* Fixed Clicking on post on widget doesn't open properly? #273
* Fixed The Score threshold dialog that is part of subreddit notification setup is inescapable #275
* Fixed Crash when marking inbox message as unread #276
* Fixed refreshing the authentication token

7.3.6 / 2025-8-1
===========
* Fixed Settings display by further opting out of edge-to-edge

7.3.5 / 2025-8-1
===========
* Updated to targetSdk 35
* Opted out of edge-to-edge in the themes
* Fixed Superscript ^ doesn't format in previews #266
* Fixed crash in SubmissionsView.java

7.3.4 / 2025-6-24
============
* Fixed External video playback pauses on videos that don't have audio #263
* Fixed "Method of sending removal reasons" setting resets on app restart #262
* Fixed Subreddit theme dialog crashing when accessed via sidebar #261
* Fixed Saving a post doesn't always highlight the star icon #257
* Fixed Sorting problem. #254

7.3.3 / 2025-5-30
============
* Fixed reddit.com/comments links
* Added ability to put the small content tag in the top right corner
* Fixed off-by-one bugs in Reddit Gallery in horizontal and verticals modes
* Fixed various crashes

7.3.2 / 2025-5-24
============
* Fixed the cascade effect while using oldSwipeMode
* Add playback speed control to video player
* Inconsistent tap behavior on comments with media
* Fixed an intermittent display issue with Selftext preview images
* Tumblr gif fixes

7.3.1 / 2025-5-9
===========
* Dismiss toolbar search and keyboard on suggestion selection
* Prioritize Search icon, move Shadowbox to overflow
* Fixed an intermittent display issue with Selftext preview images
* Fixed earch dialog scales weirdly from FAB  #215
* Fixed Tapping on "Moderation" does nothing. And I can't hide it either from drawer items #111
* Added Hide subscribed subreddit tabs setting in Settings | General
* Fixed NullPointerException on resume in MediaView (ExoPlayer) #178
* Fixed many different crashes

7.3.0 / 2025-4-24
============
* Fixed Downloaded images are always saved as <subreddit>/download.jpeg #173
* Fixed issue with badly formatted code blocks
* Fixed Brief flash when opening videos/GIFs #162
* Fixed Controls briefly reappear when tapping to hide them #121
* Fixed Controls briefly appear when opening a video #119
* Fixed Pinch-to-zoom causes jumping in video player #168
* Added support for inputting the client ID via a QR code
* Fixed opening redd.it links inside and outside Slide #172

7.2.9 / 2025-4-3
===========
* Fixed Some video downloads are corrupted #169

7.2.8 / 2025-4-3
================
* Fix for crash related to "Pause video instead of ducking"
* Fixed Add button for subreddits in "Manage your subreddits" is greyed out #167

7.2.7 / 2025-3-28
=================
* Fixed bug with old swipe mode and vertical mode Reddit galleries #165 #166
* Fixed Can't create multireddit tabs #142
* Fixed YouTube links open in browser instead of YouTube app when using custom tabs #164
* Properly themed all dialog boxes in Settings | Manage your subreddits
* Added setting to put colored border around dialog boxes. Currently limited to Settings | Manage your subreddits, and not the default.
* Truncated CHANGELOG.md to post fork, but added link to the old CHANGELOG.md

7.2.6 / 2025-3-26
=================
* Lots of improvements to Imgur albums and Reddit galleries
* Fixed /s/ style links #163
* Fixed preview image display for Reddit videos when there is no preview
* Fixed Posts with preview.redd.it urls and highlighted text using backticks display the images/urls wrong #161
* Fixed crash from going back from a video
* Ducking, lowering volume, audio play when it is interrupted by default
* Setting to pause instead of duck in general settings
* Fixed Crosspost has a blank preview when media list is empty #148
* Fixed how Subreddit sync writes the urls for multireddits #143
* Fixed Crash in SubmissionsView: NullPointerException on Click #144

7.2.5 / 2025-3-21
=================
* Fixed Slide doesn't request notification permission on first launch #146
* Disabling converting preview links into images when Show content type text beside links is enabled #159
* Fixed all storage location checks to also check for storage access
* Added longclick option to unset storage location
* Implemented same crash fix from #147 for Imgur and Tumblr
* Fixed Crash in crosspost when media list is empty #147
* Reverted change to preview images which fixes many cases

7.2.4 / 2025-3-15
=================
* Fixed "Open content" option doesn’t support Reddit Galleries #115
* Fixed Sorting in User Profiles #139
* Always fully collapse the sticky comment
* Added link to the Client ID instructions the dialog in General settings
* Allow user to unblock another user #126
* Made Peek content and No longclicks on preview images toggle each other
* Fixed an ANR when reloading subs #134
* Fixed crashes in MultiredditOverview.java #133 #141

7.2.3 / 2025-3-9
================
* Added a restore form file button to the Tutorial #132
* Added link to the Client ID instructions #106
* Added a background the the client id dialog in the tutorial #106
* Fixed Peek content on Reddit galleries opens Reddit website #123
* Removed peeking in comments
* Fixed Reddit GIF loading with peek #124
* Fixed missing video audio when saving files #122

7.2.2 / 2025-3-2
================
* Two more fixes for subreddit filters

7.2.1 / 2025-2-28
=================
* Fixed multiple free_emote_pack/snoomoji emoticons in the same comment
* Removed the Notifications dialog in MainActivity.java
* Disabled auto-offline
* Fixed the size of the Subreddit content filters dialog

7.2.0 / 2025-2-26
=================
* Fixed both regular notifications and piggyback notifications #80
* Fixed subreddit filters
* Fixed previews for Image and Reddit Video content types

7.1.9 / 2025-2-25
================
* Subreddit content filters dialog box enhancements
* Added Subreddit content filters till restart setting in Filters settings #109
* Made the Subreddit content filters till restart setting the default

7.1.8 / 2025-2-24
=================
* Made the video background black instead of transparent
* Changed the default to muted video
* Improved the mute and unmute icons
* Added setting in General settings to allow unmuted video by default
* Fixed gallery preview and thumbnail images
* Made Go to profile and Go to multis single line
* Fixed multireddit FAB search #101
* Another attempt at fixing Title text is sometimes cut off #11
* Implemented a new content filter toggle button functionality
* Added NSFW Tumblrs and NSFW Videos to the content filters #76
* Fixed LINK preview images that were appearing and disappearing
* Fixed Go to subreddit says subreddit not found for subscribed subreddits #108

7.1.7 / 2025-2-21
=================
* Added the Reddit Client ID override to the tutorial
* Fixed Crash related to reordering subreddits #105
* Fixed Crash related to multireddit overview #104
* Fixed Crash related to gif playback with null checks #103

7.1.6 / 2025-2-20
=================
* Fixed Multireddit longclick button text #100
* Fixed NSFW Content Visible Despite Being Turned Off #77
* Fixed Posts with Reddit links show empty previews #92
* Fixed Base Theme issue when swapping accounts #99
* Fixed search boxes to be single input line
* Removed Select storage location option #90
* Updated minSdk from 21(Android 5.0) to 29(Android 10)

7.1.5 / 2025-2-15
=================
* Fixed It creates a subfolder for each downloaded image/GIF/video #83
* Fixed Crossposts don't follow "Picture mode" setting in Post layout settings #86
* Fixed You button in the Navigation bar #87
* Fixed Crash on "Open Externally" in Vertical Gallery Mode #89
* Fixed Galleries won't download in vertical scroll mode #88
* Fix for Open externally for the gallery in the Reddit Gallery Pager
* Fixed image download notification crash
* Made the behavior of themes more consistent
* Fixed Slide crashes when opening a post with a large image preview #79
* Added notification permission to AndroidManifest.xml #80
* Fixed Content Settings Cause App Crash & Loading Issues #74

7.1.2 / 2025-2-10
=================
* Removing *.redd.it for Open by default
* Removed checkClipboard to fix #72
* Fixed "Crash when scrolling through image posts" with more null checks #73
* Fixed Slide crashes when trying to access subreddits #69
* Fixed Read Later crash #62
* Added support for i.redd.it links for inlined preview images
* Converted giphy emotes to inlined preview images style #38
* Themed Client ID override dialog
* Fixed FAB multi-choice #66
* Fixed separators, spacing, and padding for general settings #66
* Added separator between emote animation and longclick settings #66

7.1.1 / 2025-2-8
================
* Fixed crash when switching back from Guest mode #68
* Themed exit dialog
* Themed profile dialog

7.1.0 / 2025-2-8
================
* Added multireddit search for the FAB search button #63
* Made the search button at the top work for multireddit tabs #63
* Converted subreddit content filter dialogs to use the theme

7.0.9 / 2025-2-6
================
* Fixed crash in UserSubscriptions.java
* Converted preview reddit links to inline images #40
* Reversed the Subreddit content filter logic #55


7.0.8 / 2025-2-5
================
* Fixed sorting for multireddit tabs #44
* Fixed crash in AlbumPager.java
* Fixed comment emotes advance when you leave Slide and come back #56
* Fixed preview images for crossposts of Reddit videos #57


7.0.7 / 2025-2-3
================
* Fixed ability to download Reddit videos #49
* Fixed image picking crash #53
* Fixed ANR related to opening a Reddit link #52
* Fixed crash related to viewing a user's profile and gold #51
* Fixed crash related to blocking a subreddit #50
* Fixed Imgur for the various types #42
* Show galleries as images instead of thumbnails in Shadowbox mode #41
* Improved the strings related to no longclicks #48
* Fixed web browser selection in Settings | Link handling
* Made custom tabs setting use the selected web browser
* Improved RedGifs where more should have sound

7.0.6 / 2025-2-1
=================
* Added Old Swipe mode in General settings

* Improved support for RedGifs

7.0.5 / 2025-1-28
=================
* Restored BlankFragment for 3-button navigation

7.0.4 / 2025-1-27
=================
* Added a link to the privacy policy in the app

7.0.3 / 2025-1-26
=================
* Made PREF_IGNORE_SUB_SETTINGS default to true

7.0.2 / 2025-1-25
=================
* Fixed frontpage sort inconsistency #39
* Added Frontpage sort setting #39
* Fixed media controls by reverting the upgrade to media3

7.0.1 / 2025-1-24
=================
* Fixed crashes caused by duplicate gif emotes in comments #37
* Changed Reddit Client ID override in General settings to restart the app
* Updated from exoplayer to media3

7.0.0 / 2025-1-24
=================
* Fixed Google drive backup and restore #30
* Fixed local file backup and restore #30
* Moved client id to the top of General settings
* Renamed Save image location to Save storage location in General settings

6.9.9 / 2025-1-21
=================
Fixed share image #36
Fixed shortened URLs to work with the official Reddit app #35

6.9.8 / 2025-1-20
=================
* Fixed Multireddits button in the navigation bar #34

6.9.7 / 2025-1-18
=================
* Fixed comments screen swipe left behavior #32
* Added more options for filtering subreddit content
* Fixed gallery preview image size in comments #20

6.9.6 / 2025-1-16
=================
* Fixed comments screen #25

6.9.5 / 2025-1-16
=================
* Removed BlankFragment everywhere to fix cascade effect #22
* Fixed saving media by updating to SAF #21
* Fixed single image Imgur albums #24

6.9.3 / 2025-1-3
================
Tried android:autoVerify="false"

6.9.2 / 2025-1-1
================
* Added another fix for cutoff text in post titles

6.9.1 / 2024-12-31
==================

6.9.0 / 2024-12-31
==================
* Reverting the Open by default change

6.8.8 / 2024-12-26
==================
* Fixed bug with animated gif playback in non-galleries

6.8.7 / 2024-12-25
==================
* Removed all reference to the Donate button in Settings

6.8.6 / 2024-12-24
==================
* Fixed Open externally crash

6.8.5 / 2024-12-24
==================
* Fixed SELECT PICTURE button to only require one press
* Improved defaults
* Removed Android Beam(NFC) support
* Removed Open by default links

6.8.2 / 2024-12-24
==================
* Removed open with links

6.7.13 / 2024-12-22
===================
* Changed applicationId, minSdk, and versionCode for Google Play Store

6.7.12 / 2024-12-22
==================
* Fixed gif post issue #17
* Fixed a mix of animated and non-animated images in Reddit galleries #4

6.7.11 / 2024-12-20
===================
* Fixed gallery preview showing the last image instead of the first #16
* Fixed galleries being misdetected if the url wasn't a gallery url #15

6.7.10 / 2024-12-19
===================
* Fixed consistently of emotes in comments respecting the animation toggle
* Improved giphy gifs using the preview if it is available
* Fixed galleries to not show a preview where mediaInfo has all failed entries

6.7.9 / 2024-12-19
==================
* Fixed emotes with text before and after the gif
* Fixed the emote scaling
* Fixed the emote animation setting to be consistent
* Replaced all references to /r/slideforreddit with /r/slidereddit

6.7.8 / 2024-12-19
==================
* Added setting to disable longclicks on preview images

6.7.7 / 2024-12-18
==================
* Fixed gallery image previews to be consistent on scroll down and back

6.7.6 / 2024-12-15
==================
* Fixed crash in RedditGalleryView
* Changed layout/submission_largecard_middle.xml to be Linear instead of Relative to attempt to fix a cut off bug

6.7.5 / 2024-12-14
==================
* Removed Pro mode, but not any of the features
* Added support for giphy gifs in Reddit comments
* Added preview image for crossposted galleries
* Improved the consistency of gallery image display

6.7.4 / 2024-12-12
==================
* Added preview images for Reddit galleries
* Improved the consistency of displaying images in Reddit galleries in the default view

6.7.2 / 2024-12-11
==================
* Fixed multi-reddit tabs
* Added Reddit default emote support to comments

6.7.1.1 / 2024-12-09
==================
* Added a dynamic Client ID feature
* Added shared links fix
