/*
 * Copyright 2023 John "topjohnwu" Wu
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * An {@link AbstractList} that calls {@code onAddElement} when a new element is added to the list.
 * <p>
 * To simplify the API of {@link Shell}, both STDOUT and STDERR will output to {@link List}s.
 * This class is useful if you want to trigger a callback every time {@link Shell}
 * outputs a new line.
 * <p>
 * The {@code CallbackList} itself does not have a data store. If you need one, you can provide a
 * base {@link List}, and this class will delegate its calls to it.
 */

public abstract class CallbackList<E> extends AbstractList<E> {

    protected List<E> mBase;
    protected Executor mExecutor;

    /**
     * {@link #onAddElement(Object)} runs on the main thread; no backing list.
     */
    protected CallbackList() {
        this(UiThreadHandler.executor, null);
    }

    /**
     * {@link #onAddElement(Object)} runs on the main thread; sets a backing list.
     */
    protected CallbackList(@Nullable List<E> base) {
        this(UiThreadHandler.executor, base);
    }

    /**
     * {@link #onAddElement(Object)} runs with the executor; no backing list.
     */
    protected CallbackList(@NonNull Executor executor) {
        this(executor, null);
    }

    /**
     * {@link #onAddElement(Object)} runs with the executor; sets a backing list.
     */
    protected CallbackList(@NonNull Executor executor, @Nullable List<E> base) {
        mExecutor = executor;
        mBase = base;
    }

    /**
     * The callback when a new element is added.
     * <p>
     * This method will be called after {@code add} is called.
     * Which thread it runs on depends on which constructor is used to construct the instance.
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
        mExecutor.execute(() -> onAddElement(s));
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
