/**
 * Copyright (C) 2010-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Queue;

/**
 * Command data store (message...)
 * 
 * @author yvolk@yurivolkov.com
 */
public class CommandData implements Comparable<CommandData> {
    private CommandEnum command;
    private int priority = 0;
    
    /**
     * Unique name of {@link MyAccount} for this command. Empty string if command is not Account specific 
     * (e.g. {@link CommandEnum#AUTOMATIC_UPDATE} which works for all accounts) 
     */
    private String accountName = "";

    /**
     * Timeline type used for the {@link CommandEnum#FETCH_TIMELINE} command 
     */
    private TimelineTypeEnum timelineType = TimelineTypeEnum.UNKNOWN;
    
    /**
     * This is: 
     * 1. Generally: Message ID ({@link MyDatabase.Msg#MSG_ID} of the {@link MyDatabase.Msg}).
     * 2. User ID ( {@link MyDatabase.User#USER_ID} ) for the {@link CommandEnum#FETCH_USER_TIMELINE}, 
     *      {@link CommandEnum#FOLLOW_USER}, {@link CommandEnum#STOP_FOLLOWING_USER} 
     */
    protected long itemId = 0;

    /**
     * Other command parameters
     */
    Bundle bundle = new Bundle();

    private int hashcode = 0;

    private CommandResult commandResult = new CommandResult();
    
    public CommandData(CommandEnum commandIn, String accountNameIn, TimelineTypeEnum timelineTypeIn, long itemIdIn) {
        this(commandIn, accountNameIn, itemIdIn);
        timelineType = timelineTypeIn;
    }

    public CommandData(CommandEnum commandIn, String accountNameIn, long itemIdIn) {
        this(commandIn, accountNameIn);
        itemId = itemIdIn;
    }

    public CommandData(CommandEnum commandIn, String accountNameIn) {
        command = commandIn;
        priority = command.getPriority();
        if (!TextUtils.isEmpty(accountNameIn)) {
            accountName = accountNameIn;
        }
        getResult().resetRetries(command);
    }

    private CommandData() {
    }

    /**
     * Used to decode command from the Intent upon receiving it
     */
    public static CommandData fromIntent(Intent intent) {
        CommandData commandData = getEmpty();
        if (intent != null) {
            Bundle bundle = intent.getExtras();
            String strCommand = "";
            if (bundle != null) {
                strCommand = bundle.getString(IntentExtra.EXTRA_MSGTYPE.key);
            }
            CommandEnum command = CommandEnum.load(strCommand);
            switch (command) {
                case UNKNOWN:
                    MyLog.w(CommandData.class, "Intent had UNKNOWN command " + strCommand + "; Intent: " + intent);
                    break;
                case EMPTY:
                    break;
                default:
                    commandData = new CommandData();
                    commandData.bundle = bundle;
                    commandData.command = command;
                    commandData.accountName = commandData.bundle.getString(IntentExtra.EXTRA_ACCOUNT_NAME.key);
                    commandData.timelineType = TimelineTypeEnum.load(commandData.bundle.getString(IntentExtra.EXTRA_TIMELINE_TYPE.key));
                    commandData.itemId = commandData.bundle.getLong(IntentExtra.EXTRA_ITEMID.key);
                    commandData.commandResult = commandData.bundle.getParcelable(IntentExtra.EXTRA_COMMAND_RESULT.key);
                    break;
            }
        }
        return commandData;
    }

    public static CommandData getEmpty() {
        return new CommandData(CommandEnum.EMPTY, "");        
    }
    
    public static CommandData searchCommand(String accountName, String queryString) {
        CommandData commandData = new CommandData(CommandEnum.SEARCH_MESSAGE, accountName);
        commandData.timelineType = TimelineTypeEnum.PUBLIC;
        commandData.bundle.putString(SearchManager.QUERY, queryString);
        return commandData;
    }

    public static CommandData updateStatus(String accountName, String status, long replyToId, long recipientId) {
        CommandData commandData = new CommandData(CommandEnum.UPDATE_STATUS, accountName);
        commandData.bundle.putString(IntentExtra.EXTRA_STATUS.key, status);
        if (replyToId != 0) {
            commandData.bundle.putLong(IntentExtra.EXTRA_INREPLYTOID.key, replyToId);
        }
        if (recipientId != 0) {
            commandData.bundle.putLong(IntentExtra.EXTRA_RECIPIENTID.key, recipientId);
        }
        return commandData;
    }
    
