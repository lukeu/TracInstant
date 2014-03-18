package net.bettyluke.util;

import java.net.URLConnection;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class HttpsUtils {
    private HttpsUtils() {}

    /**
     * Code origin: http://code.google.com/p/misc-utils/wiki/JavaHttpsUrl
     * <p>
     * Quote: "be warned! The key and certificate system was not created for you to bypass it."
     */
    public static void blindlyTrustAnySslCertificates(URLConnection urlCon) {
        if (!(urlCon instanceof HttpsURLConnection)) {
            return;
        }

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType ) {
            }
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType ) {
            }
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        } };

        // Install the all-trusting trust manager
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance( "SSL" );
            sslContext.init( null, trustAllCerts, new java.security.SecureRandom() );
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Create an ssl socket factory with our all-trusting manager
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        // Tell the url connection to use our socket factory which bypasses security checks
        ((HttpsURLConnection) urlCon).setSSLSocketFactory(sslSocketFactory);
    }
}
