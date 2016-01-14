package com.aluxian.apps.muzei.facebook;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.facebook.model.GraphObject;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.MapBuilder;
import com.google.analytics.tracking.android.StandardExceptionParser;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"ConstantConditions", "deprecation"})
public class Utils {

    /**
     * Extract the "name" property from the photo
     */
    public static String getName(GraphObject photo) {
        return (String) photo.getProperty("name");
    }

    /**
     * Extract the "from.name" property from the photo
     */
    public static String getFrom(GraphObject photo) {
        try {
            return (String) photo.getPropertyAs("from", GraphObject.class).getProperty("name");
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Extract the "place.name" property from the photo
     */
    public static String getPlace(GraphObject photo) {
        try {
            return (String) photo.getPropertyAs("place", GraphObject.class).getProperty("name");
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Extract the number of likes from the photo
     */
    public static String getLikes(GraphObject photo) {
        try {
            int likes = photo.getPropertyAs("likes", GraphObject.class).getPropertyAsList("data",
                    GraphObject.class).size();
            if (likes == 0) {
                return null;
            }
            return likes + " likes";
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Return the first string that is not empty nor null
     */
    public static String firstString(String... strings) {
        for (String str : strings) {
            if (!TextUtils.isEmpty(str)) {
                return str;
            }
        }
        return null;
    }

    /**
     * Return the second string that is not empty nor null
     */
    public static String secondString(String... strings) {
        boolean foundFirst = false;
        for (String str : strings) {
            if (!TextUtils.isEmpty(str)) {
                if (foundFirst) {
                    return str;
                } else {
                    foundFirst = true;
                }
            }
        }
        return null;
    }

    /**
     * Retrieve the refresh interval from the default shared preferences
     *
     * @return interval in milliseconds
     */
    public static int getRefreshInterval(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int mins = sharedPreferences.getInt(SettingsActivity.PREF_INTERVAL, SettingsActivity.DEFAULT_INTERVAL);
        return mins * 60 * 1000;
    }

    /**
     * Pretty print minutes into `x days y hours z minutes` or `x unit(s)`
     *
     * @param minutes input time
     * @return formatted time
     */
    public static String formatTime(int minutes) {
        int d = minutes / 24 / 60;
        int h = minutes / 60 % 24;
        int m = minutes % 60;
        int sum = d + h + m;

        // If only one unit has to be displayed, display its full name
        if (d == sum) return d + (d == 1 ? " day" : " days");
        if (h == sum) return h + (h == 1 ? " hour" : " hours");
        if (m == sum) return m + (m == 1 ? " minute" : " minutes");

        // Otherwise display them all with shortened unit name
        ArrayList<String> list = new ArrayList<String>();
        if (d > 0) list.add(d + "d");
        if (h > 0) list.add(h + "h");
        if (m > 0) list.add(m + "m");

        return TextUtils.join(" ", list);
    }

    /**
     * Check if the user is connected to the internet on WiFi
     *
     * @return true is the user is connected
     */
    @SuppressWarnings("ConstantConditions")
    public static boolean hasWiFiConnection(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
    }

    /**
     * Check if there are any available apps to handle fb:// urls
     *
     * @param url url to check
     * @return true if url can be opened, false otherwise
     */
    @SuppressWarnings("ConstantConditions")
    public static boolean canOpenFacebookUrl(String url, Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        List availableApps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return availableApps.size() > 0;
    }

    /**
     * Log a caught exception to Google Analytics
     *
     * @param context exception context
     * @param ex      exception object
     */
    public static void logCaughtException(Context context, Throwable ex) {
        String threadName = Thread.currentThread().getName();
        String description = new StandardExceptionParser(context, null).getDescription(threadName, ex);
        EasyTracker.getInstance(context).send(MapBuilder.createException(description, false).build());
    }

}