    /**
     * Restore this from the SharedPreferences 
     * @param sp
     * @param index Index of the preference's name to be used
     */
    private static CommandData fromSharedPreferences(SharedPreferences sp, int index) {
        String si = Integer.toString(index);
        // Decode command
        String strCommand = sp.getString(IntentExtra.EXTRA_MSGTYPE.key + si,
                CommandEnum.UNKNOWN.save());
        CommandData commandData = new CommandData(CommandEnum.load(strCommand),
                sp.getString(IntentExtra.EXTRA_ACCOUNT_NAME.key + si, ""),
                TimelineTypeEnum.load(sp.getString(IntentExtra.EXTRA_TIMELINE_TYPE.key + si, "")),
                sp.getLong(IntentExtra.EXTRA_ITEMID.key + si, 0));

        switch (commandData.command) {
            case UPDATE_STATUS:
                commandData.bundle.putString(IntentExtra.EXTRA_STATUS.key,
                        sp.getString(IntentExtra.EXTRA_STATUS.key + si, ""));
                commandData.bundle.putLong(IntentExtra.EXTRA_INREPLYTOID.key,
                        sp.getLong(IntentExtra.EXTRA_INREPLYTOID.key + si, 0));
                commandData.bundle.putLong(IntentExtra.EXTRA_RECIPIENTID.key,
                        sp.getLong(IntentExtra.EXTRA_RECIPIENTID.key + si, 0));
                break;
            default:
                break;
        }
        commandData.getResult().loadFromSharedPreferences(sp, index);
        return commandData;
    }
    
    /**
     * @return Number of items persisted
     */
    static int saveQueue(Context context, Queue<CommandData> q, String prefsFileName) {
        int count = 0;
        // Delete any existing saved queue
        SharedPreferencesUtil.delete(context, prefsFileName);
        if (!q.isEmpty()) {
            SharedPreferences sp = MyPreferences.getSharedPreferences(prefsFileName);
            while (!q.isEmpty()) {
                CommandData cd = q.poll();
                cd.saveToSharedPreferences(sp, count);
                MyLog.v(context, "Command saved: " + cd.toString());
                count += 1;
            }
            MyLog.d(context, "Queue saved to " + prefsFileName  + ", " + count + " msgs");
        }
        return count;
    }
    
    /**
     * @return Number of items loaded
     */
    static int loadQueue(Context context, Queue<CommandData> q, String prefsFileName) {
        String method = "loadQueue: ";
        int count = 0;
        if (SharedPreferencesUtil.exists(context, prefsFileName)) {
            boolean done = false;
            SharedPreferences sp = MyPreferences.getSharedPreferences(prefsFileName);
            do {
                CommandData cd = fromSharedPreferences(sp, count);
                MyLog.v(context, "Restored command " + IntentExtra.EXTRA_MSGTYPE + count + " " + cd);
                if (cd.getCommand() == CommandEnum.UNKNOWN) {
                    done = true;
                } else {
                    if ( q.offer(cd) ) {
                        MyLog.v(context, method + cd.toString());
                        count += 1;
                    } else {
                        MyLog.e(context, method + cd.toString());
                    }
                }
            } while (!done);
            sp = null;
            // Delete this loaded queue
            SharedPreferencesUtil.delete(context, prefsFileName);
            MyLog.d(context, "Queue loaded from " + prefsFileName  + ", " + count + " msgs");
        }
        return count;
    }
    
