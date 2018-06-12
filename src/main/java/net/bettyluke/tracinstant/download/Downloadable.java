/*
 * Copyright 2011 the original author or authors.
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

package net.bettyluke.tracinstant.download;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.bettyluke.tracinstant.data.AuthenticatedHttpRequester;
import net.bettyluke.tracinstant.prefs.SiteSettings;
import net.bettyluke.tracinstant.prefs.TracInstantProperties;

public abstract class Downloadable {

    private final int m_TicketNumber;

    public abstract String getDescription();

    public abstract InputStream createInputStream() throws IOException;

    public abstract Path getRelativePath();

    public abstract long size();

    protected Downloadable(int ticketNumber) {
        m_TicketNumber = ticketNumber;
    }

    public final int getTicketNumber() {
        return m_TicketNumber;
    }

    public static final class FileDownloadable extends Downloadable {

        private final Path ticketDir;
        private final Path relativePath;

        /**
         * @param ticketDir Subdirectory of the
         */
        public FileDownloadable(int ticketNumber, Path ticketDir, Path path) {
            super(ticketNumber);

            // NB: unsafe assertion due to race conditions where the file/dir could be deleted.
            // Don't include in release checks!
            assert ticketDir.isAbsolute() && Files.isDirectory(ticketDir);
            assert !path.isAbsolute() && !Files.isDirectory(ticketDir.resolve(path));

            this.ticketDir = ticketDir;
            relativePath = path;
        }

        @Override
        public String getDescription() {
            return "" + relativePath;
        }

        @Override
        public InputStream createInputStream() throws IOException {
            return new FileInputStream(getAbsolutePath().toFile());
        }

        @Override
        public Path getRelativePath() {
            return relativePath;
        }

        @Override
        public long size() {
            return getAbsolutePath().toFile().length();
        }

        private Path getAbsolutePath() {
            return ticketDir.resolve(relativePath);
        }
    }

    public static final class TracDownloadable extends Downloadable {

        private final String m_URL;
        private final long m_Length;

        public TracDownloadable(int ticketNumber, String url, long length) {
            super(ticketNumber);
            m_URL = url;
            m_Length = length;
        }

        @Override
        public String getDescription() {
            return decode(m_URL);
        }

        @Override
        public InputStream createInputStream() throws IOException {
            try {
                URL url = new URL(TracInstantProperties.getURL() + m_URL + "?format=raw");
                return AuthenticatedHttpRequester.getInputStream(SiteSettings.getInstance(), url);
            } catch (MalformedURLException ex) {
                throw new IOException(ex.getMessage(), ex);
            }
        }

        @Override
        public Path getRelativePath() {
            return Paths.get(decode(m_URL.substring(m_URL.lastIndexOf('/') + 1)));
        }

        private String decode(String name) {
            try {
                return URLDecoder.decode(name, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return name;
            }
        }

        @Override
        public long size() {
            return m_Length;
        }
    }
}