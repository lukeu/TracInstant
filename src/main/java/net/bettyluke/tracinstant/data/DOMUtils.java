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

import java.util.Iterator;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class to reduce the verbosity of pulling data out of a DOM.
 *
 * NOTE: Why do I get the feeling XML-Pull parser is what's wanted instead? I.e. when only
 * reading XML. Shame I don't have an Internet connection to read up on that right now :-)
 */
public class DOMUtils {

    /**
     * A little bridge between DOM methods and Java 5/6 Iterators, to help write
     * simple parsers concisely. (For situations where TreeWalker and NodeIterator
     * might be considered overkill.)
     */
    private static final class ChildIterator <E extends Node>
            implements Iterable<E>, Iterator<E> {

        private final Class<E> m_Clazz;
        private E m_Next;

        /**
         * An iterator that iterates all children of a given type
         * @param typeToMatch Only Nodes of this type are considered.
         * @param elem The element containing the children to iterate.
         */
        public ChildIterator(Class<E> typeToMatch, Element elem) {
            m_Clazz = typeToMatch;
            advance(elem.getFirstChild());
        }

        @Override
        public Iterator<E> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return m_Next != null;
        }

        @Override
        public E next() {
            E next = m_Next;
            advance(next.getNextSibling());
            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void advance(Node node) {
            while (node != null && !m_Clazz.isInstance(node)) {
                node = node.getNextSibling();
            }
            m_Next = m_Clazz.cast(node);
        }
    }

    public static Iterable<Node> iterateChildNodes(Element elem) {
        return new ChildIterator<>(Node.class, elem);
    }

    public static Iterable<Element> iterateChildElements(Element elem) {
        return new ChildIterator<>(Element.class, elem);
    }

    public static Element findFirstChildElement(Element elem) {
        ChildIterator<Element> iter = new ChildIterator<>(Element.class, elem);
        return iter.hasNext() ? iter.next() : null;
    }

    public static Element findFirstChildElementNamed(Element elem, String tagName) {
        for (Element child : iterateChildElements(elem)) {
            if (tagName.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }
}