    /**
     * It's used in equals() method. We need to distinguish duplicated
     * commands but to ignore differences in results!
     */
    @Override
    public int hashCode() {
        if (hashcode == 0) {
            String text = Long.toString(command.ordinal());
            if (!TextUtils.isEmpty(getAccountName())) {
                text += getAccountName();
            }
            if (timelineType != TimelineTypeEnum.UNKNOWN) {
                text += timelineType.save();
            }
            if (itemId != 0) {
                text += Long.toString(itemId);
            }
            switch (command) {
                case UPDATE_STATUS:
                    text += bundle.getString(IntentExtra.EXTRA_STATUS.key);
                    break;
                case PUT_BOOLEAN_PREFERENCE:
                    text += bundle.getString(IntentExtra.EXTRA_PREFERENCE_KEY.key)
                            + bundle.getBoolean(IntentExtra.EXTRA_PREFERENCE_VALUE.key);
                    break;
                case PUT_LONG_PREFERENCE:
                    text += bundle.getString(IntentExtra.EXTRA_PREFERENCE_KEY.key)
                            + bundle.getLong(IntentExtra.EXTRA_PREFERENCE_VALUE.key);
                    break;
                case PUT_STRING_PREFERENCE:
                    text += bundle.getString(IntentExtra.EXTRA_PREFERENCE_KEY.key)
                            + bundle.getString(IntentExtra.EXTRA_PREFERENCE_VALUE.key);
                    break;
                default:
                    break;
            }
            hashcode = text.hashCode();
        }
        return hashcode;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return MyLog.formatKeyValue("CommandData",
            "command:" + command.save() + ","
                + (TextUtils.isEmpty(getAccountName()) ? "" : "account:" + getAccountName() + ",")
                + (timelineType == TimelineTypeEnum.UNKNOWN ? "" : timelineType + ",")
                + (itemId == 0 ? "" : "id:" + itemId + ",")
                + "hashCode:" + hashCode() + ","
                + CommandResult.toString(commandResult)
            );
    }

    /**
     * @return Intent to be sent to this.AndStatusService
     */
    public Intent toIntent(Intent intentIn) {
        Intent intent = intentIn;
        if (intent == null) {
            throw new IllegalArgumentException("toIntent: input intent is null");
        }
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putString(IntentExtra.EXTRA_MSGTYPE.key, command.save());
        if (!TextUtils.isEmpty(getAccountName())) {
            bundle.putString(IntentExtra.EXTRA_ACCOUNT_NAME.key, getAccountName());
        }
        if (timelineType != TimelineTypeEnum.UNKNOWN) {
            bundle.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key, timelineType.save());
        }
        if (itemId != 0) {
            bundle.putLong(IntentExtra.EXTRA_ITEMID.key, itemId);
        }
        bundle.putParcelable(IntentExtra.EXTRA_COMMAND_RESULT.key, commandResult);        
        intent.putExtras(bundle);
        return intent;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CommandData)) {
            return false;
        }
        CommandData cd = (CommandData) o;
        return hashCode() == cd.hashCode();
    }

    /**
     * Persist the object to the SharedPreferences 
     * We're not storing all types of commands here because not all commands
     *   go to the queue.
     * SharedPreferences should not contain any previous versions of the same entries 
     * (we don't store default values!)
     * @param sp
     * @param index Index of the preference's name to be used
     */
    private void saveToSharedPreferences(SharedPreferences sp, int index) {
        String si = Integer.toString(index);

        android.content.SharedPreferences.Editor ed = sp.edit();
        ed.putString(IntentExtra.EXTRA_MSGTYPE.key + si, command.save());
        if (!TextUtils.isEmpty(getAccountName())) {
            ed.putString(IntentExtra.EXTRA_ACCOUNT_NAME.key + si, getAccountName());
        }
        if (timelineType != TimelineTypeEnum.UNKNOWN) {
            ed.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key + si, timelineType.save());
        }
        if (itemId != 0) {
            ed.putLong(IntentExtra.EXTRA_ITEMID.key + si, itemId);
        }
        switch (command) {
            case UPDATE_STATUS:
                ed.putString(IntentExtra.EXTRA_STATUS.key + si, bundle.getString(IntentExtra.EXTRA_STATUS.key));
                ed.putLong(IntentExtra.EXTRA_INREPLYTOID.key + si, bundle.getLong(IntentExtra.EXTRA_INREPLYTOID.key));
                ed.putLong(IntentExtra.EXTRA_RECIPIENTID.key + si, bundle.getLong(IntentExtra.EXTRA_RECIPIENTID.key));
                break;
            default:
                break;
        }
        commandResult.saveToSharedPreferences(ed, index);
        ed.commit();
    }

    private String getAccountName() {
        return accountName;
    }

    /**
     * @return null if no MyAccount exists with supplied name
     */
    public MyAccount getAccount() {
        return MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
    }

    @Override
    public int compareTo(CommandData another) {
        int greater = 0;
        if ( another != null && another.priority != this.priority) {
            greater = this.priority > another.priority ? 1 : -1;
        }
        return greater;
    }

    public CommandEnum getCommand() {
        return command;
    }

    public TimelineTypeEnum getTimelineType() {
        return timelineType;
    }

    public CommandResult getResult() {
        return commandResult;
    }
}