package com.aluxian.apps.muzei.facebook;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.facebook.FacebookRequestError;
import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.model.GraphObject;
import com.facebook.model.GraphObjectList;
import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;
import com.google.android.apps.muzei.api.UserCommand;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Random;

public class ArtSource extends RemoteMuzeiArtSource {

    @SuppressWarnings("UnusedDeclaration")
    private static final String TAG = "ArtSource";
    private static final String SOURCE_NAME = "FacebookForMuzei";

    /**
     * Custom user commands IDs
     */
    private static final int COMMAND_VIEW_ALBUM = 100;
    private static final int COMMAND_SHARE = 101;
    private static final int COMMAND_PREVIOUS = 102;

    /**
     * Keys used to store extra info into the artwork intent
     */
    private static final String EXTRA_PHOTO_ID = "photo_id";
    private static final String EXTRA_PHOTO_LINK = "photo_link";
    private static final String EXTRA_ALBUM_LINK = "album_link";
    private static final String EXTRA_ARTWORK_TOKEN = "artwork_token";
    private static final String EXTRA_PREVIOUS_ARTWORK = "previous_artwork";

    /**
     * Custom user commands
     */
    private ArrayList<UserCommand> mUserCommands = new ArrayList<UserCommand>();
    private UserCommand mPreviousArtworkCommand = new UserCommand(COMMAND_PREVIOUS, "Previous Photo");

    public ArtSource() {
        super(SOURCE_NAME);

        // Add default user commands
        mUserCommands.add(new UserCommand(BUILTIN_COMMAND_ID_NEXT_ARTWORK));
        mUserCommands.add(new UserCommand(COMMAND_VIEW_ALBUM, "View Album"));
        mUserCommands.add(new UserCommand(COMMAND_SHARE, "Share Photo"));
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {
        // Make sure user is on an WiFi connection if the 'WiFi only' preference is true
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean wifiOnlyPref = sharedPreferences.getBoolean(SettingsActivity.PREF_WIFI_ONLY, false);
        if (reason != RemoteMuzeiArtSource.UPDATE_REASON_USER_NEXT && wifiOnlyPref && !Utils.hasWiFiConnection(this)) {
            scheduleUpdate(System.currentTimeMillis() + Utils.getRefreshInterval(this));
            return;
        }

        Session session = Session.getActiveSession();
        if (session == null) {
            session = Session.openActiveSessionFromCache(this);
        }

        // If user is not logged in, use initial wallpaper
        if (session == null || !session.isOpened()) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            publishArtwork(new Artwork.Builder()
                    .imageUri(Uri.parse("file:///android_asset/starrynight.jpg"))
                    .title(getString(R.string.app_name))
                    .byline(getString(R.string.default_byline))
                    .viewIntent(settingsIntent)
                    .token("initial")
                    .build());

            removeAllUserCommands();
            return;
        }

        // Query photos
        Bundle params = new Bundle();
        params.putString("fields", "id,name,from,likes,images,album.id");

        Response response = new Request(session, "me/photos", params, HttpMethod.GET).executeAndWait();
        FacebookRequestError error = response.getError();

        if (error != null) {
            throw new RetryException(new Throwable(error.getErrorMessage()));
        }

        // Get list of photo objects; if it's empty, retry later
        GraphObjectList<GraphObject> photosList = response.getGraphObject().getPropertyAsList("data",
                GraphObject.class);

        if (photosList == null) {
            throw new RetryException();
        } else if (photosList.size() == 0) {
            scheduleUpdate(System.currentTimeMillis() + Utils.getRefreshInterval(this));
            return;
        }

        // Get a random photo
        GraphObject photo = photosList.get(new Random().nextInt(photosList.size()));

        if (photo == null) {
            throw new RetryException();
        }

        // Find attributes to be used as title and byline
        String name = Utils.getName(photo);
        String from = Utils.getFrom(photo);
        String place = Utils.getPlace(photo);
        String likes = Utils.getLikes(photo);

        // Get artwork data
        String photoId, photoUrl, photoLink, photoSchemeLink, albumLink, albumSchemeLink;
        try {
            photoId = photo.getProperty("id").toString();
            photoUrl = photo.getPropertyAsList("images", GraphObject.class).get(0).getProperty("source").toString();

            photoLink = "https://www.facebook.com/photo.php?fbid=" + photoId;
            photoSchemeLink = "fb://photo/" + photoId;

            //album = photo.getPropertyAs("album", GraphObject.class).;
            albumLink = "";//photo.getProperty("album").toString();
            albumSchemeLink = "fb://album/";
        } catch (NullPointerException e) {
            Utils.logCaughtException(this, e);
            throw new RetryException(e);
        }

        // Save links into intent to be used by custom commands
        Intent artworkIntent = new Intent(Intent.ACTION_VIEW);
        artworkIntent.putExtra(EXTRA_PHOTO_LINK, photoLink);
        artworkIntent.putExtra(EXTRA_PHOTO_ID, photoId);

        if (Utils.canOpenFacebookUrl(photoSchemeLink, this)) {
            artworkIntent.setData(Uri.parse(photoSchemeLink));
            artworkIntent.putExtra(EXTRA_ALBUM_LINK, albumSchemeLink);
        } else {
            artworkIntent.setData(Uri.parse(photoLink));
            artworkIntent.putExtra(EXTRA_ALBUM_LINK, albumLink);
        }

        // Cache previous artwork
        artworkIntent.putExtra(EXTRA_ARTWORK_TOKEN, session.getAccessToken());
        try {
            Artwork currentArtwork = getCurrentArtwork();
            String previousAccessToken = currentArtwork.getViewIntent().getStringExtra(EXTRA_ARTWORK_TOKEN);

            // Check if the current user can see the previous artwork
            if (session.getAccessToken().equals(previousAccessToken)) {
                artworkIntent.putExtra(EXTRA_PREVIOUS_ARTWORK, currentArtwork.toJson().toString());
                mUserCommands.add(mPreviousArtworkCommand);
            } else {
                mUserCommands.remove(mPreviousArtworkCommand);
            }
        } catch (JSONException e) {
            Utils.logCaughtException(this, e);
        }

        // Update available user commands
        setUserCommands(mUserCommands);

        // Publish and schedule next update
        publishArtwork(new Artwork.Builder()
                .title(Utils.firstString(name, from, place, likes))
                .byline(Utils.secondString(name, from, place, likes))
                .imageUri(Uri.parse(photoUrl))
                .viewIntent(artworkIntent)
                .token(photoId)
                .build());

        scheduleUpdate(System.currentTimeMillis() + Utils.getRefreshInterval(this));
    }

