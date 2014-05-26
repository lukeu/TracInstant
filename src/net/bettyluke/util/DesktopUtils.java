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
package net.bettyluke.util;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public final class DesktopUtils {
    private DesktopUtils() {}

    public static void browseTo(URL url) {
        if (url == null) {
            return;
        }
        try {
            // Note, this can end up throwing UnsupportedOperationException
            // even if we use the various "is supported" checks provided
            // by Desktop.
            Desktop.getDesktop().browse(url.toURI());
        } catch (UnsupportedOperationException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
        }
    }
}
