package com.topjohnwu.superuser.rxjava2

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single

object RxShell {
    @JvmStatic
    fun Shell.Job.asCompletable(): Completable = Completable.create { emitter ->
        this.submit {
            emitter.onComplete()
        }
    }

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

    @JvmStatic
    fun Shell.Job.asSingle(): Single<Shell.Result> = Single.create { emitter ->
        this.submit {
            emitter.onSuccess(it)
        }
    }
}