package net.bettyluke.tracinstant.data;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import net.bettyluke.tracinstant.prefs.SiteSettings;

public class AuthenticatedHttpRequester {

    private final SiteSettings siteSettings;

    public AuthenticatedHttpRequester(SiteSettings siteSettings) {
        this.siteSettings = siteSettings;
    }

    public boolean canAuthenticate(URL url) {
        try {
            getInputStream(url).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public InputStream getInputStream(URL url) throws IOException {
        URLConnection uc = url.openConnection();

        if (!siteSettings.getUsername().isEmpty()) {
            String userpass = siteSettings.getUsername() + ":" + siteSettings.getPassword();
            String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(
                    userpass.getBytes());
            uc.setRequestProperty ("Authorization", basicAuth);
        }

        return uc.getInputStream();
    }
}
