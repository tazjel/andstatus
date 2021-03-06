package org.andstatus.app.origin;

import org.andstatus.app.net.ConnectionEmpty;
import org.andstatus.app.net.ConnectionPumpio;
import org.andstatus.app.net.ConnectionTwitter1p1;
import org.andstatus.app.net.HttpConnectionBasic;
import org.andstatus.app.net.HttpConnectionEmpty;
import org.andstatus.app.net.HttpConnectionOAuthApache;
import org.andstatus.app.net.HttpConnectionOAuthJavaNet;
import org.andstatus.app.net.Connection.ApiEnum;
import org.andstatus.app.net.ConnectionTwitterStatusNet;
import org.andstatus.app.util.TriState;

public enum OriginType {
    /**
     * Predefined Origin for Twitter system 
     * <a href="https://dev.twitter.com/docs">Twitter Developers' documentation</a>
     */
    TWITTER(1, "Twitter", ApiEnum.TWITTER1P1),
    /**
     * Predefined Origin for the pump.io system 
     * Till July of 2013 (and v.1.16 of AndStatus) the API was: 
     * <a href="http://status.net/wiki/Twitter-compatible_API">Twitter-compatible identi.ca API</a>
     * Since July 2013 the API is <a href="https://github.com/e14n/pump.io/blob/master/API.md">pump.io API</a>
     */
    PUMPIO(2, "pump.io", ApiEnum.PUMPIO),
    STATUSNET(3, "StatusNet", ApiEnum.STATUSNET_TWITTER),
    UNKNOWN(0, "?", ApiEnum.UNKNOWN_API);

    private static final String BASIC_PATH_DEFAULT = "api";
    private static final String OAUTH_PATH_DEFAULT = "oauth";
    private static final String USERNAME_REGEX_DEFAULT = "[a-zA-Z_0-9/\\.\\-\\(\\)]+";
    public static final OriginType ORIGIN_TYPE_DEFAULT = TWITTER;

    private long id;
    private String title;

    private ApiEnum api;
    protected boolean canSetHostOfOrigin = false;

    private Class<? extends Origin> originClass = Origin.class;
    private Class<? extends org.andstatus.app.net.Connection> connectionClass = ConnectionEmpty.class;
    private Class<? extends org.andstatus.app.net.HttpConnection> httpConnectionClassOauth = HttpConnectionEmpty.class;
    private Class<? extends org.andstatus.app.net.HttpConnection> httpConnectionClassBasic = HttpConnectionEmpty.class;

    /**
     * Default OAuth setting
     */
    protected boolean isOAuthDefault = true;
    /**
     * Can OAuth connection setting can be turned on/off from the default setting
     */
    protected boolean canChangeOAuth = false;

    protected boolean shouldSetNewUsernameManuallyIfOAuth = false;

    /**
     * Can user set username for the new user manually?
     * This is only for no OAuth
     */
    protected boolean shouldSetNewUsernameManuallyNoOAuth = false;
    protected String usernameRegEx = USERNAME_REGEX_DEFAULT;
    /**
     * Length of the link after changing to the shortened link
     * 0 means that length doesn't change
     * For Twitter.com see <a href="https://dev.twitter.com/docs/api/1.1/get/help/configuration">GET help/configuration</a>
     */
    protected int shortUrlLengthDefault = 0;
    
    protected boolean sslDefault = true;
    protected boolean canChangeSsl = false;

    protected boolean allowHtmlDefault = false;
    /**
     * Maximum number of characters in the message
     */
    protected int textLimitDefault = 0;
    protected String hostDefault = "";
    protected String basicPath = BASIC_PATH_DEFAULT;
    protected String oauthPath = OAUTH_PATH_DEFAULT;
    
