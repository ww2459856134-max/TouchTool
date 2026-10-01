package top.bogey.touch_tool.utils.thread;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

public class TaskQueue<T> extends LinkedBlockingQueue<T> {

    private TaskThreadPoolExecutor executor;

    public TaskQueue(int capacity) {
        super(capacity);
    }

    public void setExecutor(TaskThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @Override
    public boolean offer(T t) {
        if (executor == null) throw new RejectedExecutionException("TaskQueue not attached to a TaskThreadPoolExecutor");

        int currPoolSize = executor.getPoolSize();
        if (executor.getSubmittedTaskCount() < currPoolSize) {
            return super.offer(t);
        }

        if (currPoolSize < executor.getMaximumPoolSize()) return false;

        return super.offer(t);
    }
}
