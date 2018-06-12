
package com.github.tracinstant.app.plugins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;

import com.github.tracinstant.app.data.Ticket;

public class AnnotationPanel extends JPanel {

    public static ToolPlugin createPlugin() {
        return new AnnotationPanel().new Plugin();
    }

    private int selectedTicketId = -1;
    private TicketUpdater updater;
    private JTextComponent editor;

    /** The interface through which the application interacts with us. */
    private class Plugin extends ToolPlugin {

        private static final String ANNOTATION_FIELD = "Annotation";

        @Override
        public JComponent initialise(TicketUpdater tu) {
            tu.identifyUserField(ANNOTATION_FIELD, true);
            updater = tu;
            return AnnotationPanel.this;
        }

        @Override
        public void ticketViewUpdated(Ticket[] inView, Ticket[] selected) {
            showAnnotationsForSelected(selected);
        }

        @Override
        public String toString() {
            return "Annotate";
        }

        @Override
        public void hidden() {
            // Ensure data is saved when the editor goes out of view.
            // Includes shut-down.
            storeAnnotationInTicket();
            deselect();
        }
    }

    public AnnotationPanel() {
        super(new BorderLayout());
        editor = createEditor();
        add(new JScrollPane(editor));
        Dimension dim = new Dimension(200, 50);
        setPreferredSize(dim);
        setMinimumSize(dim);
    }

    private JTextComponent createEditor() {
        JTextArea result = new JTextArea();
        result.setBackground(new Color(255, 255, 230));
        result.addFocusListener(new FocusListener() {
            @Override
            public void focusLost(FocusEvent e) {
                storeAnnotationInTicket();
            }

            @Override
            public void focusGained(FocusEvent e) {
            }
        });
        return result;
    }

    protected void storeAnnotationInTicket() {
        if (editor.isEnabled() && selectedTicketId >= 0) {
            String text = editor.getText();
            if (text.isEmpty()) {
                text = null;
            }
            updater.setTicketField(selectedTicketId, "Annotation", text);
        }
    }

    protected void deselect() {
        showAnnotationsForSelected(new Ticket[0]);
    }

    protected final void showAnnotationsForSelected(Ticket[] selected) {
        if (selected.length == 1) {
            int newId = selected[0].getNumber();
            if (selectedTicketId != newId) {
                selectedTicketId = newId;
                String annotation = selected[0].getValue("Annotation");
                showText(annotation == null ? "" : annotation);
            }
        } else {
            selectedTicketId = -1;
            hideText();
        }
    }

    private void showText(String text) {
        editor.setEnabled(true);
        editor.setEditable(true);
        editor.setText(text);
    }

    private void hideText() {
        editor.setText("");
        editor.setEnabled(false);
        editor.setEditable(false);
    }
}
