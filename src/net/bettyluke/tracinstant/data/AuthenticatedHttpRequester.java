package net.bettyluke.tracinstant.data;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import net.bettyluke.tracinstant.prefs.SiteSettings;

public final class AuthenticatedHttpRequester {

    // Some totally INSECURE code from SO to to make HTTPS links work...
    //
    //     http://stackoverflow.com/a/2793153/932359
    //
    // TODO: Security & certificates are not my area. Can anyone improve on this??
    //
    static {

        TrustManager[] trustAllCertificates = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null; // Not relevant.
                }
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // Do nothing. Just allow them all.
                }
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // Do nothing. Just allow them all.
                }
            }
        };

        HostnameVerifier trustAllHostnames = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true; // Just allow them all.
            }
        };

        try {
            System.setProperty("jsse.enableSNIExtension", "false");
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCertificates, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames);
        }
        catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private AuthenticatedHttpRequester() {
        // First set the default cookie manager.
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    public static InputStream getInputStream(SiteSettings settings, URL url) throws IOException {
        URLConnection uc = url.openConnection();

        if (!settings.getUsername().isEmpty()) {
            String userpass = settings.getUsername() + ":" + settings.getPassword();
            String basicAuth = "Basic " +
                    javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());

// Note: change to the following when moving to Java 8, as the above will stop working in Java 9.
//                    Base64.getEncoder().encodeToString(userpass.getBytes());
            uc.setRequestProperty ("Authorization", basicAuth);
        }

        return uc.getInputStream();
    }
}
