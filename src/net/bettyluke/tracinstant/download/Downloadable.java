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

package net.bettyluke.tracinstant.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import net.bettyluke.tracinstant.prefs.TracInstantProperties;

public abstract class Downloadable {
    
    private final int m_TicketNumber;
    
    public abstract String getDescription();
    public abstract InputStream createInputStream() throws IOException;
    public abstract String getFileName();
    
    public abstract long size();
    protected Downloadable(int ticketNumber) {
        m_TicketNumber = ticketNumber;
    }
    
    public final int getTicketNumber() {
        return m_TicketNumber;
    }
    
    public static final class FileDownloadable extends Downloadable {
        
        private final File m_File;
        
        public FileDownloadable(int ticketNumber, File f) {
            super(ticketNumber);
            m_File = f;
        }
    
        public File getFile() {
            return m_File;
        }
        
        @Override
        public String getDescription() {
            return m_File.getAbsolutePath();
        }
        
        @Override
        public InputStream createInputStream() throws IOException {
            return new FileInputStream(m_File);
        }
    
        @Override
        public String getFileName() {
            return m_File.getName();
        }

        @Override
        public long size() {
            return m_File.length();
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
                return new URL(TracInstantProperties.getURL() +
                    m_URL + "?format=raw").openStream();
            } catch (MalformedURLException ex) {
                throw new IOException(ex.getMessage(), ex);
            }
        }

        @Override
        public String getFileName() {
            return decode(m_URL.substring(m_URL.lastIndexOf('/') + 1));
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