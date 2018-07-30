package com.topjohnwu.superuser.rxjava2

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single

/**
 * Static object to provide RxJava2 wrappers for {@link com.topjohnwu.superuser.Shell.Job}
 * <p>
 * Usage patterns:
 * <p>
 * <b>Java:</b>
 * RxShell.asCompletable(Shell.sh("some command")).subscribe();
 * <p>
 * <b>Kotlin:</b>
 * Shell.sh("some command").asCompletable().subscribe()
 */
object RxShell {
    /**
     * Transforms a {@link com.topjohnwu.superuser.Shell.Job} into Completable that will emit
     * completion event after the job has been executed in the background.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@link Completable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @return Completable instance
     */
    @JvmStatic
    fun Shell.Job.asCompletable(): Completable = Completable.create { emitter ->
        this.submit {
            emitter.onComplete()
        }
    }

    /**
     * Transforms a {@link com.topjohnwu.superuser.Shell.Job} into Observable that will emit
     * a sequence of output lines of the job's execution.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@link Observable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * NOTE: {@link com.topjohnwu.superuser.Shell.Result} will not be returned
     * @return Observable instance
     */
    @JvmStatic
    fun Shell.Job.asObservable(): Observable<String> = Observable.create { emitter ->
        val callback = object : CallbackList<String>() {
            override fun onAddElement(e: String) {
                emitter.onNext(e)
            }
        }
        this.to(callback)
                .submit {
                    emitter.onComplete()
                }
    }

    /**
     * Transforms a {@link com.topjohnwu.superuser.Shell.Job} into Single that will emit
     * a {@link com.topjohnwu.superuser.Shell.Result}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@link Single} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * @return Single instance
     */
    @JvmStatic
    fun Shell.Job.asSingle(): Single<Shell.Result> = Single.create { emitter ->
        this.submit {
            emitter.onSuccess(it)
        }
    }
}