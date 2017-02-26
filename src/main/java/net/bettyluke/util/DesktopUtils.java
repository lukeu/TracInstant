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
import java.util.Arrays;

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
            browseToPlanB(url);
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
        }
    }

    private static void browseToPlanB(URL url) {
        for (String command : Arrays.asList("kde-open", "open")) {
            if (browseToPlanB(command, url)) {
                break;
            }
        }
    }

    private static boolean browseToPlanB(String command, URL url) {
        String[] cmdarray = { '"' + command + '"', '"' + url.toString() + '"' };
        try {
            Process process = Runtime.getRuntime().exec(cmdarray);
            if (process != null) {
                try {
                    // NB: Getting an IllegalThreadStateException means the process is still running
                    process.exitValue();
                } catch (IllegalThreadStateException ex) {
                    return true;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            // Ignore
        }
        return false;
    }
}
