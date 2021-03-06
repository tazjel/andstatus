/**
 * Copyright (C) 2011-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.util;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import net.jcip.annotations.GuardedBy;

/**
 * There is a need to turn debug (and maybe even verbose) logging on and off
 * dynamically at any time, plus sometimes we need to start debug logging on
 * boot. For possible solutions see e.g.:
 *  http://stackoverflow.com/questions/2018263/android-logging
 *  http://stackoverflow.com/questions/6650439/android-set-default-log-level-to-debug
 *  http://stackoverflow.com/questions/4050417/android-production-logging-best-practice 
 * I could not find existing way (the way that won't require programming) to change Android
 * application logging level: 
 *  - on boot 
 *  - at any time without connecting it to the PC. 
 * So it looks like possible way to do this is to: 
 * 1. Create new persistent Preference &quot;Minimum logging level&quot; 
 * with list of values:
 * &quot;INFO&quot; (default, in order not to affect general users...),
 * &quot;DEBUG&quot; and &quot;VERBOSE&quot;. 
 * 2. Create custom MyLog class that
 * honors the &quot;Minimum logging level&quot; preference. Use this class
 * throughout the application.
 * 
 * @author yvolk@yurivolkov.com
 */
public class MyLog {
    private static final String TAG = MyLog.class.getSimpleName();

    /**
     * Use this tag to change logging level of the whole application
     * Is used in isLoggable(APPTAG, ... ) calls
     */
    public static final String APPTAG = "AndStatus";
    public static final int DEBUG = Log.DEBUG;
    public static final int VERBOSE = Log.VERBOSE;
    public static final int INFO = Log.INFO;
    private static final int IGNORED = VERBOSE - 1;
    
    private static Object lock = new Object();
    @GuardedBy("lock")
    private static volatile boolean initialized = false;
    
    /** 
     * Cached value of the persistent preference
     */
    private static volatile int minLogLevel = VERBOSE;

    private MyLog() {
        
    }

    public static void logSharedPreferencesValue(Object objTag, SharedPreferences sharedPreferences, String key) {
        if (!isLoggable(objTag, DEBUG )) {
            return;
        }
        String value = "(not set)";
        if (sharedPreferences.contains(key)) {
            try {
                value = sharedPreferences.getString(key, "");
            } catch (ClassCastException e1) {
                MyLog.ignored(objTag, e1);
                try {
                    value = Boolean.toString(sharedPreferences.getBoolean(key, false));
                } catch (ClassCastException e2) {
                    MyLog.ignored(objTag, e2);
                    value = "??";
                }
            }
        }
        d(objTag, "SharedPreference: " + key + "='" + value + "'");
    }
    
    public static int e(Object objTag, String msg, Throwable tr) {
        String tag = objTagToString(objTag);
        return Log.e(tag, msg, tr);
    }

    public static int e(Object objTag, Throwable tr) {
        String tag = objTagToString(objTag);
        return Log.e(tag, "", tr);
    }
    
    public static int e(Object objTag, String msg) {
        String tag = objTagToString(objTag);
        return Log.e(tag, msg);
    }

    public static int i(Object objTag, String msg, Throwable tr) {
        String tag = objTagToString(objTag);
        return Log.i(tag, msg, tr);
    }
    
    public static int i(Object objTag, Throwable tr) {
        String tag = objTagToString(objTag);
        return Log.i(tag, "", tr);
    }
    
    public static int i(Object objTag, String msg) {
        String tag = objTagToString(objTag);
        return Log.i(tag, msg);
    }

    public static int w(Object objTag, String msg) {
        String tag = objTagToString(objTag);
        return Log.w(tag, msg);
    }

    /**
     * Shortcut for debugging messages of the application
     */
    public static int d(Object objTag, String msg) {
        String tag = objTagToString(objTag);
        int i = 0;
        if (isLoggable(tag, DEBUG)) {
            i = Log.d(tag, msg);
        }
        return i;
    }

    /**
     * Shortcut for debugging messages of the application
     */
    public static int d(Object objTag, String msg, Throwable tr) {
        String tag = objTagToString(objTag);
        int i = 0;
        if (isLoggable(tag, DEBUG)) {
            i = Log.d(tag, msg, tr);
        }
        return i;
    }

    public static int v(Object objTag, Throwable e) {
        String tag = objTagToString(objTag);
        int i = 0;
        if (isLoggable(tag, Log.VERBOSE)) {
            i = Log.v(tag, "", e);
        }
        return i;
    }
    
    /**
     * Shortcut for verbose messages of the application
     */
    public static int v(Object objTag, String msg) {
        String tag = objTagToString(objTag);
        int i = 0;
        if (isLoggable(tag, Log.VERBOSE)) {
            i = Log.v(tag, msg);
        }
        return i;
    }

