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

public class Target {
    public enum State {
        IDLE, STARTED, ENDED, ERROR
    }

    private final Downloadable source; // TODO: Consider refactoring out, once the rest is working.
    private boolean alreadyExists;
    private File targetFile;
    private State state = State.IDLE;
    private long bytesDownloaded = 0;
    private String errorMessage = null;
    private boolean selected = true;
    private File topFolder;

    public Target(File topFolder, Downloadable source) {
        this.source = source;
        this.topFolder = topFolder;
        updateTargetFile();
    }

    private void updateTargetFile() {
        File folder = new File(topFolder, Integer.toString(source.getTicketNumber()));
        targetFile = new File(folder, source.getFileName());
        alreadyExists = targetFile.exists();
    }

    public void setTopFolder(File topFolder) {
        this.topFolder = topFolder;
        updateTargetFile();
    }

    public boolean isOverwriting() {
        return alreadyExists;
    }

    public Downloadable getSource() {
        return source;
    }

    public File getTargetFile() {
        return targetFile;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long getBytesDownloaded() {
        return bytesDownloaded;
    }

    public void setBytesDownloaded(long bytesDownloaded) {
        this.bytesDownloaded = bytesDownloaded;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "" + source.getTicketNumber() + File.separator + source.getFileName();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean b) {
        selected = b;
    }
}
