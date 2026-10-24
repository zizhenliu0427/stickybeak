package au.com.stickybeak.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在当前事务提交后执行操作；没有活动事务时立即执行。
 * 用于远程调用、Redis 写入等无法随数据库回滚的副作用：先提交，再执行，
 * 避免"数据库回滚了，副作用却已经发生"。
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
