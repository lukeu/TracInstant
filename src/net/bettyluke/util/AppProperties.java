
package net.bettyluke.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Makes properties a little more convenient and a little more type-safe.
 */
public class AppProperties {

    Properties m_Props = new Properties();
    private final String m_AppName;
    private final String m_CompanyName;
    
    public AppProperties(String companyName, String appName) {
        m_CompanyName = companyName;
        m_AppName = appName;
    }
    
    public void remove(String key) {
        m_Props.remove(key);
    }

    public void putString(String key, String value) {
        m_Props.put(key, value);
    }
    
    public String getValue(String key) {
        return (String) m_Props.get(key);
    }
    
    public String getString(String key, String fallback) {
        String result = getValue(key);
        return (result == null) ? fallback : result;
    }
    
    /**
     * @return The string value for the first item in 'keys' that maps to a non-null
     *         value, otherwise fallback.
     */
    public String getString(String[] keys, String fallback) {
        String result = null;
        for (int n = 0; n < keys.length; n++) {
            result = getValue(keys[n]);
            if (result != null) {
                break;
            }
        }
        return (result == null) ? fallback : result;
    }

    
    public void putBoolean(String key, boolean b) {
        m_Props.put(key, "" + b);
    }
    
    public boolean getBoolean(String key, boolean fallback) {
        String value = getValue(key);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        return fallback;
    }

    public void putInt(String key, int value) {
        m_Props.put(key, Integer.toString(value));
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(getValue(key));
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
    
    public int getBoundedInt(String key, int fallback, int min, int max) {
        if (fallback < min || fallback > max) {
            throw new IllegalArgumentException();
        }
        int result = getInt(key, fallback);
        return Math.max(min, Math.min(max, result));
    }

    public void putFilePath(String key, File value) {
        m_Props.put(key, value.getPath());
    }
    
    public File getFilePath(String key, File fallback) {
        String s = (String) m_Props.get(key);
        return (s == null) ? fallback : new File(s);
    }

    /**
     * @param keys List of keys to search for.
     * @return Value for the first item in 'keys' that return a non-null value.
     */
    public File getFilePath(String keys[], File fallback) {
        File result = null;
        for (int n = 0; n < keys.length; n++) {
            result = getFilePath(keys[n], fallback);
            if (result != null) {
                break;
            }
        }
        return result;
    }
    
    /**
     * @return The path specified by 'key', if the file actually exists on the
     *         file system, otherwise null.
     */
    public File getExistingFilePath(String key) {
        String s = (String) m_Props.get(key);
        File f = (s == null) ? null : new File(s);
        return (f != null && f.exists()) ? f : null;
    }

    /**
     * @param keys List of keys to search for.
     * @return Value for the first item in 'keys' that return a non-null value.
     */
    public File getExistingFilePath(String keys[]) {
        File result = null;
        for (int n = 0; n < keys.length; n++) {
            result = getExistingFilePath(keys[n]);
            if (result != null) {
                break;
            }
        }
        return result;
    }
    
    public Properties getProperties() {
        return m_Props;
    }
    
    public void saveProperties() throws IOException {
        File outFile = getPropertiesFilePath();
        File dir = outFile.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create file: " + outFile);
        }
        
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(outFile);
            m_Props.store(out, m_AppName + " Properties");
        } finally {
            FileUtils.close(out);
        }
    }


    public void loadProperties() {
        FileInputStream in = null;
        try {
            in = new FileInputStream(getPropertiesFilePath());
            m_Props.load(in);
        } catch (IOException ex) {
            System.out.println("Could not load preferences, defaults in use.\n  Reason: "
                + ex.getMessage());
        } finally {
            FileUtils.close(in);
        }
    }

    private File getPropertiesFilePath() throws IOException {
        File dir = getAppDataDirectory();
        return new File(dir, m_AppName + ".properties");
    }

    /** Gets the data directory for this application. */
    public File getAppDataDirectory() throws IOException {
        StringBuilder path = new StringBuilder();
        path.append(getGeneralDataDirectory().getPath());
        if (m_CompanyName != null) {
            path.append(File.separator);
            path.append(m_CompanyName);
        }
        path.append(File.separator);
        path.append(m_AppName);
        return new File(path.toString());
    }

    protected File getGeneralDataDirectory() throws IOException {
        File baseDir = null;
        String homePath = (String) System.getProperties().get("user.home");
        if (homePath != null) {
            baseDir = new File(homePath);
        }
        if (baseDir == null || !baseDir.isDirectory()) {
            throw new IOException("User home directory not found.");
        }
        File f = new File(baseDir, "Application Data");
        return f.isDirectory() ? f : baseDir;
    }
}
