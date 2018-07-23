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

package com.topjohnwu.superuser.internal;

import com.topjohnwu.superuser.Shell;

import java.io.InputStream;

abstract class PlaceHolderJob extends JobImpl {

    private boolean isSU;

    private PlaceHolderJob(boolean su) {
        isSU = su;
        to(NOPList.getInstance());
    }

    protected abstract void setTask(ShellImpl shell);

    @Override
    public Shell.Result exec() {
        ShellImpl shell = (ShellImpl) Shell.getShell();
        if (isSU && !shell.isRoot())
            return new ResultImpl();
        setTask(shell);
        return super.exec();
    }

    @Override
    public void submit(Shell.ResultCallback cb) {
        Shell.getShell(shell -> {
            if (isSU && !shell.isRoot() && cb != null) {
                cb.onResult(new ResultImpl());
                return;
            }
            setTask((ShellImpl) shell);
            super.submit();
        });
    }

    static class CmdPlaceHolderJob extends PlaceHolderJob {

        private String[] cmds;

        CmdPlaceHolderJob(boolean su, String... cmds) {
            super(su);
            this.cmds = cmds;
        }

        @Override
        protected void setTask(ShellImpl shell) {
            task = shell.newOutputGobblingTask(cmds);
        }
    }

    static class IsPlaceHolderJob extends PlaceHolderJob {

        private InputStream in;

        IsPlaceHolderJob(boolean su, InputStream in) {
            super(su);
            this.in = in;
        }

        @Override
        protected void setTask(ShellImpl shell) {
            task = shell.newOutputGobblingTask(in);
        }
    }
}