    public static int v(Object objTag, String msg, Throwable tr) {
        String tag = objTagToString(objTag);
        int i = 0;
        if (isLoggable(tag, Log.VERBOSE)) {
            i = Log.v(tag, msg, tr);
        }
        return i;
    }

    /**
     * This will be ignored
     */
    public static int ignored(Object objTag, Throwable tr) {
        String tag = objTagToString(objTag);
        int i = 0;
        if (isLoggable(tag, IGNORED)) {
            i = Log.v(tag, "", tr);
        }
        return i;
    }
    
    public static String objTagToString(Object objTag) {
        String tag = "";
        if (objTag == null) {
            tag = "(null)";
        } else if (objTag instanceof String) {
            tag = (String) objTag;
        } else if (objTag instanceof Class<?>) {
            tag = ((Class<?>) objTag).getSimpleName();
        }else {
            tag = objTag.getClass().getSimpleName();
        }
        return tag;
    }

    /**
     * 
     * @param tag If tag is empty then {@link #APPTAG} is used
     * @param level {@link android.util.Log#INFO} ...
     * @return
     */
    public static boolean isLoggable(Object objTag, int level) {
        boolean is = false;
        checkInit();
        if (level < VERBOSE) {
            is = false;
        } else if (level >= minLogLevel) {
            is = true;
        } else {
            String tag = objTagToString(objTag);
            if (TextUtils.isEmpty(tag)) {
                tag = APPTAG;
            }
            if (tag.length() > 23) {
                tag = tag.substring(0, 22);
            }
            is = Log.isLoggable(tag, level);
        }
        
        return is;
    }
    
    /**
     * Initialize using a double-check idiom 
     */
    private static void checkInit() {
        if (initialized) {
            return;
        }
        synchronized (lock) {
            if (initialized) {
                return;
            }
            MyContext myContext = MyContextHolder.get();
            if (!myContext.initialized()) {
                return;
            }
            // The class was not initialized yet.
            String val = "(not set)";
            try {
                SharedPreferences sp = MyPreferences.getDefaultSharedPreferences();  
                if (sp != null) {
                    val = getMinLogLevel(sp);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in isLoggable", e);
            }
            if (Log.INFO >= minLogLevel) {
                Log.i(TAG, MyPreferences.KEY_MIN_LOG_LEVEL + "='" + val +"'");
            }
            initialized = true;
        }
    }

    private static String getMinLogLevel(SharedPreferences sp) {
        String val;
        try {
            /**
             * Due to the Android bug
             * ListPreference operate with String values only...
             * See http://code.google.com/p/android/issues/detail?id=2096
             */
            val = sp.getString(MyPreferences.KEY_MIN_LOG_LEVEL, String.valueOf(Log.ASSERT));  
            minLogLevel = Integer.parseInt(val);  
        } catch (java.lang.ClassCastException e) {
            minLogLevel = sp.getInt(MyPreferences.KEY_MIN_LOG_LEVEL,Log.ASSERT);
            val = Integer.toString(minLogLevel);
            Log.e(TAG, MyPreferences.KEY_MIN_LOG_LEVEL + "='" + val +"'", e);
        }
        return val;
    }

    /**
     * Mark to reread from the sources if it will be needed
     */
    public static void forget() {
        initialized = false;
    }
    
    /**
     * from org.apache.commons.lang3.exception.ExceptionUtils
     */
    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw); // NOSONAR
        return sw.getBuffer().toString();
    }
    
    public static boolean writeStringToFile(String string, String fileName) {
        boolean ok = false;
        File dir1 = MyPreferences.getDataFilesDir("logs", null);
        if (dir1 == null) { 
            return false; 
            }
        File file = new File(dir1, fileName);
        Writer out = null;
        try {
            if (file.exists() 
                && !file.delete()) {
                MyLog.e(TAG, "Couldn't delete the file: " + fileName);
            }
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file.getAbsolutePath()), "UTF-8"));
            out.write(string);
            ok = true;
        } catch (Exception e) {
            MyLog.d(TAG, fileName, e);
        } finally {
            DbUtils.closeSilently(out, fileName);
        }        
        return ok;
    }
    
    public static String formatKeyValue(String key, StringBuilder stringBuilder) {
        return formatKeyValue(key, stringBuilder.toString());
    }
    
    public static String formatKeyValue(String key, String value) {
        String out = "";
        if (!TextUtils.isEmpty(value)) {
            out = value.trim();
            final String COMMA = ",";
            int ind = out.lastIndexOf(COMMA);
            if (ind > 0 && ind == out.length()-1) {
                out = out.substring(0, ind);
            }
        }
        return key + ":{" + out + "}";
    }
}
