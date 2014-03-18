package net.bettyluke.tracinstant.data;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import net.bettyluke.util.HttpsUtils;

import net.bettyluke.tracinstant.prefs.SiteSettings;

public final class AuthenticatedHttpRequester {
    private AuthenticatedHttpRequester() {}

    public static InputStream openStreamBlindlyTrustingAnySslCertificates(
            SiteSettings settings, URL url) throws IOException {
        return openStreamBlindlyTrustingAnySslCertificates(getUrlConnection(settings, url));
    }

    public static InputStream openStreamBlindlyTrustingAnySslCertificates(URLConnection urlCon)
            throws IOException {
        HttpsUtils.blindlyTrustAnySslCertificates(urlCon);
        return urlCon.getInputStream();
    }

    public static URLConnection getUrlConnection(SiteSettings settings, URL url) throws IOException {
        URLConnection uc = url.openConnection();

        if (!settings.getUsername().isEmpty()) {
            String userpass = settings.getUsername() + ":" + settings.getPassword();
            String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(
                    userpass.getBytes());
            uc.setRequestProperty ("Authorization", basicAuth);
        }
        return uc;
    }
}
