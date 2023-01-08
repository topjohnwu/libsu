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

package com.topjohnwu.superuser.internal;

import android.os.IBinder;
import android.os.RemoteException;

abstract class BinderHolder implements IBinder.DeathRecipient {

    private final IBinder binder;

    BinderHolder(IBinder b) throws RemoteException {
        binder = b;
        binder.linkToDeath(this, 0);
    }

    @Override
    public final void binderDied() {
        binder.unlinkToDeath(this, 0);
        UiThreadHandler.run(this::onBinderDied);
    }

    protected abstract void onBinderDied();
}
