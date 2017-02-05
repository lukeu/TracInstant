/*
 * Copyright 2011 Luke Usherwood.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.bettyluke.tracinstant.prefs;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import net.bettyluke.util.AppProperties;

public final class TracInstantProperties {

    private static final String TRAC_PWD = "TracPwd";
    private static final String TRAC_USERNAME = "TracUser";
    private static final int MAX_MRU = 8;

    private TracInstantProperties() {}

    private static final AtomicReference<AppProperties> s_SharedInstance = new AtomicReference<>();

    public static void initialise(String companyName, String appName) {
        s_SharedInstance.set(new AppProperties(companyName, appName));
        s_SharedInstance.get().loadProperties();
    }

    public static AppProperties get() {
        return s_SharedInstance.get();
    }

    public static String getUsername() {
        return get().getString(TRAC_USERNAME, "");
    }

    public static void addUsername(String username) {
        get().putString(TRAC_USERNAME, username);
    }

    public static String getPassword() {
        return get().getString(TRAC_PWD, "");
    }

    public static void addPassword(String password) {
        get().putString(TRAC_PWD, password);
    }

    public static String getURL() {
        List<String> urlList = getURL_MRU();
        return urlList.isEmpty() ? "" : urlList.get(0);
    }

    public static List<String> getURL_MRU() {
        return getStringList("TracURL_MRU", "[https://trac.edgewall.org]");
    }

    /** Whether the option to cache data was selected. */
    public static boolean getUseCache() {
        return get().getBoolean("CacheSlurpedData", true);
    }

    public static void setUseCache(boolean bool) {
        get().putBoolean("CacheSlurpedData", bool);
    }

    public static boolean getActiveTicketsOnly() {
        return TracInstantProperties.get().getBoolean("FetchActiveTicketsOnly", false);
    }

    public static void setActiveTicketsOnly(boolean b) {
        TracInstantProperties.get().putBoolean("FetchActiveTicketsOnly", b);
    }

    public static void addURL_MRU(String urlText) {
        addMRU("TracURL_MRU", urlText);
    }

    public static String getAttachmentsDir() {
        List<String> dirList = getAttachmentsDir_MRU();
        return dirList.isEmpty() ? "" : dirList.get(0);
    }

    public static List<String> getAttachmentsDir_MRU() {
        return getStringList("AttachmentsDir_MRU", "");
    }

    public static void addAttachmentsDir_MRU(String dir) {
        addMRU("AttachmentsDir_MRU", dir);
    }

    public static void putStringList(String key, List<String> list) {
        get().putString(key, list.toString());
    }

    public static List<String> getStringList(String key, String defaultStringList) {
        List<String> result = new ArrayList<>();
        String strList = get().getValue(key);
        if (strList == null) {
            strList = defaultStringList;
            if (strList.length() > 2) {
                get().putString(key, defaultStringList);
            }
        }
        if (strList.startsWith("[") && strList.endsWith("]")) {
            String[] strs = strList.substring(1, strList.length() - 1).split(",");
            for (String s : strs) {
                result.add(s.trim());
            }
        }
        return result;
    }

    private static void addMRU(String key, String value) {
        List<String> list = getStringList(key, "");
        while (list.remove(value)) {
            // carry on
        }
        list.add(0, value);
        if (list.size() > MAX_MRU) {
            list.remove(list.size() - 1);
        }
        putStringList(key, list);
    }

    public static Rectangle getRectangle(String property, Rectangle defaultValue) {
        String encodedString = get().getValue(property);
        try {
            Map<String, String> map = parseKeyValuePairs(encodedString);
            Rectangle r = new Rectangle();
            r.x = Integer.valueOf(map.get("x"));
            r.y = Integer.valueOf(map.get("y"));
            r.width = Integer.valueOf(map.get("width"));
            r.height = Integer.valueOf(map.get("height"));
            return r;
        } catch (ParseFailed | NumberFormatException ex) {
        }
        return defaultValue;
    }

    private static final class ParseFailed extends Exception {}
    
    private static Map<String, String> parseKeyValuePairs(String str) throws ParseFailed {
        if (str == null || str.isEmpty()) {
            throw new ParseFailed();
        }
        if (str.endsWith("]")) {
            int bracket = str.indexOf('[');
            if (bracket == -1) {
                throw new ParseFailed();
            }
            str = str.substring(bracket + 1, str.length() - 1);
        }
        String[] pairs = str.split(",");
        Map<String, String> result = new HashMap<>(pairs.length);
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length != 2) {
                throw new ParseFailed();
            }
            result.put(keyValue[0].trim(), keyValue[1].trim());
        }
        return result;
    }
}
