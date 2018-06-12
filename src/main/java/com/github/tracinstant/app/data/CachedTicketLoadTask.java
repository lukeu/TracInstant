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

package com.github.tracinstant.app.data;

import java.io.IOException;
import java.util.List;

public class CachedTicketLoadTask extends TicketLoadTask {
    public CachedTicketLoadTask(SiteData site) {
        super(site);
    }

    @Override
    protected List<String> doInBackground() throws IOException, InterruptedException {
        TicketProvider provider;

        publish(new Update("Loading from cache...", "Loading table data from local cache."));
        provider = SiteData.loadTicketData(SiteData.TABULAR_CACHE_FILE);
        publish(new Update(provider));

        publish(new Update("Loading from cache...", "Loading descriptions from local cache."));
        provider = SiteData.loadTicketData(SiteData.HIDDEN_FIELDS_CACHE_FILE);
        publish(new Update(provider));

        List<String> modifiedDates = extractModificationDates(provider.getTickets());

        // All data for external consumption has been passed out via the publish/process mechanism.
        // Here we return just the timestamps to update the 'last-modified' record in SiteData.
        return modifiedDates;
    }
}
