/*
 * Copyright 2018 John "topjohnwu" Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.topjohnwu.superuser;

import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

/**
 * An {@code AbstractList} that calls {@code onAddElement} when a new element is added to the list.
 * <p>
 * To simplify the API of {@link Shell}, both STDOUT and STDERR will output to
 * {@link List}. This class is useful if you want to trigger a callback
 * every time {@link Shell} outputs a new line: simply implement {@link #onAddElement(Object)}, and
 * provide an instance to the shell.
 * <p>
 * The {@code CallbackList} itself does not have a data store. If you need one, you can provide a
 * base {@link List}, and this class will delegate all of its calls to the base with synchronization;
 * it works just like a wrapper from {@link Collections#synchronizedList(List)}.
 * <p>
 * The method {@link #onAddElement(Object)} will always run on the main thread (UI thread).
 */

public abstract class CallbackList<E> extends AbstractList<E> {

    protected List<E> mBase = null;

    /**
     * Sole constructor.
     */
    protected CallbackList() { }

    /**
     * Creates a {@code CallbackList} that behaves just like {@code base} with synchronization.
     * @param base provides the data store and an actual implementation of the {@link List}
     */
    protected CallbackList(List<E> base) {
        mBase = Collections.synchronizedList(base);
    }

    /**
     * The callback when a new element is added.
     * <p>
     * This method will always run on the main thread and synchronized.
     * This method will be called after {@code add} is called.
     * @param e the new element added to the list.
     */
    public abstract void onAddElement(E e);

    /**
     * @see List#get(int)
     */
    @Override
    public E get(int i) {
        return mBase == null ? null : mBase.get(i);
    }

    /**
     * @see List#set(int, Object)
     */
    @Override
    public E set(int i, E s) {
        return mBase == null ? null : mBase.set(i, s);
    }

    /**
     * @see List#add(int, Object)
     */
    @Override
    public void add(int i, E s) {
        if (mBase != null)
            mBase.add(i, s);
        UiThreadHandler.runSynchronized(this, () -> onAddElement(s));
    }

    /**
     * @see List#remove(Object)
     */
    @Override
    public E remove(int i) {
        return mBase == null ? null : mBase.remove(i);
    }

    /**
     * @see List#size()
     */
    @Override
    public int size() {
        return mBase == null ? 0 : mBase.size();
    }
}
