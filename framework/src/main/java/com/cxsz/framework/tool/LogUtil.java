package com.cxsz.framework.tool;


import com.orhanobut.logger.Logger;

/**
 * Created by llf on 2017/2/28.
 * 日志打印工具
 */

public class LogUtil {
    private static boolean isDebug;


    public static void setIsDebug(boolean isDebug) {
        LogUtil.isDebug = isDebug;
    }

    /**
     * 设置Tag输出自定义的日志
     *
     * @param tag 标记
     * @param msg 信息
     */
    public static void setTagE(String tag, String msg) {
        if (isDebug) {
            Logger.t(tag).e(msg);
        }
    }

    /**
     * 设置Tag输出自定义的日志
     *
     * @param tag 标记
     * @param msg 信息
     */
    public static void setTagI(String tag, String msg) {
        if (isDebug) {
            Logger.t(tag).i(msg);
        }
    }
    /**
     * log.i
     *
     * @param msg
     */
    public static void i(String msg) {
        if (isDebug) {
            Logger.i(msg);
        }
    }

    /**
     * log.d
     *
     * @param msg
     */
    public static void d(String msg) {
        if (isDebug) {
            Logger.d(msg);
        }
    }

    /**
     * log.e
     *
     * @param msg
     */
    public static void e(String msg) {
        if (isDebug) {
            Logger.e(msg);
        }
    }

    /**
     * log.w
     *
     * @param msg
     */
    public static void w(String msg) {
        if (isDebug) {
            Logger.w(msg);
        }
    }

    /**
     * log.v
     *
     * @param msg
     */
    public static void v(String msg) {
        if (isDebug) {
            Logger.v(msg);
        }
    }

    /**
     * log.json
     *
     * @param msg
     */
    public static void json(String msg) {
        if (isDebug) {
            Logger.json(msg);
        }
    }

    /**
     * log.xml
     *
     * @param msg
     */
    public static void xml(String msg) {
        if (isDebug) {
            Logger.xml(msg);
        }
    }
}
