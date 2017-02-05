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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

public class FileUtils {

    private static final int BUFFER_SIZE = 4 * 1024;

    public static void copyAndClose(InputStream from, OutputStream to) throws IOException {
        try {
            copy(from, to);
        } finally {
            from.close();
            to.close();
        }
    }

    public static void copy(InputStream from, OutputStream to) throws IOException {
        BufferedInputStream input = new BufferedInputStream(from);
        BufferedOutputStream output = new BufferedOutputStream(to);

        byte buf[] = new byte[BUFFER_SIZE];
        int nRead;
        while ((nRead = input.read(buf)) != -1) {
            output.write(buf, 0, nRead);
        }
        output.flush();
    }

    public static void close(Closeable cl) {
        try {
            if (cl != null) {
                cl.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static String copyInputStreamToString(InputStream is, String charset)
            throws IOException {
        StringBuilder out = new StringBuilder();
        copyInputStreamToStringBuilder(out, is, charset);
        return out.toString();
    }

    public static void copyInputStreamToStringBuilder(
            StringBuilder out, InputStream is, String charset) throws IOException {

        Reader in = new InputStreamReader(is, "UTF-8");
        final char[] buffer = new char[BUFFER_SIZE];
        for (int n; (n = in.read(buffer, 0, BUFFER_SIZE)) != -1;) {
            out.append(buffer, 0, n);
        }
    }
}