    private OriginType(long id, String title, ApiEnum api) {
        this.id = id;
        this.title = title;
        this.api = api;
        switch (api) {
            case TWITTER1P1:
                isOAuthDefault = true;
                // Starting from 2010-09 twitter.com allows OAuth only
                canChangeOAuth = false;  
                canSetHostOfOrigin = true;
                shouldSetNewUsernameManuallyIfOAuth = false;
                shouldSetNewUsernameManuallyNoOAuth = true;
                // TODO: Read from Config
                shortUrlLengthDefault = 23; 
                usernameRegEx = USERNAME_REGEX_DEFAULT;
                textLimitDefault = 140;
                hostDefault = "api.twitter.com";
                basicPath = "1.1";
                oauthPath = OAUTH_PATH_DEFAULT;
                originClass = OriginTwitter.class;
                connectionClass = ConnectionTwitter1p1.class;
                httpConnectionClassOauth = HttpConnectionOAuthApache.class;
                httpConnectionClassBasic = HttpConnectionBasic.class;
                break;
            case PUMPIO:
                isOAuthDefault = true;  
                canChangeOAuth = false;
                canSetHostOfOrigin = false;
                shouldSetNewUsernameManuallyIfOAuth = true;
                shouldSetNewUsernameManuallyNoOAuth = false;
                usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+@[a-zA-Z_0-9/\\.\\-\\(\\)]+";
                allowHtmlDefault = true;
                // This is not a hard limit, just for convenience
                textLimitDefault = 5000;
                basicPath = BASIC_PATH_DEFAULT;
                oauthPath = OAUTH_PATH_DEFAULT;
                originClass = OriginPumpio.class;
                connectionClass = ConnectionPumpio.class;
                httpConnectionClassOauth = HttpConnectionOAuthJavaNet.class;
                break;
            case STATUSNET_TWITTER:
                isOAuthDefault = false;  
                canChangeOAuth = false; 
                canSetHostOfOrigin = true;
                shouldSetNewUsernameManuallyIfOAuth = false;
                shouldSetNewUsernameManuallyNoOAuth = true;
                usernameRegEx = USERNAME_REGEX_DEFAULT;
                canChangeSsl = true;
                basicPath = BASIC_PATH_DEFAULT;
                oauthPath = BASIC_PATH_DEFAULT;
                originClass = OriginStatusNet.class;
                connectionClass = ConnectionTwitterStatusNet.class;
                httpConnectionClassOauth = HttpConnectionOAuthApache.class;
                httpConnectionClassBasic = HttpConnectionBasic.class;
                break;
            default:
                break;
        }
    }

    public Class<? extends Origin> getOriginClass() {
        return originClass;
    }
    
    public Class<? extends org.andstatus.app.net.Connection> getConnectionClass() {
        return connectionClass;
    }
    
    public Class<? extends org.andstatus.app.net.HttpConnection> getHttpConnectionClass(boolean isOAuth) {
        if (fixIsOAuth(isOAuth)) {
            return httpConnectionClassOauth;
        } else {
            return httpConnectionClassBasic;
        }
    }
    
    public long getId() {
        return id;
    }

    public int getEntriesPosition() {
        return ordinal();
    }
    
    public String getTitle() {
        return title;
    }
    
    public ApiEnum getApi() {
        return api;
    }

    public boolean canSetHostOfOrigin() {
        return canSetHostOfOrigin;
    }
    
    @Override
    public String toString() {
        return "OriginType [ id:" + id + "; code:" + title + "]";
    }
    
    public boolean fixIsOAuth(TriState triStateOAuth) {
        return fixIsOAuth(triStateOAuth.toBoolean(isOAuthDefault));
    }

    public boolean fixIsOAuth(boolean isOAuthIn) {
        boolean fixed = isOAuthIn;
        if (fixed != isOAuthDefault && !canChangeOAuth) {
            fixed = isOAuthDefault;
        }
        return fixed;
    }
    
    public static OriginType fromId( long id) {
        OriginType originEnum = UNKNOWN;
        for(OriginType oe : values()) {
            if (oe.id == id) {
                originEnum = oe;
                break;
            }
        }
        return originEnum;
    }

    public static OriginType fromName( String name) {
        OriginType originEnum = UNKNOWN;
        for(OriginType oe : values()) {
            if (oe.title.equalsIgnoreCase(name)) {
                originEnum = oe;
                break;
            }
        }
        return originEnum;
    }

    public static OriginType fromEntriesPosition( int position) {
        OriginType originEnum = UNKNOWN;
        for(OriginType oe : values()) {
            if (oe.ordinal() == position) {
                originEnum = oe;
                break;
            }
        }
        return originEnum;
    }
}