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

package net.bettyluke.tracinstant.plugins;

public interface TicketUpdater {

        /**
         * Whether changes to this field will be persisted locally by the application.
         * Can be used to create writable fields associated with tickets.
         * The default state of all fields is non-persistent.
         *
         * @param persistent true: values written to the named field will be persisted.
         *        false: field values will only be remembered until the application exits.
         */
        void identifyUserField(String fieldName, boolean persistent);

        void setTicketField(int ticketId, String field, String value);

// Ideas...
//        void deleteTicket(int ticketNumber);

    }