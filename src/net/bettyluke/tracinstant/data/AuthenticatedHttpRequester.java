package net.bettyluke.tracinstant.data;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import net.bettyluke.tracinstant.prefs.SiteSettings;

public final class AuthenticatedHttpRequester {
    private AuthenticatedHttpRequester() {}

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
