/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.data;

import android.app.Activity;
import android.app.ProgressDialog;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccountConverter;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabaseConverterController.UpgradeParams;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;

class MyDatabaseConverter {
    long startTime = java.lang.System.currentTimeMillis();
    private Activity activity;
    private ProgressDialog progress = null;

    protected void execute(UpgradeParams params) {
        activity = params.upgradeRequestor; 
        try {
            upgradeStarted();
            convertAll(params.db, params.oldVersion, params.newVersion);
        } finally {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                MyLog.d(this, "while sleeping", e);
            }
            upgradeEnded();
            if (MyContextHolder.get().isTestRun()) {
                activity.finish();
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    MyLog.d(this, "while sleeping", e);
                }
            }
        }
        long endTime = java.lang.System.currentTimeMillis();
        MyLog.w(this, "Upgrade successfully completed in " + Math.round((endTime - startTime)/1000) + " seconds");
    }

    private void upgradeStarted() {
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progress = new ProgressDialog(activity, ProgressDialog.STYLE_SPINNER);
                    progress.setTitle(R.string.app_name);
                    progress.setMessage(activity.getText(R.string.label_upgrading));
                    progress.show();
                }
            });
        } catch (Exception e) {
            MyLog.d(this, "upgradeStarted", e);
        }
    }

    private void upgradeEnded() {
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DialogFactory.dismissSafely(progress);
                }
              });
        } catch (Exception e) {
            MyLog.d(this, "upgradeEnded", e);
        }
    }
    
    private void convertAll(SQLiteDatabase db, int oldVersion, int newVersion) {
        int currentVersion = oldVersion;
        MyLog.i(this, "Upgrading database from version " + oldVersion + " to version " + newVersion);
        if (oldVersion < 12) {
            throw new IllegalArgumentException("Upgrade from this database version is not supported. Please reinstall the application");
        } 
        if (currentVersion == 12) {
            currentVersion = convert12to13(db, currentVersion);
        }
        if (currentVersion == 13) {
            currentVersion = convert13to14(db, currentVersion);
        }
        if (currentVersion == 14) {
            currentVersion = convert14to15(db, currentVersion);
        }
        if ( currentVersion == newVersion) {
            MyLog.i(this, "Successfully upgraded database from version " + oldVersion + " to version "
                    + newVersion + ".");
        } else {
            MyLog.e(this, "Error upgrading database from version " + oldVersion + " to version "
                    + newVersion + ". Current database version=" + currentVersion);
            throw new IllegalStateException("Database upgrade failed. Current database version=" + currentVersion);
        }
    }
 
    private int convert12to13(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 13;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );

            MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_SHOW_AVATARS, false).commit();
            
            db.execSQL("CREATE TABLE avatar (_id INTEGER PRIMARY KEY AUTOINCREMENT," 
                    + "user_id INTEGER NOT NULL," 
                    + "avatar_valid_from INTEGER NOT NULL," 
                    + "avatar_url TEXT NOT NULL," 
                    + "avatar_file_name TEXT," 
                    + "avatar_status INTEGER NOT NULL DEFAULT 0," 
                    + "avatar_loaded_date INTEGER)");

            db.execSQL("CREATE INDEX idx_avatar_user ON avatar (" 
                    + "user_id, "
                    + "avatar_status"
                    + ")");
                        
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }

    private int convert13to14(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 14;
        boolean ok = false;
        String sql = "";
        String twitterName = "Twitter";
        String statusNetSystemName = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );

            sql = "CREATE TABLE origin (" 
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                    + "origin_type_id INTEGER NOT NULL," 
                    + "origin_name TEXT NOT NULL," 
                    + "host TEXT NOT NULL," 
                    + "ssl BOOLEAN DEFAULT 0 NOT NULL," 
                    + "allow_html BOOLEAN DEFAULT 0 NOT NULL," 
                    + "text_limit INTEGER NOT NULL,"
                    + "short_url_length INTEGER NOT NULL DEFAULT 0" 
                    + ")";
            db.execSQL(sql);

            sql = "CREATE UNIQUE INDEX idx_origin_name ON origin (" 
                    + "origin_name"
                    + ")";
            db.execSQL(sql);
            
            String statusNetHost = MyPreferences.getDefaultSharedPreferences().getString("host_of_origin3","");
            statusNetSystemName = (TextUtils.isEmpty(statusNetHost) ? "StatusNet" : statusNetHost);
            if ("quitter.se".equalsIgnoreCase(statusNetHost)) {
                statusNetSystemName = "Quitter";
            } else if (!TextUtils.isEmpty(statusNetHost)) {
                statusNetSystemName = statusNetHost;
            }

            boolean statusNetSsl = MyPreferences.getDefaultSharedPreferences().getBoolean("ssl3", true);
            int statusNetTextLimit = MyPreferences.getDefaultSharedPreferences().getInt("textlimit3", 0);
            
            String sqlIns = "INSERT INTO origin (" 
                    + "_id, origin_type_id, origin_name, host, ssl, allow_html, text_limit, short_url_length"
                    + ") VALUES ("
                    + "%s"
                    + ")";
            String[] values = {
                    "1, 1, '" + twitterName + "', 'api.twitter.com', 1, 0,  140, 23",
                    "6, 1, 'twitter',   'api.twitter.com', 1, 0,  140, 23",
                    "2, 2, 'pump.io',   '',                1, 0, 5000,  0",
                    "3, 3, '" + statusNetSystemName + "','" + statusNetHost + "', " + (statusNetSsl ? "1" : "0" ) + ", 0, " + Integer.toString(statusNetTextLimit) + ", 0",
                    "7, 3, 'status.net','',                1, 0,  140,  0",
                    "4, 3, 'Quitter',   'quitter.se',      1, 1,  140,  0"
            };
            boolean quitterFound = false;
            boolean friendiCaFound = false;
            for (String value : values) {
                boolean quitter = value.contains("quitter.se");
                boolean friendiCa = value.contains("friendi.ca");
                boolean skip = false;
                if (quitter) {
                    skip = quitterFound;
                    quitterFound = true;
                }
                if (friendiCa) {
                    skip = friendiCaFound;
                    friendiCaFound = true;
                }
                if (!skip) {
                    sql = sqlIns.replace("%s", value);
                    db.execSQL(sql);
                }
            }
            ok = ( MyAccountConverter.convert12to14(db, oldVersion, twitterName, statusNetSystemName) == versionTo);
            if (ok) {
                sql = "DELETE FROM Origin WHERE _ID IN(6, 7)";
                db.execSQL(sql);
            }
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }

    private int convert14to15(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 15;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );
            
            sql = "ALTER TABLE msg ADD COLUMN public BOOLEAN DEFAULT 0 NOT NULL";
            db.execSQL(sql);
            sql = "UPDATE msg SET public=0";
            db.execSQL(sql);
            
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }
}
