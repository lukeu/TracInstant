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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingWorker;

import net.bettyluke.tracinstant.download.AttachmentDownloader.Result;
import net.bettyluke.tracinstant.download.Target.State;
import net.bettyluke.util.FileUtils;

public class AttachmentDownloader extends SwingWorker<Void, Result> {

    private final List<Target> downloadQueue = new ArrayList<>();
    private DownloadModel model;
    private Runnable allDoneCallback;

    protected static class Result {
        public Result(Target target, State newState) {
            this.target = target;
            this.newState = newState;
        }

        Target target;
        State newState;
    }

    AttachmentDownloader(DownloadModel model) {
        this(model, null);
    }

    AttachmentDownloader(DownloadModel model, Runnable doneCallback) {
        this.model = model;
        this.allDoneCallback = doneCallback;
    }

    public void add(Target target) {
        downloadQueue.add(target);
    }

    @Override
    protected Void doInBackground() throws Exception {
        for (Target target : downloadQueue) {
            if (isCancelled()) {
                return null;
            }
            publish(new Result(target, State.STARTED));
            File outFile = model.getAbsolutePath(target).toFile();
            outFile.getParentFile().mkdirs();
            try {
                FileUtils.copyAndClose(
                    target.getSource().createInputStream(),
                    new FileOutputStream(outFile));
                publish(new Result(target, State.ENDED));
            } catch (IOException ex) {
                target.setErrorMessage(ex.toString());
                publish(new Result(target, State.ERROR));
            }
        }
        return null;
    }

    @Override
    protected void process(List<Result> chunks) {
        for (Result result : chunks) {
            model.setTargetState(result.target, result.newState);
        }
    }

    @Override
    protected void done() {
        if (allDoneCallback != null) {
            allDoneCallback.run();
        }
    }
}
