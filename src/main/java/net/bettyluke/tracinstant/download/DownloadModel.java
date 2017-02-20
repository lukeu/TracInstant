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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

import net.bettyluke.tracinstant.data.Ticket;
import net.bettyluke.tracinstant.download.AttachmentCounter.CountCallback;
import net.bettyluke.tracinstant.download.Downloadable.FileDownloadable;
import net.bettyluke.tracinstant.download.Downloadable.TracDownloadable;

public class DownloadModel {

    public final class ListModelView extends AbstractListModel<Target> {

        int oldSize = 0;

        @Override
        public int getSize() {
            return targets.size();
        }

        @Override
        public Target getElementAt(int index) {
            return targets.get(index);
        }

        public void modifiedElementAt(int index) {
            fireContentsChanged(this, index, index);
        }

        public void allChanged() {
            int newSize = targets.size();
            if (oldSize > newSize) {
                fireIntervalRemoved(this, newSize, oldSize - 1);
                if (newSize > 0) {
                    fireContentsChanged(this, 0, newSize - 1);
                }
            } else {
                if (oldSize < newSize) {
                    fireIntervalAdded(this, oldSize + 1, newSize - 1);
                }
                if (oldSize > 0) {
                    fireContentsChanged(this, 0, oldSize - 1);
                }
            }
            oldSize = newSize;
        }
    }

    public enum State {
        IDLE, COUNTING, DOWNLOADING, CANCELLING
    }

    /** A list of event listeners for this component. */
    private final EventListenerList listenerList = new EventListenerList();
    private final ChangeEvent changeEvent = new ChangeEvent(this);

    private final List<Target> targets = new ArrayList<>();
    private final ListModelView listModel = new ListModelView();

    private Path bugsDir;

    private State state = State.IDLE;

    private AttachmentDownloader tracDownloader = null;
    private AttachmentDownloader fileDownloader = null;

    public Path getBugsFolder() {
        return bugsDir;
    }

    public void setBugsFolder(File bugsFolder) {
        this.bugsDir = bugsFolder.toPath();
        for (Target target : targets) {
            target.setTopFolder(this.bugsDir);
        }
        fireStateChanged();
    }

    public void addAll(List<? extends Downloadable> attachments) {
        for (Downloadable att : attachments) {
            targets.add(new Target(bugsDir, att));
        }
        fireStateChanged();
    }

    public ListModelView getListModel() {
        return listModel;
    }

    public void download() {
        setState(State.DOWNLOADING);

        Runnable doneRunner = new Runnable() {
            int countdown = 2;

            @Override
            public void run() {
                if (--countdown == 0) {
                    System.out.println("Downloader DONE");
                    setState(State.IDLE);
                    tracDownloader = null;
                    fileDownloader = null;
                }
            }
        };

        tracDownloader = new AttachmentDownloader(this, doneRunner);
        fileDownloader = new AttachmentDownloader(this, doneRunner);
        for (Target target : targets) {
            if (!target.isSelected()) {
                continue;
            }
            Downloadable source = target.getSource();
            if (source instanceof TracDownloadable) {
                tracDownloader.add(target);
            } else if (source instanceof FileDownloadable) {
                fileDownloader.add(target);
            } else {
                System.err.println("Unexpected source type: " + source);
            }
        }

        tracDownloader.execute();
        fileDownloader.execute();
    }

    public void cancelDownload() {
        if (tracDownloader != null) {
            assert state == State.DOWNLOADING;
            setState(State.CANCELLING);

            System.out.println("cancel TracDownloader");

            // Subtle: the fileDownloader can BECOME null during this call if it was
            // already complete, and the state will then become IDLE.
            tracDownloader.cancel(true);
        }

        if (fileDownloader != null) {
            assert state == State.DOWNLOADING;
            setState(State.CANCELLING);

            System.out.println("cancel fileDownloader");
            fileDownloader.cancel(true);
        }
    }

    public void setTargetState(Target target, Target.State newState) {
        if (newState == target.getState()) {
            return;
        }
        target.setState(newState);
        if (newState == Target.State.ENDED || newState == Target.State.ERROR) {
            if (newState == Target.State.ERROR) {
                System.err.println(target.getErrorMessage()); // TODO: Show somehow?
            } else {
                target.setSelected(false);
            }
            if (countComplete() == targets.size()) {
                setState(State.IDLE);
            }
        }
        fireStateChanged();
    }

    public int countComplete() {
        int complete = 0;
        for (Target t : targets) {
            if (t.getState() == Target.State.ENDED) {
                ++complete;
            }
        }
        return complete;
    }

    /**
     * Adds a <code>ChangeListener</code> to the list that is notified each time the model has
     * changed.
     */
    public void addChangeListener(ChangeListener l) {
        listenerList.add(ChangeListener.class, l);
    }

    /**
     * Removes a <code>ChangeListener</code> from the list that's notified each time the model has
     * changed.
     */
    public void removeChangeListener(ChangeListener l) {
        listenerList.remove(ChangeListener.class, l);
    }

    /**
     * Returns an array of all the <code>ChangeListener</code>s
     */
    public ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(ChangeListener.class);
    }

    public void fireStateChanged() {
        listModel.allChanged();
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ChangeListener.class) {
                ((ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
            }
        }
    }

    public boolean isBusy() {
        return state != State.IDLE;
    }

    public boolean isDownloading() {
        return state == State.DOWNLOADING;
    }

    public int getNumDownloads() {
        return targets.size();
    }

    public String getDownloadSummary() {
        int num = getNumDownloads();
        switch (state) {
        case COUNTING:
            // Will be displayed with a busy icon, no need to add "..." (for eg)
            return (num > 0) ? Integer.toString(num) : "";
        case DOWNLOADING:
        case CANCELLING:
            return countComplete() + " / " + countFilesToDownloadOrDownloaded();
        case IDLE:
            break;
        }
        return "" + num + "   ";
    }

    public void count(Ticket[] tickets) {

        // Simple dumb implementation. Ignore all count requests unless idle. Once idle,
        // user will need to change something to cause a recount. Should be fine.
        if (state == State.DOWNLOADING || state == State.CANCELLING) {
            return;
        }

        AttachmentCounter.restartCounting(tickets, new CountCallback() {
            @Override
            public void restart() {
                targets.clear();
                setState(State.COUNTING);
            }

            @Override
            public void downloadsFound(List<? extends Downloadable> attachments) {
                addAll(attachments);
            }

            @Override
            public void done() {
                setState(State.IDLE);
            }
        });
    }

    protected final void setState(State newState) {
        if (state != newState) {
            state = newState;
            fireStateChanged();
        }
    }

    public State getState() {
        return state;
    }

    public int countFilesToOverwrite() {
        int count = 0;
        for (Target target : targets) {
            if (target.isSelected() && target.isOverwriting()) {
                count++;
            }
        }
        return count;
    }

    public int countSelected() {
        int count = 0;
        for (Target target : targets) {
            if (target.isSelected()) {
                count++;
            }
        }
        return count;
    }

    public int countFilesToDownloadOrDownloaded() {
        int count = 0;
        for (Target target : targets) {
            if (target.isSelected() || target.getState() == Target.State.ENDED) {
                count++;
            }
        }
        return count;
    }

    public Path getAbsolutePath(Target target) {
        Downloadable source = target.getSource();
        return bugsDir.resolve("" + source.getTicketNumber()).resolve(source.getRelativePath());
    }
}
