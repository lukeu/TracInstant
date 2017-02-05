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

package net.bettyluke.tracinstant.ui;

import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import net.bettyluke.swing.ScrollingMenu;

public class MenuCascader {

    private static final int MAX_COMPACT_TOP_HITS = 20;

    interface Item extends Action {
        String getName();

        int getHits();
    }

    private final int maxSubMenuSize;
    private final int minSubMenuSize;

    private static String m_LastName, m_NextName;

    public MenuCascader() {
        this(35, 15);
    }

    public MenuCascader(int maxSubMenuSize, int minSubMenuSize) {
        if (maxSubMenuSize < 2) {
            throw new IllegalArgumentException(
                    "MenuCascader requires a maximum target size of at least 2");
        }
        if (minSubMenuSize < 1) {
            throw new IllegalArgumentException(
                    "MenuCascader requires a minimum target size of at least 2");
        }
        this.maxSubMenuSize = maxSubMenuSize;
        this.minSubMenuSize = minSubMenuSize;
    }

    public JPopupMenu create(List<Item> items) {
        if (items.size() <= maxSubMenuSize) {
            return createTinyMenu(items);
        }
        JPopupMenu menu = createCascadedMenu(items);
        insertTopHits(menu, items);
        return menu.getComponentCount() < 40 ? menu : new ScrollingMenu(menu, 40);
    }

    private void insertTopHits(JPopupMenu menu, List<Item> items) {
        int nSubMenus = menu.getComponentCount();
        int compact = Math.min(MAX_COMPACT_TOP_HITS, maxSubMenuSize - nSubMenus - 2 - 5);
        if (compact > minSubMenuSize) {
            List<Item> topHits = getTopHits(items, compact);
            menu.insert(createHeadingPanel("All"), 0);
            menu.add(createHeadingPanel("Top"));
            addAllToMenu(menu, topHits);
        } else {
            List<Item> topHits = getTopHits(items, maxSubMenuSize - 5);
            JMenu subMenu = new JMenu("Top");
            styleHeadingComponent(subMenu);
            addAllToMenu(subMenu, topHits);
            menu.insert(subMenu, 0);
        }
    }

    private JPanel createHeadingPanel(String text) {
        JPanel result = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        JLabel label = new JLabel(" " + text + " ");
        styleHeadingComponent(label);
        result.add(label);
        return result;
    }

    private void styleHeadingComponent(JComponent comp) {
        Font font = comp.getFont();
        comp.setOpaque(true);
        comp.setFont(font.deriveFont(Font.ITALIC | Font.BOLD));
    }

    private List<Item> getTopHits(List<Item> items, int count) {
        Item[] sorted = items.toArray(new Item[0]);

        // Sort by number of hits. Note: this uses a stable sort.
        Arrays.sort(sorted, new Comparator<Item>() {
            @Override
            public int compare(Item o1, Item o2) {
                return -(o1.getHits() - o2.getHits());
            }
        });

        // Trim to length.
        sorted = Arrays.copyOf(sorted, count);

        // Resort alphabetically.
        Arrays.sort(sorted, new Comparator<Item>() {
            @Override
            public int compare(Item o1, Item o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        return Arrays.asList(sorted);
    }

    private JPopupMenu createTinyMenu(List<Item> items) {
        final JPopupMenu menu = new JPopupMenu();
        addAllToMenu(menu, items);
        return menu;
    }

    private JPopupMenu createCascadedMenu(List<Item> items) {
        final JPopupMenu menu = new JPopupMenu();
        m_LastName = "";

        // Destroy a copy of the list, not the passed argument.
        items = new LinkedList<Item>(items);

        List<Item> batch = new ArrayList<Item>();
        int depth = 1;
        while (!items.isEmpty()) {
            String firstName = items.get(0).getName();
            if (firstName.length() < depth) {
                transferFromHead(items, 1, batch);
                continue;
            }
            String start = firstName.substring(0, depth);

            int count = countItemsBeginingWith(items, start);
            if (count + batch.size() <= maxSubMenuSize) {
                transferFromHead(items, count, batch);
            } else if (batch.size() >= minSubMenuSize) {
                menu.add(createSubMenu(batch, items));
                batch.clear();
                depth = 1;
            } else {
                ++depth;
            }
        }
        menu.add(createSubMenu(batch, items));
        return menu;
    }

    private JMenu createSubMenu(List<Item> batch, List<Item> subsequent) {
        m_NextName = subsequent.isEmpty() ? "" : subsequent.get(0).getName();
        String from = batch.get(0).getName();
        String to = batch.get(batch.size() - 1).getName();
        final JMenu subMenu = new JMenu(createMenuName(from, to));
        addAllToMenu(subMenu, batch);
        m_LastName = to;
        return subMenu;
    }

    private String createMenuName(String from, String to) {
        from = abbreviate(from, m_LastName);
        to = abbreviate(to, m_LastName, m_NextName);
        if (from.equalsIgnoreCase(to)) {
            return titleCaps(from);
        }
        return titleCaps(from) + " - " + titleCaps(to);
    }

    /** Return the shortest substring from 'name' that distinguishes it from 'against' */
    private String abbreviate(String name, String... others) {
        next_char: for (int i = 0; i < name.length(); ++i) {
            for (String other : others) {
                if (i < other.length() && charsMatch(name, other, i)) {
                    continue next_char;
                }
            }
            return name.substring(0, i + 1);
        }
        return name;
    }

    private boolean charsMatch(String s1, String s2, int index) {
        return Character.toUpperCase(s1.charAt(index)) == Character.toUpperCase(s2.charAt(index));
    }

    private String titleCaps(String str) {
        if (str.isEmpty()) {
            return "";
        }

        String result = str.substring(0, 1).toUpperCase();
        return (str.length() == 1) ? result : result + str.substring(1).toLowerCase();
    }

    private int countItemsBeginingWith(List<Item> items, String start) {
        int count = 0;
        for (Item item : items) {
            String text = item.getName();
            if (text.startsWith(start) || text.toUpperCase().startsWith(start.toUpperCase())) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private void transferFromHead(List<Item> from, int count, List<Item> to) {
        for (int i = 0; i < count; ++i) {
            to.add(from.remove(0));
        }
    }

    private void addAllToMenu(final JMenu menu, List<Item> items) {
        for (JMenuItem mi : createMenuItems(items)) {
            menu.add(mi);
        }
    }

    private void addAllToMenu(final JPopupMenu menu, List<Item> items) {
        for (JMenuItem mi : createMenuItems(items)) {
            menu.add(mi);
        }
    }

    private List<JMenuItem> createMenuItems(List<Item> items) {
        List<JMenuItem> menuItems = new ArrayList<JMenuItem>(items.size());
        for (Item item : items) {
            JMenuItem mi = new JMenuItem(item);
            menuItems.add(mi);
        }
        return menuItems;
    }
}
