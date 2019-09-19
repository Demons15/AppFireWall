package com.cxsz.framework.constant;

import com.cxsz.framework.BuildConfig;

/**
 * 全局的静态变量
 */
public class KeyConstants {
    public static final String SP_INFO = "code_wevoice";
    public static final String NET_USER_ID = "user_id";
    public static final String NET_TOKEN = "net_token";
    public static String CHANNEL_ONE_ID = "com.net.cxsz.yiluwo_new.service";
    public static String CHANNEL_ONE_NAME = "yiluwo_new";
    public static final String SECRETS = BuildConfig.APP_SECRETS;
    public static final String APP_ID = BuildConfig.APP_ID;
    public static final String NONCE_STR = "sanjitongchuanandchanxingshenzhou";
    public static final String FIRST_LOGIN = "first_login"; //第一次登录
    /*Permissions Code*/
    public static final int CHECK_GPS_CODE = 0x1;
    public static final int PERMISSION_LOCATION_CODE = 0x2;
    public static final int PERMISSION_STORAGE_CODE = 0x3;
    public static final int PERMISSION_READ_PHONE_STATE_CODE = 0x5;
    public static final int PERMISSION_ACCESS_COARSE_LOCATION_CODE = 0x6;
    public static final int REQUEST_SIM_DETAIL_INFO_FAILURE = 0x7;
    public static final int REQUEST_SIM_PACKAGE_INFO_FAILURE = 0x8;
    public static final int REQUEST_SIM_INFO = 0x9;
    public static final int SHOW_BIND_WINDOW = 0x10;
    public static final int GET_CONTACT_CODE = 0x12;
    public static final int CLOSE_LOGIN = 0x13;
    public static final int REFRESH_APP_NET = 0x20;
}
