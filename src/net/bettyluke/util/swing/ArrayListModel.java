package net.bettyluke.util.swing;

import java.util.Arrays;
import java.util.Collection;

import javax.swing.AbstractListModel;

public final class ArrayListModel<T> extends AbstractListModel {
    private final Object[] data;

    public static <T> ArrayListModel<T> of(Collection<T> coll) {
        return new ArrayListModel<T>(coll.toArray(new Object[coll.size()]));
    }

    public static <T> ArrayListModel<T> of(T[] array) {
        return new ArrayListModel<T>(Arrays.copyOf(array, array.length));
    }

    private ArrayListModel(Object[] array) {
        data = array;
    }

    public int getSize() { return data.length; }

    @SuppressWarnings("unchecked")
    public T getElementAt(int index) { return (T) data[index]; }
}
