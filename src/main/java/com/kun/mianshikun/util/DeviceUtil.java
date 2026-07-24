package com.kun.mianshikun.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 设备类型检测工具类
 */
public class DeviceUtil {

    /**
     * 设备类型枚举
     */
    public static final String DEVICE_PC = "PC";
    public static final String DEVICE_MOBILE = "MOBILE";
    public static final String DEVICE_TABLET = "TABLET";
    public static final String DEVICE_OTHER = "OTHER";

    /**
     * 根据 User-Agent 判断设备类型
     */
    public static String detectDeviceType(String userAgent) {
        if (StringUtils.isBlank(userAgent)) {
            return DEVICE_OTHER;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("ipad") || ua.contains("tablet") || ua.contains("playbook")
                || ua.contains("silk") || (ua.contains("android") && !ua.contains("mobile"))) {
            return DEVICE_TABLET;
        }
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("ipod")
                || ua.contains("android") || ua.contains("blackberry")
                || ua.contains("windows phone")) {
            return DEVICE_MOBILE;
        }
        return DEVICE_PC;
    }
}
