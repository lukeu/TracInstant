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

package net.bettyluke.tracinstant.data;

import java.io.IOException;

public class CachedTicketLoadTask extends TicketLoadTask {
    public CachedTicketLoadTask(TicketTableModel tableModel) {
        super(tableModel);
    }

    @Override
    protected Void doInBackground() throws IOException, InterruptedException {
        TicketProvider provider;

        publish(new Update("Loading from cache...", "Loading table data from local cache."));
        provider = SiteData.loadTicketData(SiteData.TABULAR_CACHE_FILE);
        publish(new Update(provider));

        publish(new Update("Loading from cache...", "Loading descriptions from local cache."));
        provider = SiteData.loadTicketData(SiteData.HIDDEN_FIELDS_CACHE_FILE);
        publish(new Update(provider));

        // All data was passed (and must be consumed) via the publish/process mechanism
        return null;
    }
}
