
package com.github.tracinstant.app.data;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import com.github.tracinstant.app.data.Ticket;
import org.junit.Test;

public class TicketTest {

    @Test
    public void testCompatibilityWithTrac1_0() {
        Ticket t = new Ticket(1);
        assertNull(t.getValue("changetime"));
        assertNull(t.getValue("Modified"));

        t.putField("changetime", "");
        assertThat(t.getValue("changetime"), is(""));
        assertThat(t.getValue("Modified"), is(""));

        t.putField("Modified", "");
        assertThat(t.getValue("changetime"), is(""));
        assertThat(t.getValue("Modified"), is(""));

        t.putField("Modified", "A");
        assertThat(t.getValue("changetime"), is("A"));
        assertThat(t.getValue("Modified"), is("A"));

        t.putField("Modified", "");
        t.putField("changetime", "C");
        assertThat(t.getValue("changetime"), is("C"));
        assertThat(t.getValue("Modified"), is("C"));

        t.putField("Modified", "M");
        assertThat(t.getValue("changetime"), is("C"));
        assertThat(t.getValue("Modified"), is("M"));
    }
}
