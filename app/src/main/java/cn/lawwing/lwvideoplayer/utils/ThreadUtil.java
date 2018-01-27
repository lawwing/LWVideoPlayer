package cn.lawwing.lwvideoplayer.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by ky on 2017/12/26.
 */

public class ThreadUtil {
    private static final String TAG = "ThreadUtil";
    private static ExecutorService executorService;


    private ThreadUtil() {
        executorService = new ThreadPoolExecutor(2, 5, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    }

    private static class ThreadUtilHolder {
        private static final ThreadUtil INSTANCE = new ThreadUtil();
    }

    public static ThreadUtil getInstance() {
        return ThreadUtilHolder.INSTANCE;
    }

    public void execute(Runnable runnable) {
        if (executorService != null && runnable != null) {
            executorService.execute(runnable);
        }
    }


}