    @Override
    protected void onCustomCommand(int id) {
        switch (id) {
            case COMMAND_VIEW_ALBUM: {
                Intent artworkIntent = getCurrentArtwork().getViewIntent();
                Intent intent = new Intent(Intent.ACTION_VIEW);

                intent.setData(Uri.parse(artworkIntent.getStringExtra(EXTRA_ALBUM_LINK)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                break;
            }
            case COMMAND_SHARE: {
                Intent artworkIntent = getCurrentArtwork().getViewIntent();
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, artworkIntent.getStringExtra(EXTRA_PHOTO_LINK));
                intent.setType("text/plain");

                Intent shareIntent = Intent.createChooser(intent, "Share Photo");
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(shareIntent);

                break;
            }
            case COMMAND_PREVIOUS: {
                try {
                    String jsonString = getCurrentArtwork().getViewIntent().getStringExtra(EXTRA_PREVIOUS_ARTWORK);
                    Artwork previousArtwork = Artwork.fromJson(new JSONObject(jsonString));
                    publishArtwork(previousArtwork);

                    // Remove the `Previous photo` command if there are no more photos to go back to
                    if (previousArtwork.getViewIntent().getStringExtra(EXTRA_PREVIOUS_ARTWORK) == null) {
                        mUserCommands.remove(mPreviousArtworkCommand);
                        setUserCommands(mUserCommands);
                    }
                } catch (JSONException e) {
                    Utils.logCaughtException(this, e);
                }

                break;
            }
            default:
                super.onCustomCommand(id);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();

        // Force a wallpaper update
        if (SettingsActivity.ACTION_FORCE_UPDATE.equals(action)) {
            scheduleUpdate(System.currentTimeMillis() + 1000);
        }

        // Reschedule next update
        if (SettingsActivity.ACTION_RESCHEDULE_UPDATE.equals(action)) {
            scheduleUpdate(System.currentTimeMillis() + Utils.getRefreshInterval(this));
        }

        super.onHandleIntent(intent);
    }

}
