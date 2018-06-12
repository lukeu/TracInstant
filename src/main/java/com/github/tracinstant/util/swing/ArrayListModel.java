package com.github.tracinstant.util.swing;

import java.util.Arrays;
import java.util.Collection;

import javax.swing.AbstractListModel;

public final class ArrayListModel<T> extends AbstractListModel<T> {
    private final Object[] data;

    public static <T> ArrayListModel<T> of(Collection<T> coll) {
        return new ArrayListModel<>(coll.toArray());
    }

    public static <T> ArrayListModel<T> of(T[] array) {
        return new ArrayListModel<>(Arrays.copyOf(array, array.length));
    }

    private ArrayListModel(Object[] array) {
        data = array;
    }

    @Override
    public int getSize() { return data.length; }

    @Override
    @SuppressWarnings("unchecked")
    public T getElementAt(int index) { return (T) data[index]; }
}
