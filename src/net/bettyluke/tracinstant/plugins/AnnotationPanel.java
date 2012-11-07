
package net.bettyluke.tracinstant.plugins;

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

import net.bettyluke.tracinstant.data.Ticket;

public class AnnotationPanel extends JPanel {
    public static ToolPlugin createPlugin() {
        return new AnnotationPanel().new Plugin();
    }
    
    private int selectedTicketId = -1;
    private TicketUpdater m_Updater;
    
    private JTextComponent m_Text;
    
    /** Implement the interface by which the Main Frame interacts with us. */
    public class Plugin extends ToolPlugin {

        private static final String ANNOTATION_FIELD = "Annotation";

        @Override
        public JComponent initialise(TicketUpdater updater) {
            updater.identifyUserField(ANNOTATION_FIELD, true);
            m_Updater = updater;
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
        m_Text = createEditor();
        add(new JScrollPane(m_Text));
        Dimension dim = new Dimension(200, 50);
        setPreferredSize(dim);
        setMinimumSize(dim);
    }

    private JTextComponent createEditor() {
        JTextArea result = new JTextArea();
        result.setBackground(new Color(255,255,230));
        result.addFocusListener(new FocusListener() {
            public void focusLost(FocusEvent e) {
                storeAnnotationInTicket();
            }
            public void focusGained(FocusEvent e) {
            }
        });
        return result;
    }

    protected void storeAnnotationInTicket() {
        if (m_Text.isEnabled() && selectedTicketId >= 0) {
            String text = m_Text.getText();
            if (text.isEmpty()) {
                text = null;
            }
            m_Updater.setTicketField(selectedTicketId, "Annotation", text);
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
        m_Text.setEnabled(true);
        m_Text.setEditable(true);
        m_Text.setText(text);
    }
    
    private void hideText() {
        m_Text.setText("");
        m_Text.setEnabled(false);
        m_Text.setEditable(false);
    }
}
